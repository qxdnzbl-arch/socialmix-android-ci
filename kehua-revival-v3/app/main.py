from __future__ import annotations
import os,random,secrets
from pathlib import Path
from typing import Dict,List,Optional
from fastapi import Depends,FastAPI,Header,HTTPException,WebSocket,WebSocketDisconnect
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel,Field
from sqlalchemy import or_,select
from sqlalchemy.orm import Session
from .db import *
from .core import *
ROOT=Path(__file__).resolve().parent
app=FastAPI(title='说想说的话');app.mount('/static',StaticFiles(directory=ROOT/'static'),name='static')
class AuthIn(BaseModel):username:str=Field(min_length=3,max_length=24);password:str=Field(min_length=6,max_length=72)
class RecoveryIn(BaseModel):username:str=Field(min_length=3,max_length=24);recovery_code:str=Field(min_length=8,max_length=64);new_password:str=Field(min_length=6,max_length=72)
class PostIn(BaseModel):body:str=Field(min_length=1,max_length=1200);image_data:Optional[str]=None;is_private:bool=False
class MessageIn(BaseModel):body:str=Field(min_length=1,max_length=2000)
class ProfileIn(BaseModel):nickname:str=Field(min_length=1,max_length=20);bio:str=Field(default='',max_length=120);avatar_data:Optional[str]=None
class ReportIn(BaseModel):reason:str=Field(min_length=2,max_length=160)
@app.get('/')
def index():return FileResponse(ROOT/'static'/'index.html')
@app.get('/manifest.webmanifest')
def manifest():return FileResponse(ROOT/'static'/'manifest.webmanifest',media_type='application/manifest+json')
@app.get('/sw.js')
def sw():return FileResponse(ROOT/'static'/'sw.js',media_type='application/javascript')
@app.get('/health')
def health():return {'ok':True,'database':'postgres' if 'postgres' in DB_URL else 'sqlite','matching':'symmetric-threshold'}
@app.post('/api/auth/register')
def register(p:AuthIn,db:Session=Depends(db_session)):
 username=p.username.strip().lower()
 if not username.replace('_','').isalnum():raise HTTPException(400,'用户名只用字母、数字或下划线')
 if db.scalar(select(User).where(User.username==username)):raise HTTPException(409,'这个用户名已经有人用了')
 recovery=secrets.token_hex(10).upper();u=User(username=username,password_hash=password_hash(p.password),recovery_hash=token_hash(recovery),nickname=username,avatar_seed=secrets.token_hex(5));db.add(u);db.flush();t=issue_session(db,u);return {'token':t,'recovery_code':recovery,'user':user_json(u,True)}
@app.post('/api/auth/login')
def login(p:AuthIn,db:Session=Depends(db_session)):
 u=db.scalar(select(User).where(User.username==p.username.strip().lower()))
 if not u or not u.password_hash or not verify_password(p.password,u.password_hash):raise HTTPException(401,'用户名或密码不对')
 return {'token':issue_session(db,u),'user':user_json(u,True)}
@app.post('/api/auth/recover')
def recover(p:RecoveryIn,db:Session=Depends(db_session)):
 username=p.username.strip().lower();u=db.scalar(select(User).where(User.username==username))
 if not u or not u.recovery_hash or not secrets.compare_digest(u.recovery_hash,token_hash(p.recovery_code.strip().upper())):raise HTTPException(401,'账号或恢复码不正确')
 u.password_hash=password_hash(p.new_password);new_code=secrets.token_hex(10).upper();u.recovery_hash=token_hash(new_code)
 for s in db.scalars(select(SessionToken).where(SessionToken.user_id==u.id)).all():db.delete(s)
 db.flush();t=issue_session(db,u);return {'token':t,'recovery_code':new_code,'user':user_json(u,True)}
@app.post('/api/auth/guest')
def guest(db:Session=Depends(db_session)):
 t=secrets.token_urlsafe(32);u=User(nickname=f'未命名{random.randint(100,999)}',avatar_seed=secrets.token_hex(5));db.add(u);db.flush();db.add(SessionToken(user_id=u.id,token_hash=token_hash(t)));db.commit();return {'token':t,'user':user_json(u,True)}
@app.post('/api/auth/logout')
def logout(authorization:str|None=Header(default=None),db:Session=Depends(db_session)):
 if authorization and authorization.lower().startswith('bearer '):
  row=db.scalar(select(SessionToken).where(SessionToken.token_hash==token_hash(authorization.split(' ',1)[1].strip())))
  if row:db.delete(row);db.commit()
 return {'ok':True}
@app.get('/api/me')
def me(user:User=Depends(current_user),db:Session=Depends(db_session)):
 posts=db.scalars(select(Post).where(Post.user_id==user.id,Post.active==True).order_by(Post.created_at.desc())).all();lights=db.scalars(select(Light).where(Light.actor_user_id==user.id).order_by(Light.created_at.desc())).all();lit=[]
 for l in lights:
  q=db.get(Post,l.target_post_id)
  if q:lit.append(serialize_post(q))
 return {**user_json(user,True),'posts':[serialize_post(x) for x in posts],'lit_posts':lit}
@app.delete('/api/me')
def delete_me(user:User=Depends(current_user),db:Session=Depends(db_session)):
 db.delete(user);db.commit();return {'ok':True}
@app.post('/api/me/recovery')
def rotate_recovery(user:User=Depends(current_user),db:Session=Depends(db_session)):
 code=secrets.token_hex(10).upper();user.recovery_hash=token_hash(code);db.commit();return {'recovery_code':code}
@app.patch('/api/me')
def update_me(p:ProfileIn,user:User=Depends(current_user),db:Session=Depends(db_session)):
 if p.avatar_data and (not p.avatar_data.startswith('data:image/') or len(p.avatar_data)>MAX_IMAGE_CHARS):raise HTTPException(400,'头像图片过大')
 user.nickname=p.nickname.strip();user.bio=p.bio.strip();user.avatar_data=p.avatar_data;db.commit();return user_json(user,True)
@app.post('/api/posts')
def create_post(p:PostIn,user:User=Depends(current_user),db:Session=Depends(db_session)):
 if p.image_data and (not p.image_data.startswith('data:image/') or len(p.image_data)>MAX_IMAGE_CHARS):raise HTTPException(400,'图片过大')
 q=Post(user_id=user.id,body=p.body.strip(),image_data=p.image_data,is_private=p.is_private);db.add(q);db.commit();db.refresh(q);refresh_matches_around(db,q);cnt=len(db.scalars(select(Match).where(Match.source_post_id==q.id,Match.skipped==False)).all());return serialize_post(q,cnt)
@app.get('/api/posts/mine')
def my_posts(user:User=Depends(current_user),db:Session=Depends(db_session)):
 rows=db.scalars(select(Post).where(Post.user_id==user.id,Post.active==True).order_by(Post.created_at.desc())).all();return [serialize_post(p,len(db.scalars(select(Match).where(Match.source_post_id==p.id,Match.skipped==False)).all())) for p in rows]
@app.delete('/api/posts/{post_id}')
def delete_post(post_id:int,user:User=Depends(current_user),db:Session=Depends(db_session)):
 p=db.get(Post,post_id)
 if not p or p.user_id!=user.id:raise HTTPException(404)
 p.active=False;db.commit();return {'ok':True}
@app.get('/api/posts/{post_id}/resonances')
def resonances(post_id:int,user:User=Depends(current_user),db:Session=Depends(db_session)):
 p=db.get(Post,post_id)
 if not p or p.user_id!=user.id:raise HTTPException(404)
 out=[]
 for m in db.scalars(select(Match).where(Match.source_post_id==post_id,Match.skipped==False).order_by(Match.score.desc())).all():
  target=db.get(Post,m.target_post_id);other=db.get(User,target.user_id) if target and target.active else None
  if not target or not other:continue
  item={'match_id':m.id,'post_id':target.id,'body':target.body,'image_data':target.image_data,'score':round(m.score,3),'lit':m.lit,'is_demo':other.is_demo}
  if m.lit:item['user']={**user_json(other),'friend_status':friend_status(db,user.id,other.id)}
  out.append(item)
 return out
@app.post('/api/matches/{match_id}/skip')
def skip(match_id:int,user:User=Depends(current_user),db:Session=Depends(db_session)):
 m=db.get(Match,match_id);s=db.get(Post,m.source_post_id) if m else None
 if not m or not s or s.user_id!=user.id:raise HTTPException(404)
 m.skipped=True;db.commit();return {'ok':True}
@app.post('/api/matches/{match_id}/light')
def light(match_id:int,user:User=Depends(current_user),db:Session=Depends(db_session)):
 m=db.get(Match,match_id);s=db.get(Post,m.source_post_id) if m else None;t=db.get(Post,m.target_post_id) if m else None
 if not m or not s or not t or s.user_id!=user.id:raise HTTPException(404)
 other=db.get(User,t.user_id);c=conversation_for(db,user.id,other.id);m.lit=True
 if not db.scalar(select(Light).where(Light.actor_user_id==user.id,Light.source_post_id==s.id,Light.target_post_id==t.id)):
  db.add(Light(actor_user_id=user.id,target_user_id=other.id,source_post_id=s.id,target_post_id=t.id,conversation_id=c.id));db.add(Notification(user_id=other.id,actor_id=user.id,kind='light',text='有人点亮了你的一句话',conversation_id=c.id))
 db.commit();return {'conversation_id':c.id,'user':{**user_json(other),'friend_status':friend_status(db,user.id,other.id)}}
@app.get('/api/notifications')
def notes(user:User=Depends(current_user),db:Session=Depends(db_session)):
 out=[]
 for n in db.scalars(select(Notification).where(Notification.user_id==user.id).order_by(Notification.created_at.desc()).limit(50)).all():
  a=db.get(User,n.actor_id) if n.actor_id else None;out.append({'id':n.id,'kind':n.kind,'text':n.text,'read':n.read,'created_at':n.created_at.isoformat(),'conversation_id':n.conversation_id,'actor':user_json(a) if a else None})
 return out
@app.post('/api/notifications/{notification_id}/read')
def read_note(notification_id:int,user:User=Depends(current_user),db:Session=Depends(db_session)):
 n=db.get(Notification,notification_id)
 if not n or n.user_id!=user.id:raise HTTPException(404)
 n.read=True;db.commit();return {'ok':True}
@app.get('/api/conversations')
def conversations(user:User=Depends(current_user),db:Session=Depends(db_session)):
 out=[]
 for c in db.scalars(select(Conversation).where(or_(Conversation.user_low==user.id,Conversation.user_high==user.id))).all():
  oid=c.user_high if c.user_low==user.id else c.user_low;o=db.get(User,oid)
  if not o or blocked_pair(db,user.id,oid):continue
  last=db.scalar(select(Message).where(Message.conversation_id==c.id).order_by(Message.created_at.desc()).limit(1));out.append({'id':c.id,'other':{**user_json(o),'friend_status':friend_status(db,user.id,oid)},'last_message':last.body if last else '从一次共鸣开始','updated_at':(last.created_at if last else c.created_at).isoformat()})
 return sorted(out,key=lambda x:x['updated_at'],reverse=True)
@app.get('/api/conversations/{cid}/messages')
def messages(cid:int,user:User=Depends(current_user),db:Session=Depends(db_session)):
 c=db.get(Conversation,cid)
 if not c or user.id not in(c.user_low,c.user_high):raise HTTPException(404)
 return [{'id':m.id,'sender_id':m.sender_id,'body':m.body,'created_at':m.created_at.isoformat()} for m in db.scalars(select(Message).where(Message.conversation_id==cid).order_by(Message.created_at.asc()).limit(400)).all()]
@app.post('/api/conversations/{cid}/messages')
def send_message(cid:int,p:MessageIn,user:User=Depends(current_user),db:Session=Depends(db_session)):
 c=db.get(Conversation,cid)
 if not c or user.id not in(c.user_low,c.user_high):raise HTTPException(404)
 oid=c.user_high if c.user_low==user.id else c.user_low
 if blocked_pair(db,user.id,oid):raise HTTPException(403,'已无法继续联系')
 m=Message(conversation_id=cid,sender_id=user.id,body=p.body.strip());db.add(m);db.add(Notification(user_id=oid,actor_id=user.id,kind='message',text='给你发来一条消息',conversation_id=cid));db.commit();db.refresh(m);return {'id':m.id,'sender_id':m.sender_id,'body':m.body,'created_at':m.created_at.isoformat()}
@app.get('/api/users/{uid}')
def profile(uid:int,user:User=Depends(current_user),db:Session=Depends(db_session)):
 o=db.get(User,uid)
 if not o or blocked_pair(db,user.id,uid):raise HTTPException(404)
 c=conversation_for(db,user.id,uid);db.commit();return {**user_json(o),'friend_status':friend_status(db,user.id,uid),'conversation_id':c.id}
@app.post('/api/users/{uid}/friend-request')
def friend_req(uid:int,user:User=Depends(current_user),db:Session=Depends(db_session)):
 if uid==user.id or not db.get(User,uid):raise HTTPException(404)
 if are_friends(db,user.id,uid):return {'status':'friends'}
 rev=db.scalar(select(FriendRequest).where(FriendRequest.sender_id==uid,FriendRequest.receiver_id==user.id,FriendRequest.status=='pending'))
 if rev:
  rev.status='accepted';low,high=sorted([user.id,uid]);db.add(Friendship(user_low=low,user_high=high));db.add(Notification(user_id=uid,actor_id=user.id,kind='friend',text='你们现在是好友啦'));db.commit();return {'status':'friends'}
 req=db.scalar(select(FriendRequest).where(FriendRequest.sender_id==user.id,FriendRequest.receiver_id==uid))
 if not req:db.add(FriendRequest(sender_id=user.id,receiver_id=uid,status='pending'));db.add(Notification(user_id=uid,actor_id=user.id,kind='friend_request',text='想和你成为好友'));db.commit()
 return {'status':'sent'}
@app.post('/api/friend-requests/{rid}/accept')
def accept_friend(rid:int,user:User=Depends(current_user),db:Session=Depends(db_session)):
 r=db.get(FriendRequest,rid)
 if not r or r.receiver_id!=user.id or r.status!='pending':raise HTTPException(404)
 r.status='accepted';low,high=sorted([r.sender_id,r.receiver_id])
 if not db.scalar(select(Friendship).where(Friendship.user_low==low,Friendship.user_high==high)):db.add(Friendship(user_low=low,user_high=high))
 db.add(Notification(user_id=r.sender_id,actor_id=user.id,kind='friend',text='你们现在是好友啦'));db.commit();return {'ok':True}
@app.get('/api/friend-requests')
def requests(user:User=Depends(current_user),db:Session=Depends(db_session)):
 return [{'id':r.id,'user':user_json(db.get(User,r.sender_id)),'created_at':r.created_at.isoformat()} for r in db.scalars(select(FriendRequest).where(FriendRequest.receiver_id==user.id,FriendRequest.status=='pending').order_by(FriendRequest.created_at.desc())).all()]
@app.get('/api/friends')
def friends(user:User=Depends(current_user),db:Session=Depends(db_session)):
 out=[]
 for f in db.scalars(select(Friendship).where(or_(Friendship.user_low==user.id,Friendship.user_high==user.id))).all():
  oid=f.user_high if f.user_low==user.id else f.user_low
  o=db.get(User,oid)
  if o and not blocked_pair(db,user.id,oid):out.append(user_json(o))
 return out
@app.get('/api/friends/latest')
def friends_latest(user:User=Depends(current_user),db:Session=Depends(db_session)):
 out=[]
 for f in db.scalars(select(Friendship).where(or_(Friendship.user_low==user.id,Friendship.user_high==user.id))).all():
  oid=f.user_high if f.user_low==user.id else f.user_low;u=db.get(User,oid);p=db.scalar(select(Post).where(Post.user_id==oid,Post.active==True,Post.is_private==False).order_by(Post.created_at.desc()).limit(1))
  if u and p:out.append({'user':user_json(u),'post':serialize_post(p)})
 return out[:12]
@app.post('/api/users/{uid}/block')
def block(uid:int,user:User=Depends(current_user),db:Session=Depends(db_session)):
 if uid==user.id:raise HTTPException(400)
 if not db.scalar(select(Block).where(Block.blocker_id==user.id,Block.blocked_id==uid)):db.add(Block(blocker_id=user.id,blocked_id=uid));db.commit()
 return {'ok':True}
@app.post('/api/users/{uid}/report')
def report(uid:int,p:ReportIn,user:User=Depends(current_user),db:Session=Depends(db_session)):
 db.add(Report(reporter_id=user.id,target_user_id=uid,reason=p.reason.strip()));db.commit();return {'ok':True}
class SocketManager:
 def __init__(self):self.rooms:Dict[int,List[WebSocket]]={}
 async def connect(self,r,w):await w.accept();self.rooms.setdefault(r,[]).append(w)
 def disconnect(self,r,w):
  if r in self.rooms and w in self.rooms[r]:self.rooms[r].remove(w)
 async def broadcast(self,r,p):
  dead=[]
  for w in self.rooms.get(r,[]):
   try:await w.send_json(p)
   except:dead.append(w)
  for w in dead:self.disconnect(r,w)
manager=SocketManager()
@app.websocket('/ws/chat/{cid}')
async def ws_chat(ws:WebSocket,cid:int,token:str):
 db=SessionLocal()
 try:
  s=db.scalar(select(SessionToken).where(SessionToken.token_hash==token_hash(token)))
  if not s:await ws.close(code=4401);return
  u=db.get(User,s.user_id);c=db.get(Conversation,cid)
  if not c or u.id not in(c.user_low,c.user_high):await ws.close(code=4403);return
  await manager.connect(cid,ws)
  while True:
   data=await ws.receive_json();body=str(data.get('body','')).strip()
   if not body:continue
   oid=c.user_high if c.user_low==u.id else c.user_low
   if blocked_pair(db,u.id,oid):continue
   m=Message(conversation_id=cid,sender_id=u.id,body=body[:2000]);db.add(m);db.add(Notification(user_id=oid,actor_id=u.id,kind='message',text='给你发来一条消息',conversation_id=cid));db.commit();db.refresh(m);await manager.broadcast(cid,{'id':m.id,'sender_id':m.sender_id,'body':m.body,'created_at':m.created_at.isoformat()})
 except WebSocketDisconnect:manager.disconnect(cid,ws)
 finally:db.close()
def seed_demo_if_requested():
 if os.environ.get('DEV_SEED_DEMO')!='1':return
 db=SessionLocal()
 try:
  if db.scalar(select(User).where(User.is_demo==True)):return
  for nick,body in [('风停在窗边','晚上坐地铁的时候忽然觉得城市很安静，耳机里的歌像把人轻轻包住了。'),('海盐星星','有些软件明明很久没打开了，知道它真的离开以后还是会难过，好像一个小世界关灯了。'),('不眠云','一个人生活其实很自在，但偶尔还是会希望有个人刚好听懂自己没说完的那句。')]:
   u=User(nickname=nick,avatar_seed=secrets.token_hex(5),is_demo=True);db.add(u);db.flush();db.add(Post(user_id=u.id,body=body))
  db.commit()
 finally:db.close()
seed_demo_if_requested()
