import http from 'node:http';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 3000);
const API_URL = 'https://api.302.ai/v1/chat/completions';
const RATES = Object.freeze({
  'gpt-6.1-sol': { input: 2, output: 10 },
  'claude-opus-5-5': { input: 4, output: 20 }
});
const GPT = 'gpt-6.1-sol';
const CLAUDE = 'claude-opus-5-5';
const MAX_HISTORY = 12;
const MAX_MESSAGE_CHARS = 4000;
const MAX_PER_CALL_OUTPUT = 900;
const MAX_BODY_BYTES = 24000;
const MAX_CONCURRENT = 1;
// Complete up to three calls within a capped Vercel 180s function.
const MAX_REQUEST_MS = 160000;
const DEFAULT_ESTIMATE_CAP = 0.06;
const DAILY_CAP = 0.70;
let active = 0;
let usageDay = new Date().toISOString().slice(0,10);
let estimatedDailySpend = 0;

function json(data, status=200, extra={}) {
  return new Response(JSON.stringify(data), {status, headers:{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff',...extra}});
}
function secureHeaders(res){
  const headers = new Headers(res.headers);
  headers.set('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
  headers.set('Referrer-Policy','no-referrer');
  headers.set('X-Frame-Options','DENY');
  return new Response(res.body,{status:res.status,headers});
}
function isAuth(req){
  const expected = process.env.TONGPIN_ACCESS_PASSWORD;
  if(!expected || expected.length<12) return false;
  const a = req.headers.get('x-tongpin-password') || '';
  // length-and-bytes constant time via manual comparison, no runtime deps
  if(a.length!==expected.length)return false;
  let v=0;for(let i=0;i<a.length;i++)v|=a.charCodeAt(i)^expected.charCodeAt(i);
  return v===0;
}
function getKey(){return process.env.AI302_API_KEY || '';}
function validateMessageHistory(payload){
 const list = Array.isArray(payload.messages)?payload.messages:[];
 if(list.length<1 || list.length>MAX_HISTORY*2+1)throw new Error('每次最多保留25条上下文消息');
 return list.map(item=>{
   if(!item || !['user','assistant'].includes(item.role) || typeof item.content!=='string' || !item.content.trim() || item.content.length>MAX_MESSAGE_CHARS) throw new Error('消息内容无效或超过4000字');
   return {role:item.role,content:item.content.trim()};
 });
}
function usdEstimate(messages,maxOutput=MAX_PER_CALL_OUTPUT,model=GPT){
 const rate=RATES[model];
 // Conservative rough upper bound for common Chinese/English chat; not a billing guarantee.
 const chars = messages.reduce((x,m)=>x+m.content.length,0);
 const tokenEstimate = Math.ceil(chars*2.2)+300;
 return (tokenEstimate*rate.input + maxOutput*rate.output)/1e6;
}
function estimateMax(messages,mode){
 if(mode==='solo')return usdEstimate(messages,MAX_PER_CALL_OUTPUT,GPT);
 // text can expand over calls, cap transmitted draft/review lengths below
 const draft=[...messages,{role:'assistant',content:'测'.repeat(3600)}];
 const critique=[...draft,{role:'assistant',content:'测'.repeat(3600)}];
 return usdEstimate(messages,900,GPT)+usdEstimate(draft,900,CLAUDE)+usdEstimate(critique,900,GPT);
}
function nowDay(){const d=new Date().toISOString().slice(0,10);if(usageDay!==d){usageDay=d;estimatedDailySpend=0;}}
function safeError(message,status=400){return json({ok:false,error:message},status);}
async function llm(model,messages,signal){
 const payload={model,messages,temperature:0.55,max_tokens:MAX_PER_CALL_OUTPUT,stream:false};
 const response=await fetch(API_URL,{method:'POST',headers:{'Authorization':'Bearer '+getKey(),'Content-Type':'application/json'},body:JSON.stringify(payload),signal});
 let obj;
 try{obj=await response.json();}catch{throw new Error('模型接口没有返回可解析的JSON（HTTP '+response.status+'）');}
 if(!response.ok){
   const reason=typeof obj?.error?.message==='string'?obj.error.message:'';
   throw new Error('302.AI 请求失败（HTTP '+response.status+'）：'+reason.slice(0,180));
 }
 const content=obj?.choices?.[0]?.message?.content;
 if(typeof content!=='string'||!content.trim())throw new Error(model+' 没有返回文本，已停止后续调用');
 return {text:content,usage:obj.usage||null,model:obj.model||model};
}
async function processChat(req){
 if(!isAuth(req))return safeError('访问密码未设置或不正确',401);
 if(!getKey())return safeError('尚未连接302.AI：服务端没有配置受限API密钥',503);
 // In-memory counters reset on serverless restarts and scale-out; they are not a hard limit.
 // Only verified 302.AI key-level total + daily quotas prevent financial surprises.
 if(process.env.TONGPIN_LIMITED_KEY_VERIFIED!=='1')return safeError('尚未核验302.AI密钥的总额度和每日额度，已禁止收费调用',503);
 if(active>=MAX_CONCURRENT)return safeError('上一条消息还在处理中，请等它结束',429);
 const bodyText=await req.text();
 if(Buffer.byteLength(bodyText,'utf8')>MAX_BODY_BYTES)return safeError('请求过大',413);
 let obj; try{obj=JSON.parse(bodyText);}catch{return safeError('请求格式无效');}
 let messages;try{messages=validateMessageHistory(obj);}catch(e){return safeError(e.message)}
 if(messages.at(-1).role!=='user')return safeError('最后一条必须是用户消息');
 const choice=['auto','solo','duo'].includes(obj.mode)?obj.mode:'auto';
 const current=messages.at(-1).content;
 const complex=current.length>100||/(软件|代码|开发|法律|研究|调查|验证|比较|决策|复盘|方案|修改|修复|分析|架构|逻辑|工作)/.test(current);
 const mode=choice==='auto'?(complex?'duo':'solo'):choice;
 const budget=typeof obj.maxUsd==='number'&&Number.isFinite(obj.maxUsd)?Math.min(1,Math.max(0.001,obj.maxUsd)):DEFAULT_ESTIMATE_CAP;
 const est=estimateMax(messages,mode);
 nowDay();
 if(est>budget)return safeError('预计最高费用约 $'+est.toFixed(4)+'，超过本条预算 $'+budget.toFixed(4)+'。可减少上下文或增加单次预算，但不保证平台实际扣费。',422);
 if(estimatedDailySpend+est>DAILY_CAP)return safeError('本实例当日预计调用金额达到 
 // reserve estimated budget upfront; no auto retries to avoid unexpected extra charging.
 estimatedDailySpend+=est;active++;
 const ctrl=new AbortController();const timer=setTimeout(()=>ctrl.abort(),MAX_REQUEST_MS);
 const trace=[];
 try{
  let final;
  if(mode==='solo'){
    const r=await llm(GPT,messages,ctrl.signal);trace.push({model:r.model,usage:r.usage});final=r.text;
  }else{
    const first=await llm(GPT,[{role:'system',content:'你是同频聊天助手。用中文解决用户问题，忠于用户目标。不声称已执行未执行的动作。'},...messages],ctrl.signal);
    trace.push({model:first.model,usage:first.usage});
    const second=await llm(CLAUDE,[{role:'system',content:'你是独立审查助手。指出初稿最重要的事实漏洞、遗漏与更好的解决方式。只提供简短审查，不和用户直接对话。'},...messages,{role:'assistant',content:'GPT初稿：\n'+first.text.slice(0,3600)},{role:'user',content:'请检查初稿的关键缺点并提供修正意见。'}],ctrl.signal);
    trace.push({model:second.model,usage:second.usage});
    const third=await llm(GPT,[{role:'system',content:'你是同频最终回答者。参考Claude审查，但必须自己判断意见是否成立；只向用户输出一次最终可执行的回答，不提无关的后台流程。'},...messages,{role:'assistant',content:'GPT初稿：\n'+first.text.slice(0,3600)},{role:'user',content:'Claude审查意见：\n'+second.text.slice(0,3600)+'\n请整合完善最终答复。'}],ctrl.signal);
    trace.push({model:third.model,usage:third.usage});final=third.text;
  }
  return json({ok:true,answer:final,mode,trace,estimatedUpperBoundUsd:est,notice:'费用为调用前近似估算，非真实支出硬上限；302.AI密钥的总额及每日配额才是跨实例保护。'});
 }catch(err){return json({ok:false,error:err.name==='AbortError'?'请求超时，请稍后再试':String(err.message).slice(0,230),trace,mode},502)}
 finally{clearTimeout(timer);active--}
}
async function staticFile(name,type){
 const content=await readFile(path.join(dirname,'public',name));
 return new Response(content,{status:200,headers:{'Content-Type':type,'Cache-Control':'no-store'}});
}
export async function handler(req){
 const url=new URL(req.url);
 const loc=url.pathname;
 if(req.method==='GET'&&loc==='/api/status')return secureHeaders(json({ok:true,configured:!!getKey()&&process.env.TONGPIN_LIMITED_KEY_VERIFIED==='1',passwordProtected:!!process.env.TONGPIN_ACCESS_PASSWORD&&process.env.TONGPIN_ACCESS_PASSWORD.length>=12,models:{main:GPT,review:CLAUDE},instanceDailyEstimateUsd:DAILY_CAP,serverlessUsageLimitIsHard:false}));
 if(req.method==='POST'&&loc==='/api/chat')return secureHeaders(await processChat(req));
 if(req.method==='GET'&&loc==='/')return secureHeaders(await staticFile('index.html','text/html; charset=utf-8'));
 if(req.method==='GET'&&loc==='/styles.css')return secureHeaders(await staticFile('styles.css','text/css; charset=utf-8'));
 if(req.method==='GET'&&loc==='/app.js')return secureHeaders(await staticFile('app.js','text/javascript; charset=utf-8'));
 return secureHeaders(safeError('未找到页面',404));
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
 const server=http.createServer(async(req,res)=>{
   const base='http://localhost:'+PORT;
   const inReq=new Request(new URL(req.url,base),{method:req.method,headers:req.headers,body:req.method==='GET'||req.method==='HEAD'?undefined: (async function*(){for await(const x of req)yield x})(),duplex:'half'});
   try{const out=await handler(inReq);res.writeHead(out.status,Object.fromEntries(out.headers.entries()));res.end(Buffer.from(await out.arrayBuffer()));}
   catch(e){res.writeHead(500,{'Content-Type':'application/json'});res.end(JSON.stringify({ok:false,error:'服务端内部错误'}));}
 });
 server.listen(PORT,'0.0.0.0',()=>console.log('同频 http://localhost:'+PORT));
}
+DAILY_CAP+'，已暂停。跨实例费用硬限制以302.AI密钥配额为准。',429);
 // reserve estimated budget upfront; no auto retries to avoid unexpected extra charging.
 estimatedDailySpend+=est;active++;
 const ctrl=new AbortController();const timer=setTimeout(()=>ctrl.abort(),MAX_REQUEST_MS);
 const trace=[];
 try{
  let final;
  if(mode==='solo'){
    const r=await llm(GPT,messages,ctrl.signal);trace.push({model:r.model,usage:r.usage});final=r.text;
  }else{
    const first=await llm(GPT,[{role:'system',content:'你是同频聊天助手。用中文解决用户问题，忠于用户目标。不声称已执行未执行的动作。'},...messages],ctrl.signal);
    trace.push({model:first.model,usage:first.usage});
    const second=await llm(CLAUDE,[{role:'system',content:'你是独立审查助手。指出初稿最重要的事实漏洞、遗漏与更好的解决方式。只提供简短审查，不和用户直接对话。'},...messages,{role:'assistant',content:'GPT初稿：\n'+first.text.slice(0,3600)},{role:'user',content:'请检查初稿的关键缺点并提供修正意见。'}],ctrl.signal);
    trace.push({model:second.model,usage:second.usage});
    const third=await llm(GPT,[{role:'system',content:'你是同频最终回答者。参考Claude审查，但必须自己判断意见是否成立；只向用户输出一次最终可执行的回答，不提无关的后台流程。'},...messages,{role:'assistant',content:'GPT初稿：\n'+first.text.slice(0,3600)},{role:'user',content:'Claude审查意见：\n'+second.text.slice(0,3600)+'\n请整合完善最终答复。'}],ctrl.signal);
    trace.push({model:third.model,usage:third.usage});final=third.text;
  }
  return json({ok:true,answer:final,mode,trace,estimatedUpperBoundUsd:est,notice:'费用为调用前估算上限，不是302.AI实际结算额；真实账单以服务商为准。'});
 }catch(err){return json({ok:false,error:err.name==='AbortError'?'请求超时，请稍后再试':String(err.message).slice(0,230),trace,mode},502)}
 finally{clearTimeout(timer);active--}
}
async function staticFile(name,type){
 const content=await readFile(path.join(dirname,'public',name));
 return new Response(content,{status:200,headers:{'Content-Type':type,'Cache-Control':'no-store'}});
}
export async function handler(req){
 const url=new URL(req.url);
 const loc=url.pathname;
 if(req.method==='GET'&&loc==='/api/status')return secureHeaders(json({ok:true,configured:!!getKey(),passwordProtected:!!process.env.TONGPIN_ACCESS_PASSWORD,models:{main:GPT,review:CLAUDE},dailyEstimatedBudgetUsd:DAILY_CAP}));
 if(req.method==='POST'&&loc==='/api/chat')return secureHeaders(await processChat(req));
 if(req.method==='GET'&&loc==='/')return secureHeaders(await staticFile('index.html','text/html; charset=utf-8'));
 if(req.method==='GET'&&loc==='/styles.css')return secureHeaders(await staticFile('styles.css','text/css; charset=utf-8'));
 if(req.method==='GET'&&loc==='/app.js')return secureHeaders(await staticFile('app.js','text/javascript; charset=utf-8'));
 return secureHeaders(safeError('未找到页面',404));
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
 const server=http.createServer(async(req,res)=>{
   const base='http://localhost:'+PORT;
   const inReq=new Request(new URL(req.url,base),{method:req.method,headers:req.headers,body:req.method==='GET'||req.method==='HEAD'?undefined: (async function*(){for await(const x of req)yield x})(),duplex:'half'});
   try{const out=await handler(inReq);res.writeHead(out.status,Object.fromEntries(out.headers.entries()));res.end(Buffer.from(await out.arrayBuffer()));}
   catch(e){res.writeHead(500,{'Content-Type':'application/json'});res.end(JSON.stringify({ok:false,error:'服务端内部错误'}));}
 });
 server.listen(PORT,'0.0.0.0',()=>console.log('同频 http://localhost:'+PORT));
}
