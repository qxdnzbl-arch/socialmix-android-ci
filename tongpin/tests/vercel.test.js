import test from 'node:test';
import assert from 'node:assert/strict';
import {Readable} from 'node:stream';
import status from '../api/status.js';
import chat from '../api/chat.js';
const mock=()=>({statusCode:200,headersSent:false,headers:{},body:'',setHeader(k,v){this.headers[k]=v},end(v){this.body=Buffer.from(v).toString('utf8');this.headersSent=true},status(n){this.statusCode=n;return this},json(x){this.body=JSON.stringify(x);this.headersSent=true;return this}});
test('Vercel status 不透露密钥和内部预算为硬上限',async()=>{
 delete process.env.AI302_API_KEY;delete process.env.TONGPIN_LIMITED_KEY_VERIFIED;
 const r=mock();await status({method:'GET',headers:{}},r);
 assert.equal(r.statusCode,200);assert.equal(JSON.parse(r.body).configured,false);assert.equal(JSON.parse(r.body).serverlessUsageLimitIsHard,false);
});
test('Vercel 自动解析JSON请求正确转发',async()=>{
 process.env.TONGPIN_ACCESS_PASSWORD='strong-test-password';
 const r=mock();await chat({method:'POST',headers:{'x-tongpin-password':'strong-test-password'},body:{messages:[{role:'user',content:'你好'}],mode:'solo',maxUsd:1}},r);
 assert.equal(r.statusCode,503);assert.match(JSON.parse(r.body).error,/密钥/);
});
test('Vercel 原始流式请求正确转发',async()=>{
 const req=Readable.from([Buffer.from(JSON.stringify({messages:[{role:'user',content:'你好'}],mode:'solo',maxUsd:1}))]);
 req.method='POST';req.headers={'x-tongpin-password':'strong-test-password'};
 const r=mock();await chat(req,r);assert.equal(r.statusCode,503);assert.match(JSON.parse(r.body).error,/密钥/);
});
