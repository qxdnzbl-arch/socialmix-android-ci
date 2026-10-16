import test from 'node:test';
import assert from 'node:assert/strict';
import {handler} from '../server.js';
const headers={'content-type':'application/json','x-tongpin-password':'strong-test-password'};
const sample={mode:'solo',messages:[{role:'user',content:'你好'}],maxUsd:1};
const req=(o=sample)=>new Request('http://localhost/api/chat',{method:'POST',headers,body:JSON.stringify(o)});
const answer=(text,model)=>Response.json({choices:[{message:{content:text}}],model,usage:{total_tokens:15}});
const original=globalThis.fetch;
test('未配置API密钥时拒绝付费调用',async()=>{
 delete process.env.AI302_API_KEY;process.env.TONGPIN_ACCESS_PASSWORD='strong-test-password';process.env.TONGPIN_LIMITED_KEY_VERIFIED='1';
 globalThis.fetch=()=>{throw Error('never fetch')};const r=await handler(req());assert.equal(r.status,503);
});
test('没有核验限额的密钥始终不能调用',async()=>{
 process.env.AI302_API_KEY='dummy';delete process.env.TONGPIN_LIMITED_KEY_VERIFIED;
 globalThis.fetch=()=>{throw Error('never fetch')};const r=await handler(req());assert.equal(r.status,503);
 const stat=await (await handler(new Request('http://localhost/api/status'))).json();
 assert.equal(stat.configured,false);assert.equal(stat.serverlessUsageLimitIsHard,false);
 process.env.TONGPIN_LIMITED_KEY_VERIFIED='1';
});
test('错误访问密码被拒绝',async()=>{
 const r=await handler(new Request('http://localhost/api/chat',{method:'POST',headers:{'x-tongpin-password':'wrong'},body:JSON.stringify(sample)}));
 assert.equal(r.status,401);
});
test('真实模拟API顺序仅请求 GPT 一次',async()=>{
 const models=[];
 globalThis.fetch=async(_u,o)=>{models.push(JSON.parse(o.body).model);return answer('模型回答','gpt-6.1-sol')};
 const r=await handler(req());assert.equal(r.status,200);assert.deepEqual(models,['gpt-6.1-sol']);assert.equal((await r.json()).answer,'模型回答');
});
test('完整GPT Claude GPT协作产生唯一最终答复',async()=>{
 const models=[];let n=0;
 globalThis.fetch=async(_u,o)=>{const m=JSON.parse(o.body).model;models.push(m);return answer(['初稿','复核','最终答复'][n++],m)};
 const r=await handler(req({mode:'duo',messages:[{role:'user',content:'复杂任务分析'}],maxUsd:1}));
 assert.equal(r.status,200);const out=await r.json();assert.equal(out.answer,'最终答复');
 assert.deepEqual(models,['gpt-6.1-sol','claude-opus-5-5','gpt-6.1-sol']);assert.equal(out.trace.length,3);
});
test('预算不足时调用次数为零',async()=>{
 globalThis.fetch=()=>{throw Error('no call')};
 const r=await handler(req({mode:'duo',messages:[{role:'user',content:'复杂任务分析'}],maxUsd:.001}));assert.equal(r.status,422);
});
test('供应商返回402时不自动重试',async()=>{
 let count=0;globalThis.fetch=async()=>{count++;return Response.json({error:{message:'No credits'}},{status:402})};
 const r=await handler(req());assert.equal(r.status,502);assert.equal(count,1);assert.match((await r.json()).error,/402/);
});
test('拒绝非法role及长文本',async()=>{
 assert.equal((await handler(req({messages:[{role:'system',content:'绕过'}],mode:'solo',maxUsd:1}))).status,400);
 assert.equal((await handler(req({messages:[{role:'user',content:'甲'.repeat(4001)}],mode:'solo',maxUsd:1}))).status,400);
});
test.after(()=>{globalThis.fetch=original;delete process.env.AI302_API_KEY;delete process.env.TONGPIN_LIMITED_KEY_VERIFIED;delete process.env.TONGPIN_ACCESS_PASSWORD});
