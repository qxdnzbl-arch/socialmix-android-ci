import http from "node:http";
const TARGET="https://kehua-original-live.onrender.com";
const RPC="https://nvwdtfnhsyfdopaxdylx.supabase.co/rest/v1/rpc/kehua_prod_selftest";
const KEY="sb_publishable_S4IE-ziO7WQ_JAK9tuQGgQ_cszwKBWB";
let result={ok:false,stage:"starting"};
try{
  const page=await fetch(TARGET,{redirect:"follow"});
  const html=await page.text();
  const db=await fetch(RPC,{method:"POST",headers:{"content-type":"application/json","apikey":KEY,"authorization":"Bearer "+KEY},body:"{}"});
  const dbResult=await db.json();
  const dbOk=!!dbResult&&dbResult.ok===true&&dbResult.same_account_two_sessions===true&&dbResult.friend_sync===true&&dbResult.chat_sync===true;
  result={
    ok:page.ok&&html.includes("可话~!")&&html.includes("此刻，说你想说的话~")&&db.ok&&dbOk,
    public_http:page.status,
    login_ui:html.includes("手机号登录"),
    home_ui:html.includes("此刻，说你想说的话~"),
    rpc_http:db.status,
    same_account_two_sessions:!!dbResult.same_account_two_sessions,
    friend_sync:!!dbResult.friend_sync,
    chat_sync:!!dbResult.chat_sync,
    checked_at:new Date().toISOString()
  };
  console.log("VERIFY_RESULT "+JSON.stringify(result));
}catch(e){
  result={ok:false,error:String(e),checked_at:new Date().toISOString()};
  console.error("VERIFY_RESULT "+JSON.stringify(result));
}
const port=Number(process.env.PORT||10000);
http.createServer((req,res)=>{
  res.writeHead(result.ok?200:500,{"content-type":"application/json; charset=utf-8"});
  res.end(JSON.stringify(result));
}).listen(port,()=>console.log("verifier listening "+port));
