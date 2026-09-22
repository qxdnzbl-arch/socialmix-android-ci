import http from 'node:http';
import { URL } from 'node:url';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const PORT = Number(process.env.PORT || 8080);
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const INDEX_HTML = process.env.INDEX_HTML || fs.readFileSync(path.join(__dirname, 'index.html'), 'utf8');

const EVENTS = new Set([
  'landing_view','example_clicked','verification_started','verification_completed',
  'source_opened','paywall_viewed','checkout_started','checkout_completed','verification_failed'
]);

function json(res, status, body) {
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'access-control-allow-origin': '*',
  });
  res.end(JSON.stringify(body));
}

function html(res, body) {
  res.writeHead(200, {
    'content-type': 'text/html; charset=utf-8',
    'cache-control': 'no-store',
  });
  res.end(body);
}

function decodeHtml(s='') {
  return s
    .replace(/&amp;/g,'&').replace(/&quot;/g,'"').replace(/&#39;/g,"'")
    .replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&#x27;/g,"'")
    .replace(/&#x2F;/g,'/');
}

function stripTags(s='') {
  return decodeHtml(s.replace(/<script[\s\S]*?<\/script>/gi,' ').replace(/<style[\s\S]*?<\/style>/gi,' ').replace(/<[^>]+>/g,' '))
    .replace(/\s+/g,' ').trim();
}

function domainOf(url) {
  try { return new URL(url).hostname.replace(/^www\./,''); } catch { return ''; }
}

function strengthFor(url) {
  const d = domainOf(url).toLowerCase();
  if (!d) return '弱';
  if (/\.gov(\.|$)|\.edu(\.|$)|\.gov\.cn$|\.edu\.cn$|who\.int$|un\.org$/.test(d)) return '较强';
  if (/(reuters|apnews|bbc|nytimes|theguardian|nature|science|thelancet|nejm|who|cdc|nih|gov\.cn|xinhuanet|people\.com\.cn)/.test(d)) return '中强';
  if (/(wikipedia|baike|zhihu|reddit|medium|substack)/.test(d)) return '中等';
  return '待核';
}

function cleanDuckUrl(href='') {
  try {
    const u = new URL(href, 'https://duckduckgo.com');
    const target = u.searchParams.get('uddg');
    return target ? decodeURIComponent(target) : u.href;
  } catch { return href; }
}

async function fetchWithTimeout(url, options={}, ms=8000) {
  const c = new AbortController();
  const t = setTimeout(() => c.abort(), ms);
  try {
    return await fetch(url, { ...options, signal: c.signal, headers: { 'user-agent':'Mozilla/5.0 EvidenceResearchBot/1.0', ...(options.headers||{}) } });
  } finally { clearTimeout(t); }
}

async function searchDuckDuckGo(q) {
  const url = `https://html.duckduckgo.com/html/?q=${encodeURIComponent(q)}`;
  const r = await fetchWithTimeout(url, {}, 9000);
  if (!r.ok) throw new Error(`DDG ${r.status}`);
  const text = await r.text();
  const out = [];
  const re = /<a[^>]*class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>[\s\S]*?(?:<a[^>]*class="[^"]*result__snippet[^"]*"[^>]*>|<div[^>]*class="[^"]*result__snippet[^"]*"[^>]*>)([\s\S]*?)(?:<\/a>|<\/div>)/gi;
  let m;
  while ((m = re.exec(text)) && out.length < 8) {
    const direct = cleanDuckUrl(decodeHtml(m[1]));
    if (!/^https?:/i.test(direct)) continue;
    out.push({ title: stripTags(m[2]), url: direct, snippet: stripTags(m[3]), source: domainOf(direct), channel:'网页搜索' });
  }
  return out;
}

async function searchWikipedia(q) {
  const url = `https://zh.wikipedia.org/w/api.php?action=opensearch&search=${encodeURIComponent(q)}&limit=4&namespace=0&format=json&origin=*`;
  const r = await fetchWithTimeout(url, {}, 7000);
  if (!r.ok) throw new Error(`Wiki ${r.status}`);
  const d = await r.json();
  const titles=d[1]||[], desc=d[2]||[], urls=d[3]||[];
  return urls.map((u,i)=>({ title:titles[i]||u, url:u, snippet:desc[i]||'', source:'zh.wikipedia.org', channel:'百科' }));
}

async function searchNews(q) {
  const url = `https://www.bing.com/news/search?q=${encodeURIComponent(q)}&format=rss&setlang=zh-cn`;
  const r = await fetchWithTimeout(url, {}, 9000);
  if (!r.ok) throw new Error(`News ${r.status}`);
  const xml = await r.text();
  const items = [...xml.matchAll(/<item>([\s\S]*?)<\/item>/gi)].slice(0,6);
  return items.map((x)=>{
    const item=x[1];
    const get=(tag)=>decodeHtml((item.match(new RegExp(`<${tag}>(?:<!\\[CDATA\\[)?([\\s\\S]*?)(?:\\]\\]>)?<\\/${tag}>`,'i'))||[])[1]||'').trim();
    const link=get('link');
    return { title:stripTags(get('title')), url:link, snippet:stripTags(get('description')), source:domainOf(link), date:get('pubDate'), channel:'新闻' };
  }).filter(x=>/^https?:/i.test(x.url));
}

async function enrichSource(src) {
  try {
    const r = await fetchWithTimeout(src.url, { redirect:'follow' }, 5000);
    const finalUrl = r.url || src.url;
    const ct = r.headers.get('content-type') || '';
    if (!ct.includes('text/html') && !ct.includes('text/plain')) return { ...src, url:finalUrl, source:domainOf(finalUrl)||src.source };
    const body = (await r.text()).slice(0,180000);
    const text = stripTags(body).slice(0,1400);
    return { ...src, url:finalUrl, source:domainOf(finalUrl)||src.source, pageText:text };
  } catch { return src; }
}

function dedupeSources(list) {
  const seen = new Set();
  const out=[];
  for (const s of list) {
    const key=(s.url||'').replace(/[#?].*$/,'').replace(/\/$/,'');
    if (!key || seen.has(key)) continue;
    seen.add(key);
    out.push({ ...s, strength: strengthFor(s.url) });
  }
  return out.slice(0,12);
}

function extractJson(text='') {
  const cleaned=text.replace(/^```(?:json)?/i,'').replace(/```$/,'').trim();
  try { return JSON.parse(cleaned); } catch {}
  const a=cleaned.indexOf('{'), b=cleaned.lastIndexOf('}');
  if (a>=0 && b>a) { try { return JSON.parse(cleaned.slice(a,b+1)); } catch {} }
  return null;
}

async function synthesize(claim, sources) {
  const compact = sources.slice(0,9).map((s,i)=>({
    id:`S${i+1}`, title:s.title, domain:s.source, date:s.date||'', strength:s.strength,
    snippet:(s.pageText||s.snippet||'').slice(0,900)
  }));
  const prompt = `你是严谨的证据审查员。只允许根据下面给出的来源判断，不准补充不存在的事实。\n主张：${claim}\n来源：${JSON.stringify(compact)}\n\n返回严格JSON，不要Markdown：{\"conclusion\":\"证据支持|证据反对|部分成立|证据不足\",\"confidence\":\"高|中|低\",\"confidence_reason\":\"一句话\",\"evidence\":[{\"id\":\"S1\",\"supports\":\"该来源具体支持什么\"}],\"counterevidence\":[{\"id\":\"S2\",\"contradicts\":\"具体冲突什么\"}],\"unknowns\":[\"仍然不知道的关键点\"],\"reasoning_summary\":\"证据如何导向暂时结论，最多3句\",\"warning\":\"来源陈旧/二手/相互引用/不足等风险，没有就留空\"}。若来源只是在重复主张、无法直接证明、或来源质量弱，必须降低置信度或判证据不足。`;

  const aiUrl = `https://text.pollinations.ai/${encodeURIComponent(prompt)}?model=openai&private=true`;
  const r = await fetchWithTimeout(aiUrl, {}, 15000);
  if (!r.ok) throw new Error(`AI ${r.status}`);
  const text = await r.text();
  const obj = extractJson(text);
  if (!obj) throw new Error('AI JSON parse failed');
  const valid = new Map(compact.map((x,i)=>[x.id, sources[i]]));
  const mapEvidence = (arr, field) => Array.isArray(arr) ? arr.flatMap(x => {
    const src=valid.get(String(x?.id||''));
    if (!src) return [];
    return [{ ...src, statement:String(x?.[field]||'').slice(0,500) }];
  }) : [];
  const allowed = new Set(['证据支持','证据反对','部分成立','证据不足']);
  return {
    conclusion: allowed.has(obj.conclusion) ? obj.conclusion : '证据不足',
    confidence: ['高','中','低'].includes(obj.confidence) ? obj.confidence : '低',
    confidence_reason: String(obj.confidence_reason||'').slice(0,500),
    evidence: mapEvidence(obj.evidence,'supports'),
    counterevidence: mapEvidence(obj.counterevidence,'contradicts'),
    unknowns: Array.isArray(obj.unknowns) ? obj.unknowns.map(String).slice(0,6) : [],
    reasoning_summary: String(obj.reasoning_summary||'').slice(0,1000),
    warning: String(obj.warning||'').slice(0,700),
    synthesis:'ai'
  };
}

function fallbackSynthesis(sources, errors=[]) {
  return {
    conclusion:'证据不足', confidence:'低',
    confidence_reason:'已找到可核查来源，但自动综合服务暂时不可用，因此不替你强行下结论。',
    evidence:sources.slice(0,5).map(s=>({...s, statement:s.snippet||s.pageText||'该来源与主张相关，请打开原文核对。'})),
    counterevidence:[],
    unknowns:['这些来源是否直接证明了主张，而不只是重复同一说法。','是否存在未被当前搜索覆盖的关键反证或原始材料。'],
    reasoning_summary:'先保留原始来源和可核查链接；在无法可靠综合时，宁可标记“证据不足”，也不制造确定性。',
    warning: errors.length ? `部分检索/综合通道失败：${errors.join('；').slice(0,500)}` : '当前缺少足以形成高置信结论的独立证据。',
    synthesis:'fallback'
  };
}

async function verifyClaim(claim) {
  const errors=[];
  const jobs=[
    searchDuckDuckGo(claim).catch(e=>(errors.push(`网页搜索 ${e.message}`),[])),
    searchWikipedia(claim).catch(e=>(errors.push(`百科 ${e.message}`),[])),
    searchNews(claim).catch(e=>(errors.push(`新闻 ${e.message}`),[])),
  ];
  const parts=await Promise.all(jobs);
  let sources=dedupeSources(parts.flat());
  if (!sources.length) return { claim, generated_at:new Date().toISOString(), search_errors:errors, ...fallbackSynthesis([],errors), sources:[] };
  const enriched=await Promise.all(sources.slice(0,7).map(enrichSource));
  sources=dedupeSources([...enriched,...sources.slice(7)]);
  let analysis;
  try { analysis=await synthesize(claim,sources); }
  catch(e) { errors.push(`自动综合 ${e.message}`); analysis=fallbackSynthesis(sources,errors); }
  return { claim, generated_at:new Date().toISOString(), search_errors:errors, source_count:sources.length, ...analysis, sources };
}

async function readBody(req) {
  let body='';
  for await (const c of req) { body += c; if (body.length > 100000) throw new Error('body too large'); }
  return body;
}

const server=http.createServer(async (req,res)=>{
  const u=new URL(req.url||'/', `http://${req.headers.host||'localhost'}`);
  if (req.method==='OPTIONS') { res.writeHead(204,{'access-control-allow-origin':'*','access-control-allow-methods':'GET,POST,OPTIONS','access-control-allow-headers':'content-type'}); return res.end(); }
  if (u.pathname==='/health') return json(res,200,{ok:true,service:'拿证据'});
  if (u.pathname.startsWith('/event/')) {
    const name=decodeURIComponent(u.pathname.slice('/event/'.length));
    if (EVENTS.has(name)) console.log(JSON.stringify({type:'event',event:name,ts:new Date().toISOString()}));
    res.writeHead(204,{'cache-control':'no-store'}); return res.end();
  }
  if (u.pathname==='/api/verify' && req.method==='GET') {
    const q=(u.searchParams.get('q')||'').trim().slice(0,2000);
    if (!q) return json(res,400,{error:'请输入要查证的主张'});
    try { return json(res,200,await verifyClaim(q)); }
    catch(e) { console.error(e); return json(res,500,{error:'查证失败，请重试'}); }
  }
  if (u.pathname==='/api/verify' && req.method==='POST') {
    try {
      const raw=await readBody(req); const d=JSON.parse(raw||'{}'); const q=String(d.claim||'').trim().slice(0,2000);
      if (!q) return json(res,400,{error:'请输入要查证的主张'});
      return json(res,200,await verifyClaim(q));
    } catch(e) { console.error(e); return json(res,500,{error:'查证失败，请重试'}); }
  }
  if (u.pathname==='/api/status') return json(res,200,{ok:true,search:['DuckDuckGo HTML','Wikipedia','Bing News RSS'],synthesis:'Pollinations + validated source IDs',payment:'not_enabled'});
  return html(res,INDEX_HTML);
});

server.listen(PORT,'0.0.0.0',()=>console.log(`拿证据 listening on ${PORT}`));
