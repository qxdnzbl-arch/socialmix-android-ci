import { handler } from '../server.js';

export default async function status(req, res) {
  const request = new Request('https://local.invalid/api/status', {
    method: req.method,
    headers: req.headers,
  });
  const answer = await handler(request);
  for (const [name, value] of answer.headers) res.setHeader(name, value);
  res.statusCode = answer.status;
  res.end(Buffer.from(await answer.arrayBuffer()));
}
