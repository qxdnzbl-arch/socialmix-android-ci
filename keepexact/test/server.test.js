import test from 'node:test';
import assert from 'node:assert/strict';
import { buildAuditPrompt, validateAuditBody, validateEditBody, auditSchema } from '../server.js';

const pngData = 'data:image/png;base64,iVBORw0KGgo=';
const maskData = 'data:image/png;base64,iVBORw0KGgo=';

test('audit prompt checks unintended edits and visible artifacts', () => {
  const p = buildAuditPrompt('Remove the cup only.');
  assert.match(p, /BEFORE/);
  assert.match(p, /AFTER/);
  assert.match(p, /Remove the cup only/);
  assert.match(p, /unintended/i);
  assert.match(p, /artifact/i);
});

test('audit validation rejects missing instruction', () => {
  assert.equal(validateAuditBody({}).error, 'Enter the exact edit instruction you gave the AI.');
});

test('audit validation accepts complete normalized request', () => {
  const r = validateAuditBody({ instruction: '  change shirt to white  ', original: pngData, edited: pngData });
  assert.equal(r.instruction, 'change shirt to white');
  assert.equal(r.original.mime, 'image/png');
  assert.equal(r.edited.mime, 'image/png');
});

test('edit precheck accepts one small supported local edit', () => {
  const r = validateEditBody({ editType: 'remove_object', instruction: '删掉圈出的杯子', original: pngData, mask: maskData, regionFraction: 0.08 });
  assert.equal(r.editType, 'remove_object');
  assert.equal(r.regionFraction, 0.08);
});

test('edit precheck rejects oversized region before paid API use', () => {
  const r = validateEditBody({ editType: 'remove_object', instruction: '删掉圈出的杯子', original: pngData, mask: maskData, regionFraction: 0.5 });
  assert.match(r.error, /35%/);
});

test('edit precheck rejects unsupported complex edits before paid API use', () => {
  const r = validateEditBody({ editType: 'replace_object', instruction: '换脸并且改发型', original: pngData, mask: maskData, regionFraction: 0.1 });
  assert.match(r.error, /outside the current supported range/);
});

test('schema requires all top-level result fields', () => {
  assert.deepEqual(auditSchema.required, ['verdict', 'score', 'summary', 'requested_changes', 'unintended_changes', 'repair_prompt']);
  assert.equal(auditSchema.additionalProperties, false);
});

test('running server serves explicit local-edit product, modules, health and safe precheck', async t => {
  const { app } = await import('../server.js');
  await new Promise(resolve => app.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => app.close(resolve)));
  const base = `http://127.0.0.1:${app.address().port}`;

  const home = await fetch(`${base}/`);
  assert.equal(home.status, 200);
  assert.equal(home.headers.get('x-content-type-options'), 'nosniff');
  const html = await home.text();
  assert.match(html, /只改你圈出的这一小块/);
  assert.match(html, /现在能做/);
  assert.match(html, /现在不接/);
  assert.match(html, /不合格结果不会交付/);

  for (const asset of ['/app.js', '/styles.css']) {
    const response = await fetch(`${base}${asset}`);
    assert.equal(response.status, 200);
  }

  const health = await fetch(`${base}/health`);
  const healthJson = await health.json();
  assert.equal(healthJson.ok, true);
  assert.equal(healthJson.service, 'keepexact');
  assert.equal(typeof healthJson.editorLive, 'boolean');
  assert.equal(typeof healthJson.verifierLive, 'boolean');
  assert.equal(typeof healthJson.live, 'boolean');

  const precheck = await fetch(`${base}/api/edit`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ editType: 'remove_object', instruction: '删掉杯子', original: pngData, mask: maskData, regionFraction: 0.7 })
  });
  assert.equal(precheck.status, 400);
  assert.equal((await precheck.json()).precheck, true);
});
