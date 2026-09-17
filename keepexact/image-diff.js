import sharp from 'sharp';

function decodeDataUrl(value) {
  const match = /^data:image\/(?:jpeg|png|webp);base64,(.+)$/.exec(String(value || ''));
  if (!match) throw new Error('Unsupported image data URL.');
  return Buffer.from(match[1], 'base64');
}

function dilate(mask, width, height) {
  const out = new Uint8Array(mask.length);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const i = y * width + x;
      if (!mask[i]) continue;
      for (let dy = -1; dy <= 1; dy++) {
        for (let dx = -1; dx <= 1; dx++) {
          const nx = x + dx, ny = y + dy;
          if (nx >= 0 && ny >= 0 && nx < width && ny < height) out[ny * width + nx] = 1;
        }
      }
    }
  }
  return out;
}

function components(mask, width, height) {
  const seen = new Uint8Array(mask.length);
  const sizes = [];
  for (let start = 0; start < mask.length; start++) {
    if (!mask[start] || seen[start]) continue;
    let size = 0;
    const stack = [start];
    seen[start] = 1;
    while (stack.length) {
      const idx = stack.pop();
      size++;
      const x = idx % width, y = Math.floor(idx / width);
      for (let dy = -1; dy <= 1; dy++) {
        for (let dx = -1; dx <= 1; dx++) {
          if (!dx && !dy) continue;
          const nx = x + dx, ny = y + dy;
          if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
          const ni = ny * width + nx;
          if (mask[ni] && !seen[ni]) { seen[ni] = 1; stack.push(ni); }
        }
      }
    }
    sizes.push(size);
  }
  return sizes.sort((a, b) => b - a);
}

function strictLocalInstruction(instruction) {
  const s = String(instruction || '').toLowerCase();
  const noChange = /do not change|change nothing|keep every visible element exactly the same|keep everything exactly the same/.test(s);
  const strictLocal = noChange || /\bonly\b|nothing else|everything else unchanged|change nothing else|keep .* unchanged/.test(s);
  return { noChange, strictLocal };
}

export async function analyzePixelDiff(originalDataUrl, editedDataUrl, instruction) {
  const originalBuffer = decodeDataUrl(originalDataUrl);
  const editedBuffer = decodeDataUrl(editedDataUrl);
  const [metaA, metaB] = await Promise.all([sharp(originalBuffer).metadata(), sharp(editedBuffer).metadata()]);
  const dimensionMismatch = metaA.width !== metaB.width || metaA.height !== metaB.height;

  const width = 128, height = 128;
  const [a, b] = await Promise.all([
    sharp(originalBuffer).resize(width, height, { fit: 'fill' }).ensureAlpha().raw().toBuffer(),
    sharp(editedBuffer).resize(width, height, { fit: 'fill' }).ensureAlpha().raw().toBuffer()
  ]);

  const mask = new Uint8Array(width * height);
  let changed = 0, minX = width, minY = height, maxX = -1, maxY = -1;
  for (let i = 0; i < width * height; i++) {
    const p = i * 4;
    const delta = (Math.abs(a[p] - b[p]) + Math.abs(a[p + 1] - b[p + 1]) + Math.abs(a[p + 2] - b[p + 2])) / 3;
    if (delta >= 18) {
      mask[i] = 1;
      changed++;
      const x = i % width, y = Math.floor(i / width);
      if (x < minX) minX = x; if (x > maxX) maxX = x;
      if (y < minY) minY = y; if (y > maxY) maxY = y;
    }
  }

  const changedFraction = changed / (width * height);
  const bboxFraction = changed ? ((maxX - minX + 1) * (maxY - minY + 1)) / (width * height) : 0;
  const componentSizes = components(dilate(mask, width, height), width, height);
  const significant = componentSizes.filter(size => size >= 20);
  const secondFraction = significant[1] ? significant[1] / (width * height) : 0;
  const { noChange, strictLocal } = strictLocalInstruction(instruction);

  const reasons = [];
  if (dimensionMismatch) reasons.push('image dimensions changed');
  if (noChange && changedFraction > 0.002) reasons.push('instruction requested no visible change');
  if (strictLocal && significant.length >= 2 && secondFraction > 0.0025) reasons.push('multiple separate regions changed');
  if (strictLocal && changedFraction > 0.22) reasons.push('a large share of the image changed');
  if (strictLocal && bboxFraction > 0.55 && significant.length >= 2) reasons.push('changes span a wide area of the composition');

  return {
    suspicious: reasons.length > 0,
    reasons,
    changedFraction: Number(changedFraction.toFixed(4)),
    bboxFraction: Number(bboxFraction.toFixed(4)),
    significantComponents: significant.length,
    secondComponentFraction: Number(secondFraction.toFixed(4)),
    dimensionMismatch
  };
}
