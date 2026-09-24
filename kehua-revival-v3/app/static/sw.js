const CACHE='keh-shell-v4';
const SHELL=['/','/static/style.css','/static/app.js'];
self.addEventListener('install',e=>e.waitUntil(caches.open(CACHE).then(c=>c.addAll(SHELL)).then(()=>self.skipWaiting())));
self.addEventListener('activate',e=>e.waitUntil(Promise.all([
  caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)))),
  self.clients.claim()
])));
self.addEventListener('fetch',e=>{
  if(e.request.method!=='GET')return;
  const u=new URL(e.request.url);
  if(u.origin!==self.location.origin||u.pathname.startsWith('/api/')||u.pathname.startsWith('/ws/'))return;
  e.respondWith(fetch(e.request).then(r=>{
    if(r&&r.ok){const x=r.clone();caches.open(CACHE).then(c=>c.put(e.request,x));}
    return r;
  }).catch(()=>caches.match(e.request)));
});
