(() => {
'use strict';
const $ = id => document.getElementById(id);
const KEY = 'tongpin-chats-v1';
const createId = () => (crypto.randomUUID ? crypto.randomUUID() : 'c-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2));
function load(){try{const x=JSON.parse(localStorage.getItem(KEY)||'[]');return Array.isArray(x)?x.filter(c=>c&&typeof c.id==='string'&&Array.isArray(c.messages)).slice(0,35):[]}catch{return []}}
let chats = load();
let current = chats[0]?.id || null;
let busy = false;
let connected = false;
function closeSidebar(){$('sidebar').classList.remove('open');$('sidebarBackdrop').classList.add('hidden')}
function toggleSidebar(){const open=$('sidebar').classList.toggle('open');$('sidebarBackdrop').classList.toggle('hidden',!open)}
let accessPassword = sessionStorage.getItem('tongpin-auth') || '';
let maxUsd = 0.06;
const modeLabels = {solo:'GPT 单独回答',duo:'GPT + Claude 协作'};
function save(){try{localStorage.setItem(KEY,JSON.stringify(chats.slice(0,35)))}catch{showError('浏览器储存空间不足，请在设置中导出聊天备份。')}}
function activeChat(){return chats.find(c=>c.id===current)}
function showError(s){$('alert').textContent=s;$('alert').classList.remove('hidden')}
function clearError(){$('alert').textContent='';$('alert').classList.add('hidden')}
function renderList(){const list=$('historyList');list.replaceChildren();for(const c of chats){const b=document.createElement('button');b.className='history-item'+(current===c.id?' selected':'');b.textContent=c.title||'新对话';b.title=c.title||'新对话';b.onclick=()=>{if(!busy){current=c.id;closeSidebar();clearError();render()}};list.appendChild(b)}}
function makeMessage(role,text,meta){const wrap=document.createElement('div');wrap.className='bubble-wrap '+role;const bubble=document.createElement('div');bubble.className='bubble';bubble.textContent=text;wrap.append(bubble);if(meta){const label=document.createElement('div');label.className='bubble-meta';label.textContent=meta;wrap.append(label)}return wrap}
function renderMessages(){const container=$('messages');container.replaceChildren();const chat=activeChat();if(!chat||chat.messages.length===0){const el=document.createElement('section');el.className='empty-state';el.id='emptyState';el.innerHTML='<div class="sunflower" aria-hidden="true">✳</div><h1>在这里，慢慢说。</h1><p>不必想好怎么提问。<br>简单的事直接回答，复杂的问题再让 GPT 和 Claude 一起想。</p>';container.append(el);return}for(const m of chat.messages){container.append(makeMessage(m.role,m.content,m.meta||''))}if(busy){const wait=makeMessage('assistant','正在思考…','调用模型中，请不要重复发送');wait.classList.add('pending');container.append(wait)}container.scrollTop=container.scrollHeight}
function render(){renderList();renderMessages()}
function createChat(){if(busy)return;closeSidebar();const c={id:createId(),title:'新对话',messages:[],createdAt:Date.now()};chats.unshift(c);current=c.id;save();clearError();render();$('messageInput').focus()}
function openSettings(){ $('accessPassword').value=accessPassword;$('maxUsd').value=String(maxUsd);$('scrim').classList.remove('hidden');$('settings').classList.remove('hidden');$('accessPassword').focus()}
function closeSettings(){$('scrim').classList.add('hidden');$('settings').classList.add('hidden')}
async function status(){try{const r=await fetch('/api/status',{cache:'no-store'});const d=await r.json();connected=!!d.configured&&!!d.passwordProtected;$('statusDot').className='status-dot '+(connected?'ready':'error');$('statusText').textContent=connected?'密钥已配置':'尚未连接 API';if(!connected)showError('服务端尚未配置受限 API 密钥和访问密码。不会发送付费请求。')}catch{connected=false;$('statusDot').className='status-dot error';$('statusText').textContent='连接检查失败';showError('无法连接服务端。')}}
async function onSend(ev){ev.preventDefault();if(busy)return;const area=$('messageInput');const text=area.value.trim();if(!text)return;clearError();if(!connected){showError('尚未接入302.AI，消息没有发送，也不会扣费。');return}if(!accessPassword){openSettings();showError('先在设置里填入此服务的访问密码。');return}if(!activeChat())createChat();const chat=activeChat();chat.messages.push({role:'user',content:text});if(chat.title==='新对话')chat.title=text.slice(0,26);area.value='';area.style.height='auto';busy=true;$('sendBtn').disabled=true;save();render();try{
const mode=$('mode').value;
const response=await fetch('/api/chat',{method:'POST',headers:{'Content-Type':'application/json','X-Tongpin-Password':accessPassword},body:JSON.stringify({messages:chat.messages.filter(m=>m.role==='user'||m.role==='assistant').map(({role,content})=>({role,content})).slice(-25),mode,maxUsd})});
const data=await response.json();if(!response.ok||!data.ok){showError(data.error||'模型调用失败。');if(data.trace?.length)showError(data.error+' 已执行 '+data.trace.length+' 次模型调用，可能产生费用。');}
else{const modelText=data.trace?.map(x=>x.model).join(' → ')||modeLabels[data.mode];const usages=data.trace?.map(x=>x.usage).filter(Boolean);const tokens=usages?.reduce((n,u)=>n+(u.total_tokens||0),0)||0;chat.messages.push({role:'assistant',content:data.answer,meta:modelText+(tokens?' · '+tokens+' Token':'')+' · 费用以302.AI账单为准'});save();}
}catch{showError('连接异常。可能已开始调用，请先检查302.AI用量，不要立即重复发送。')}
finally{busy=false;$('sendBtn').disabled=false;render();area.focus()}}
$('chatForm').addEventListener('submit',onSend);
$('messageInput').addEventListener('keydown',ev=>{if(ev.key==='Enter'&&!ev.shiftKey&&!ev.isComposing){ev.preventDefault();$('chatForm').requestSubmit()}});
$('messageInput').addEventListener('input',ev=>{const el=ev.target;el.style.height='auto';el.style.height=Math.min(el.scrollHeight,143)+'px'});
$('newChat').addEventListener('click',createChat);
$('mobileNewChat').addEventListener('click',createChat);
$('historyBtn').addEventListener('click',toggleSidebar);
$('sidebarBackdrop').addEventListener('click',closeSidebar);
$('settingsBtn').addEventListener('click',openSettings);
$('closeSettings').addEventListener('click',closeSettings);
$('scrim').addEventListener('click',closeSettings);
$('saveSettings').addEventListener('click',()=>{accessPassword=$('accessPassword').value;sessionStorage.setItem('tongpin-auth',accessPassword);const x=Number($('maxUsd').value);maxUsd=Number.isFinite(x)?Math.max(.001,Math.min(1,x)):.06;closeSettings();clearError()});
$('exportChat').addEventListener('click',()=>{const blob=new Blob([JSON.stringify({type:'tongpin-chat-export',version:1,exportedAt:new Date().toISOString(),chats},null,2)],{type:'application/json'});const u=URL.createObjectURL(blob);const a=document.createElement('a');a.href=u;a.download='同频-聊天备份.json';a.click();setTimeout(()=>URL.revokeObjectURL(u),500)});
$('mode').addEventListener('change',ev=>{$('modeHint').textContent={auto:'普通聊天更省钱',solo:'只调用一次 GPT',duo:'复杂任务调用三次模型'}[ev.target.value]});
render();status();
})();
