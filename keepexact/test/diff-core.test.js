import test from 'node:test';
import assert from 'node:assert/strict';
import { analyzePixelDiff } from '../public/diff-core.js';

const W = 100, H = 100;
const base = () => {
  const a = new Uint8ClampedArray(W * H * 4);
  for (let i = 0; i < a.length; i += 4) a[i] = a[i+1] = a[i+2] = 128, a[i+3] = 255;
  return a;
};
function paint(buf, x0, y0, x1, y1, v) {
  for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
    const i = (y * W + x) * 4; buf[i] = buf[i+1] = buf[i+2] = v;
  }
}
const allowed = { x: .2, y: .2, w: .3, h: .3 };

test('no change passes', () => {
  const a = base(), b = a.slice();
  assert.equal(analyzePixelDiff(a, b, W, H, allowed).verdict, 'PASS');
});

test('change only inside allowed area passes', () => {
  const a = base(), b = a.slice(); paint(b, 25, 25, 40, 40, 220);
  const r = analyzePixelDiff(a, b, W, H, allowed);
  assert.equal(r.verdict, 'PASS'); assert.ok(r.insideRatio > 0); assert.equal(r.outsideChanged, 0);
});

test('large unexpected change outside allowed area fails', () => {
  const a = base(), b = a.slice(); paint(b, 70, 70, 85, 85, 220);
  const r = analyzePixelDiff(a, b, W, H, allowed);
  assert.equal(r.verdict, 'FAIL'); assert.ok(r.unexpectedBounds); assert.ok(r.outsideRatio >= .01);
});

test('tiny isolated noise below review threshold passes', () => {
  const a = base(), b = a.slice(); paint(b, 90, 90, 92, 92, 220);
  assert.equal(analyzePixelDiff(a, b, W, H, allowed).verdict, 'PASS');
});

test('small suspicious change returns review', () => {
  const a = base(), b = a.slice(); paint(b, 90, 90, 95, 95, 220);
  assert.equal(analyzePixelDiff(a, b, W, H, allowed).verdict, 'REVIEW');
});
