import http from "node:http";
import net from "node:net";
import crypto from "node:crypto";

const TARGET="https://kehua-original.onrender.com";
const RPC="https://nvwdtfnhsyfdopaxdylx.supabase.co/rest/v1/rpc/kehua_prod_selftest";
const KEY="sb_publishable_S4IE-ziO7WQ_JAK9tuQGgQ_cszwKBWB";
const VPN_UUID=(process.env.VPN_UUID||"").toLowerCase();
const VPN_PATH=process.env.VPN_PATH||"";

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

function uuidFromBytes(buf){
  const h=buf.toString("hex");
  return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;
}

function parseVlessHeader(buf){
  if(buf.length<18) return null;
  const version=buf[0];
  const uuid=uuidFromBytes(buf.subarray(1,17)).toLowerCase();
  const addonLen=buf[17];
  let offset=18+addonLen;
  if(buf.length<offset+4) return null;
  const command=buf[offset++];
  const port=buf.readUInt16BE(offset); offset+=2;
  const addrType=buf[offset++];
  let host;
  if(addrType===1){
    if(buf.length<offset+4) return null;
    host=[...buf.subarray(offset,offset+4)].join(".");
    offset+=4;
  }else if(addrType===2){
    if(buf.length<offset+1) return null;
    const len=buf[offset++];
    if(buf.length<offset+len) return null;
    host=buf.subarray(offset,offset+len).toString("utf8");
    offset+=len;
  }else if(addrType===3){
    if(buf.length<offset+16) return null;
    const parts=[];
    for(let i=0;i<16;i+=2) parts.push(buf.readUInt16BE(offset+i).toString(16));
    host=parts.join(":");
    offset+=16;
  }else{
    throw new Error("unsupported address type");
  }
  return {version,uuid,command,port,host,offset};
}

function makeWsFrame(payload,opcode=2){
  if(!Buffer.isBuffer(payload)) payload=Buffer.from(payload);
  const len=payload.length;
  let header;
  if(len<126){
    header=Buffer.from([0x80|opcode,len]);
  }else if(len<=0xffff){
    header=Buffer.alloc(4);
    header[0]=0x80|opcode;
    header[1]=126;
    header.writeUInt16BE(len,2);
  }else{
    header=Buffer.alloc(10);
    header[0]=0x80|opcode;
    header[1]=127;
    header.writeBigUInt64BE(BigInt(len),2);
  }
  return Buffer.concat([header,payload]);
}

function attachVlessWebSocket(req,client){
  if(!VPN_UUID||!VPN_PATH) return client.destroy();
  const path=(req.url||"").split("?")[0];
  if(path!==VPN_PATH) return client.destroy();
  if(String(req.headers.upgrade||"").toLowerCase()!=="websocket") return client.destroy();
  const wsKey=req.headers["sec-websocket-key"];
  if(typeof wsKey!=="string") return client.destroy();

  const accept=crypto.createHash("sha1")
    .update(wsKey+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
    .digest("base64");
  client.write(
    "HTTP/1.1 101 Switching Protocols\r\n"+
    "Upgrade: websocket\r\n"+
    "Connection: Upgrade\r\n"+
    "Sec-WebSocket-Accept: "+accept+"\r\n\r\n"
  );
  client.setNoDelay(true);

  let raw=Buffer.alloc(0);
  let vless=Buffer.alloc(0);
  let target=null;
  let connecting=false;
  let closed=false;
  let fragment=null;

  const closeAll=()=>{
    if(closed) return;
    closed=true;
    try{target?.destroy();}catch{}
    try{client.destroy();}catch{}
  };

  const sendBinary=(data)=>{
    if(closed||client.destroyed) return;
    try{client.write(makeWsFrame(data,2));}catch{closeAll();}
  };

  const openTarget=(parsed)=>{
    if(parsed.uuid!==VPN_UUID) throw new Error("unauthorized");
    if(parsed.command!==1) throw new Error("tcp only");
    connecting=true;
    const first=vless.subarray(parsed.offset);
    vless=Buffer.alloc(0);
    target=net.createConnection({host:parsed.host,port:parsed.port});
    target.setNoDelay(true);
    target.setTimeout(15000);
    target.once("connect",()=>{
      target.setTimeout(0);
      connecting=false;
      sendBinary(Buffer.from([parsed.version,0]));
      if(first.length) target.write(first);
    });
    target.on("data",sendBinary);
    target.on("end",closeAll);
    target.on("close",closeAll);
    target.on("error",closeAll);
    target.on("timeout",closeAll);
  };

  const consumeVless=(payload)=>{
    if(target){
      if(!target.destroyed) target.write(payload);
      return;
    }
    vless=Buffer.concat([vless,payload]);
    if(vless.length>65536) return closeAll();
    if(connecting) return;
    try{
      const parsed=parseVlessHeader(vless);
      if(parsed) openTarget(parsed);
    }catch{
      closeAll();
    }
  };

  const handleMessage=(opcode,fin,payload)=>{
    if(opcode===8) return closeAll();
    if(opcode===9){
      try{client.write(makeWsFrame(payload,10));}catch{closeAll();}
      return;
    }
    if(opcode===10) return;
    if(opcode===2){
      if(fin) consumeVless(payload);
      else fragment=Buffer.from(payload);
      return;
    }
    if(opcode===0&&fragment){
      fragment=Buffer.concat([fragment,payload]);
      if(fragment.length>16*1024*1024) return closeAll();
      if(fin){
        const msg=fragment;
        fragment=null;
        consumeVless(msg);
      }
    }
  };

  const parseFrames=()=>{
    while(raw.length>=2){
      const b0=raw[0],b1=raw[1];
      const fin=(b0&0x80)!==0;
      const opcode=b0&0x0f;
      const masked=(b1&0x80)!==0;
      let len=b1&0x7f;
      let offset=2;
      if(len===126){
        if(raw.length<4) return;
        len=raw.readUInt16BE(2); offset=4;
      }else if(len===127){
        if(raw.length<10) return;
        const n=raw.readBigUInt64BE(2);
        if(n>BigInt(16*1024*1024)) return closeAll();
        len=Number(n); offset=10;
      }
      if(!masked) return closeAll();
      if(raw.length<offset+4+len) return;
      const mask=raw.subarray(offset,offset+4);
      offset+=4;
      const payload=Buffer.from(raw.subarray(offset,offset+len));
      for(let i=0;i<payload.length;i++) payload[i]^=mask[i&3];
      raw=raw.subarray(offset+len);
      handleMessage(opcode,fin,payload);
      if(closed) return;
    }
  };

  client.on("data",(chunk)=>{
    raw=Buffer.concat([raw,chunk]);
    if(raw.length>20*1024*1024) return closeAll();
    parseFrames();
  });
  client.on("error",closeAll);
  client.on("end",closeAll);
  client.on("close",()=>{ try{target?.destroy();}catch{} });
}

const port=Number(process.env.PORT||10000);
const server=http.createServer((req,res)=>{
  res.writeHead(result.ok?200:500,{"content-type":"application/json; charset=utf-8"});
  res.end(JSON.stringify(result));
});
server.on("upgrade",(req,socket)=>attachVlessWebSocket(req,socket));
server.listen(port,()=>console.log("verifier listening "+port));
