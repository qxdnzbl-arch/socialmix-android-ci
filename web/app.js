import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.116.0';

const supabase = createClient(
  'https://lzylcqozczsaxtdqfhrs.supabase.co',
  'sb_publishable_Wu7Xa-2bx6QARotVaTX_8g_yBlToQ-e',
  { auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true } }
);

const root = document.getElementById('app');
const state = {
  session: null, user: null, profile: null, authMode: 'login', view: 'home', chatId: null,
  busy: false, notice: '', error: '', pendingFiles: [], ownPosts: [], resonance: [],
  conversations: [], friends: [], chatMessages: [], chatPeer: null, entitlement: null,
  interest: null, chatChannel: null
};

const nav = [
  ['home','⌁','此刻'], ['resonance','◌','共鸣'], ['messages','◒','消息'], ['friends','◇','朋友'], ['me','○','我的']
];

const esc = (v='') => String(v).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[c]));
const fmt = ts => ts ? new Date(ts).toLocaleString('zh-CN',{month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'}) : '';
const first = v => (String(v||'可').trim()[0] || '可').toUpperCase();
const ok = msg => { state.notice = msg; state.error = ''; };
const fail = msg => { state.error = msg; state.notice = ''; };
const clear = () => { state.notice=''; state.error=''; };

async function track(name, metadata={}) {
  if (!state.user) return;
  try { await supabase.from('product_events').insert({user_id:state.user.id,event_name:name,metadata}); } catch(_) {}
}

function parseRoute() {
  const h = location.hash.replace(/^#/,'');
  if (h.startsWith('chat=')) return {view:'chat',chatId:h.slice(5)};
  const allowed = ['home','resonance','messages','friends','me','membership','settings','privacy','terms','community'];
  return {view:allowed.includes(h)?h:'home',chatId:null};
}

async function ensureProfile() {
  if (!state.user) return;
  const nickname = state.user.user_metadata?.nickname || null;
  const {data,error} = await supabase.rpc('ensure_profile',{_nickname:nickname});
  if (error) throw error;
  state.profile = data;
}

async function boot() {
  if (localStorage.getItem('kehua-theme') === 'dark') document.documentElement.classList.add('dark');
  const {data} = await supabase.auth.getSession();
  state.session = data.session;
  state.user = data.session?.user || null;
  if (state.user) await ensureProfile();
  const r = parseRoute();
  if (state.user) await navigate(r.view,r.chatId); else render();
  supabase.auth.onAuthStateChange(async (_event,session)=>{
    state.session=session; state.user=session?.user||null;
    if (state.user) {
      try { await ensureProfile(); await track('signup_or_login_completed'); const rr=parseRoute(); await navigate(rr.view,rr.chatId); }
      catch(e){ fail(e.message||'登录后初始化失败。'); render(); }
    } else { state.profile=null; state.view='home'; render(); }
  });
  window.addEventListener('hashchange', async ()=>{ if(!state.user){render();return;} const rr=parseRoute(); await navigate(rr.view,rr.chatId); });
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(()=>{});
}

async function navigate(view,chatId=null) {
  clear(); state.view=view; state.chatId=chatId;
  if (state.chatChannel) { try { await supabase.removeChannel(state.chatChannel); } catch(_){} state.chatChannel=null; }
  render();
  try {
    if (view==='home') await loadOwnPosts();
    if (view==='resonance') await loadResonance();
    if (view==='messages') await loadConversations();
    if (view==='friends') await loadFriends();
    if (view==='me') await Promise.all([loadOwnPosts(),loadEntitlement()]);
    if (view==='membership') await Promise.all([loadEntitlement(),loadInterest()]);
    if (view==='chat' && chatId) await loadChat(chatId);
  } catch(e) { fail(e.message||'加载失败，请稍后重试。'); }
  render();
}

function avatar(p) {
  const name=p?.nickname||'可话用户';
  return '<div class="avatar">'+(p?.avatar_url?'<img src="'+esc(p.avatar_url)+'" alt="">':esc(first(name)))+'</div>';
}
function notice() {
  if (state.error) return '<div class="notice error">'+esc(state.error)+'</div>';
  if (state.notice) return '<div class="notice ok">'+esc(state.notice)+'</div>';
  return '';
}
function topbar(title='可话',sub='想说的话，都聊得来') {
  return '<header class="topbar"><div class="topbar-inner"><div class="brand">'+esc(title)+'<small>'+esc(sub)+'</small></div><button class="icon-btn" data-route="settings">⋯</button></div></header>';
}
function navHtml() {
  if (state.view==='chat') return '';
  return '<nav class="nav"><div class="nav-inner">'+nav.map(([v,i,l])=>'<button data-route="'+v+'" class="'+(state.view===v?'active':'')+'"><span class="ico">'+i+'</span><span>'+l+'</span></button>').join('')+'</div></nav>';
}

function landing() {
  const reg=state.authMode==='register';
  return '<main class="shell" style="padding-top:44px">'+
    '<section class="card hero"><h1>可话</h1><p>把此刻想说的话放下来。没有公开广场，没有点赞竞赛。真正有共鸣的人，才会走近彼此。</p><div class="slogan">想说的话，都聊得来。</div></section>'+notice()+
    '<section class="card"><div class="tabs" style="margin-bottom:14px"><button class="'+(!reg?'primary-btn':'ghost-btn')+'" data-auth-mode="login">登录</button><button class="'+(reg?'primary-btn':'ghost-btn')+'" data-auth-mode="register">注册</button></div>'+
    '<form id="auth-form" class="auth-grid">'+
    (reg?'<div class="field"><label>昵称</label><input class="input" name="nickname" maxlength="24" required placeholder="别人看到你的名字"></div>':'')+
    '<div class="field"><label>邮箱</label><input class="input" name="email" type="email" autocomplete="email" required placeholder="you@example.com"></div>'+
    '<div class="field"><label>密码</label><input class="input" name="password" type="password" minlength="8" required placeholder="至少 8 位"></div>'+
    '<button class="primary-btn" type="submit" '+(state.busy?'disabled':'')+'>'+(state.busy?'处理中…':(reg?'创建账号':'进入可话'))+'</button></form>'+
    '<div class="divider"></div><p class="tiny muted">继续即表示你同意 <a href="#terms">服务条款</a> 与 <a href="#privacy">隐私说明</a>。这是独立 clean-room 重建服务，不代表原运营方。</p></section></main>';
}

function postCard(p,opts={}) {
  const media=(p.media||[]).map(m=>m.kind==='video'?'<video controls playsinline src="'+esc(m.url)+'"></video>':'<img loading="lazy" src="'+esc(m.url)+'" alt="">').join('');
  const actions=opts.resonance?'<div class="row wrap"><button class="soft-btn" data-light-post="'+p.id+'" data-author="'+p.author_id+'">点亮并回应</button><button class="ghost-btn" data-start-chat="'+p.author_id+'">聊一聊</button><button class="ghost-btn tiny" data-report-user="'+p.author_id+'" data-report-post="'+p.id+'">举报</button><button class="ghost-btn tiny" data-block-user="'+p.author_id+'">屏蔽</button></div>':'';
  return '<article class="card post"><div class="post-head">'+avatar(p.profile)+'<div><div class="post-author">'+esc(p.profile?.nickname||'可话用户')+'</div><div class="post-time">'+fmt(p.created_at)+'</div></div>'+(p.pinned?'<span class="tag">置顶</span>':'')+'</div><div class="post-body">'+esc(p.content||'')+'</div>'+(media?'<div class="media-grid">'+media+'</div>':'')+(opts.score!=null?'<div class="tiny muted">共鸣匹配 '+Math.round(Number(opts.score||0)*100)+'%</div>':'')+actions+'</article>';
}

function home() {
  return topbar('此刻','写下来，然后把世界交还给世界')+'<main class="shell">'+notice()+
  '<section class="card"><form id="post-form"><textarea class="textarea" name="content" maxlength="4000" required placeholder="此刻，有什么想说～"></textarea><div class="row between wrap" style="margin-top:10px"><label class="file-label">＋ 图片 / 视频<input id="media-input" type="file" accept="image/*,video/*" multiple></label><button class="primary-btn" type="submit" '+(state.busy?'disabled':'')+'>'+(state.busy?'正在发表…':'发表')+'</button></div><div id="file-summary" class="tiny muted" style="margin-top:8px">'+(state.pendingFiles.length?esc(state.pendingFiles.map(f=>f.name).join(' · ')):'媒体会保存到你的私密云空间，并跟随动态权限。')+'</div></form></section>'+
  '<h2 class="section-title">我的最近动态</h2>'+(state.ownPosts.length?state.ownPosts.map(p=>postCard(p)).join(''):'<div class="card empty">还没有动态。第一条就从现在开始。</div>')+'</main>'+navHtml();
}
function resonance() {
  return topbar('共鸣','不是广场，只展示与你产生连接的内容')+'<main class="shell">'+notice()+(state.resonance.length?state.resonance.map(x=>postCard(x.post,{resonance:true,score:x.score})).join(''):'<div class="card empty">现在还没有真实共鸣。<br>可话不会用假用户或假内容填满这里。等更多真实的人写下东西，共鸣才会出现。</div>')+'</main>'+navHtml();
}
function messages() {
  return topbar('消息','所有回应与私聊汇在同一个聊天框')+'<main class="shell">'+notice()+'<div class="chat-list">'+(state.conversations.length?state.conversations.map(c=>'<button class="chat-item" data-chat="'+c.id+'">'+avatar(c.peer)+'<div class="chat-main"><div class="chat-name">'+esc(c.peer?.nickname||'可话用户')+'</div><div class="chat-preview">'+esc(c.last?.body||'还没有消息')+'</div></div><div class="tiny muted">'+fmt(c.last?.created_at||c.last_message_at)+'</div></button>').join(''):'<div class="card empty">还没有会话。<br>从一条共鸣开始，聊天才会出现。</div>')+'</div></main>'+navHtml();
}
function friends() {
  const accepted=state.friends.filter(f=>f.status==='accepted');
  const incoming=state.friends.filter(f=>f.status==='pending'&&f.requested_by!==state.user.id);
  const outgoing=state.friends.filter(f=>f.status==='pending'&&f.requested_by===state.user.id);
  const row=(f,label)=>'<div class="chat-item">'+avatar(f.peer)+'<div class="chat-main"><div class="chat-name">'+esc(f.peer?.nickname||'可话用户')+'</div><div class="chat-preview">'+label+'</div></div>'+(f.status==='accepted'?'<button class="ghost-btn" data-start-chat="'+f.peer.id+'">聊天</button>':'')+(f.status==='pending'&&f.requested_by!==state.user.id?'<button class="primary-btn" data-accept-friend="'+f.peer.id+'">接受</button>':'')+'</div>';
  return topbar('朋友','关系从理解开始，不从关注数开始')+'<main class="shell">'+notice()+(incoming.length?'<h2 class="section-title">收到的申请</h2>'+incoming.map(f=>row(f,'想成为你的朋友')).join(''):'')+'<h2 class="section-title">朋友</h2><div class="chat-list">'+(accepted.length?accepted.map(f=>row(f,'已成为朋友')).join(''):'<div class="card empty">还没有朋友。</div>')+'</div>'+(outgoing.length?'<h2 class="section-title">等待对方</h2>'+outgoing.map(f=>row(f,'申请已发出')).join(''):'')+'</main>'+navHtml();
}
function me() {
  const vip=!!state.entitlement?.is_vip&&(!state.entitlement.vip_until||new Date(state.entitlement.vip_until)>new Date());
  return topbar('我的','你的表达、关系和设置')+'<main class="shell">'+notice()+'<section class="card"><div class="post-head">'+avatar(state.profile)+'<div class="grow"><div class="post-author">'+esc(state.profile?.nickname||'可话用户')+'</div><div class="post-time">'+esc(state.user?.email||'')+'</div></div>'+(vip?'<span class="tag">VIP</span>':'')+'</div><p class="small muted">'+esc(state.profile?.bio||'还没有写自我介绍。')+'</p><div class="row wrap"><button class="soft-btn" data-route="settings">编辑资料</button><button class="ghost-btn" data-route="membership">会员</button></div></section><h2 class="section-title">我的动态</h2>'+(state.ownPosts.length?state.ownPosts.map(p=>postCard(p)).join(''):'<div class="card empty">还没有动态。</div>')+'</main>'+navHtml();
}
function membership() {
  const vip=!!state.entitlement?.is_vip&&(!state.entitlement.vip_until||new Date(state.entitlement.vip_until)>new Date());
  return topbar('会员','会员能力由服务端权限控制')+'<main class="shell">'+notice()+'<section class="card hero"><h1 style="font-size:26px">可话会员</h1><p>附近优先、共鸣性别筛选、每日遇见额度提升、动态置顶、历史动态自见。</p><div class="slogan">'+(vip?'当前会员有效':'当前为普通用户')+'</div></section><section class="card"><div class="notice">支付账户尚未完成真实商户接入，因此这里不会伪造付款成功。应用其他功能已经可以真实使用。</div><div class="row between wrap" style="margin-top:14px"><div><strong>开通提醒</strong><div class="tiny muted">真实支付开放后通知已登记用户。</div></div><button class="primary-btn" data-membership-interest '+(state.interest?'disabled':'')+'>'+(state.interest?'已登记':'登记开通提醒')+'</button></div></section><button class="ghost-btn" data-route="me">← 返回我的</button></main>';
}
function settings() {
  return topbar('设置','只保留真正需要的控制')+'<main class="shell">'+notice()+'<section class="card"><form id="profile-form" class="auth-grid"><div class="field"><label>昵称</label><input class="input" name="nickname" maxlength="24" value="'+esc(state.profile?.nickname||'')+'" required></div><div class="field"><label>自我介绍</label><textarea class="textarea" name="bio" maxlength="240">'+esc(state.profile?.bio||'')+'</textarea></div><div class="field"><label>城市（可选，粗粒度）</label><input class="input" name="city" maxlength="48" value="'+esc(state.profile?.city||'')+'" placeholder="例如 深圳"></div><button class="primary-btn" type="submit">保存资料</button></form></section><section class="card"><div class="row between"><div><strong>深色模式</strong><div class="tiny muted">只保存在当前设备。</div></div><button class="soft-btn" data-toggle-theme>'+(document.documentElement.classList.contains('dark')?'切换浅色':'切换深色')+'</button></div></section><section class="card"><div class="row wrap"><button class="ghost-btn" data-route="privacy">隐私说明</button><button class="ghost-btn" data-route="terms">服务条款</button><button class="ghost-btn" data-route="community">社区与安全</button></div><div class="divider"></div><button class="danger-btn" data-signout>退出登录</button></section><button class="ghost-btn" data-route="me">← 返回</button></main>';
}
function legal(kind) {
  const docs={
    privacy:['隐私说明','<p>可话只收集提供服务所必需的数据：账号信息、个人资料、你主动发布的动态、共鸣关系、好友和聊天记录，以及你主动上传的媒体。</p><h2>谁能看到内容</h2><p>动态默认不是公开广场内容。数据库权限按作者、好友或已获得对应共鸣访问的人进行限制；聊天只对会话双方开放。</p><h2>媒体</h2><p>图片和视频存放在私有存储桶中，通过动态可见性决定读取权限。</p><h2>位置</h2><p>当前只允许用户自愿填写城市级信息，不采集精确坐标。</p><h2>安全</h2><p>用户可以举报或屏蔽他人；被屏蔽的双方不能新建会话或继续发送消息。</p>'],
    terms:['服务条款','<p>这是独立 clean-room 重建服务，不代表原「可话」运营方，也不声称拥有原产品源代码或专有算法。</p><h2>使用规则</h2><p>不得发布违法内容、骚扰、威胁、仇恨、欺诈、侵犯隐私或未经许可的私密内容。不得利用服务进行垃圾营销、抓取他人数据或绕过访问控制。</p><h2>会员</h2><p>会员权限以服务器记录为准。真实支付接入前不会收取会员费用，也不会伪造购买成功。</p>'],
    community:['社区与安全','<p>可话希望保留一种低噪音、低表演压力的交流方式。这里不是公开流量广场。</p><h2>举报</h2><p>在共鸣卡片中可以直接举报具体用户或动态。举报记录写入服务器。</p><h2>屏蔽</h2><p>屏蔽后，系统会阻止双方新建聊天，并阻止已有会话继续发送新消息；共鸣匹配也会排除被屏蔽关系。</p><h2>紧急情况</h2><p>如果内容涉及现实中的即时危险，请优先联系当地紧急服务，而不是只依赖应用内举报。</p>']
  };
  const [title,body]=docs[kind]||docs.privacy;
  return topbar(title,'公开运营必要说明')+'<main class="shell"><article class="card legal"><h1>'+title+'</h1>'+body+'</article><button class="ghost-btn" data-route="'+(state.user?'settings':'home')+'">← 返回</button></main>';
}
function chat() {
  return topbar(state.chatPeer?.nickname||'聊天','回应与私聊在同一会话里')+'<main class="shell" style="padding-bottom:100px">'+notice()+'<div class="row between wrap"><button class="ghost-btn" data-route="messages">← 消息</button>'+(state.chatPeer?'<div class="row"><button class="ghost-btn tiny" data-request-friend="'+state.chatPeer.id+'">加朋友</button><button class="ghost-btn tiny" data-report-user="'+state.chatPeer.id+'">举报</button><button class="ghost-btn tiny" data-block-user="'+state.chatPeer.id+'">屏蔽</button></div>':'')+'</div><div class="messages">'+(state.chatMessages.length?state.chatMessages.map(m=>'<div class="msg '+(m.sender_id===state.user.id?'me':'other')+'">'+esc(m.body)+'</div>').join(''):'<div class="empty">从一句真正想说的话开始。</div>')+'</div></main><form id="message-form" class="composer-fixed"><div class="composer-inner"><input class="input" name="body" maxlength="4000" autocomplete="off" placeholder="说点什么…" required><button class="primary-btn" type="submit">发送</button></div></form>';
}

function render() {
  if (!state.session || !state.user) {
    const r=parseRoute();
    root.innerHTML=['privacy','terms','community'].includes(r.view)?legal(r.view):landing();
    return;
  }
  const views={home,resonance,messages,friends,me,membership,settings,privacy:()=>legal('privacy'),terms:()=>legal('terms'),community:()=>legal('community'),chat};
  root.innerHTML='<div class="app">'+(views[state.view]||home)()+'</div>';
}

async function enrichPosts(posts) {
  if (!posts?.length) return [];
  const authors=[...new Set(posts.map(p=>p.author_id))];
  const {data:profiles}=await supabase.from('profiles').select('*').in('id',authors);
  const pmap=new Map((profiles||[]).map(p=>[p.id,p]));
  const out=[];
  for (const post of posts) {
    const {data:mediaRows}=await supabase.from('post_media').select('*').eq('post_id',post.id).order('position');
    const media=[];
    for (const m of mediaRows||[]) {
      const {data}=await supabase.storage.from('kehua-media').createSignedUrl(m.storage_path,3600);
      if (data?.signedUrl) media.push({...m,url:data.signedUrl});
    }
    out.push({...post,profile:pmap.get(post.author_id),media});
  }
  return out;
}
async function loadOwnPosts() {
  const {data,error}=await supabase.from('posts').select('*').eq('author_id',state.user.id).order('created_at',{ascending:false}).limit(30);
  if(error) throw error; state.ownPosts=await enrichPosts(data||[]);
}
async function loadResonance() {
  const {data:rows,error}=await supabase.from('resonance_candidates').select('*').eq('user_id',state.user.id).order('created_at',{ascending:false}).limit(40);
  if(error) throw error;
  const ids=[...new Set((rows||[]).map(r=>r.candidate_post_id))];
  if(!ids.length){state.resonance=[];return;}
  const {data:posts,error:pe}=await supabase.from('posts').select('*').in('id',ids); if(pe) throw pe;
  const enriched=await enrichPosts(posts||[]); const map=new Map(enriched.map(p=>[p.id,p]));
  state.resonance=(rows||[]).filter(r=>map.has(r.candidate_post_id)).map(r=>({score:r.score,post:map.get(r.candidate_post_id)}));
  await track('resonance_viewed',{count:state.resonance.length});
}
async function loadConversations() {
  const {data:memberships,error}=await supabase.from('conversation_members').select('conversation_id').eq('user_id',state.user.id); if(error) throw error;
  const ids=(memberships||[]).map(x=>x.conversation_id); if(!ids.length){state.conversations=[];return;}
  const {data:convs,error:ce}=await supabase.from('conversations').select('*').in('id',ids).order('last_message_at',{ascending:false}); if(ce) throw ce;
  const {data:allMembers,error:me}=await supabase.from('conversation_members').select('*').in('conversation_id',ids); if(me) throw me;
  const peerIds=[...new Set((allMembers||[]).filter(m=>m.user_id!==state.user.id).map(m=>m.user_id))];
  const {data:profiles}=peerIds.length?await supabase.from('profiles').select('*').in('id',peerIds):{data:[]}; const pmap=new Map((profiles||[]).map(p=>[p.id,p]));
  const by=new Map(); for(const m of allMembers||[]){if(!by.has(m.conversation_id))by.set(m.conversation_id,[]);by.get(m.conversation_id).push(m);}
  const out=[]; for(const c of convs||[]){const peerId=(by.get(c.id)||[]).find(m=>m.user_id!==state.user.id)?.user_id;const {data:last}=await supabase.from('messages').select('*').eq('conversation_id',c.id).order('created_at',{ascending:false}).limit(1);out.push({...c,peer:pmap.get(peerId),last:last?.[0]||null});}
  state.conversations=out;
}
async function loadFriends() {
  const uid=state.user.id; const {data,error}=await supabase.from('friendships').select('*').or('user_low.eq.'+uid+',user_high.eq.'+uid).order('updated_at',{ascending:false}); if(error) throw error;
  const peerIds=[...new Set((data||[]).map(f=>f.user_low===uid?f.user_high:f.user_low))]; const {data:profiles}=peerIds.length?await supabase.from('profiles').select('*').in('id',peerIds):{data:[]}; const map=new Map((profiles||[]).map(p=>[p.id,p]));
  state.friends=(data||[]).map(f=>({...f,peer:map.get(f.user_low===uid?f.user_high:f.user_low)}));
}
async function loadEntitlement(){const {data}=await supabase.from('entitlements').select('*').eq('user_id',state.user.id).maybeSingle();state.entitlement=data||null;}
async function loadInterest(){const {data}=await supabase.from('membership_interest').select('*').eq('user_id',state.user.id).maybeSingle();state.interest=data||null;}
async function loadChat(id) {
  const {data:members,error:me}=await supabase.from('conversation_members').select('*').eq('conversation_id',id);if(me)throw me;
  const peerId=(members||[]).find(m=>m.user_id!==state.user.id)?.user_id;if(!peerId)throw new Error('会话不存在或你没有访问权限。');
  const {data:peer}=await supabase.from('profiles').select('*').eq('id',peerId).single();state.chatPeer=peer;
  const {data:msgs,error}=await supabase.from('messages').select('*').eq('conversation_id',id).order('created_at',{ascending:true}).limit(200);if(error)throw error;state.chatMessages=msgs||[];
  await supabase.rpc('mark_conversation_read',{_conversation_id:id});
  state.chatChannel=supabase.channel('chat-'+id).on('postgres_changes',{event:'INSERT',schema:'public',table:'messages',filter:'conversation_id=eq.'+id},payload=>{if(!state.chatMessages.some(m=>m.id===payload.new.id)){state.chatMessages.push(payload.new);render();window.scrollTo({top:document.body.scrollHeight,behavior:'smooth'});}}).subscribe();
}
async function startChatWith(userId){const {data,error}=await supabase.rpc('get_or_create_conversation',{_other:userId});if(error)throw error;await track('conversation_started',{peer:userId});location.hash='chat='+data;}

async function handleAuth(form) {
  state.busy=true;clear();render(); const fd=new FormData(form); const email=String(fd.get('email')||'').trim(); const password=String(fd.get('password')||'');
  try {
    if(state.authMode==='register'){const nickname=String(fd.get('nickname')||'').trim();const {data,error}=await supabase.auth.signUp({email,password,options:{data:{nickname}}});if(error)throw error;if(!data.session)ok('账号已创建。请到邮箱完成真实确认后再登录。');else ok('账号已创建并登录。');}
    else {const {error}=await supabase.auth.signInWithPassword({email,password});if(error)throw error;}
  } catch(e){fail(e.message||'操作失败。');}
  state.busy=false;render();
}
async function handlePost(form) {
  state.busy=true;clear();render();
  try {
    const fd=new FormData(form);const content=String(fd.get('content')||'').trim();if(!content)throw new Error('先写下你真正想说的话。');
    const {data:post,error}=await supabase.from('posts').insert({author_id:state.user.id,content,visibility:'mutual'}).select('*').single();if(error)throw error;
    let pos=0;for(const file of state.pendingFiles){if(file.size>25*1024*1024)throw new Error('单个媒体文件暂时不能超过 25MB。');const safe=file.name.replace(/[^a-zA-Z0-9._-]+/g,'-');const path=state.user.id+'/'+post.id+'/'+Date.now()+'-'+pos+'-'+safe;const {error:ue}=await supabase.storage.from('kehua-media').upload(path,file,{upsert:false,contentType:file.type});if(ue)throw ue;const kind=file.type.startsWith('video/')?'video':'image';const {error:ie}=await supabase.from('post_media').insert({post_id:post.id,owner_id:state.user.id,storage_path:path,kind,position:pos});if(ie)throw ie;pos++;}
    await track('post_created',{media_count:state.pendingFiles.length});const {error:re}=await supabase.rpc('generate_resonance',{_seed_post_id:post.id,_gender:null,_nearby:false});if(re)throw re;state.pendingFiles=[];location.hash='resonance';
  } catch(e){fail(e.message||'发表失败。');state.busy=false;render();}
}
async function sendMessage(form) {
  const fd=new FormData(form);const body=String(fd.get('body')||'').trim();if(!body)return;const input=form.querySelector('[name="body"]');input.value='';
  const {data,error}=await supabase.from('messages').insert({conversation_id:state.chatId,sender_id:state.user.id,body}).select('*').single();if(error){fail(error.message);render();return;}if(!state.chatMessages.some(m=>m.id===data.id))state.chatMessages.push(data);await track('message_sent',{conversation_id:state.chatId});render();window.scrollTo({top:document.body.scrollHeight,behavior:'smooth'});
}

document.addEventListener('submit',async e=>{
  if(e.target.id==='auth-form'){e.preventDefault();await handleAuth(e.target);}
  if(e.target.id==='post-form'){e.preventDefault();await handlePost(e.target);}
  if(e.target.id==='message-form'){e.preventDefault();await sendMessage(e.target);}
  if(e.target.id==='profile-form'){e.preventDefault();const fd=new FormData(e.target);const patch={nickname:String(fd.get('nickname')||'').trim(),bio:String(fd.get('bio')||'').trim()||null,city:String(fd.get('city')||'').trim()||null,updated_at:new Date().toISOString()};const {data,error}=await supabase.from('profiles').update(patch).eq('id',state.user.id).select('*').single();if(error)fail(error.message);else{state.profile=data;ok('资料已保存。');}render();}
});

document.addEventListener('change',e=>{if(e.target.id==='media-input'){state.pendingFiles=[...e.target.files].slice(0,6);const x=document.getElementById('file-summary');if(x)x.textContent=state.pendingFiles.length?state.pendingFiles.map(f=>f.name).join(' · '):'未选择媒体';}});

document.addEventListener('click',async e=>{
  const el=e.target.closest('button,a');if(!el)return;
  if(el.dataset.authMode){state.authMode=el.dataset.authMode;clear();render();return;}
  if(el.dataset.route){e.preventDefault();location.hash=el.dataset.route;return;}
  if(el.dataset.chat){location.hash='chat='+el.dataset.chat;return;}
  if(el.dataset.startChat){try{await startChatWith(el.dataset.startChat);}catch(err){fail(err.message);render();}return;}
  if(el.dataset.lightPost){try{const {error}=await supabase.from('reactions').insert({post_id:el.dataset.lightPost,user_id:state.user.id,author_id:el.dataset.author,kind:'light'});if(error&&!String(error.message).includes('duplicate'))throw error;const {data:cid,error:ce}=await supabase.rpc('get_or_create_conversation',{_other:el.dataset.author});if(ce)throw ce;await track('conversation_started',{source:'resonance'});location.hash='chat='+cid;}catch(err){fail(err.message);render();}return;}
  if(el.dataset.acceptFriend){try{const {error}=await supabase.rpc('accept_friend',{_other:el.dataset.acceptFriend});if(error)throw error;await track('friend_added',{source:'accepted'});await loadFriends();ok('已经成为朋友。');render();}catch(err){fail(err.message);render();}return;}
  if(el.dataset.requestFriend){try{const {data,error}=await supabase.rpc('request_friend',{_other:el.dataset.requestFriend});if(error)throw error;if(data?.status==='accepted')await track('friend_added',{source:'mutual_request'});ok(data?.status==='accepted'?'已经成为朋友。':'好友申请已发出。');render();}catch(err){fail(err.message);render();}return;}
  if(el.dataset.reportUser){const reason=prompt('举报原因（例如：骚扰、欺诈、违法内容、侵犯隐私）');if(!reason)return;const details=prompt('补充说明（可留空）')||null;const payload={reporter_id:state.user.id,reported_user_id:el.dataset.reportUser,reason,details};if(el.dataset.reportPost)payload.post_id=el.dataset.reportPost;const {error}=await supabase.from('user_reports').insert(payload);error?fail(error.message):ok('举报已提交。');render();return;}
  if(el.dataset.blockUser){if(!confirm('屏蔽后，双方将无法继续发新消息，也不会再互相匹配共鸣。确定吗？'))return;const {error}=await supabase.from('user_blocks').insert({blocker_id:state.user.id,blocked_id:el.dataset.blockUser});if(error&&!String(error.message).includes('duplicate'))fail(error.message);else{ok('已屏蔽。');if(state.view==='chat')location.hash='messages';else await loadResonance();}render();return;}
  if(el.dataset.membershipInterest!==undefined){const {data,error}=await supabase.from('membership_interest').upsert({user_id:state.user.id,plan:'vip',updated_at:new Date().toISOString()}).select('*').single();if(error)fail(error.message);else{state.interest=data;ok('已登记。真实支付开放后会按这条记录通知。');await track('membership_viewed',{interest:true});}render();return;}
  if(el.dataset.toggleTheme!==undefined){const dark=document.documentElement.classList.toggle('dark');localStorage.setItem('kehua-theme',dark?'dark':'light');render();return;}
  if(el.dataset.signout!==undefined){await supabase.auth.signOut();return;}
});

boot().catch(e=>{root.innerHTML='<div class="boot">启动失败：'+esc(e.message||e)+'</div>';});
