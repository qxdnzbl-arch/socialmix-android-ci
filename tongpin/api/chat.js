import { handler } from '../server.js';

export default async function chat(req, res) {
  const body = typeof req.body === 'string' ? req.body : JSON.stringify(req.body ?? {});
  const request = new Request('https://local.invalid/api/chat', {
    method: req.method,
    headers: { ...req.headers, 'content-type': 'application/json' },
    body: req.method === 'POST' ? body : undefined,
  });
  const answer = await handler(request);
  for (const [name, value] of answer.headers) res.setHeader(name, value);
  res.statusCode = answer.status;
  res.end(Buffer.from(await answer.arrayBuffer()));
}
