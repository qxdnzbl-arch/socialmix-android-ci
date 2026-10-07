const base = `http://127.0.0.1:${process.env.PORT || 8080}`;

function assert(cond, msg) {
  if (!cond) throw new Error(msg);
}
async function api(path, opts = {}, cookie = "") {
  const headers = { ...(opts.headers || {}) };
  if (cookie) headers.cookie = cookie;
  const r = await fetch(base + path, { ...opts, headers });
  const text = await r.text();
  return { r, text };
}

(async () => {
  console.log("[smoke] start");

  let x = await api("/health");
  assert(x.r.ok && x.text === "ok", "health failed");
  console.log("[smoke] health ok");

  x = await api("/api/login", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ password: process.env.APP_PASSWORD }),
  });
  assert(x.r.ok, "login failed: " + x.text);
  const cookie = (x.r.headers.get("set-cookie") || "").split(";")[0];
  assert(cookie.includes("="), "login cookie missing");
  console.log("[smoke] login ok");

  x = await api("/api/state", {}, cookie);
  assert(x.r.ok, "state failed: " + x.text);
  const state = JSON.parse(x.text);
  assert(state.keyConfigured === true, "OpenRouter key not configured");
  assert(state.settings?.model === "openrouter/free", "default model is not free route");
  console.log("[smoke] state ok");

  x = await api("/api/memories", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ content: "__SMOKE_MEMORY__" }),
  }, cookie);
  assert(x.r.ok, "memory create failed: " + x.text);
  const memory = JSON.parse(x.text);

  x = await api("/api/memories", {}, cookie);
  assert(x.r.ok && JSON.parse(x.text).some(v => v.id === memory.id), "memory readback failed");
  x = await api("/api/memories/" + memory.id, { method: "DELETE" }, cookie);
  assert(x.r.ok, "memory delete failed");
  console.log("[smoke] memory CRUD ok");

  x = await api("/api/wishes", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ title: "__SMOKE_WISH__", detail: "test" }),
  }, cookie);
  assert(x.r.ok, "wish create failed: " + x.text);
  const wish = JSON.parse(x.text);
  x = await api("/api/wishes/" + wish.id, { method: "DELETE" }, cookie);
  assert(x.r.ok, "wish delete failed");
  console.log("[smoke] wish CRUD ok");

  const wav = Buffer.from("524946462400000057415645666d74201000000001000100401f0000803e0000020010006461746100000000", "hex");
  x = await api("/api/music?name=smoke.wav", {
    method: "PUT",
    headers: { "content-type": "audio/wav" },
    body: wav,
  }, cookie);
  assert(x.r.ok, "music upload failed: " + x.text);
  const song = JSON.parse(x.text);

  x = await api("/media/" + song.id, { headers: { range: "bytes=0-7" } }, cookie);
  assert(x.r.status === 206 && x.text.length > 0, "music range stream failed");
  x = await api("/api/music/" + song.id, { method: "DELETE" }, cookie);
  assert(x.r.ok, "music delete failed");
  console.log("[smoke] music upload/range/delete ok");

  x = await api("/api/chat", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ text: "这是自动验收测试。请只回复 SMOKE_OK，不要调用工具。" }),
  }, cookie);
  assert(x.r.ok, "chat HTTP failed: " + x.text);
  assert(x.text.includes('"type":"done"'), "chat missing done event");
  assert(!x.text.includes('"type":"error"'), "chat returned error: " + x.text.slice(-1000));
  console.log("[smoke] real OpenRouter chat ok");

  console.log("[smoke] ALL_PASS");
})().catch((e) => {
  console.error("[smoke] FAIL", e?.stack || e);
  process.exitCode = 1;
});
