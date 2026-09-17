const MAX_BYTES = 10 * 1024 * 1024;
const allowedTypes = new Set(['image/jpeg','image/png','image/webp']);
const form = document.querySelector('#edit-form');
const fileInput = document.querySelector('#original');
const preview = document.querySelector('#source-preview');
const canvas = document.querySelector('#selection-canvas');
const selectionSection = document.querySelector('#selection-section');
const editOptions = document.querySelector('#edit-options');
const instructionBlock = document.querySelector('#instruction-block');
const instruction = document.querySelector('#instruction');
const submit = document.querySelector('#submit');
const errorBox = document.querySelector('#form-error');
const liveStatus = document.querySelector('#live-status');
const result = document.querySelector('#result');
const selectionStatus = document.querySelector('#selection-status');
const uploadCopy = document.querySelector('#upload-copy');

let selectedFile = null;
let objectUrl = null;
let selection = null;
let dragStart = null;
let serviceReady = false;

function setError(message='') {
  errorBox.textContent = message;
  errorBox.hidden = !message;
}

function selectedType() {
  return form.querySelector('input[name="edit-type"]:checked')?.value || '';
}

function updateSubmit() {
  submit.disabled = !(serviceReady && selectedFile && selection && selectedType() && instruction.value.trim());
}

async function checkLive() {
  try {
    const response = await fetch('/health', { cache:'no-store' });
    const data = await response.json();
    serviceReady = Boolean(response.ok && data.live);
    liveStatus.textContent = serviceReady ? '修改服务 + 质检服务已就绪。' : '服务暂时不可用。';
  } catch {
    serviceReady = false;
    liveStatus.textContent = '服务暂时不可用。';
  }
  updateSubmit();
}
checkLive();

function resetSelection() {
  selection = null;
  dragStart = null;
  selectionStatus.textContent = '还没圈选';
  drawSelection();
  updateSubmit();
}

document.querySelector('#reset-selection').addEventListener('click', resetSelection);

fileInput.addEventListener('change', () => {
  setError();
  result.hidden = true;
  const file = fileInput.files?.[0];
  if (!file) return;
  if (!allowedTypes.has(file.type)) {
    fileInput.value = '';
    return setError('请选择 JPG、PNG 或 WEBP 图片。');
  }
  if (file.size > MAX_BYTES) {
    fileInput.value = '';
    return setError('图片不能超过 10MB。');
  }
  selectedFile = file;
  if (objectUrl) URL.revokeObjectURL(objectUrl);
  objectUrl = URL.createObjectURL(file);
  preview.src = objectUrl;
  uploadCopy.textContent = file.name;
  resetSelection();
  selectionSection.hidden = false;
  editOptions.hidden = false;
  instructionBlock.hidden = false;
});

preview.addEventListener('load', () => {
  resizeCanvas();
  drawSelection();
});
window.addEventListener('resize', () => {
  if (!selectionSection.hidden) {
    resizeCanvas();
    drawSelection();
  }
});

function resizeCanvas() {
  const rect = preview.getBoundingClientRect();
  canvas.width = Math.max(1, Math.round(rect.width));
  canvas.height = Math.max(1, Math.round(rect.height));
}

function pointFromEvent(event) {
  const rect = canvas.getBoundingClientRect();
  return {
    x: Math.max(0, Math.min(rect.width, event.clientX - rect.left)),
    y: Math.max(0, Math.min(rect.height, event.clientY - rect.top))
  };
}

function normalizedRect(a, b) {
  const w = canvas.clientWidth || canvas.width;
  const h = canvas.clientHeight || canvas.height;
  const x1 = Math.min(a.x, b.x), x2 = Math.max(a.x, b.x);
  const y1 = Math.min(a.y, b.y), y2 = Math.max(a.y, b.y);
  return { x: x1 / w, y: y1 / h, w: (x2 - x1) / w, h: (y2 - y1) / h };
}

function drawSelection(tempPoint=null) {
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  let r = selection;
  if (dragStart && tempPoint) r = normalizedRect(dragStart, tempPoint);
  if (!r) return;
  const x = r.x * canvas.width, y = r.y * canvas.height;
  const w = r.w * canvas.width, h = r.h * canvas.height;
  ctx.fillStyle = 'rgba(61, 108, 75, .14)';
  ctx.fillRect(x, y, w, h);
  ctx.strokeStyle = '#255f37';
  ctx.lineWidth = 2;
  ctx.setLineDash([7,5]);
  ctx.strokeRect(x + 1, y + 1, Math.max(0, w - 2), Math.max(0, h - 2));
}

canvas.addEventListener('pointerdown', event => {
  if (!selectedFile) return;
  event.preventDefault();
  canvas.setPointerCapture(event.pointerId);
  dragStart = pointFromEvent(event);
  selection = null;
  drawSelection(dragStart);
});

canvas.addEventListener('pointermove', event => {
  if (!dragStart) return;
  event.preventDefault();
  drawSelection(pointFromEvent(event));
});

canvas.addEventListener('pointerup', event => {
  if (!dragStart) return;
  event.preventDefault();
  const end = pointFromEvent(event);
  const next = normalizedRect(dragStart, end);
  dragStart = null;
  const fraction = next.w * next.h;
  if (fraction < 0.001) {
    selection = null;
    selectionStatus.textContent = '圈选太小，请重新圈';
  } else if (fraction > 0.35) {
    selection = null;
    selectionStatus.textContent = '圈选超过 35%，请缩小范围';
  } else {
    selection = next;
    selectionStatus.textContent = `已圈选约 ${(fraction * 100).toFixed(1)}% 的画面`;
  }
  drawSelection();
  updateSubmit();
});

canvas.addEventListener('pointercancel', () => {
  dragStart = null;
  drawSelection();
});

form.addEventListener('change', updateSubmit);
instruction.addEventListener('input', updateSubmit);

function readAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error('读取图片失败。'));
    reader.readAsDataURL(file);
  });
}

function buildMaskDataUrl() {
  if (!selection || !preview.naturalWidth || !preview.naturalHeight) throw new Error('请先圈出要修改的位置。');
  const mask = document.createElement('canvas');
  mask.width = preview.naturalWidth;
  mask.height = preview.naturalHeight;
  const ctx = mask.getContext('2d');
  if (!ctx) throw new Error('当前浏览器无法创建修改区域。');
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, mask.width, mask.height);
  const padX = Math.max(2, Math.round(mask.width * 0.006));
  const padY = Math.max(2, Math.round(mask.height * 0.006));
  const x = Math.max(0, Math.floor(selection.x * mask.width) - padX);
  const y = Math.max(0, Math.floor(selection.y * mask.height) - padY);
  const w = Math.min(mask.width - x, Math.ceil(selection.w * mask.width) + padX * 2);
  const h = Math.min(mask.height - y, Math.ceil(selection.h * mask.height) + padY * 2);
  ctx.clearRect(x, y, w, h);
  return mask.toDataURL('image/png');
}

function friendlyError(response, data) {
  if (response.status === 422) return '这次结果没有达到交付标准，所以没有展示半成品，也没有继续自动重试。请缩小圈选范围或换一张图再试。';
  if (response.status === 429) return '今天这个网络的免费测试次数已经用完了。';
  if (response.status === 503) return '修改服务暂时不可用。';
  if (response.status === 400) {
    const raw = String(data?.error || '');
    if (/too large|35%/i.test(raw)) return '圈选范围太大，这一版只处理不超过整张图 35% 的局部修改。';
    if (/one edit|single/i.test(raw)) return '一次只能改一个地方、做一件事。';
    if (/outside the current supported range/i.test(raw)) return '这个需求现在不接。当前只做：删一个物体、改一个区域颜色、替换一个小物体、去掉一个路人。';
    if (/mask|draw a box|area/i.test(raw)) return '请先圈出唯一要修改的位置。';
  }
  return data?.error || '这次没有可靠完成，因此没有交付结果。';
}

form.addEventListener('submit', async event => {
  event.preventDefault();
  setError();
  result.hidden = true;
  if (!selectedFile) return setError('请先上传原图。');
  if (!selection) return setError('请先圈出唯一要修改的位置。');
  const editType = selectedType();
  if (!editType) return setError('请选择一种修改功能。');
  const text = instruction.value.trim();
  if (!text) return setError('请用一句话说清楚你要的结果。');

  submit.disabled = true;
  submit.textContent = '正在修改并质检…';
  liveStatus.textContent = '先修改，再自动检查；不合格不会展示。';
  try {
    const [originalData, mask] = await Promise.all([readAsDataUrl(selectedFile), Promise.resolve(buildMaskDataUrl())]);
    const response = await fetch('/api/edit', {
      method: 'POST',
      headers: { 'Content-Type':'application/json' },
      body: JSON.stringify({ original: originalData, mask, instruction: text, editType, regionFraction: selection.w * selection.h })
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok || !data.delivered || !data.image) throw new Error(friendlyError(response, data));
    const finalImage = document.querySelector('#final-image');
    finalImage.src = data.image;
    document.querySelector('#quality-summary').textContent = data.quality?.summary || '指定修改已完成，未发现需要拦截的额外改动。';
    document.querySelector('#download-result').href = data.image;
    result.hidden = false;
    result.scrollIntoView({ behavior:'smooth', block:'start' });
    liveStatus.textContent = '已完成：只有通过质检的结果才会显示。';
  } catch (error) {
    setError(error.message || '这次没有可靠完成，因此没有交付结果。');
    liveStatus.textContent = '没有交付不合格结果。';
  } finally {
    submit.textContent = '开始修改并质检';
    updateSubmit();
  }
});
