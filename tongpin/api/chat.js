import { handler } from '../server.js';

export default async function chat(req, res) {
  try {
    let body;
    if (typeof req.body === 'string') body = req.body;
    else if (Buffer.isBuffer(req.body)) body = req.body.toString('utf8');
    else if (req.body !== undefined) body = JSON.stringify(req.body);
    else {
      const chunks = []; let length = 0;
      for await (const chunk of req) {
        const b = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
        length += b.length;
        if (length > 24000) { res.status(413).json({ok:false,error:'请求过大'}); return; }
        chunks.push(b);
      }
      body = Buffer.concat(chunks).toString('utf8');
    }
    const request = new Request('https://local.invalid/api/chat', {
      method: req.method,
      headers: {...req.headers,'content-type':'application/json'},
      body: req.method === 'POST' ? body : undefined,
    });
    const answer = await handler(request);
    for (const [name,value] of answer.headers) res.setHeader(name,value);
    res.statusCode = answer.status;
    res.end(Buffer.from(await answer.arrayBuffer()));
  } catch {
    if (!res.headersSent) res.status(500).json({ok:false,error:'服务端内部错误'});
  }
}
