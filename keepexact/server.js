import http from 'node:http';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const publicDir = path.join(__dirname, 'public');
const port = Number(process.env.PORT || 3000);
const MAX_JSON_BYTES = 28 * 1024 * 1024;
const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
const ALLOWED_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);

export const auditSchema = {
  type: 'object',
  additionalProperties: false,
  properties: {
    verdict: { type: 'string', enum: ['PASS', 'REVIEW', 'FAIL'] },
    score: { type: 'integer', minimum: 0, maximum: 100 },
    summary: { type: 'string' },
    requested_changes: { type: 'array', items: { type: 'object', additionalProperties: false, properties: { item: { type: 'string' }, status: { type: 'string', enum: ['done', 'partial', 'not_done', 'unclear'] }, evidence: { type: 'string' } }, required: ['item', 'status', 'evidence'] } },
    unintended_changes: { type: 'array', items: { type: 'object', additionalProperties: false, properties: { severity: { type: 'string', enum: ['low', 'medium', 'high'] }, area: { type: 'string' }, change: { type: 'string' }, why_it_matters: { type: 'string' } }, required: ['severity', 'area', 'change', 'why_it_matters'] } },
    repair_prompt: { type: 'string' }
  },
  required: ['verdict', 'score', 'summary', 'requested_changes', 'unintended_changes', 'repair_prompt']
};

export function buildAuditPrompt(instruction) {
  return `You are KeepExact, an independent quality-control verifier for AI image edits.\n\nThe user gave an image editor this instruction:\n\"\"\"${instruction}\"\"\"\n\nImage 1 is BEFORE (original).\nImage 2 is AFTER (edited).\n\nYour job:\n1. Decompose the user's instruction into concrete requested changes.\n2. Check whether each requested change was completed.\n3. Compare BEFORE and AFTER for material changes the user did NOT request. Check identity/face, body/pose, hairstyle, clothing, accessories, objects, text/logos/numbers, composition/crop, background structure, colors, lighting, and other visible details when relevant.\n4. Do not flag necessary local side effects that are clearly required to perform the requested edit, unless they materially alter unrelated content.\n5. Use PASS only when the requested edit is complete and you see no material unintended change. Use FAIL when a clear material unintended change exists or a requested change clearly failed. Use REVIEW when the evidence is genuinely ambiguous.\n6. The score is instruction compliance, not visual quality.\n7. The repair prompt must be directly reusable with an image editor. It must state what to fix and explicitly preserve unaffected content. If verdict is PASS, repair_prompt should say no repair is needed.\n8. Be conservative about claiming tiny details you cannot reliably see; use REVIEW rather than inventing certainty.`;
}

function parseDataUrl(value) {
  if (typeof value !== 'string') return { error: 'Image data is missing.' };
  const match = /^data:(image\/(?:jpeg|png|webp));base64,([A-Za-z0-9+/=]+)$/.exec(value);
  if (!match || !ALLOWED_TYPES.has(match[1])) return { error: 'Use a JPG, PNG, or WEBP image.' };
  const bytes = Buffer.from(match[2], 'base64').length;
  if (bytes === 0) return { error: 'The selected image is empty.' };
  if (bytes > MAX_IMAGE_BYTES) return { error: 'Each image must be 10 MB or smaller.' };
  return { mime: match[1], bytes, dataUrl: value };
}

export function validateAuditBody(body) {
  const instruction = String(body?.instruction || '').trim();
  if (!instruction) return { error: 'Enter the exact edit instruction you gave the AI.' };
  if (instruction.length > 4000) return { error: 'The edit instruction is too long (maximum 4,000 characters).' };
  const original = parseDataUrl(body?.original);
  if (original.error) return { error: original.error === 'Image data is missing.' ? 'Upload the original image.' : `Original: ${original.error}` };
  const edited = parseDataUrl(body?.edited);
  if (edited.error) return { error: edited.error === 'Image data is missing.' ? 'Upload the edited image.' : `Edited: ${edited.error}` };
  return { instruction, original, edited };
}

async function runAudit({ instruction, original, edited }) {
  if (!process.env.OPENAI_API_KEY) { const error = new Error('Live AI verification is not configured yet.'); error.code = 'NO_API_KEY'; throw error; }
  const apiResponse = await fetch('https://api.openai.com/v1/responses', {
    method: 'POST', headers: { 'Authorization': `Bearer ${process.env.OPENAI_API_KEY}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      model: process.env.OPENAI_MODEL || 'gpt-5.6-luna', reasoning: { effort: 'low' }, max_output_tokens: 1400,
      input: [{ role: 'user', content: [ { type: 'input_text', text: buildAuditPrompt(instruction) }, { type: 'input_image', image_url: original.dataUrl, detail: 'high' }, { type: 'input_image', image_url: edited.dataUrl, detail: 'high' } ] }],
      text: { format: { type: 'json_schema', name: 'keepexact_edit_audit', strict: true, schema: auditSchema } }
    })
  });
  const payload = await apiResponse.json().catch(() => ({}));
  if (!apiResponse.ok) { const error = new Error(payload?.error?.message || `OpenAI API error ${apiResponse.status}`); error.status = apiResponse.status; throw error; }
  const text = payload.output?.flatMap(item => item.content || []).find(item => item.type === 'output_text')?.text;
  if (!text) throw new Error('The AI service returned an empty result.');
  return JSON.parse(text);
}

function json(res, status, payload) { const body = JSON.stringify(payload); res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body), 'Cache-Control': 'no-store' }); res.end(body); }
async function readJson(req) { return new Promise((resolve, reject) => { let size = 0; const chunks = []; req.on('data', chunk => { size += chunk.length; if (size > MAX_JSON_BYTES) { const error = new Error('Request too large.'); error.code = 'TOO_LARGE'; reject(error); req.destroy(); return; } chunks.push(chunk); }); req.on('end', () => { try { resolve(JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}')); } catch { reject(new Error('Invalid JSON.')); } }); req.on('error', reject); }); }
const MIME = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.ico': 'image/x-icon' };
async function serveStatic(req, res) { const url = new URL(req.url, 'http://localhost'); const requested = url.pathname === '/' ? '/index.html' : url.pathname; const normalized = path.normalize(requested).replace(/^(\.\.[/\\])+/, ''); const filePath = path.join(publicDir, normalized); if (!filePath.startsWith(publicDir)) return false; try { const data = await readFile(filePath); const ext = path.extname(filePath).toLowerCase(); res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream', 'Content-Length': data.length, 'Cache-Control': process.env.NODE_ENV === 'production' ? 'public, max-age=3600' : 'no-cache' }); res.end(data); return true; } catch { return false; } }

export async function requestHandler(req, res) {
  const url = new URL(req.url, 'http://localhost');
  if (req.method === 'GET' && url.pathname === '/health') return json(res, 200, { ok: true, service: 'keepexact' });
  if (req.method === 'POST' && url.pathname === '/api/audit') {
    try {
      if (!String(req.headers['content-type'] || '').startsWith('application/json')) return json(res, 415, { error: 'Unsupported request format.' });
      const body = await readJson(req); const validated = validateAuditBody(body); if (validated.error) return json(res, 400, { error: validated.error });
      return json(res, 200, await runAudit(validated));
    } catch (error) {
      if (error?.code === 'TOO_LARGE') return json(res, 413, { error: 'The upload is too large.' });
      if (error?.code === 'NO_API_KEY') return json(res, 503, { error: 'Live AI verification is not configured yet.' });
      console.error('audit_failed', error?.status || '', error?.message || error);
      return json(res, 502, { error: 'Verification failed. Your selected images are still on this device; retry in a moment.' });
    }
  }
  if (req.method === 'GET' && await serveStatic(req, res)) return;
  json(res, 404, { error: 'Not found.' });
}

export const app = http.createServer(requestHandler);
const isMain = process.argv[1] && path.resolve(process.argv[1]) === __filename;
if (isMain) app.listen(port, '0.0.0.0', () => console.log(`KeepExact listening on http://0.0.0.0:${port}`));
