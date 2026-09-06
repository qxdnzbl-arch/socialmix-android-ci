const test = require('node:test');
const assert = require('node:assert/strict');
const unzipper = require('unzipper');
const { startServer } = require('../server');

async function waitForReady(base, code, instant = false, timeoutMs = 3000) {
  const start = Date.now();
  const path = instant ? `/api/instant/status/${code}` : `/api/status/${code}`;
  while (Date.now() - start < timeoutMs) {
    const r = await fetch(`${base}${path}`);
    if (r.ok) {
      const j = await r.json();
      if (j.receiverReady) return true;
    }
    await new Promise(r => setTimeout(r, 30));
  }
  return false;
}

test('streams multiple uploaded files into one ZIP for receiver', async (t) => {
  const { server } = startServer(0);
  await new Promise(resolve => server.once('listening', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const port = server.address().port;
  const base = `http://127.0.0.1:${port}`;

  const createRes = await fetch(`${base}/api/create`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: '{}'
  });
  assert.equal(createRes.status, 200);
  const { code, senderToken } = await createRes.json();
  assert.match(code, /^\d{6}$/);

  const receiverPromise = fetch(`${base}/receive/${code}`);
  assert.equal(await waitForReady(base, code), true);

  const fd = new FormData();
  fd.append('files', new Blob([Buffer.from('hello-one')]), '第一份.txt');
  fd.append('files', new Blob([Buffer.from('hello-two')]), 'second.txt');

  const sendRes = await fetch(`${base}/send/${code}`, {
    method: 'POST',
    headers: { 'x-sender-token': senderToken },
    body: fd
  });
  assert.equal(sendRes.status, 200);
  const sendJson = await sendRes.json();
  assert.equal(sendJson.files, 2);

  const receiverRes = await receiverPromise;
  assert.equal(receiverRes.status, 200);
  assert.match(receiverRes.headers.get('content-type'), /application\/zip/);
  const zipBuffer = Buffer.from(await receiverRes.arrayBuffer());
  assert.ok(zipBuffer.length > 100);

  const directory = await unzipper.Open.buffer(zipBuffer);
  const names = directory.files.map(f => f.path).sort();
  assert.deepEqual(names, ['second.txt', '第一份.txt'].sort());
  const first = directory.files.find(f => f.path === '第一份.txt');
  const second = directory.files.find(f => f.path === 'second.txt');
  assert.equal((await first.buffer()).toString(), 'hello-one');
  assert.equal((await second.buffer()).toString(), 'hello-two');
});

test('refuses sender until receiver is connected', async (t) => {
  const { server } = startServer(0);
  await new Promise(resolve => server.once('listening', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const base = `http://127.0.0.1:${server.address().port}`;

  const created = await (await fetch(`${base}/api/create`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}'
  })).json();

  const fd = new FormData();
  fd.append('files', new Blob([Buffer.from('x')]), 'x.txt');
  const r = await fetch(`${base}/send/${created.code}`, {
    method: 'POST', headers: { 'x-sender-token': created.senderToken }, body: fd
  });
  assert.equal(r.status, 409);
  const j = await r.json();
  assert.match(j.error, /iPhone/);
});

test('generates a QR image for the direct iPhone receive link', async (t) => {
  const { server } = startServer(0);
  await new Promise(resolve => server.once('listening', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const base = `http://127.0.0.1:${server.address().port}`;

  const created = await (await fetch(`${base}/api/create`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}'
  })).json();

  const qrRes = await fetch(`${base}/api/qr/${created.code}`);
  assert.equal(qrRes.status, 200);
  assert.match(qrRes.headers.get('content-type'), /image\/svg\+xml/);
  const svg = await qrRes.text();
  assert.match(svg, /<svg/);
  assert.ok(svg.length > 500);
});

test('instant QR session can be created by receiver and then streamed by sender', async (t) => {
  const { server } = startServer(0);
  await new Promise(resolve => server.once('listening', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const base = `http://127.0.0.1:${server.address().port}`;
  const id = '0123456789abcdef0123456789abcdef';
  const token = 'sender-secret-token';

  const before = await fetch(`${base}/api/instant/status/${id}`);
  assert.equal(before.status, 404);

  const receiverPromise = fetch(`${base}/instant/receive/${id}`);
  assert.equal(await waitForReady(base, id, true), true);

  const fd = new FormData();
  fd.append('files', new Blob([Buffer.from('instant-ok')]), '即时传输.txt');
  const sendRes = await fetch(`${base}/instant/send/${id}`, {
    method: 'POST',
    headers: { 'x-sender-token': token },
    body: fd
  });
  assert.equal(sendRes.status, 200);
  const sendJson = await sendRes.json();
  assert.equal(sendJson.ok, true);
  assert.equal(sendJson.files, 1);

  const receiverRes = await receiverPromise;
  assert.equal(receiverRes.status, 200);
  const zipBuffer = Buffer.from(await receiverRes.arrayBuffer());
  const directory = await unzipper.Open.buffer(zipBuffer);
  const file = directory.files.find(f => f.path === '即时传输.txt');
  assert.ok(file);
  assert.equal((await file.buffer()).toString(), 'instant-ok');
});

test('instant QR rejects malformed session ids', async (t) => {
  const { server } = startServer(0);
  await new Promise(resolve => server.once('listening', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const base = `http://127.0.0.1:${server.address().port}`;

  const r = await fetch(`${base}/instant/receive/not-valid`);
  assert.equal(r.status, 400);
});
