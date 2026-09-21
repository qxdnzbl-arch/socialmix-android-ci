import hashlib,hmac,math,os,secrets
from datetime import datetime,timezone
from difflib import SequenceMatcher
from typing import Dict
from fastapi import Depends,Header,HTTPException
from sqlalchemy import or_,select
from sqlalchemy.orm import Session
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from .db import *
UTC=timezone.utc;MATCH_THRESHOLD=float(os.environ.get('MATCH_THRESHOLD','0.245'));MAX_IMAGE_CHARS=2_200_000
TOPIC_WORDS={'work':['上班','工作','加班','同事','老板','辞职','工资','产线','累'],'lonely':['孤独','孤单','一个人','没人','难过','想哭','想念','怀念','失去'],'love':['喜欢','爱','恋爱','心动','分手','想你','朋友','关系'],'life':['生活','今天','晚上','天气','吃饭','散步','回家','睡觉','地铁','海边'],'study':['学习','考试','学校','老师','作业','上课'],'hope':['开心','希望','期待','终于','幸运','好看','舒服','温柔'],'digital':['软件','游戏','停运','更新','账号','手机','app','APP','聊天']}
NEG=['难过','烦','累','哭','痛苦','孤独','绝望','讨厌','害怕','不想','失去','伤心'];POS=['开心','喜欢','希望','期待','舒服','幸运','温柔','好看','快乐','满足']
def token_hash(t):return hashlib.sha256(t.encode()).hexdigest()
def password_hash(p):
 s=secrets.token_bytes(16);r=220_000;d=hashlib.pbkdf2_hmac('sha256',p.encode(),s,r);return f'pbkdf2_sha256${r}${s.hex()}${d.hex()}'
def verify_password(p,e):
 try:
  scheme,r,s,d=e.split('$',3);actual=hashlib.pbkdf2_hmac('sha256',p.encode(),bytes.fromhex(s),int(r)).hex();return scheme=='pbkdf2_sha256' and hmac.compare_digest(actual,d)
 except:return False
def db_session():
 db=SessionLocal()
 try:yield db
 finally:db.close()
def issue_session(db,u):
 t=secrets.token_urlsafe(32);db.add(SessionToken(user_id=u.id,token_hash=token_hash(t)));db.commit();return t
def current_user(authorization:str|None=Header(default=None),db:Session=Depends(db_session)):
 if not authorization or not authorization.lower().startswith('bearer '):raise HTTPException(401,'missing session')
 row=db.scalar(select(SessionToken).where(SessionToken.token_hash==token_hash(authorization.split(' ',1)[1].strip())))
 if not row:raise HTTPException(401,'invalid session')
 u=db.get(User,row.user_id)
 if not u:raise HTTPException(401,'user not found')
 return u
def user_json(u,private=False):
 o={'id':u.id,'nickname':u.nickname,'bio':u.bio,'avatar_seed':u.avatar_seed,'avatar_data':u.avatar_data,'is_demo':u.is_demo}
 if private:o['username']=u.username
 return o
def topic_vector(text)->Dict[str,float]:
 r={k:float(sum(text.lower().count(w.lower()) for w in ws)) for k,ws in TOPIC_WORDS.items()};n=sum(text.count(w) for w in NEG);p=sum(text.count(w) for w in POS);r['sentiment']=(p-n)/max(1,p+n);return r
def aux_similarity(a,b):
 x,y=topic_vector(a),topic_vector(b);ks=list(TOPIC_WORDS);sh=sum(min(x[k],y[k]) for k in ks);tot=sum(max(x[k],y[k]) for k in ks);topic=sh/tot if tot else 0;sent=1-min(1,abs(x['sentiment']-y['sentiment'])/2);return .72*topic+.28*sent
def recency(dt):
 if dt.tzinfo is None:dt=dt.replace(tzinfo=UTC)
 return math.exp(-max(0,(datetime.now(UTC)-dt).total_seconds()/86400)/30)
def _char_ngram_counts(text):
 text=''.join(str(text or '').lower().split())
 out={}
 if not text:return out
 for n in (1,2,3,4):
  if len(text)<n:continue
  for i in range(len(text)-n+1):
   g=text[i:i+n];out[g]=out.get(g,0)+1
 return out
def _cosine_counts(a,b):
 A=_char_ngram_counts(a);B=_char_ngram_counts(b)
 if not A or not B:return 0.0
 dot=sum(v*B.get(k,0) for k,v in A.items())
 na=math.sqrt(sum(v*v for v in A.values()));nb=math.sqrt(sum(v*v for v in B.values()))
 return dot/(na*nb) if na and nb else 0.0
def pair_score(a,b):
 sem=_cosine_counts(a.body,b.body)
 return .58*sem+.17*SequenceMatcher(None,a.body,b.body).ratio()+.20*aux_similarity(a.body,b.body)+.05*recency(b.created_at)
def blocked_pair(db,a,b):return db.scalar(select(Block).where(or_((Block.blocker_id==a)&(Block.blocked_id==b),(Block.blocker_id==b)&(Block.blocked_id==a)))) is not None
def insert_match(db,s,t,score):
 if score<MATCH_THRESHOLD or s.user_id==t.user_id or blocked_pair(db,s.user_id,t.user_id):return False
 if db.scalar(select(Match).where(Match.source_post_id==s.id,Match.target_post_id==t.id)):return False
 db.add(Match(source_post_id=s.id,target_post_id=t.id,score=score));return True
def refresh_matches_around(db,new,limit=6):
 candidates=db.scalars(select(Post).where(Post.active==True,Post.user_id!=new.user_id).order_by(Post.created_at.desc()).limit(240)).all();ranked=sorted([(pair_score(new,p),p) for p in candidates if not blocked_pair(db,new.user_id,p.user_id)],key=lambda x:x[0],reverse=True);seen=set();chosen=[]
 for score,p in ranked:
  if score<MATCH_THRESHOLD or p.user_id in seen:continue
  seen.add(p.user_id);chosen.append((score,p))
  if len(chosen)>=limit:break
 for score,p in chosen:insert_match(db,new,p,score)
 for score,p in ranked[:80]:
  if score>=MATCH_THRESHOLD:insert_match(db,p,new,score)
 db.commit()
def serialize_post(p,count=0):return {'id':p.id,'body':p.body,'image_data':p.image_data,'created_at':p.created_at.isoformat(),'resonance_count':count}
def conversation_for(db,a,b):
 low,high=sorted([a,b]);c=db.scalar(select(Conversation).where(Conversation.user_low==low,Conversation.user_high==high))
 if not c:c=Conversation(user_low=low,user_high=high);db.add(c);db.flush()
 return c
def are_friends(db,a,b):
 low,high=sorted([a,b]);return db.scalar(select(Friendship).where(Friendship.user_low==low,Friendship.user_high==high)) is not None
def friend_status(db,a,b):
 if are_friends(db,a,b):return 'friends'
 if db.scalar(select(FriendRequest).where(FriendRequest.sender_id==a,FriendRequest.receiver_id==b,FriendRequest.status=='pending')):return 'sent'
 if db.scalar(select(FriendRequest).where(FriendRequest.sender_id==b,FriendRequest.receiver_id==a,FriendRequest.status=='pending')):return 'incoming'
 return 'none'
