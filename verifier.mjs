import http from "node:http";
import { handleEvidence } from "./evidence.mjs";
const APP="https://kehua-app-public.onrender.com";
const SB="https://lzylcqozczsaxtdqfhrs.supabase.co";
const KEY="sb_publishable_Wu7Xa-2bx6QARotVaTX_8g_yBlToQ-e";
async function check(){
  const out={checked_at:new Date().toISOString(),ok:false};
  try{
    const app=await fetch(APP,{redirect:"follow"});
    const html=await app.text();
    const js=await fetch(APP+"/app.js",{redirect:"follow"});
    const jsText=await js.text();
    const auth=await fetch(SB+"/auth/v1/settings",{headers:{apikey:KEY}});
    const authText=await auth.text();
    const rest=await fetch(SB+"/rest/v1/",{headers:{apikey:KEY}});
    const restText=await rest.text();
    Object.assign(out,{
      app_http:app.status,
      app_has_kehua:html.includes("可话"),
      js_http:js.status,
      js_has_client:jsText.includes("createClient"),
      auth_http:auth.status,
      auth_body:authText.slice(0,300),
      rest_http:rest.status,
      rest_body:restText.slice(0,300)
    });
    out.ok=app.ok&&out.app_has_kehua&&js.ok&&out.js_has_client&&auth.status<500&&rest.status<500;
  }catch(e){out.error=String(e)}
  console.log("VERIFY_RESULT "+JSON.stringify(out));
  return out;
}
let latest=await check();
setInterval(async()=>{latest=await check()},60000);
const server=http.createServer(async(req,res)=>{
  if((req.url||"").startsWith("/evidence")) return handleEvidence(req,res);
  if(req.url==="/refresh") latest=await check();
  res.writeHead(latest.ok?200:500,{"content-type":"application/json; charset=utf-8"});
  res.end(JSON.stringify(latest));
});
const PORT=Number(process.env.PORT||10000);
server.listen(PORT,async()=>{
  console.log("verifier listening");
  try{
    const page=await fetch(`http://127.0.0.1:${PORT}/evidence`);
    const pageText=await page.text();
    const health=await fetch(`http://127.0.0.1:${PORT}/evidence/health`);
    const verify=await fetch(`http://127.0.0.1:${PORT}/evidence/api/verify?q=${encodeURIComponent("维生素C可以预防普通感冒吗")}`);
    const data=await verify.json();
    console.log("EVIDENCE_SELFTEST "+JSON.stringify({
      page_http:page.status,
      page_has_quote:pageText.includes("如果你说是真的，那请你拿出证据来"),
      page_has_api_path:pageText.includes("/evidence/api/verify"),
      health_http:health.status,
      verify_http:verify.status,
      conclusion:data.conclusion||null,
      confidence:data.confidence||null,
      source_count:Number(data.source_count||data.sources?.length||0),
      evidence_count:Array.isArray(data.evidence)?data.evidence.length:0,
      source_urls_ok:Array.isArray(data.sources)&&data.sources.length>0&&data.sources.every(x=>/^https?:\/\//.test(x.url||"")),
      synthesis:data.synthesis||null,
      search_errors:data.search_errors||[]
    }));
  }catch(e){console.error("EVIDENCE_SELFTEST_ERROR "+String(e))}
});