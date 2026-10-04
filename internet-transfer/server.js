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
const INSTANT_TOKEN_RE = /^[a-f0-9]{32,128}$/i;

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
    webSenderReady: false,
    archive: null,
    senderReq: null,
    totalFiles: 0,
    usedNames: new Set()
  };
}

function iphoneUploadPage(id, token) {
  const idJson = JSON.stringify(id);
  const tokenJson = JSON.stringify(token);
  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="color-scheme" content="light">
<title>发送到 Android</title>
<style>
  :root{--primary:#006a60;--on:#191c1b;--muted:#3f4946;--bg:#fafdfb;--panel:#f4f7f5;--line:#bec9c5;--soft:#eef2f0;--ok:#d8f5ed;}
  *{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
  body{margin:0;background:var(--bg);color:var(--on);font-family:-apple-system,BlinkMacSystemFont,"SF Pro Text","PingFang SC","Helvetica Neue",Arial,sans-serif}
  .wrap{max-width:560px;margin:0 auto;padding:calc(env(safe-area-inset-top) + 26px) 20px calc(env(safe-area-inset-bottom) + 24px)}
  .brand{font-size:20px;font-weight:650;letter-spacing:.01em;margin-bottom:30px}
  h1{font-size:28px;line-height:1.25;margin:0;font-weight:720;letter-spacing:-.02em}
  .sub{font-size:15px;color:var(--muted);margin-top:8px;line-height:1.5}
  .card{margin-top:26px;background:var(--panel);border-radius:18px;padding:20px}
  .status{display:flex;align-items:center;gap:9px;font-size:14px;color:var(--muted);margin-bottom:18px}
  .dot{width:8px;height:8px;border-radius:50%;background:#8a9491}
  .status.ready .dot{background:var(--primary)}
  .picker{display:block;width:100%;border:1px solid var(--line);border-radius:14px;background:#fff;padding:18px;text-align:center;font-size:16px;font-weight:620;color:var(--on);cursor:pointer}
  input{position:absolute;width:1px;height:1px;opacity:0;pointer-events:none}
  .meta{min-height:22px;margin-top:12px;font-size:13px;color:var(--muted);text-align:center}
  button{width:100%;height:54px;border:0;border-radius:14px;margin-top:18px;background:var(--primary);color:white;font-size:16px;font-weight:650}
  button:disabled{background:#dfe6e3;color:#83908b}
  .bar{height:6px;background:#e3e9e6;border-radius:999px;overflow:hidden;margin-top:16px;display:none}
  .fill{height:100%;width:0;background:var(--primary);transition:width .12s linear}
  .done{margin-top:18px;padding:14px;border-radius:12px;background:var(--ok);color:#174e45;font-size:14px;display:none;text-align:center}
  .foot{font-size:12px;color:#75807c;text-align:center;margin-top:20px;line-height:1.5}
</style>
</head>
<body>
  <main class="wrap">
    <div class="brand">手机互传</div>
    <h1>发送到 Android</h1>
    <div class="sub">选择 iPhone 里的文件，直接发送到正在接收的 Android 手机。</div>
    <section class="card">
      <div class="status" id="status"><span class="dot"></span><span id="statusText">正在连接 Android…</span></div>
      <label class="picker" for="files">选择文件</label>
      <input id="files" type="file" multiple>
      <div class="meta" id="meta">还没有选择文件</div>
      <button id="send" disabled>发送</button>
      <div class="bar" id="bar"><div class="fill" id="fill"></div></div>
      <div class="done" id="done">发送完成，可以回到 Android 查看文件。</div>
    </section>
    <div class="foot">文件只用于本次实时传输，不在服务器长期保存。</div>
  </main>
<script>
(function(){
  var sessionId=${idJson};
  var senderToken=${tokenJson};
  var ready=false;
  var busy=false;
  var filesInput=document.getElementById('files');
  var sendBtn=document.getElementById('send');
  var status=document.getElementById('status');
  var statusText=document.getElementById('statusText');
  var meta=document.getElementById('meta');
  var bar=document.getElementById('bar');
  var fill=document.getElementById('fill');
  var done=document.getElementById('done');

  function updateButton(){ sendBtn.disabled=!ready || busy || !filesInput.files || filesInput.files.length===0; }
  function setReady(v){
    ready=v;
    if(v){ status.className='status ready'; statusText.textContent='Android 已连接'; }
    else { status.className='status'; statusText.textContent='正在连接 Android…'; }
    updateButton();
  }
  function formatBytes(bytes){
    if(bytes<1000) return bytes+' B';
    if(bytes<1000000) return (bytes/1000).toFixed(1)+' KB';
    if(bytes<1000000000) return (bytes/1000000).toFixed(1)+' MB';
    return (bytes/1000000000).toFixed(2)+' GB';
  }
  function poll(){
    fetch('/api/instant/status/'+sessionId,{cache:'no-store'})
      .then(function(r){ return r.ok ? r.json() : null; })
      .then(function(j){ if(j) setReady(!!j.receiverReady); })
      .catch(function(){ setReady(false); })
      .then(function(){ if(!busy && done.style.display!=='block') setTimeout(poll,800); });
  }

  filesInput.addEventListener('change',function(){
    done.style.display='none';
    fill.style.width='0%';
    var list=filesInput.files;
    if(!list || list.length===0){ meta.textContent='还没有选择文件'; updateButton(); return; }
    var total=0;
    for(var i=0;i<list.length;i++) total+=list[i].size || 0;
    meta.textContent=list.length+' 个文件 · '+formatBytes(total);
    updateButton();
  });

  sendBtn.addEventListener('click',function(){
    if(!ready || busy || !filesInput.files || filesInput.files.length===0) return;
    busy=true;
    updateButton();
    statusText.textContent='正在发送';
    bar.style.display='block';
    var fd=new FormData();
    for(var i=0;i<filesInput.files.length;i++) fd.append('files',filesInput.files[i],filesInput.files[i].name);
    var xhr=new XMLHttpRequest();
    xhr.open('POST','/instant/send/'+sessionId,true);
    xhr.setRequestHeader('x-sender-token',senderToken);
    xhr.upload.onprogress=function(e){ if(e.lengthComputable) fill.style.width=Math.round(e.loaded/e.total*100)+'%'; };
    xhr.onload=function(){
      busy=false;
      if(xhr.status>=200 && xhr.status<300){
        fill.style.width='100%';
        status.className='status ready';
        statusText.textContent='发送完成';
        done.style.display='block';
        sendBtn.disabled=true;
      }else{
        status.className='status';
        statusText.textContent='发送失败，请重试';
        updateButton();
      }
    };
    xhr.onerror=function(){ busy=false; status.className='status'; statusText.textContent='网络中断，请重试'; updateButton(); };
    xhr.send(fd);
  });

  poll();
})();
</script>
</body>
</html>`;
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
      webSenderReady: !!session.webSenderReady,
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
      'Content-Disposition': 'attachment; filename="Phone-Transfer.zip"',
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
      return res.status(409).json({ error: '请先让接收设备连接' });
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
      // Use DEFLATE framing at level 0 instead of STORED streaming entries.
      // Java/Android ZipInputStream can then read streamed entries whose sizes are unknown until the data descriptor arrives.
      archive.append(file, { name: filename, store: false });
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

  app.get('/instant/upload/:id', (req, res) => {
    const id = req.params.id;
    const token = String(req.query.t || '');
    if (!INSTANT_ID_RE.test(id) || !INSTANT_TOKEN_RE.test(token)) {
      return res.status(400).send('二维码无效');
    }

    let session = sessions.get(id);
    if (!session || isExpired(session)) {
      session = newSession(id, token);
      sessions.set(id, session);
    } else if (session.senderToken && session.senderToken !== token) {
      return res.status(403).send('二维码已失效');
    } else if (!session.senderToken) {
      session.senderToken = token;
    }

    session.webSenderReady = true;
    session.lastActivity = Date.now();
    res.set('Cache-Control', 'no-store, no-cache, must-revalidate');
    res.type('html').send(iphoneUploadPage(id, token));
  });

  app.post('/instant/send/:id', (req, res) => {
    const id = req.params.id;
    if (!INSTANT_ID_RE.test(id)) {
      return res.status(400).json({ error: '二维码无效' });
    }
    const session = sessions.get(id);
    if (!session || isExpired(session)) {
      return res.status(409).json({ error: '请先让接收设备连接' });
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