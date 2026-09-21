import http from "node:http";
const APP="https://kehua-live.onrender.com";
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
http.createServer(async(req,res)=>{
  if(req.url==="/refresh") latest=await check();
  res.writeHead(latest.ok?200:500,{"content-type":"application/json; charset=utf-8"});
  res.end(JSON.stringify(latest));
}).listen(process.env.PORT||10000,()=>console.log("verifier listening"));