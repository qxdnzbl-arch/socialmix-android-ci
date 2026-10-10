import os,sys,time,uuid,json,hashlib,base64,struct,subprocess,sqlite3,pathlib,urllib.request
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

OUT=pathlib.Path("dualphone/accepted")
OUT.mkdir(parents=True,exist_ok=True)
PKG="com.qxdnzbl.shuangjichuan.offline"
BASE="https://oppo-iphone-transfer-qr.onrender.com"
CODE="ABCDEFGHJKLMNPQRSTUV"
PEER="00000000-0000-0000-0000-000000000001"
assert len(CODE)==20

def hash_for(which):
    return hashlib.sha256(("SJC-V58-"+which+"|"+CODE).encode()).digest()
KEY=hash_for("aes-gcm")
TOKEN=hash_for("relay-auth").hex()
ROOM=hashlib.sha256(TOKEN.encode()).hexdigest()[:32]
AAD=b"shuangjichuan:e2e:v58"

def enc(plain):
    nonce=os.urandom(12)
    return nonce+AESGCM(KEY).encrypt(nonce,plain,AAD)

def post(kind,data):
    headers={
      "x-dual-token":TOKEN,"x-dual-kind":kind,
      "x-dual-id":str(uuid.uuid4()),"x-dual-created":str(int(time.time()*1000)),
      "Content-Type":"application/octet-stream"
    }
    if kind=="file": headers["x-dual-file-name"]=base64.b64encode(b"encrypted.sjc").decode()
    req=urllib.request.Request(BASE+"/api/dual/send/"+ROOM+"/qa-remote-device",
      method="POST",data=data,headers=headers)
    import urllib.error
    for attempt in range(35):
        try:
            with urllib.request.urlopen(req,timeout=75) as r:
                assert r.status==200
                return
        except urllib.error.HTTPError as exc:
            body=exc.read().decode(errors="replace")
            if exc.code!=409: raise
            print("relay peer waiting",attempt,body,flush=True)
            if attempt==34:
                (OUT/"relay-failed-logcat.txt").write_bytes(adb("logcat","-d","-s","DualE2E:W","AndroidRuntime:E"))
                (OUT/"relay-failed-services.txt").write_bytes(adb("shell","dumpsys","activity","services",PKG))
                raise AssertionError("paired Android receiver remained offline after retry") from exc
            time.sleep(.7)

def adb(*args):
    return subprocess.run(["adb",*args],check=True,capture_output=True).stdout

def rows():
    folder=OUT/"state";folder.mkdir(exist_ok=True)
    for name in ("shuangjichuan.db","shuangjichuan.db-wal","shuangjichuan.db-shm"):
        remote="/data/data/"+PKG+"/databases/"+name
        try:(folder/name).write_bytes(adb("exec-out","cat",remote))
        except Exception:pass
    with sqlite3.connect(folder/"shuangjichuan.db") as db:
        return db.execute("SELECT id,kind,status,file_path FROM messages WHERE mine=0").fetchall()

def wait_for(identifier,timeout=50):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        match=[r for r in rows() if r[0]==identifier]
        if match:return match[0]
        time.sleep(1.5)
    raise AssertionError("Missing decrypted received message "+identifier+" rows="+repr(rows()))

# Forged plaintext from an untrusted copy of the old app must not enter the database.
plain_id=str(uuid.uuid4())
post("text",json.dumps({"op":"text","sender":PEER,"id":plain_id,"text":"INSECURE"}).encode())
time.sleep(2)
assert not any(r[0]==plain_id for r in rows()),"plaintext accepted!"

# A valid ciphertext from a third unpaired device must not be added.
third_id=str(uuid.uuid4())
wrong_sender="00000000-0000-0000-0000-000000000999"
obj={"op":"text","sender":wrong_sender,"id":third_id,"created":int(time.time()*1000),"text":"REJECT_THIS"}
post("text",b"SJC58:"+base64.b64encode(enc(json.dumps(obj).encode())))
time.sleep(2)
assert not any(r[0]==third_id for r in rows()),"third device accepted!"

message_id=str(uuid.uuid4())
message={"op":"text","sender":PEER,"id":message_id,"created":int(time.time()*1000),"text":"private paired text accepted"}
post("text",b"SJC58:"+base64.b64encode(enc(json.dumps(message).encode())))
m=wait_for(message_id)
assert m[1]=="text" and m[2]=="received"

file_id=str(uuid.uuid4())
original=os.urandom(4_900_000)
metadata={"op":"file","sender":PEER,"id":file_id,"created":int(time.time()*1000),
  "name":"secured-4.9MB.jpg","size":len(original),"sha":hashlib.sha256(original).hexdigest()}
meta=json.dumps(metadata,separators=(",",":"),ensure_ascii=False).encode()
container=enc(struct.pack(">I",len(meta))+meta+original)
start=time.monotonic()
post("file",container)
file_record=wait_for(file_id)
assert file_record[1]=="file" and file_record[2]=="received",file_record
received=adb("exec-out","cat",file_record[3])
assert received==original,"image changed or truncated"
secs=round(time.monotonic()-start,3)
print("PASS: E2E Android receiver saved 4,900,000 original bytes:",secs,"seconds")
# Replayed message must not create duplicate rows.
post("text",b"SJC58:"+base64.b64encode(enc(json.dumps(message).encode())))
time.sleep(2)
assert sum(1 for r in rows() if r[0]==message_id)==1,"duplicate message"
notification=adb("shell","dumpsys","notification","--noredact").decode(errors="replace")
(OUT/"notifications.txt").write_text(notification)
assert "dual_incoming_v1" in notification or "双机传" in notification,"missing notification channel"
(OUT/"private-e2e-result.json").write_text(json.dumps({
  "encrypted":True,"peer_locked":True,"plaintext_rejected":True,"foreign_sender_rejected":True,
  "text_received":True,"image_bytes":len(original),"image_integrity_sha256":hashlib.sha256(received).hexdigest(),
  "relay_transfer_seconds":secs,"replay_deduplicated":True},indent=2))
print("PASS: Unpaired traffic rejected, paired E2E image intact, Android notifications exist")
