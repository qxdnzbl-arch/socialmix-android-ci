import hashlib, socket, struct, sys, time

port=int(sys.argv[1])
token=hashlib.sha256(b"testlocal\npass1234").hexdigest()

def ws(sock, value):
    b=value.encode("utf-8")
    sock.sendall(struct.pack(">I",len(b))+b)

s=socket.create_connection(("127.0.0.1",port),timeout=5)
s.sendall(struct.pack(">I",0x534A4331))
ws(s,token)
ws(s,"ci-incoming")
ws(s,"mock-peer")
s.sendall(struct.pack(">q",int(time.time()*1000)))
s.sendall(b"\x01")
ws(s,"离线测试消息")
ack=struct.unpack(">I",s.recv(4))[0]
assert ack==1, ack
s.close()
