const express = require('express');
const Busboy = require('busboy');
const archiver = require('archiver');
const QRCode = require('qrcode');
const crypto = require('crypto');
const http = require('http');
const path = require('path');

const SESSION_TTL_MS = 15 * 60 * 1000;
const CLEANUP_INTERVAL_MS = 60 * 1000;
const INSTANT_ID_RE = /^[a-f0-9]{32}$/i;

function randomCode(existing) {
  for (let i = 0; i < 30; i++) {
    const code = String(crypto.randomInt(0, 1000000)).padStart(6, '0');
    if (!existing.has(code)) return code;
  }
  throw new Error('暂时无法生成取件码');
}

function safeName(name, usedNames) {
  let base = String(name || 'file')
    .replace(/[\\/\r\n\0]/g, '_')
    .replace(/^\.+$/, 'file')
    .trim() || 'file';

  let candidate = base;
  let n = 2;
  while (usedNames.has(candidate)) {
    const dot = base.lastIndexOf('.');
    if (dot > 0) candidate = `${base.slice(0, dot)} (${n})${base.slice(dot)}`;
    else candidate = `${base} (${n})`;
    n += 1;
  }
  usedNames.add(candidate);
  return candidate;
}

function newSession(code, senderToken = null) {
  return {
    code,
    senderToken,
    createdAt: Date.now(),
    lastActivity: Date.now(),
    receiverRes: null,
    receiverClosed: false,
    senderActive: false,
    senderFinished: false,
    archive: null,
    senderReq: null,
    totalFiles: 0,
    usedNames: new Set()
  };
}

function createApp() {
  const app = express();
  const sessions = new Map();

  app.set('trust proxy', 1);
  app.disable('x-powered-by');
  app.use(express.json({ limit: '32kb' }));
  app.use(express.static(path.join(__dirname, 'public'), { maxAge: 0, etag: false }));

  function isExpired(session) {
    return !session.senderActive && Date.now() - session.lastActivity > SESSION_TTL_MS;
  }

  function statusPayload(session) {
    return {
      receiverReady: !!session.receiverRes && !session.receiverClosed,
      senderActive: session.senderActive,
      senderFinished: session.senderFinished,
      totalFiles: session.totalFiles
    };
  }

  function openReceiver(session, res) {
    if (session.receiverRes && !session.receiverClosed) {
      return res.status(409).send('这个接收链接已经有设备连接');
    }
    if (session.senderFinished) {
      return res.status(410).send('这次传输已经结束，请重新扫码');
    }

    session.lastActivity = Date.now();
    session.receiverRes = res;
    session.receiverClosed = false;

    res.status(200);
    res.set({
      'Content-Type': 'application/zip',
      'Content-Disposition': 'attachment; filename="OPPO-to-iPhone.zip"',
      'Cache-Control': 'no-store, no-cache, must-revalidate',
      'Pragma': 'no-cache',
      'X-Content-Type-Options': 'nosniff',
      'Connection': 'keep-alive'
    });
    res.flushHeaders();

    res.on('close', () => {
      session.receiverClosed = true;
      session.receiverRes = null;
      session.lastActivity = Date.now();
      if (session.archive && !session.senderFinished) {
        try { session.archive.abort(); } catch (_) {}
      }
      if (session.senderReq && !session.senderReq.destroyed) {
        try { session.senderReq.destroy(new Error('接收端已断开')); } catch (_) {}
      }
    });
  }

  function sendToReceiver(session, req, res, allowTokenClaim) {
    const suppliedToken = req.get('x-sender-token') || '';
    if (!suppliedToken) {
      return res.status(403).json({ error: '发送端验证失败' });
    }
    if (allowTokenClaim && !session.senderToken) {
      session.senderToken = suppliedToken;
    }
    if (suppliedToken !== session.senderToken) {
      return res.status(403).json({ error: '发送端验证失败' });
    }
    if (!session.receiverRes || session.receiverClosed) {
      return res.status(409).json({ error: '请先让 iPhone 扫二维码并打开接收链接' });
    }
    if (session.senderActive || session.senderFinished) {
      return res.status(409).json({ error: '这次传输已经开始或结束' });
    }
    if (!/^multipart\/form-data/i.test(req.headers['content-type'] || '')) {
      return res.status(400).json({ error: '上传格式错误' });
    }

    session.senderActive = true;
    session.senderReq = req;
    session.lastActivity = Date.now();

    const receiver = session.receiverRes;
    const archive = archiver('zip', {
      zlib: { level: 0 },
      forceZip64: true
    });
    session.archive = archive;

    archive.on('warning', (err) => {
      if (err.code !== 'ENOENT') console.error('archive warning', err);
    });
    archive.on('error', (err) => {
      console.error('archive error', err);
      try { receiver.destroy(err); } catch (_) {}
      if (!res.headersSent) res.status(500).json({ error: '打包失败' });
      else res.end();
    });
    archive.pipe(receiver, { end: true });

    let bb;
    try {
      bb = Busboy({
        headers: req.headers,
        defParamCharset: 'utf8',
        limits: { files: 10000, fields: 20, parts: 10020 }
      });
    } catch (e) {
      session.senderActive = false;
      session.archive = null;
      return res.status(400).json({ error: '无法读取上传内容' });
    }

    let filesSeen = 0;
    let failed = false;

    bb.on('file', (fieldname, file, info) => {
      filesSeen += 1;
      session.totalFiles = filesSeen;
      session.lastActivity = Date.now();
      const filename = safeName(info && info.filename, session.usedNames);
      archive.append(file, { name: filename, store: true });
      file.on('data', () => {
        session.lastActivity = Date.now();
      });
      file.on('error', (err) => {
        failed = true;
        console.error('upload file stream error', err);
        try { archive.abort(); } catch (_) {}
      });
    });

    bb.on('filesLimit', () => {
      failed = true;
      try { archive.abort(); } catch (_) {}
    });

    bb.on('error', (err) => {
      failed = true;
      console.error('busboy error', err);
      try { archive.abort(); } catch (_) {}
      if (!res.headersSent) res.status(500).json({ error: '上传中断' });
      else res.end();
    });

    bb.on('close', async () => {
      if (failed) {
        session.senderActive = false;
        return;
      }
      if (filesSeen === 0) {
        try { archive.abort(); } catch (_) {}
        session.senderActive = false;
        return res.status(400).json({ error: '没有收到文件' });
      }
      try {
        await archive.finalize();
        session.senderFinished = true;
        session.senderActive = false;
        session.lastActivity = Date.now();
        res.json({ ok: true, files: filesSeen });
        setTimeout(() => sessions.delete(session.code), 60 * 1000).unref();
      } catch (e) {
        session.senderActive = false;
        if (!res.headersSent) res.status(500).json({ error: '结束传输失败' });
      }
    });

    req.on('aborted', () => {
      if (!session.senderFinished) {
        session.senderActive = false;
        try { archive.abort(); } catch (_) {}
      }
    });

    req.pipe(bb);
  }

  app.post('/api/create', (req, res) => {
    try {
      const code = randomCode(sessions);
      const senderToken = crypto.randomBytes(24).toString('hex');
      sessions.set(code, newSession(code, senderToken));
      res.json({ code, senderToken, expiresInSeconds: Math.floor(SESSION_TTL_MS / 1000) });
    } catch (e) {
      res.status(500).json({ error: e.message || '创建失败' });
    }
  });

  app.get('/api/qr/:code', async (req, res) => {
    const session = sessions.get(req.params.code);
    if (!session || isExpired(session)) {
      return res.status(404).send('取件码不存在或已过期');
    }
    session.lastActivity = Date.now();
    try {
      const origin = `${req.protocol}://${req.get('host')}`;
      const receiveUrl = `${origin}/receive/${encodeURIComponent(session.code)}`;
      const svg = await QRCode.toString(receiveUrl, {
        type: 'svg',
        errorCorrectionLevel: 'M',
        margin: 2,
        width: 420
      });
      res.set({
        'Content-Type': 'image/svg+xml; charset=utf-8',
        'Cache-Control': 'no-store, no-cache, must-revalidate'
      });
      res.send(svg);
    } catch (e) {
      res.status(500).send('二维码生成失败');
    }
  });

  app.get('/api/status/:code', (req, res) => {
    const session = sessions.get(req.params.code);
    if (!session || isExpired(session)) {
      return res.status(404).json({ error: '取件码不存在或已过期' });
    }
    session.lastActivity = Date.now();
    res.set('Cache-Control', 'no-store');
    res.json(statusPayload(session));
  });

  app.get('/receive/:code', (req, res) => {
    const session = sessions.get(req.params.code);
    if (!session || isExpired(session)) {
      return res.status(404).send('取件码不存在或已过期');
    }
    return openReceiver(session, res);
  });

  app.post('/send/:code', (req, res) => {
    const session = sessions.get(req.params.code);
    if (!session || isExpired(session)) {
      return res.status(404).json({ error: '取件码不存在或已过期' });
    }
    return sendToReceiver(session, req, res, false);
  });

  app.get('/api/instant/status/:id', (req, res) => {
    const id = req.params.id;
    if (!INSTANT_ID_RE.test(id)) {
      return res.status(400).json({ error: '无效的二维码' });
    }
    const session = sessions.get(id);
    if (!session || isExpired(session)) {
      return res.status(404).json({ error: '等待接收设备' });
    }
    session.lastActivity = Date.now();
    res.set('Cache-Control', 'no-store');
    res.json(statusPayload(session));
  });

  app.get('/instant/receive/:id', (req, res) => {
    const id = req.params.id;
    if (!INSTANT_ID_RE.test(id)) {
      return res.status(400).send('二维码无效');
    }
    let session = sessions.get(id);
    if (!session || isExpired(session)) {
      session = newSession(id, null);
      sessions.set(id, session);
    }
    return openReceiver(session, res);
  });

  app.post('/instant/send/:id', (req, res) => {
    const id = req.params.id;
    if (!INSTANT_ID_RE.test(id)) {
      return res.status(400).json({ error: '二维码无效' });
    }
    const session = sessions.get(id);
    if (!session || isExpired(session)) {
      return res.status(409).json({ error: '请先让 iPhone 扫二维码并打开接收链接' });
    }
    return sendToReceiver(session, req, res, true);
  });

  app.get('/health', (req, res) => {
    res.type('text/plain').send('ok');
  });

  const cleanupTimer = setInterval(() => {
    const now = Date.now();
    for (const [code, session] of sessions) {
      if (!session.senderActive && now - session.lastActivity > SESSION_TTL_MS) {
        if (session.receiverRes) {
          try { session.receiverRes.end(); } catch (_) {}
        }
        if (session.archive && !session.senderFinished) {
          try { session.archive.abort(); } catch (_) {}
        }
        sessions.delete(code);
      }
    }
  }, CLEANUP_INTERVAL_MS);
  cleanupTimer.unref();

  app.locals.sessions = sessions;
  return app;
}

function startServer(port = process.env.PORT || 3000) {
  const app = createApp();
  const server = http.createServer(app);
  server.requestTimeout = 0;
  server.headersTimeout = 65 * 1000;
  server.keepAliveTimeout = 65 * 1000;
  server.listen(port, '0.0.0.0');
  return { app, server };
}

if (require.main === module) {
  const { server } = startServer();
  server.on('listening', () => {
    const addr = server.address();
    console.log(`internet transfer relay listening on ${addr.port}`);
  });
}

module.exports = { createApp, startServer, safeName };
