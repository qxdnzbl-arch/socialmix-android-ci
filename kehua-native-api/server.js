import http from 'node:http';
import crypto from 'node:crypto';
import pg from 'pg';

const { Pool } = pg;
const PORT = Number(process.env.PORT || 8080);
const DATABASE_URL = process.env.DATABASE_URL;
if (!DATABASE_URL) throw new Error('DATABASE_URL is required');
const pool = new Pool({ connectionString: DATABASE_URL, ssl: /localhost|railway\.internal/.test(DATABASE_URL) ? false : { rejectUnauthorized: false }, max: 10 });

const MAX_BODY = 64 * 1024;
const SESSION_DAYS = 90;
const fail = (message, status = 400) => Object.assign(new Error(message), { status });
const uuid = () => crypto.randomUUID();
const sha256 = (s) => crypto.createHash('sha256').update(String(s)).digest('hex');
const recoveryCode = () => crypto.randomBytes(10).toString('hex').toUpperCase();
const passwordHash = (password, salt = crypto.randomBytes(16).toString('hex')) => ({ salt, hash: crypto.scryptSync(password, salt, 64).toString('hex') });
const verifyPassword = (password, salt, expected) => crypto.timingSafeEqual(Buffer.from(passwordHash(password, salt).hash, 'hex'), Buffer.from(expected, 'hex'));
const cleanText = (v, max, name) => { const s = String(v ?? '').trim(); if (!s || s.length > max) throw fail(`${name}格式不正确`); return s; };
const cleanLogin = (v) => { const s=String(v??'').trim().toLowerCase(); if(!/^[a-z0-9_]{3,32}$/.test(s)) throw fail('账号只使用3-32位字母、数字或下划线'); return s; };
const cleanPassword = (v) => { const s=String(v??''); if(s.length<6||s.length>72) throw fail('密码长度需要6-72位'); return s; };

async function init(){
  await pool.query(`
    create table if not exists native_prod_accounts(
      id uuid primary key, login text unique not null, password_salt text not null, password_hash text not null,
      recovery_hash text not null, nickname text not null, bio text not null default '', gender text, region text,
      created_at timestamptz not null default now(), updated_at timestamptz not null default now()
    );
    create table if not exists native_prod_sessions(
      token uuid primary key, account_id uuid not null references native_prod_accounts(id) on delete cascade,
      created_at timestamptz not null default now(), expires_at timestamptz not null
    );
    create index if not exists native_prod_sessions_account_idx on native_prod_sessions(account_id);
    create table if not exists native_prod_posts(
      id uuid primary key, account_id uuid not null references native_prod_accounts(id) on delete cascade,
      content text not null, is_private boolean not null default false, created_at timestamptz not null default now()
    );
    create index if not exists native_prod_posts_account_idx on native_prod_posts(account_id,created_at desc);
    create table if not exists native_prod_resonances(
      id uuid primary key, user_id uuid not null references native_prod_accounts(id) on delete cascade,
      own_post_id uuid not null references native_prod_posts(id) on delete cascade,
      candidate_post_id uuid not null references native_prod_posts(id) on delete cascade,
      candidate_user_id uuid not null references native_prod_accounts(id) on delete cascade,
      status text not null default 'new' check(status in ('new','opened','lit','dismissed')),
      created_at timestamptz not null default now(), unique(user_id,own_post_id,candidate_post_id)
    );
    create index if not exists native_prod_resonances_user_idx on native_prod_resonances(user_id,created_at desc);
    create table if not exists native_prod_lights(
      id uuid primary key, resonance_id uuid not null references native_prod_resonances(id) on delete cascade,
      post_id uuid not null references native_prod_posts(id) on delete cascade,
      from_account_id uuid not null references native_prod_accounts(id) on delete cascade,
      created_at timestamptz not null default now(), unique(resonance_id,from_account_id)
    );
    create table if not exists native_prod_messages(
      id bigserial primary key, from_id uuid not null references native_prod_accounts(id) on delete cascade,
      to_id uuid not null references native_prod_accounts(id) on delete cascade,
      body text not null, created_at timestamptz not null default now()
    );
    create index if not exists native_prod_messages_pair_idx on native_prod_messages(from_id,to_id,created_at);
    create table if not exists native_prod_friend_requests(
      id uuid primary key, from_user uuid not null references native_prod_accounts(id) on delete cascade,
      to_user uuid not null references native_prod_accounts(id) on delete cascade,
      status text not null default 'pending' check(status in ('pending','accepted','rejected')),
      created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
      unique(from_user,to_user)
    );
    create table if not exists native_prod_friendships(
      id uuid primary key, user_a uuid not null references native_prod_accounts(id) on delete cascade,
      user_b uuid not null references native_prod_accounts(id) on delete cascade,
      created_at timestamptz not null default now(), unique(user_a,user_b), check(user_a::text < user_b::text)
    );
    create table if not exists native_prod_blocks(
      blocker_id uuid not null references native_prod_accounts(id) on delete cascade,
      blocked_id uuid not null references native_prod_accounts(id) on delete cascade,
      created_at timestamptz not null default now(), primary key(blocker_id,blocked_id)
    );
    create table if not exists native_prod_reports(
      id uuid primary key, reporter_id uuid not null references native_prod_accounts(id) on delete cascade,
      reported_user_id uuid references native_prod_accounts(id) on delete set null,
      post_id uuid references native_prod_posts(id) on delete set null,
      reason text not null, created_at timestamptz not null default now()
    );
  `);
  await pool.query('select 1');
}

async function bodyJson(req){
  let total=0, chunks=[];
  for await (const chunk of req){ total+=chunk.length; if(total>MAX_BODY) throw fail('请求过大',413); chunks.push(chunk); }
  if(!chunks.length) return {};
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { throw fail('请求格式错误'); }
}
async function tx(fn){ const c=await pool.connect(); try{ await c.query('begin'); const v=await fn(c); await c.query('commit'); return v; } catch(e){ await c.query('rollback'); throw e; } finally{ c.release(); } }
async function auth(c, token){
  if(!token) throw fail('登录已失效',401);
  const q=await c.query(`select a.* from native_prod_sessions s join native_prod_accounts a on a.id=s.account_id where s.token=$1 and s.expires_at>now()`,[token]);
  if(!q.rows[0]) throw fail('登录已失效',401);
  return q.rows[0];
}
const pair=(a,b)=> a < b ? [a,b] : [b,a];
async function isBlocked(c,a,b){ const q=await c.query('select 1 from native_prod_blocks where (blocker_id=$1 and blocked_id=$2) or (blocker_id=$2 and blocked_id=$1) limit 1',[a,b]); return q.rowCount>0; }
async function isFriend(c,a,b){ const [x,y]=pair(a,b); const q=await c.query('select 1 from native_prod_friendships where user_a=$1 and user_b=$2 limit 1',[x,y]); return q.rowCount>0; }
async function canChat(c,a,b){ if(await isBlocked(c,a,b)) return false; if(await isFriend(c,a,b)) return true; const q=await c.query(`select 1 from native_prod_resonances where status='lit' and ((user_id=$1 and candidate_user_id=$2) or (user_id=$2 and candidate_user_id=$1)) limit 1`,[a,b]); return q.rowCount>0; }
async function issueSession(c,accountId){ const t=uuid(); await c.query(`insert into native_prod_sessions(token,account_id,expires_at) values($1,$2,now()+($3||' days')::interval)`,[t,accountId,String(SESSION_DAYS)]); return t; }
async function ensureResonances(c,userId){
  const own=await c.query(`select id from native_prod_posts where account_id=$1 and is_private=false order by created_at desc limit 1`,[userId]);
  if(!own.rows[0]) return;
  const candidates=await c.query(`select p.id,p.account_id from native_prod_posts p where p.account_id<>$1 and p.is_private=false and not exists(select 1 from native_prod_blocks b where (b.blocker_id=$1 and b.blocked_id=p.account_id) or (b.blocker_id=p.account_id and b.blocked_id=$1)) order by p.created_at desc limit 30`,[userId]);
  for(const x of candidates.rows){ await c.query(`insert into native_prod_resonances(id,user_id,own_post_id,candidate_post_id,candidate_user_id) values($1,$2,$3,$4,$5) on conflict(user_id,own_post_id,candidate_post_id) do nothing`,[uuid(),userId,own.rows[0].id,x.id,x.account_id]); }
}

async function rpc(name,a){ return tx(async c=>{
  switch(name){
    case 'kehua_prod_health': { await c.query('select 1'); return {ok:true,database:'postgres',service:'kehua_native_prod'}; }
    case 'kehua_prod_register': {
      const login=cleanLogin(a.p_phone), password=cleanPassword(a.p_password), nickname=cleanText(a.p_nickname,30,'昵称');
      const exists=await c.query('select 1 from native_prod_accounts where login=$1',[login]); if(exists.rowCount) throw fail('这个账号已经存在',409);
      const id=uuid(), rec=recoveryCode(), ph=passwordHash(password);
      await c.query(`insert into native_prod_accounts(id,login,password_salt,password_hash,recovery_hash,nickname) values($1,$2,$3,$4,$5,$6)`,[id,login,ph.salt,ph.hash,sha256(rec),nickname]);
      const token=await issueSession(c,id); return {ok:true,token,recovery_code:rec,user:{id,nickname}};
    }
    case 'kehua_prod_login': {
      const login=cleanLogin(a.p_phone), password=cleanPassword(a.p_password); const q=await c.query('select * from native_prod_accounts where login=$1',[login]); const u=q.rows[0];
      if(!u || !verifyPassword(password,u.password_salt,u.password_hash)) throw fail('账号或密码不正确',401); const token=await issueSession(c,u.id); return {ok:true,token,user:{id:u.id,nickname:u.nickname}};
    }
    case 'kehua_prod_recover': {
      const login=cleanLogin(a.p_login), code=cleanText(a.p_recovery_code,64,'恢复码').toUpperCase(), password=cleanPassword(a.p_new_password); const q=await c.query('select * from native_prod_accounts where login=$1',[login]); const u=q.rows[0];
      if(!u || sha256(code)!==u.recovery_hash) throw fail('账号或恢复码不正确',401); const rec=recoveryCode(), ph=passwordHash(password); await c.query('update native_prod_accounts set password_salt=$1,password_hash=$2,recovery_hash=$3,updated_at=now() where id=$4',[ph.salt,ph.hash,sha256(rec),u.id]); await c.query('delete from native_prod_sessions where account_id=$1',[u.id]); const token=await issueSession(c,u.id); return {ok:true,token,user_id:u.id,recovery_code:rec};
    }
    case 'kehua_prod_logout': { const u=await auth(c,a.p_token); await c.query('delete from native_prod_sessions where token=$1 and account_id=$2',[a.p_token,u.id]); return {ok:true}; }
    case 'kehua_prod_rotate_recovery': { const u=await auth(c,a.p_token), rec=recoveryCode(); await c.query('update native_prod_accounts set recovery_hash=$1,updated_at=now() where id=$2',[sha256(rec),u.id]); return {ok:true,recovery_code:rec}; }
    case 'kehua_prod_create_post': { const u=await auth(c,a.p_token), content=cleanText(a.p_content,1200,'内容'), id=uuid(); await c.query('insert into native_prod_posts(id,account_id,content,is_private) values($1,$2,$3,$4)',[id,u.id,content,Boolean(a.p_private)]); return {ok:true,id}; }
    case 'kehua_prod_home': {
      const u=await auth(c,a.p_token); await ensureResonances(c,u.id);
      const posts=(await c.query(`select p.id,p.content,p.created_at,p.is_private,count(l.id)::int light_count from native_prod_posts p left join native_prod_lights l on l.post_id=p.id where p.account_id=$1 group by p.id order by p.created_at desc limit 50`,[u.id])).rows;
      const rs=(await c.query(`select r.id,p.content,r.status,case when r.status='lit' then a.nickname else null end nickname,case when r.status='lit' then a.id else null end author_id,r.created_at from native_prod_resonances r join native_prod_posts p on p.id=r.candidate_post_id join native_prod_accounts a on a.id=r.candidate_user_id where r.user_id=$1 and r.status<>'dismissed' order by r.created_at desc limit 50`,[u.id])).rows;
      return {ok:true,my_posts:posts,resonances:rs,new_count:rs.filter(x=>x.status==='new').length};
    }
    case 'kehua_prod_open_resonance': { const u=await auth(c,a.p_token); await c.query(`update native_prod_resonances set status=case when status='lit' then status else 'opened' end where id=$1 and user_id=$2`,[a.p_resonance_id,u.id]); return {ok:true}; }
    case 'kehua_prod_dismiss_resonance': { const u=await auth(c,a.p_token); await c.query(`update native_prod_resonances set status='dismissed' where id=$1 and user_id=$2`,[a.p_resonance_id,u.id]); return {ok:true}; }
    case 'kehua_prod_light': {
      const u=await auth(c,a.p_token); const q=await c.query(`select r.*,p.id post_id,a.nickname,a.bio from native_prod_resonances r join native_prod_posts p on p.id=r.candidate_post_id join native_prod_accounts a on a.id=r.candidate_user_id where r.id=$1 and r.user_id=$2`,[a.p_post_id,u.id]); const r=q.rows[0]; if(!r) throw fail('共鸣不存在',404);
      await c.query(`update native_prod_resonances set status='lit' where id=$1`,[r.id]); await c.query(`insert into native_prod_lights(id,resonance_id,post_id,from_account_id) values($1,$2,$3,$4) on conflict(resonance_id,from_account_id) do nothing`,[uuid(),r.id,r.post_id,u.id]); return {ok:true,user:{id:r.candidate_user_id,nickname:r.nickname,bio:r.bio||''}};
    }
    case 'kehua_prod_send_message': { const u=await auth(c,a.p_token), to=String(a.p_to||''); if(!(await canChat(c,u.id,to))) throw fail('还不能和这个人聊天',403); const body=cleanText(a.p_body,2000,'消息'); const q=await c.query('insert into native_prod_messages(from_id,to_id,body) values($1,$2,$3) returning id',[u.id,to,body]); return {ok:true,id:Number(q.rows[0].id)}; }
    case 'kehua_prod_chat': { const u=await auth(c,a.p_token), other=String(a.p_other||''); if(await isBlocked(c,u.id,other)) throw fail('已无法继续联系',403); const p=(await c.query('select id,nickname,bio from native_prod_accounts where id=$1',[other])).rows[0]; if(!p) throw fail('用户不存在',404); const items=(await c.query(`select id,from_id,body,created_at from native_prod_messages where (from_id=$1 and to_id=$2) or (from_id=$2 and to_id=$1) order by created_at asc limit 400`,[u.id,other])).rows.map(x=>({...x,id:Number(x.id)})); return {ok:true,peer:p,is_friend:await isFriend(c,u.id,other),items}; }
    case 'kehua_prod_threads': { const u=await auth(c,a.p_token); const q=await c.query(`with peers as (select case when from_id=$1 then to_id else from_id end peer_id,max(created_at) last_at from native_prod_messages where from_id=$1 or to_id=$1 group by 1) select a.id,a.nickname,coalesce((select m.body from native_prod_messages m where (m.from_id=$1 and m.to_id=a.id) or (m.from_id=a.id and m.to_id=$1) order by m.created_at desc limit 1),'') last_message,p.last_at from peers p join native_prod_accounts a on a.id=p.peer_id order by p.last_at desc`,[u.id]); const items=[]; for(const x of q.rows){ if(!(await isBlocked(c,u.id,x.id))) items.push({...x,is_friend:await isFriend(c,u.id,x.id)}); } return {ok:true,items}; }
    case 'kehua_prod_friend_request': { const u=await auth(c,a.p_token), other=String(a.p_other||''); if(other===u.id) throw fail('不能添加自己'); if(await isBlocked(c,u.id,other)) throw fail('无法添加这个人',403); if(await isFriend(c,u.id,other)) return {ok:true,status:'accepted'}; const rev=await c.query(`select id from native_prod_friend_requests where from_user=$1 and to_user=$2 and status='pending'`,[other,u.id]); if(rev.rows[0]){ const [x,y]=pair(u.id,other); await c.query(`update native_prod_friend_requests set status='accepted',updated_at=now() where id=$1`,[rev.rows[0].id]); await c.query(`insert into native_prod_friendships(id,user_a,user_b) values($1,$2,$3) on conflict(user_a,user_b) do nothing`,[uuid(),x,y]); return {ok:true,status:'accepted'}; } await c.query(`insert into native_prod_friend_requests(id,from_user,to_user,status) values($1,$2,$3,'pending') on conflict(from_user,to_user) do update set status='pending',updated_at=now()`,[uuid(),u.id,other]); return {ok:true,status:'pending'}; }
    case 'kehua_prod_incoming_friend_requests': { const u=await auth(c,a.p_token); const items=(await c.query(`select id,from_user from native_prod_friend_requests where to_user=$1 and status='pending' order by created_at desc`,[u.id])).rows; return {ok:true,items}; }
    case 'kehua_prod_respond_friend_request': { const u=await auth(c,a.p_token); const q=await c.query(`select * from native_prod_friend_requests where id=$1 and to_user=$2 and status='pending'`,[a.p_request,u.id]); const r=q.rows[0]; if(!r) throw fail('好友申请不存在',404); const status=Boolean(a.p_accept)?'accepted':'rejected'; await c.query('update native_prod_friend_requests set status=$1,updated_at=now() where id=$2',[status,r.id]); if(status==='accepted'){ const [x,y]=pair(r.from_user,r.to_user); await c.query(`insert into native_prod_friendships(id,user_a,user_b) values($1,$2,$3) on conflict(user_a,user_b) do nothing`,[uuid(),x,y]); } return {ok:true,status}; }
    case 'kehua_prod_friends': { const u=await auth(c,a.p_token); const rows=(await c.query(`select a.id,a.nickname,a.bio from native_prod_friendships f join native_prod_accounts a on a.id=case when f.user_a=$1 then f.user_b else f.user_a end where f.user_a=$1 or f.user_b=$1 order by f.created_at desc`,[u.id])).rows; const items=[]; for(const x of rows){ if(!(await isBlocked(c,u.id,x.id))) items.push(x); } return {ok:true,items}; }
    case 'kehua_prod_me': { const u=await auth(c,a.p_token); const pc=Number((await c.query('select count(*) c from native_prod_posts where account_id=$1',[u.id])).rows[0].c); const fc=Number((await c.query('select count(*) c from native_prod_friendships where user_a=$1 or user_b=$1',[u.id])).rows[0].c); return {ok:true,user:{id:u.id,nickname:u.nickname,bio:u.bio||'',post_count:pc,friend_count:fc}}; }
    case 'kehua_prod_update_profile': { const u=await auth(c,a.p_token), nickname=cleanText(a.p_nickname,30,'昵称'), bio=String(a.p_bio??'').trim().slice(0,300); await c.query('update native_prod_accounts set nickname=$1,bio=$2,gender=$3,region=$4,updated_at=now() where id=$5',[nickname,bio,a.p_gender??null,a.p_region??null,u.id]); return {ok:true}; }
    case 'kehua_prod_block': { const u=await auth(c,a.p_token), other=String(a.p_other||''); if(other===u.id) throw fail('不能屏蔽自己'); await c.query(`insert into native_prod_blocks(blocker_id,blocked_id) values($1,$2) on conflict do nothing`,[u.id,other]); const [x,y]=pair(u.id,other); await c.query('delete from native_prod_friendships where user_a=$1 and user_b=$2',[x,y]); return {ok:true}; }
    case 'kehua_prod_report': { const u=await auth(c,a.p_token), reason=cleanText(a.p_reason,500,'举报原因'); await c.query('insert into native_prod_reports(id,reporter_id,reported_user_id,post_id,reason) values($1,$2,$3,$4,$5)',[uuid(),u.id,a.p_user??null,a.p_post??null,reason]); return {ok:true}; }
    case 'kehua_prod_delete_account': { const u=await auth(c,a.p_token); await c.query('delete from native_prod_accounts where id=$1',[u.id]); return {ok:true}; }
    default: throw fail('接口不存在',404);
  }
}); }

function send(res,status,obj){ const data=Buffer.from(JSON.stringify(obj)); res.writeHead(status,{'content-type':'application/json; charset=utf-8','content-length':data.length,'cache-control':'no-store'}); res.end(data); }
const server=http.createServer(async(req,res)=>{
  try{
    if(req.method==='GET' && req.url==='/health'){ const q=await pool.query('select 1'); return send(res,200,{ok:true,database:q.rowCount===1?'postgres':'unknown',service:'kehua_native_prod'}); }
    if(req.method==='POST' && req.url?.startsWith('/rpc/')){ const name=req.url.slice('/rpc/'.length).split('?')[0]; const args=await bodyJson(req); const out=await rpc(name,args); return send(res,200,out); }
    send(res,404,{message:'Not found'});
  }catch(e){ console.error(JSON.stringify({level:'error',message:e?.message||String(e),path:req.url})); send(res,Number(e?.status)||500,{message:e?.message||'服务暂时不可用'}); }
});

await init();
server.listen(PORT,'0.0.0.0',()=>console.log(`kehua-native-prod-api listening on ${PORT}`));
