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
const EDIT_TYPES = new Set(['remove_object', 'recolor', 'replace_object', 'remove_person']);
const WINDOW_MS = 24 * 60 * 60 * 1000;
const PER_IP_AUDIT_LIMIT = Number(process.env.PER_IP_DAILY_LIMIT || 5);
const GLOBAL_AUDIT_LIMIT = Number(process.env.GLOBAL_DAILY_LIMIT || 200);
const PER_IP_EDIT_LIMIT = Number(process.env.PER_IP_EDIT_DAILY_LIMIT || 3);
const GLOBAL_EDIT_LIMIT = Number(process.env.GLOBAL_EDIT_DAILY_LIMIT || 40);
const ipAuditTimes = new Map();
const ipEditTimes = new Map();
let globalAuditTimes = [];
let globalEditTimes = [];

const SECURITY_HEADERS = {
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'no-referrer',
  'X-Frame-Options': 'DENY',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
  'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'"
};

export const auditSchema = {
  type: 'object', additionalProperties: false,
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
  return `You are KeepExact, an independent quality-control verifier for AI image edits.\n\nThe user gave an image editor this instruction:\n\"\"\"${instruction}\"\"\"\n\nImage 1 is BEFORE (original).\nImage 2 is AFTER (edited).\n\nYour job:\n1. Decompose the user's instruction into concrete requested changes.\n2. Check whether each requested change was completed.\n3. Compare BEFORE and AFTER for material changes the user did NOT request. Check identity/face, body/pose, hairstyle, clothing, accessories, objects, text/logos/numbers, composition/crop, background structure, colors, lighting, and other visible details when relevant.\n4. Inspect the edited region for obvious generative defects: warped edges, melted or repeated texture, broken anatomy, duplicated structures, corrupt text, implausible seams, or other visible AI artifacts. A clear defect is a FAIL even when the requested change happened.\n5. Do not flag necessary local side effects that are clearly required to perform the requested edit, unless they materially alter unrelated content.\n6. Use PASS only when the requested edit is complete, no material unintended change exists, and no obvious visible artifact remains. Use FAIL for a clear material error. Use REVIEW only when evidence is genuinely ambiguous.\n7. The score is instruction compliance and deliverability, not artistic taste.\n8. If verdict is PASS, repair_prompt should say no repair is needed. Otherwise state what must be fixed while preserving unaffected content.\n9. Keep the JSON concise: summary <= 35 words; each evidence/change/why_it_matters <= 25 words; repair_prompt <= 70 words.`;
}

function parseDataUrl(value) {
  if (typeof value !== 'string') return { error: 'Image data is missing.' };
  const match = /^data:(image\/(?:jpeg|png|webp));base64,([A-Za-z0-9+/=]+)$/.exec(value);
  if (!match || !ALLOWED_TYPES.has(match[1])) return { error: 'Use a JPG, PNG, or WEBP image.' };
  const buffer = Buffer.from(match[2], 'base64');
  if (!buffer.length) return { error: 'The selected image is empty.' };
  if (buffer.length > MAX_IMAGE_BYTES) return { error: 'Each image must be 10 MB or smaller.' };
  return { mime: match[1], bytes: buffer.length, dataUrl: value, buffer };
}

function parseMask(value) {
  if (typeof value !== 'string') return { error: 'Select the area to edit.' };
  const match = /^data:image\/png;base64,([A-Za-z0-9+/=]+)$/.exec(value);
  if (!match) return { error: 'The edit mask is invalid.' };
  const buffer = Buffer.from(match[1], 'base64');
  if (!buffer.length || buffer.length > MAX_IMAGE_BYTES) return { error: 'The edit mask is invalid.' };
  return { mime: 'image/png', buffer, dataUrl: value };
}

function sanitizePixelDiff(value) {
  if (!value || typeof value !== 'object') return null;
  const clamp01 = n => Math.max(0, Math.min(1, Number.isFinite(Number(n)) ? Number(n) : 0));
  return {
    suspicious: Boolean(value.suspicious),
    reasons: Array.isArray(value.reasons) ? value.reasons.slice(0, 6).map(x => String(x).slice(0, 120)) : [],
    changedFraction: clamp01(value.changedFraction),
    bboxFraction: clamp01(value.bboxFraction),
    significantComponents: Math.max(0, Math.min(50, Math.trunc(Number(value.significantComponents) || 0))),
    secondComponentFraction: clamp01(value.secondComponentFraction),
    dimensionMismatch: Boolean(value.dimensionMismatch)
  };
}

export function validateAuditBody(body) {
  const instruction = String(body?.instruction || '').trim();
  if (!instruction) return { error: 'Enter the exact edit instruction you gave the AI.' };
  if (instruction.length > 4000) return { error: 'The edit instruction is too long (maximum 4,000 characters).' };
  const original = parseDataUrl(body?.original);
  if (original.error) return { error: original.error === 'Image data is missing.' ? 'Upload the original image.' : `Original: ${original.error}` };
  const edited = parseDataUrl(body?.edited);
  if (edited.error) return { error: edited.error === 'Image data is missing.' ? 'Upload the edited image.' : `Edited: ${edited.error}` };
  return { instruction, original, edited, pixelDiff: sanitizePixelDiff(body?.pixelDiff) };
}

const UNSUPPORTED_EDIT_PATTERNS = [
  /换脸|改脸|脸型|五官|眼睛|鼻子|嘴唇|手部|手指|改手|发型|头发|文字|字体|logo|商标|水印|换背景|更换背景|整个背景|整张|全图|整体风格|风格化|构图|光影|多人|多个人/i,
  /\b(face\s*swap|change\s+face|facial|eyes?|nose|lips?|hands?|fingers?|hairstyle|hair|text|font|logo|watermark|replace\s+background|whole\s+image|entire\s+image|style\s+transfer|composition|lighting|multiple\s+people)\b/i
];

export function validateEditBody(body) {
  const instruction = String(body?.instruction || '').trim();
  const editType = String(body?.editType || '').trim();
  const regionFraction = Number(body?.regionFraction);
  if (!EDIT_TYPES.has(editType)) return { error: 'Choose one supported edit type.' };
  if (!instruction) return { error: 'Say exactly what you want changed in the selected area.' };
  if (instruction.length < 2 || instruction.length > 300) return { error: 'Keep the edit request to one short sentence.' };
  if (UNSUPPORTED_EDIT_PATTERNS.some(re => re.test(instruction))) return { error: 'This request is outside the current supported range. Keep it to one small object/person removal, one local color change, or one small-object replacement.' };
  if (/(同时|以及|并且|再把|然后再|\band\b|\balso\b)/i.test(instruction)) return { error: 'Only one edit is allowed per request. Select one target and make one change.' };
  if (!Number.isFinite(regionFraction) || regionFraction < 0.001) return { error: 'Draw a box around the single area you want changed.' };
  if (regionFraction > 0.35) return { error: 'The selected area is too large. This version only accepts a local edit covering at most 35% of the image.' };
  const original = parseDataUrl(body?.original);
  if (original.error) return { error: original.error === 'Image data is missing.' ? 'Upload the original image.' : `Original: ${original.error}` };
  const mask = parseMask(body?.mask);
  if (mask.error) return { error: mask.error };
  return { instruction, editType, regionFraction, original, mask };
}

function clientIp(req) {
  const forwarded = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim();
  return forwarded || req.socket.remoteAddress || 'unknown';
}

function takeSlot(req, map, globalTimes, perIpLimit, globalLimit) {
  const now = Date.now();
  const cutoff = now - WINDOW_MS;
  const trimmedGlobal = globalTimes.filter(t => t > cutoff);
  if (trimmedGlobal.length >= globalLimit) return { ok: false, globalTimes: trimmedGlobal, message: 'Today’s free beta capacity is full. Try again tomorrow.' };
  const ip = clientIp(req);
  const times = (map.get(ip) || []).filter(t => t > cutoff);
  if (times.length >= perIpLimit) {
    map.set(ip, times);
    return { ok: false, globalTimes: trimmedGlobal, message: `Free beta limit reached for this connection (${perIpLimit} attempts per 24 hours).` };
  }
  times.push(now);
  map.set(ip, times);
  trimmedGlobal.push(now);
  return { ok: true, globalTimes: trimmedGlobal };
}

function takeAuditSlot(req) {
  const r = takeSlot(req, ipAuditTimes, globalAuditTimes, PER_IP_AUDIT_LIMIT, GLOBAL_AUDIT_LIMIT);
  globalAuditTimes = r.globalTimes;
  return r;
}

function takeEditSlot(req) {
  const r = takeSlot(req, ipEditTimes, globalEditTimes, PER_IP_EDIT_LIMIT, GLOBAL_EDIT_LIMIT);
  globalEditTimes = r.globalTimes;
  return r;
}

async function callDeepSeek({ instruction, original, edited }) {
  const apiResponse = await fetch('https://api.deepseek.com/responses', {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${process.env.DEEPSEEK_API_KEY}`, 'Content-Type': 'application/json' },
    signal: AbortSignal.timeout(25000),
    body: JSON.stringify({
      model: process.env.DEEPSEEK_MODEL || 'deepseek-flash',
      reasoning: { effort: 'none' },
      temperature: 0,
      max_output_tokens: 1800,
      input: [{ role: 'user', content: [
        { type: 'input_text', text: buildAuditPrompt(instruction) },
        { type: 'input_image', image_url: original.dataUrl, detail: 'high' },
        { type: 'input_image', image_url: edited.dataUrl, detail: 'high' }
      ] }],
      text: { format: { type: 'json_schema', name: 'keepexact_edit_audit', schema: auditSchema } }
    })
  });
  const payload = await apiResponse.json().catch(() => ({}));
  if (!apiResponse.ok) {
    const error = new Error(payload?.error?.message || `DeepSeek API error ${apiResponse.status}`);
    error.status = apiResponse.status;
    throw error;
  }
  const text = payload.output?.flatMap(item => item.content || []).find(item => item.type === 'output_text')?.text;
  return { payload, text };
}

function applyPixelGuardrail(result, pixelDiff) {
  if (!pixelDiff?.suspicious || result.verdict !== 'PASS') return result;
  const reasons = pixelDiff.reasons.length ? pixelDiff.reasons.join('; ') : 'deterministic pixel comparison found a suspicious change pattern';
  return {
    ...result,
    verdict: 'REVIEW',
    score: Math.min(Number(result.score || 100), 84),
    summary: `Semantic check passed, but deterministic pixel comparison found a pattern that may indicate an extra edit: ${reasons}.`,
    unintended_changes: [
      ...(Array.isArray(result.unintended_changes) ? result.unintended_changes : []),
      { severity: 'medium', area: 'image-difference guardrail', change: reasons, why_it_matters: 'The visible changes extend beyond the pattern expected from this instruction.' }
    ],
    repair_prompt: 'Re-run the edit from the original image and change only the requested target. Preserve all unrelated regions exactly; verify the result again before use.'
  };
}

export async function runAudit({ instruction, original, edited, pixelDiff = null, includeMeta = false }) {
  if (!process.env.DEEPSEEK_API_KEY) {
    const error = new Error('Live AI verification is not configured yet.');
    error.code = 'NO_API_KEY';
    throw error;
  }
  let lastError = null;
  let totalInputTokens = 0;
  let totalOutputTokens = 0;
  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      const { payload, text } = await callDeepSeek({ instruction, original, edited });
      totalInputTokens += Number(payload?.usage?.input_tokens || 0);
      totalOutputTokens += Number(payload?.usage?.output_tokens || 0);
      if (payload?.status === 'incomplete') throw new Error(`DeepSeek response incomplete: ${payload?.incomplete_details?.reason || 'unknown reason'}`);
      if (!text) throw new Error('The AI service returned an empty result.');
      const result = applyPixelGuardrail(JSON.parse(text), pixelDiff);
      return includeMeta ? { result, usage: { input_tokens: totalInputTokens, output_tokens: totalOutputTokens, attempts: attempt }, pixelDiff } : result;
    } catch (error) {
      lastError = error;
      if (error?.status && error.status >= 400 && error.status < 500 && error.status !== 408 && error.status !== 429) break;
      if (attempt < 3) await new Promise(resolve => setTimeout(resolve, 300 * attempt));
    }
  }
  throw lastError || new Error('Verification failed.');
}

function extensionFor(mime) {
  return mime === 'image/jpeg' ? 'jpg' : mime === 'image/webp' ? 'webp' : 'png';
}

function buildEditPrompt(editType, instruction) {
  const typeGuide = {
    remove_object: 'Remove the single selected object and reconstruct only the local background needed to fill its former area.',
    recolor: 'Change only the selected region’s color as requested. Preserve its shape, texture, material, lighting, and all unrelated pixels.',
    replace_object: 'Replace only the single selected small object as requested. Keep scale, perspective, lighting, and surrounding scene coherent.',
    remove_person: 'Remove only the selected person and reconstruct only the local background needed to fill that area.'
  }[editType];
  return `${typeGuide}\nUser request: ${instruction}\n\nHard constraints:\n- Edit only the transparent masked region.\n- Everything outside the masked region must remain visually unchanged.\n- Do not alter any face, identity, body, pose, hair, clothing, background structure, composition, crop, text, logo, number, lighting, or color outside the selected region.\n- Do not beautify, restyle, sharpen, relight, or globally regenerate the image.\n- Match the original image's texture, noise, focus, perspective, and lighting so the edited area does not look AI-generated.\n- Return one finished image only.`;
}

async function callOpenAIEdit({ editType, instruction, original, mask }) {
  if (!process.env.OPENAI_API_KEY) {
    const error = new Error('Image editing is not configured.');
    error.code = 'NO_EDITOR_KEY';
    throw error;
  }
  const form = new FormData();
  form.append('model', process.env.IMAGE_EDIT_MODEL || 'gpt-image-2.5-sunburst');
  form.append('prompt', buildEditPrompt(editType, instruction));
  form.append('image', new Blob([original.buffer], { type: original.mime }), `original.${extensionFor(original.mime)}`);
  form.append('mask', new Blob([mask.buffer], { type: 'image/png' }), 'mask.png');
  form.append('quality', process.env.IMAGE_EDIT_QUALITY || 'medium');
  form.append('size', 'auto');
  form.append('output_format', 'png');
  const response = await fetch('https://api.openai.com/v1/images/edits', {
    method: 'POST',
    headers: { Authorization: `Bearer ${process.env.OPENAI_API_KEY}` },
    body: form,
    signal: AbortSignal.timeout(90000)
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(payload?.error?.message || `Image edit API error ${response.status}`);
    error.status = response.status;
    throw error;
  }
  const b64 = payload?.data?.[0]?.b64_json;
  if (!b64) throw new Error('The image editor returned no image.');
  return { dataUrl: `data:image/png;base64,${b64}` };
}

function deliveryPass(audit) {
  return audit?.verdict === 'PASS'
    && Number(audit?.score || 0) >= 95
    && Array.isArray(audit?.requested_changes)
    && audit.requested_changes.length > 0
    && audit.requested_changes.every(item => item.status === 'done')
    && Array.isArray(audit?.unintended_changes)
    && audit.unintended_changes.length === 0;
}

export async function runVerifiedEdit(validated) {
  const candidate = await callOpenAIEdit(validated);
  const edited = { dataUrl: candidate.dataUrl };
  const verificationInstruction = `${validated.instruction}\nThis was a single local edit inside a user-selected mask. Everything outside that selected region had to remain visually unchanged.`;
  const audit = await runAudit({ instruction: verificationInstruction, original: validated.original, edited });
  if (!deliveryPass(audit)) return { delivered: false, audit };
  return { delivered: true, image: candidate.dataUrl, audit };
}

function json(res, status, payload, extraHeaders = {}) {
  const body = JSON.stringify(payload);
  res.writeHead(status, { ...SECURITY_HEADERS, ...extraHeaders, 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(body), 'Cache-Control': 'no-store' });
  res.end(body);
}

async function readJson(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', chunk => {
      size += chunk.length;
      if (size > MAX_JSON_BYTES) {
        const error = new Error('Request too large.');
        error.code = 'TOO_LARGE';
        reject(error);
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => { try { resolve(JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}')); } catch { reject(new Error('Invalid JSON.')); } });
    req.on('error', reject);
  });
}

const MIME = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.ico': 'image/x-icon' };
async function serveStatic(req, res) {
  const url = new URL(req.url, 'http://localhost');
  const requested = url.pathname === '/' ? '/index.html' : url.pathname;
  const normalized = path.normalize(requested).replace(/^(\.\.[/\\])+/, '');
  const filePath = path.join(publicDir, normalized);
  if (!filePath.startsWith(publicDir)) return false;
  try {
    const data = await readFile(filePath);
    const ext = path.extname(filePath).toLowerCase();
    res.writeHead(200, { ...SECURITY_HEADERS, 'Content-Type': MIME[ext] || 'application/octet-stream', 'Content-Length': data.length, 'Cache-Control': process.env.NODE_ENV === 'production' ? 'public, max-age=300' : 'no-cache' });
    res.end(data);
    return true;
  } catch { return false; }
}

export async function requestHandler(req, res) {
  const url = new URL(req.url, 'http://localhost');
  if (req.method === 'GET' && url.pathname === '/health') return json(res, 200, { ok: true, service: 'keepexact', verifier: 'deepseek-flash', editor: process.env.IMAGE_EDIT_MODEL || 'gpt-image-2.5-sunburst', verifierLive: Boolean(process.env.DEEPSEEK_API_KEY), editorLive: Boolean(process.env.OPENAI_API_KEY), live: Boolean(process.env.DEEPSEEK_API_KEY && process.env.OPENAI_API_KEY), beta: true });
  if (req.method === 'POST' && url.pathname === '/api/edit') {
    try {
      if (!String(req.headers['content-type'] || '').startsWith('application/json')) return json(res, 415, { error: 'Unsupported request format.' });
      const body = await readJson(req);
      const validated = validateEditBody(body);
      if (validated.error) return json(res, 400, { error: validated.error, precheck: true });
      if (!process.env.OPENAI_API_KEY || !process.env.DEEPSEEK_API_KEY) return json(res, 503, { error: 'The verified editor is temporarily unavailable.' });
      const slot = takeEditSlot(req);
      if (!slot.ok) return json(res, 429, { error: slot.message }, { 'Retry-After': '3600' });
      const outcome = await runVerifiedEdit(validated);
      if (!outcome.delivered) {
        console.log('edit_rejected_by_quality_gate', JSON.stringify({ verdict: outcome.audit?.verdict, score: outcome.audit?.score }));
        return json(res, 422, { delivered: false, error: 'This attempt did not meet the delivery standard, so no edited image is being shown. The system stopped after one paid edit attempt instead of retrying and spending more.', quality: { verdict: outcome.audit?.verdict || 'REVIEW', score: Number(outcome.audit?.score || 0) } });
      }
      return json(res, 200, { delivered: true, image: outcome.image, quality: { verdict: outcome.audit.verdict, score: outcome.audit.score, summary: outcome.audit.summary } });
    } catch (error) {
      if (error?.code === 'TOO_LARGE') return json(res, 413, { error: 'The upload is too large.' });
      if (error?.code === 'NO_EDITOR_KEY' || error?.code === 'NO_API_KEY') return json(res, 503, { error: 'The verified editor is temporarily unavailable.' });
      console.error('verified_edit_failed', error?.status || '', error?.message || error);
      return json(res, 502, { error: 'The edit could not be completed reliably. No result was delivered.' });
    }
  }
  if (req.method === 'POST' && url.pathname === '/api/audit') {
    try {
      if (!String(req.headers['content-type'] || '').startsWith('application/json')) return json(res, 415, { error: 'Unsupported request format.' });
      const body = await readJson(req);
      const validated = validateAuditBody(body);
      if (validated.error) return json(res, 400, { error: validated.error });
      const slot = takeAuditSlot(req);
      if (!slot.ok) return json(res, 429, { error: slot.message }, { 'Retry-After': '3600' });
      return json(res, 200, await runAudit(validated));
    } catch (error) {
      if (error?.code === 'TOO_LARGE') return json(res, 413, { error: 'The upload is too large.' });
      if (error?.code === 'NO_API_KEY') return json(res, 503, { error: 'Live AI verification is temporarily unavailable.' });
      console.error('audit_failed', error?.status || '', error?.message || error);
      return json(res, 502, { error: 'Verification failed. Your selected images are still on this device; retry in a moment.' });
    }
  }
  if (req.method === 'GET' && await serveStatic(req, res)) return;
  json(res, 404, { error: 'Not found.' });
}

export const app = http.createServer(requestHandler);
const isMain = process.argv[1] && path.resolve(process.argv[1]) === __filename;
if (isMain) {
  app.listen(port, '0.0.0.0', () => console.log(`KeepExact listening on http://0.0.0.0:${port}`));
}
