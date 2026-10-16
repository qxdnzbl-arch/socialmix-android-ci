import { createServer } from 'node:http';

const UPSTREAM = new URL('https://hqjwhucxpafwpolubhdy.supabase.co');
const PUBKEY = 'sb_publishable_7ri__qZ4EUYI4bbrrGJBUA_-nVfU5JT';
const API_PATH = /^\/(?:auth|rest|storage|functions)\/v1(?:\/|$)/;
const METHODS = new Set(['GET','POST','PUT','PATCH','DELETE','HEAD']);
const REQUEST_HEADERS = ['apikey','authorization','content-type','accept','prefer','x-client-info','range','content-range','if-match','if-none-match','x-upsert'];
const RESPONSE_HEADERS = ['content-type','cache-control','content-disposition','etag','last-modified','location','content-range','accept-ranges','vary','x-total-count'];
const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS',
  'Access-Control-Allow-Headers': 'apikey,authorization,content-type,accept,prefer,x-client-info,range,content-range,if-match,if-none-match,x-upsert',
  'Access-Control-Expose-Headers': 'content-type,content-range,x-total-count',
  'Cache-Control': 'no-store',
};
function reply(res, status, payload, headers = {}) {
  res.writeHead(status, {...cors, 'Content-Type':'application/json; charset=utf-8',...headers});
  res.end(JSON.stringify(payload));
}
async function readBody(req, limit=2_000_000) {
  const chunks=[];let size=0;
  for await(const chunk of req){size+=chunk.length;if(size>limit)throw new Error('BODY_TOO_LARGE');chunks.push(chunk);}
  return Buffer.concat(chunks);
}
const server=createServer(async (req,res)=>{
  if(req.method==='OPTIONS'){res.writeHead(204,cors);return res.end();}
  const url = new URL(req.url ?? '/', 'http://localhost');
  if(req.method==='GET'&&url.pathname==='/health'){
    try {
      const r=await fetch(new URL('/auth/v1/health',UPSTREAM),{headers:{'apikey':PUBKEY},redirect:'manual',signal:AbortSignal.timeout(12000)});
      const type=r.headers.get('content-type')??'';
      if(r.status!==200||!type.includes('application/json'))return reply(res,503,{status:'upstream_unhealthy'});
      return reply(res,200,{status:'ok',backend:'zhiyin',upstream:'healthy'});
    }catch{return reply(res,503,{status:'upstream_unavailable'});}
  }
  if(!API_PATH.test(url.pathname)||!METHODS.has(req.method??''))return reply(res,404,{code:'not_found',message:'Unknown API endpoint'});
  // The app has a public Supabase key. Require it to limit unintended traffic.
  if(req.headers.apikey!==PUBKEY)return reply(res,401,{code:'missing_api_key',message:'Unrecognized API key'});
  const target = new URL(url.pathname+url.search,UPSTREAM);
  const headers = new Headers();
  for(const key of REQUEST_HEADERS){
    const value=req.headers[key];
    if(typeof value==='string')headers.set(key,value);
  }
  let body;
  try{if(req.method!=='GET'&&req.method!=='HEAD')body=await readBody(req);}
  catch{return reply(res,413,{code:'payload_too_large',message:'Request exceeded the size limit'});}
  try{
    const upstream=await fetch(target,{
      method:req.method,
      headers,
      body:body?.length?body:undefined,
      redirect:'manual',
      signal:AbortSignal.timeout(22000),
    });
    const type=upstream.headers.get('content-type')??'';
    // Supabase auth API MUST return JSON. HTML is an upstream gateway error,
    // never a login/signup result; no HTML or account secrets are logged.
    if(url.pathname.startsWith('/auth/v1/')&&
       !type.includes('application/json')&&
       ![301,302,303,307,308].includes(upstream.status)){
      return reply(res,502,{code:'auth_backend_non_json',message:'Authentication server returned an invalid response'});
    }
    const responseHeaders={...cors};
    for(const key of RESPONSE_HEADERS){
      const value=upstream.headers.get(key);
      if(value)responseHeaders[key]=value;
    }
    // Don't forward compressed-content headers; fetch transparently decompresses.
    res.writeHead(upstream.status,responseHeaders);
    const data=Buffer.from(await upstream.arrayBuffer());
    res.end(data);
  }catch{
    reply(res,503,{code:'backend_connection_failed',message:'Backend temporarily unavailable'});
  }
});
server.listen(Number(process.env.PORT||10000),'0.0.0.0');
