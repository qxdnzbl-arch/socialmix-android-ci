import http from "node:http";
const TARGET="https://kehua-original-live.onrender.com";
let result={ok:false,stage:"starting"};
try{
  const page=await fetch(TARGET,{redirect:"follow"});
  const html=await page.text();
  result={
    ok:page.ok&&html.includes("可话~!")&&html.includes("此刻，说你想说的话~"),
    public_http:page.status,
    login_ui:html.includes("手机号登录"),
    home_ui:html.includes("此刻，说你想说的话~"),
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
