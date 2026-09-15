from __future__ import annotations
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional
from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String, Text, UniqueConstraint, create_engine
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, sessionmaker

UTC=timezone.utc
ROOT=Path(__file__).resolve().parent
DB_URL=os.environ.get('DATABASE_URL',f"sqlite:///{(ROOT.parent/'data'/'kehua.db').as_posix()}")
if DB_URL.startswith('postgres://'): DB_URL='postgresql+psycopg://'+DB_URL[len('postgres://'):]
elif DB_URL.startswith('postgresql://') and '+psycopg' not in DB_URL: DB_URL='postgresql+psycopg://'+DB_URL[len('postgresql://'):]
engine=create_engine(DB_URL,connect_args={'check_same_thread':False} if DB_URL.startswith('sqlite') else {},pool_pre_ping=True)
SessionLocal=sessionmaker(bind=engine,autoflush=False,expire_on_commit=False)
class Base(DeclarativeBase): pass
class User(Base):
 __tablename__='users';id:Mapped[int]=mapped_column(Integer,primary_key=True);username:Mapped[Optional[str]]=mapped_column(String(32),unique=True,index=True,nullable=True);password_hash:Mapped[Optional[str]]=mapped_column(String(180),nullable=True);nickname:Mapped[str]=mapped_column(String(40));avatar_seed:Mapped[str]=mapped_column(String(24));avatar_data:Mapped[Optional[str]]=mapped_column(Text,nullable=True);bio:Mapped[str]=mapped_column(String(140),default='');is_demo:Mapped[bool]=mapped_column(Boolean,default=False);created_at:Mapped[datetime]=mapped_column(DateTime(timezone=True),default=lambda:datetime.now(UTC))
class SessionToken(Base):
 __tablename__='sessions';id:Mapped[int]=mapped_column(Integer,primary_key=True);user_id:Mapped[int]=mapped_column(ForeignKey('users.id',ondelete='CASCADE'),index=True);token_hash:Mapped[str]=mapped_column(String(64),unique=True,index=True);created_at:Mapped[datetime]=mapped_column(DateTime(timezone=True),default=lambda:datetime.now(UTC))
class Post(Base):
 __tablename__='posts';id:Mapped[int]=mapped_column(Integer,primary_key=True);user_id:Mapped[int]=mapped_column(ForeignKey('users.id',ondelete='CASCADE'),index=True);body:Mapped[str]=mapped_column(Text);image_data:Mapped[Optional[str]]=mapped_column(Text,nullable=True);created_at:Mapped[datetime]=mapped_column(DateTime(timezone=True),default=lambda:datetime.now(UTC),index=True);active:Mapped[bool]=mapped_column(Boolean,default=True)
class Match(Base):
 __tablename__='matches';__table_args__=(UniqueConstraint('source_post_id','target_post_id',name='uq_match_pair'),);id:Mapped[int]=mapped_column(Integer,primary_key=True);source_post_id:Mapped[int]=mapped_column(ForeignKey('posts.id',ondelete='CASCADE'),index=True);target_post_id:Mapped[int]=mapped_column(ForeignKey('posts.id',ondelete='CASCADE'),index=True);score:Mapped[float]=mapped_column(Float);skipped:Mapped[bool]=mapped_column(Boolean,default=False);lit:Mapped[bool]=mapped_column(Boolean,default=False);created_at:Mapped[datetime]=mapped_column(DateTime(timezone=True),default=lambda:datetime.now(UTC))
class Conversation(Base):
 __tablename__='conversations';__table_args__=(UniqueConstraint('user_low','user_high',name='uq_conversation_users'),);id:Mapped[int]=mapped_column(Integer,primary_key=True);user_low:Mapped[int]=mapped_column(ForeignKey('users.id',ondelete='CASCADE'),index=True);user_high:Mapped[int]=mapped_column(ForeignKey('users.id',ondelete='CASCADE'),index=True);created_at:Mapped[datetime]=mapped_column(DateTime(timezone=True),default=lambda:datetime.now(UTC))
class Light(Base):
 __tablename__='lights';__table_args__=(UniqueConstraint('actor_user_id','source_post_id','target_post_id',name='uq_light'),);id:Mapped[int]=mapped_column(Integer,primary_key=True);actor_user_id:Mapped[int]=mapped_column(ForeignKey('users.id',ondelete='CASCADE'),index=True);target_user_id:Mapped[int]=mapped_column(ForeignKey('users.id',ondelete='CASCADE'),index=True);source_post_id:Mapped[int]=mapped_column(ForeignKey('posts.id',ondelete='CASCADE'));target_post_id:Mapped[int]=mapped_column(ForeignKey('posts.id',ondelete='CASCADE'));conversation_id:Mapped[int]=mapped_column(ForeignKey('conversations.id',ondelete='CASCADE'),index=True);created_at:Mapped[datetime]=mapped_column(DateTime(timezone=True),default=lambda:datetime.now(UTC))
class Message(Base):
 __tablename__='messages';id:Mapped[int]=mapped_column(Integer,primary_key=True);conversation_id:Mapped[int]=mapped_column(ForeignKey('conversations.id',ondelete='CASCADE'),index=True);sender_id:Mapped[int]=mapped_column(ForeignKey('users.id',ondelete='CASCADE'),index=True);body:Mapped[str]=mapped_column(Text);created_at:Mapped[datetime]=mapped_column(DateTime(timezone=True),default=lambda:datetime.now(UTC),index=True)
class FriendRequest(Base):
 __tablename__='friend_requests';__table_args__=(UniqueConstraint('sender_id','receiver_id',name='uq_friend_request'),);id:Mapped[int]=mapped_column(Integer,primary_key=True);sender_id:Mapped[int]=mapped_column(ForeignKey('users.id',ondelete='CASCADE'),index=True);receiver_id:Mapped[int]=mapped_column(ForeignKey('users.id',ondelete='CASCADE'),index=True);status:Mapped[str]=mapped_column(String(16),default='pending');created_at:Mapped[datetime]=mapped_column(DateTime(timezone=True),default=lambda:datetime.now(UTC))
class Friendship(Base):
 __tablename__='friendships';__table_args__=(UniqueConstraint('user_low','user_high',name='uq_friendship_users'),);id:Mapped[int]=mapped_column(Integer,primary_key=True);user_low:Mapped[int]=mapped_column(ForeignKey('users.id',ondelete='CASCADE'),index=True);user_high:Mapped[int]=mapped_column(ForeignKey('users.id',ondelete='CASCADE'),index=True);created_at:Mapped[datetime]=mapped_column(DateTime(timezone=True),default=lambda:datetime.now(UTC))
class Notification(Base):
 __tablename__='notifications';id:Mapped[int]=mapped_column(Integer,primary_key=True);user_id:Mapped[int]=mapped_column(ForeignKey('users.id',ondelete='CASCADE'),index=True);actor_id:Mapped[Optional[int]]=mapped_column(ForeignKey('users.id',ondelete='CASCADE'),nullable=True);kind:Mapped[str]=mapped_column(String(32));text:Mapped[str]=mapped_column(String(180));conversation_id:Mapped[Optional[int]]=mapped_column(ForeignKey('conversations.id',ondelete='CASCADE'),nullable=True);read:Mapped[bool]=mapped_column(Boolean,default=False);created_at:Mapped[datetime]=mapped_column(DateTime(timezone=True),default=lambda:datetime.now(UTC),index=True)
class Block(Base):
 __tablename__='blocks';__table_args__=(UniqueConstraint('blocker_id','blocked_id',name='uq_block'),);id:Mapped[int]=mapped_column(Integer,primary_key=True);blocker_id:Mapped[int]=mapped_column(ForeignKey('users.id',ondelete='CASCADE'),index=True);blocked_id:Mapped[int]=mapped_column(ForeignKey('users.id',ondelete='CASCADE'),index=True);created_at:Mapped[datetime]=mapped_column(DateTime(timezone=True),default=lambda:datetime.now(UTC))
class Report(Base):
 __tablename__='reports';id:Mapped[int]=mapped_column(Integer,primary_key=True);reporter_id:Mapped[int]=mapped_column(ForeignKey('users.id',ondelete='CASCADE'),index=True);target_user_id:Mapped[int]=mapped_column(ForeignKey('users.id',ondelete='CASCADE'),index=True);reason:Mapped[str]=mapped_column(String(160));created_at:Mapped[datetime]=mapped_column(DateTime(timezone=True),default=lambda:datetime.now(UTC))
Base.metadata.create_all(engine)
