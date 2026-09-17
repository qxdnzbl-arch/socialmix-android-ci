import { deflateSync } from 'node:zlib';
import { runAudit } from './server.js';

const W = 256, H = 256;

function crc32(buffer) {
  let c = 0xffffffff;
  for (const byte of buffer) {
    c ^= byte;
    for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
  }
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const t = Buffer.from(type);
  const out = Buffer.alloc(12 + data.length);
  out.writeUInt32BE(data.length, 0);
  t.copy(out, 4);
  data.copy(out, 8);
  out.writeUInt32BE(crc32(Buffer.concat([t, data])), 8 + data.length);
  return out;
}

function pngDataUrl(drawFn) {
  const pixels = Buffer.alloc(W * H * 4, 255);
  const set = (x, y, rgb) => {
    if (x < 0 || y < 0 || x >= W || y >= H) return;
    const i = (y * W + x) * 4;
    pixels[i] = rgb[0]; pixels[i + 1] = rgb[1]; pixels[i + 2] = rgb[2]; pixels[i + 3] = 255;
  };
  const rect = (x1, y1, x2, y2, rgb) => {
    for (let y = y1; y <= y2; y++) for (let x = x1; x <= x2; x++) set(x, y, rgb);
  };
  const circle = (cx, cy, r, rgb) => {
    for (let y = cy - r; y <= cy + r; y++) for (let x = cx - r; x <= cx + r; x++) {
      if ((x - cx) ** 2 + (y - cy) ** 2 <= r ** 2) set(x, y, rgb);
    }
  };
  drawFn({ rect, circle, set });
  const raw = Buffer.alloc((W * 4 + 1) * H);
  for (let y = 0; y < H; y++) {
    const row = y * (W * 4 + 1);
    raw[row] = 0;
    pixels.copy(raw, row + 1, y * W * 4, (y + 1) * W * 4);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  const png = Buffer.concat([
    Buffer.from([137,80,78,71,13,10,26,10]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw)),
    chunk('IEND', Buffer.alloc(0))
  ]);
  return `data:image/png;base64,${png.toString('base64')}`;
}

function scene({ cup = true, shirt = [50,110,220], face = [238,198,168], shift = 0, bg = [244,241,234], plant = true } = {}) {
  return pngDataUrl(({ rect, circle }) => {
    rect(0, 0, 255, 179, bg);
    rect(0, 180, 255, 255, [210,185,150]);
    circle(72 + shift, 66, 30, face);
    circle(61 + shift, 59, 3, [20,20,20]);
    circle(83 + shift, 59, 3, [20,20,20]);
    rect(42 + shift, 96, 108 + shift, 172, shirt);
    rect(148 + shift, 22, 235 + shift, 52, [40,40,40]);
    rect(158 + shift, 30, 225 + shift, 44, [245,245,245]);
    if (plant) {
      rect(190 + shift, 132, 211 + shift, 178, [125,85,50]);
      circle(188 + shift, 124, 14, [70,155,80]);
      circle(211 + shift, 122, 14, [60,145,75]);
    }
    if (cup) {
      rect(132 + shift, 144, 158 + shift, 176, [210,45,50]);
      rect(129 + shift, 140, 161 + shift, 147, [235,75,80]);
    }
  });
}

const cases = [
  { name: 'remove_cup_clean', expected: 'PASS', instruction: 'Remove the red cup on the counter. Keep the person, face, shirt, plant, composition, background, colors, and everything else unchanged.', original: scene({ cup: true }), edited: scene({ cup: false }) },
  { name: 'remove_cup_face_changed', expected: 'FAIL', instruction: 'Remove the red cup on the counter. Keep the person, face, shirt, plant, composition, background, colors, and everything else unchanged.', original: scene({ cup: true }), edited: scene({ cup: false, face: [175,225,190] }) },
  { name: 'remove_cup_composition_shift', expected: 'FAIL', instruction: 'Remove the red cup on the counter. Keep the composition and everything else unchanged.', original: scene({ cup: true }), edited: scene({ cup: false, shift: 14 }) },
  { name: 'shirt_blue_to_white', expected: 'PASS', instruction: 'Change only the shirt from blue to white. Keep the face, cup, plant, composition, background and everything else unchanged.', original: scene({ shirt: [50,110,220] }), edited: scene({ shirt: [245,245,245] }) },
  { name: 'shirt_extra_plant_removed', expected: 'FAIL', instruction: 'Change only the shirt from blue to white. Keep the face, cup, plant, composition, background and everything else unchanged.', original: scene({ shirt: [50,110,220], plant: true }), edited: scene({ shirt: [245,245,245], plant: false }) },
  { name: 'remove_cup_not_done', expected: 'FAIL', instruction: 'Remove the red cup on the counter and change nothing else.', original: scene({ cup: true }), edited: scene({ cup: true }) },
  { name: 'remove_cup_background_changed', expected: 'FAIL', instruction: 'Remove the red cup on the counter. Keep the background color, lighting and everything else unchanged.', original: scene({ cup: true, bg: [244,241,234] }), edited: scene({ cup: false, bg: [205,232,250] }) },
  { name: 'no_requested_change_but_plant_removed', expected: 'FAIL', instruction: 'Do not change the image. Keep every visible element exactly the same.', original: scene({ plant: true }), edited: scene({ plant: false }) }
];

export async function runLiveBenchmark() {
  const started = Date.now();
  let exactCorrect = 0, safeCorrect = 0, unsafePasses = 0, inputTokens = 0, outputTokens = 0;
  const rows = [];
  console.log('live_benchmark_start', JSON.stringify({ cases: cases.length, model: process.env.DEEPSEEK_MODEL || 'deepseek-flash' }));
  for (const item of cases) {
    const t0 = Date.now();
    try {
      const { result, usage, pixelDiff } = await runAudit({
        instruction: item.instruction,
        original: { dataUrl: item.original },
        edited: { dataUrl: item.edited },
        includeMeta: true
      });
      const exactOk = result.verdict === item.expected;
      const safeOk = item.expected === 'PASS' ? result.verdict === 'PASS' : result.verdict !== 'PASS';
      if (exactOk) exactCorrect++;
      if (safeOk) safeCorrect++;
      if (item.expected === 'FAIL' && result.verdict === 'PASS') unsafePasses++;
      inputTokens += Number(usage?.input_tokens || 0);
      outputTokens += Number(usage?.output_tokens || 0);
      const row = {
        name: item.name, expected: item.expected, actual: result.verdict,
        score: result.score, exactOk, safeOk, ms: Date.now() - t0,
        unintended: Array.isArray(result.unintended_changes) ? result.unintended_changes.length : null,
        requested: Array.isArray(result.requested_changes) ? result.requested_changes.map(x => x.status) : null,
        pixel: pixelDiff ? { suspicious: pixelDiff.suspicious, reasons: pixelDiff.reasons, changedFraction: pixelDiff.changedFraction, components: pixelDiff.significantComponents } : null
      };
      rows.push(row);
      console.log('live_benchmark_case', JSON.stringify(row));
    } catch (error) {
      const row = { name: item.name, expected: item.expected, actual: 'ERROR', exactOk: false, safeOk: false, ms: Date.now() - t0, error: String(error?.message || error).slice(0, 300) };
      rows.push(row);
      console.log('live_benchmark_case', JSON.stringify(row));
    }
  }
  const summary = {
    cases: cases.length,
    exact_correct: exactCorrect,
    exact_accuracy: Number((exactCorrect / cases.length).toFixed(3)),
    safe_correct: safeCorrect,
    safe_accuracy: Number((safeCorrect / cases.length).toFixed(3)),
    unsafe_passes: unsafePasses,
    input_tokens: inputTokens,
    output_tokens: outputTokens,
    total_ms: Date.now() - started,
    rows
  };
  console.log('live_benchmark_summary', JSON.stringify(summary));
  return summary;
}
