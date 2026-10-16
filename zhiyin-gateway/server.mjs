import { createServer } from 'node:http';

const ORIGIN = 'https://hqjwhucxpafwpolubhdy.supabase.co';
const PUBLIC_KEY = 'sb_publishable_7ri__qZ4EUYI4bbrrGJBUA_-nVfU5JT';
const routes = /^\/(?:auth|rest|storage|functions)\/v1(?:\/|$)/;
const methods = new Set(['GET', 'POST', 'PATCH', 'PUT', 'DELETE', 'HEAD']);
const permittedRequestHeaders = new Set([
  'apikey', 'authorization', 'content-type', 'accept', 'prefer',
  'x-client-info', 'range', 'content-range', 'if-match', 'if-none-match',
  'x-upsert', 'accept-profile', 'content-profile'
]);
const permittedResponseHeaders = [
  'content-type', 'content-disposition', 'cache-control', 'etag',
  'last-modified', 'content-range', 'accept-ranges', 'location',
  'content-profile', 'content-location', 'x-total-count'
];
const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET,POST,PUT,PATCH,DELETE,HEAD,OPTIONS',
  'Access-Control-Allow-Headers': [...permittedRequestHeaders].join(','),
  'Access-Control-Expose-Headers': 'content-range,content-profile,content-location,x-total-count,etag',
  'Cache-Control': 'no-store'
};
function json(res, status, body) {
  res.writeHead(status, { ...cors, 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(body));
}
function readBody(req, maxBytes = 7_500_000) {
  return new Promise((resolve, reject) => {
    let n = 0; const chunks = [];
    req.on('data', c => {
      n += c.length;
      if (n > maxBytes) {
        reject(new Error('payload_too_large')); req.destroy();
      } else chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}
const server = createServer(async (req, res) => {
  const pathname = new URL(req.url || '/', 'http://127.0.0.1').pathname;
  if (req.method === 'OPTIONS') {res.writeHead(204, cors);res.end();return;}
  if (pathname === '/health' && req.method === 'GET') {
    try {
      const r = await fetch(ORIGIN + '/auth/v1/health', {
        headers: { apikey: PUBLIC_KEY }, signal: AbortSignal.timeout(12_000)
      });
      const data = await r.json();
      return json(res, r.ok && data?.name === 'GoTrue' ? 200 : 503,
        { healthy: r.ok && data?.name === 'GoTrue', service: 'zhiyin-api-gateway' });
    } catch {return json(res, 503, { healthy: false });}
  }
  if (!methods.has(req.method) || !routes.test(pathname) ||
      pathname.startsWith('/auth/v1/admin') ||
      pathname.startsWith('/storage/v1/admin')) {
    return json(res, 404, { code: 'not_found' });
  }
  if (req.headers.apikey !== PUBLIC_KEY) {
    return json(res, 401, { code: 'invalid_public_key' });
  }
  const target = new URL(req.url, ORIGIN);
  if (target.origin !== ORIGIN || target.pathname !== pathname) {
    return json(res, 403, { code: 'invalid_route' });
  }
  const requestHeaders = new Headers();
  for (const name of permittedRequestHeaders) {
    const val = req.headers[name];
    if (typeof val === 'string' && val.length < 8192) requestHeaders.set(name, val);
  }
  try {
    const body = req.method === 'GET' || req.method === 'HEAD'
      ? undefined : await readBody(req);
    const upstream = await fetch(target, {
      method: req.method, headers: requestHeaders,
      body: body?.length ? body : undefined,
      redirect: 'manual', signal: AbortSignal.timeout(25_000)
    });
    const type = upstream.headers.get('content-type') || '';
    // A server/WAF HTML document must never be treated as an account result.
    if (pathname.startsWith('/auth/v1/') &&
        ![301,302,303,307,308].includes(upstream.status) &&
        !type.toLowerCase().includes('application/json')) {
      await upstream.arrayBuffer();
      return json(res, 502, { code: 'auth_upstream_not_json' });
    }
    const headers = { ...cors };
    for (const name of permittedResponseHeaders) {
      const val = upstream.headers.get(name);
      if (val && name !== 'location') headers[name] = val;
      if (name === 'location' && val) {
        // Supabase auth redirect is only allowed back to the pinned origin.
        try {if (new URL(val, ORIGIN).origin === ORIGIN) headers[name] = val;}
        catch {}
      }
    }
    // Response is buffered to avoid mismatched transfer-length headers.
    const content = Buffer.from(await upstream.arrayBuffer());
    res.writeHead(upstream.status, headers);
    res.end(content);
  } catch {
    return json(res, 503, { code: 'backend_unavailable' });
  }
});
// Privacy: do not add an access logger or print auth headers or request bodies.
server.listen(Number(process.env.PORT || 10000), '0.0.0.0');
