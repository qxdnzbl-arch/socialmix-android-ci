import test from 'node:test';
import assert from 'node:assert/strict';
import { analyzeRawPixelDiff } from '../public/diff-core.js';

const W = 64, H = 64;
function image(rgb = [240, 240, 240]) {
  const a = new Uint8Array(W * H * 4);
  for (let i = 0; i < W * H; i++) {
    const p = i * 4;
    a[p] = rgb[0]; a[p + 1] = rgb[1]; a[p + 2] = rgb[2]; a[p + 3] = 255;
  }
  return a;
}
function rect(a, x1, y1, x2, y2, rgb) {
  for (let y = y1; y <= y2; y++) for (let x = x1; x <= x2; x++) {
    const p = (y * W + x) * 4;
    a[p] = rgb[0]; a[p + 1] = rgb[1]; a[p + 2] = rgb[2];
  }
}

test('one localized requested region is not suspicious', () => {
  const a = image(), b = image();
  rect(b, 10, 10, 25, 25, [20, 80, 220]);
  const r = analyzeRawPixelDiff(a, b, W, H, 'Change only the shirt color. Keep everything else unchanged.');
  assert.equal(r.suspicious, false);
  assert.equal(r.significantComponents, 1);
});

test('extra separate region under a one-target instruction is suspicious', () => {
  const a = image(), b = image();
  rect(b, 8, 8, 22, 22, [20, 80, 220]);
  rect(b, 45, 45, 55, 55, [20, 160, 60]);
  const r = analyzeRawPixelDiff(a, b, W, H, 'Change only the shirt color. Keep everything else unchanged.');
  assert.equal(r.suspicious, true);
  assert.match(r.reasons.join(' '), /more separate changed regions/);
});

test('move instruction allows old and new object footprints', () => {
  const a = image(), b = image();
  rect(a, 40, 40, 48, 48, [220, 20, 20]);
  rect(b, 15, 40, 23, 48, [220, 20, 20]);
  const r = analyzeRawPixelDiff(a, b, W, H, 'Move only the red cup to the left. Keep everything else unchanged.');
  assert.equal(r.suspicious, false);
  assert.equal(r.expectedChangedRegions, 2);
});

test('two requested edits allow two changed regions', () => {
  const a = image(), b = image();
  rect(b, 8, 8, 20, 20, [20, 80, 220]);
  rect(b, 42, 42, 54, 54, [20, 160, 60]);
  const r = analyzeRawPixelDiff(a, b, W, H, 'Change the shirt color and remove the cup. Keep everything else unchanged.');
  assert.equal(r.suspicious, false);
  assert.equal(r.expectedChangedRegions, 2);
});

test('broad background edit is not rejected merely for changing many pixels', () => {
  const a = image([240, 235, 220]), b = image([200, 225, 245]);
  const r = analyzeRawPixelDiff(a, b, W, H, 'Change only the wall background from beige to light blue. Keep everything else unchanged.');
  assert.equal(r.suspicious, false);
});

test('no-change instruction flags any material change', () => {
  const a = image(), b = image();
  rect(b, 20, 20, 30, 30, [0, 0, 0]);
  const r = analyzeRawPixelDiff(a, b, W, H, 'Do not change the image. Keep every visible element exactly the same.');
  assert.equal(r.suspicious, true);
  assert.match(r.reasons.join(' '), /no visible change/);
});

test('dimension change is suspicious unless requested', () => {
  const a = image(), b = image();
  const r1 = analyzeRawPixelDiff(a, b, W, H, 'Remove the cup only.', { dimensionMismatch: true });
  assert.equal(r1.suspicious, true);
  const r2 = analyzeRawPixelDiff(a, b, W, H, 'Resize the canvas to a new aspect ratio.', { dimensionMismatch: true });
  assert.equal(r2.suspicious, false);
});
