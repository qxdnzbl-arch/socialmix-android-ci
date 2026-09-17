export function analyzePixelDiff(before, after, width, height, allowedRect = null, options = {}) {
  if (!before || !after || before.length !== after.length || before.length !== width * height * 4) {
    throw new Error('Image buffers must have the same dimensions.');
  }
  const pixelThreshold = options.pixelThreshold ?? 24;
  const reviewRatio = options.reviewRatio ?? 0.0015;
  const failRatio = options.failRatio ?? 0.01;
  let outsideChanged = 0, outsidePixels = 0, insideChanged = 0, insidePixels = 0;
  let minX = width, minY = height, maxX = -1, maxY = -1;
  const rect = allowedRect && {
    x: Math.max(0, Math.min(1, allowedRect.x)),
    y: Math.max(0, Math.min(1, allowedRect.y)),
    w: Math.max(0, Math.min(1, allowedRect.w)),
    h: Math.max(0, Math.min(1, allowedRect.h))
  };
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4;
      const d = (Math.abs(before[i] - after[i]) + Math.abs(before[i + 1] - after[i + 1]) + Math.abs(before[i + 2] - after[i + 2])) / 3;
      const changed = d >= pixelThreshold;
      const nx = (x + 0.5) / width, ny = (y + 0.5) / height;
      const inside = rect && nx >= rect.x && nx <= rect.x + rect.w && ny >= rect.y && ny <= rect.y + rect.h;
      if (inside) {
        insidePixels++;
        if (changed) insideChanged++;
      } else {
        outsidePixels++;
        if (changed) {
          outsideChanged++;
          minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
        }
      }
    }
  }
  const outsideRatio = outsidePixels ? outsideChanged / outsidePixels : 0;
  const insideRatio = insidePixels ? insideChanged / insidePixels : 0;
  const verdict = outsideRatio >= failRatio ? 'FAIL' : outsideRatio >= reviewRatio ? 'REVIEW' : 'PASS';
  return {
    verdict, outsideChanged, outsidePixels, outsideRatio, insideChanged, insidePixels, insideRatio,
    unexpectedBounds: maxX >= 0 ? { x: minX / width, y: minY / height, w: (maxX - minX + 1) / width, h: (maxY - minY + 1) / height } : null
  };
}
