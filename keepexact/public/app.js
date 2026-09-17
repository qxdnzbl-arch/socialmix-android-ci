import { analyzeRawPixelDiff } from '/diff-core.js';

const MAX_BYTES = 10 * 1024 * 1024;
const allowedTypes = new Set(['image/jpeg','image/png','image/webp']);
const form = document.querySelector('#audit-form');
const submit = document.querySelector('#submit');
const errorBox = document.querySelector('#form-error');
const result = document.querySelector('#result');
const instruction = document.querySelector('#instruction');
const liveStatus = document.querySelector('#live-status');

function setError(message='') {
  errorBox.textContent = message;
  errorBox.hidden = !message;
}

async function checkLive() {
  try {
    const r = await fetch('/health', { cache:'no-store' });
    const d = await r.json();
    if (!d.live) {
      form.querySelectorAll('input,textarea,button[type="submit"]').forEach(el => el.disabled = true);
      liveStatus.textContent = 'Semantic AI verification is not enabled yet. This build remains internal until it passes the benchmark.';
    } else {
      liveStatus.textContent = 'Semantic AI verification is enabled for internal testing.';
    }
  } catch {
    liveStatus.textContent = 'Verifier status is temporarily unavailable.';
  }
}
checkLive();

function setupFileInput(id) {
  const input = document.querySelector(`#${id}`);
  const preview = document.querySelector(`#${id}-preview`);
  const name = document.querySelector(`#${id}-name`);
  let objectUrl = null;
  input.addEventListener('change', () => {
    setError();
    const file = input.files?.[0];
    if (!file) return;
    if (!allowedTypes.has(file.type)) {
      input.value = '';
      return setError('Use a JPG, PNG, or WEBP image.');
    }
    if (file.size > MAX_BYTES) {
      input.value = '';
      return setError('Each image must be 10 MB or smaller.');
    }
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    objectUrl = URL.createObjectURL(file);
    preview.src = objectUrl;
    preview.hidden = false;
    name.textContent = file.name;
  });
}
setupFileInput('original');
setupFileInput('edited');

function findingHtml(title, detail) {
  const item = document.createElement('div');
  item.className = 'finding';
  const strong = document.createElement('strong');
  strong.textContent = title;
  const p = document.createElement('p');
  p.textContent = detail;
  item.append(strong, p);
  return item;
}

function renderResult(data) {
  const verdict = document.querySelector('#verdict');
  verdict.textContent = data.verdict;
  verdict.className = `verdict ${data.verdict}`;
  document.querySelector('#score').textContent = data.score;
  document.querySelector('#summary').textContent = data.summary;
  const requested = document.querySelector('#requested-list');
  requested.replaceChildren();
  for (const item of data.requested_changes || []) requested.append(findingHtml(`${item.status.replaceAll('_',' ').toUpperCase()} · ${item.item}`, item.evidence));
  if (!data.requested_changes?.length) {
    const empty = findingHtml('No requested changes identified', 'The verifier could not split the instruction into a concrete requested edit.');
    empty.classList.add('empty');
    requested.append(empty);
  }
  const unintended = document.querySelector('#unintended-list');
  unintended.replaceChildren();
  for (const item of data.unintended_changes || []) unintended.append(findingHtml(`${item.severity.toUpperCase()} · ${item.area}`, `${item.change} ${item.why_it_matters}`));
  if (!data.unintended_changes?.length) {
    const empty = findingHtml('No material unintended change detected', 'KeepExact did not identify an unrelated visible change with enough confidence to flag it.');
    empty.classList.add('empty');
    unintended.append(empty);
  }
  document.querySelector('#repair-prompt').textContent = data.repair_prompt;
  result.hidden = false;
  result.scrollIntoView({ behavior:'smooth', block:'start' });
}

function readAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error('Could not read the selected image.'));
    reader.readAsDataURL(file);
  });
}

async function samplePixels(file) {
  const bitmap = await createImageBitmap(file);
  try {
    const canvas = document.createElement('canvas');
    canvas.width = 128;
    canvas.height = 128;
    const ctx = canvas.getContext('2d', { willReadFrequently: true });
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, 128, 128);
    ctx.drawImage(bitmap, 0, 0, 128, 128);
    return {
      pixels: ctx.getImageData(0, 0, 128, 128).data,
      originalWidth: bitmap.width,
      originalHeight: bitmap.height
    };
  } finally {
    bitmap.close?.();
  }
}

async function computePixelDiff(original, edited, text) {
  try {
    const [a, b] = await Promise.all([samplePixels(original), samplePixels(edited)]);
    return analyzeRawPixelDiff(a.pixels, b.pixels, 128, 128, text, {
      dimensionMismatch: a.originalWidth !== b.originalWidth || a.originalHeight !== b.originalHeight
    });
  } catch {
    return null;
  }
}

form.addEventListener('submit', async event => {
  event.preventDefault();
  setError();
  const original = document.querySelector('#original').files?.[0];
  const edited = document.querySelector('#edited').files?.[0];
  const text = instruction.value.trim();
  if (!original) return setError('Upload the original image.');
  if (!edited) return setError('Upload the edited image.');
  if (!text) return setError('Enter the exact edit instruction you gave the AI.');
  submit.disabled = true;
  submit.textContent = 'Checking…';
  result.hidden = true;
  try {
    const [originalData, editedData, pixelDiff] = await Promise.all([
      readAsDataUrl(original),
      readAsDataUrl(edited),
      computePixelDiff(original, edited, text)
    ]);
    const response = await fetch('/api/audit', {
      method: 'POST',
      headers: { 'Content-Type':'application/json' },
      body: JSON.stringify({ original: originalData, edited: editedData, instruction: text, pixelDiff })
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.error || 'Verification failed.');
    renderResult(data);
  } catch (error) {
    setError(error.message || 'Verification failed.');
  } finally {
    submit.disabled = false;
    submit.textContent = 'Run internal check';
  }
});

document.querySelector('#copy-repair').addEventListener('click', async event => {
  const text = document.querySelector('#repair-prompt').textContent;
  if (!text) return;
  await navigator.clipboard.writeText(text);
  const button = event.currentTarget;
  const old = button.textContent;
  button.textContent = 'Copied';
  setTimeout(() => { button.textContent = old; }, 1200);
});
