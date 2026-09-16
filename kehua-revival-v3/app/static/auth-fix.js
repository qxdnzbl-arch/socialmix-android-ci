let authFixInstalled=false;
let confirmResendBtn=null;

function setAuthMode(mode){
  S.authMode=mode;
  $$('[data-auth-mode]').forEach(x=>x.classList.toggle('active',x.dataset.authMode===mode));
  $('#authSubmit').textContent=mode==='login'?'登录':'进去';
  const forgot=document.querySelector('#forgotPasswordBtn');
  if(forgot)forgot.style.display=mode==='login'?'block':'none';
  if(confirmResendBtn)confirmResendBtn.style.display='none';
  const p=$('#authPassword');
  if(p)p.autocomplete=mode==='login'?'current-password':'new-password';
}

function authMessage(e){
  const raw=String(e?.message||e||'').toLowerCase();
  if(e?.code==='email_not_confirmed'||raw.includes('email not confirmed'))return '这个邮箱还没完成验证。请先打开注册邮件里的确认链接。';
  if(raw.includes('invalid login credentials'))return '邮箱或密码不对。忘记密码可以点下面的“忘记密码？”。';
  if(raw.includes('rate')||raw.includes('too many'))return '操作太频繁了，请稍后再试。';
  if(raw.includes('password'))return '密码至少需要 6 位。';
  return '暂时无法完成，请稍后再试。';
}

async function resendConfirmation(){
  const email=$('#authUsername').value.trim().toLowerCase();
  const box=$('#authError');
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)){box.textContent='先填写你注册时使用的邮箱。';return}
  confirmResendBtn.disabled=true;box.textContent='';
  try{
    const {error}=await sb.auth.resend({type:'signup',email,options:{emailRedirectTo:`${location.origin}/?email_confirmed=1`}});
    if(error)throw error;
    box.textContent='确认邮件已重新发送。';
  }catch(e){box.textContent=authMessage(e)}
  finally{confirmResendBtn.disabled=false}
}

async function auth(mode){
  const email=$('#authUsername').value.trim().toLowerCase();
  const password=$('#authPassword').value;
  const box=$('#authError');
  const submit=$('#authSubmit');
  box.textContent='';
  if(confirmResendBtn)confirmResendBtn.style.display='none';
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)||password.length<6){box.textContent='请填写有效邮箱，密码至少 6 位';return}
  submit.disabled=true;
  try{
    if(mode==='register'){
      const {data,error}=await sb.auth.signUp({
        email,password,
        options:{
          emailRedirectTo:`${location.origin}/?email_confirmed=1`,
          data:{nickname:email.split('@')[0].slice(0,20)||'可话用户',kehua_app:'true'}
        }
      });
      if(error)throw error;
      if(data?.session){await boot();return}
      if(data?.user&&Array.isArray(data.user.identities)&&data.user.identities.length===0){
        setAuthMode('login');
        box.textContent='这个邮箱已经有账号了。请直接登录；忘记密码可以点下面的“忘记密码？”。';
        return;
      }
      setAuthMode('login');
      box.textContent='确认邮件已经发送。打开邮件里的确认链接后，再回来登录。';
      confirmResendBtn.style.display='block';
      return;
    }
    const {error}=await sb.auth.signInWithPassword({email,password});
    if(error){
      if(error.code==='email_not_confirmed'||String(error.message||'').toLowerCase().includes('email not confirmed')){
        confirmResendBtn.style.display='block';
      }
      throw error;
    }
    await boot();
  }catch(e){box.textContent=authMessage(e)}
  finally{submit.disabled=false}
}

function recoveryOverlay(){
  let el=document.getElementById('passwordRecoveryPanel');
  if(el)return el;
  el=document.createElement('div');
  el.id='passwordRecoveryPanel';
  el.style.cssText='display:none;position:fixed;inset:0;z-index:99999;background:#f7f7f8;padding:env(safe-area-inset-top) 22px env(safe-area-inset-bottom);box-sizing:border-box;overflow:auto;';
  el.innerHTML=`<div style="max-width:520px;margin:0 auto;padding-top:28px;">
    <button type="button" id="recoveryBack" style="border:0;background:transparent;font-size:28px;line-height:1;padding:8px 8px 8px 0;color:#333;">‹</button>
    <h2 style="font-size:24px;margin:32px 0 8px;color:#222;">设置新密码</h2>
    <p style="margin:0 0 28px;color:#8b8b91;font-size:15px;line-height:1.65;">输入新的登录密码。</p>
    <label style="display:block;border-bottom:1px solid #e3e3e7;padding:12px 0;"><input id="newPassword1" type="password" autocomplete="new-password" placeholder="新密码，至少 6 位" style="width:100%;border:0;outline:0;background:transparent;font-size:17px;color:#222;box-sizing:border-box;"></label>
    <label style="display:block;border-bottom:1px solid #e3e3e7;padding:12px 0;"><input id="newPassword2" type="password" autocomplete="new-password" placeholder="再输入一次" style="width:100%;border:0;outline:0;background:transparent;font-size:17px;color:#222;box-sizing:border-box;"></label>
    <p id="recoveryError" style="min-height:24px;margin:16px 0;color:#d8576b;font-size:14px;"></p>
    <button type="button" id="saveNewPassword" style="width:100%;height:52px;border:0;border-radius:26px;background:#ff3e68;color:white;font-size:17px;font-weight:600;">保存新密码</button>
  </div>`;
  document.body.appendChild(el);
  el.querySelector('#recoveryBack').onclick=()=>{el.style.display='none';history.replaceState({},'',location.pathname)};
  el.querySelector('#saveNewPassword').onclick=async()=>{
    const p1=el.querySelector('#newPassword1').value;
    const p2=el.querySelector('#newPassword2').value;
    const err=el.querySelector('#recoveryError');
    const btn=el.querySelector('#saveNewPassword');
    err.textContent='';
    if(p1.length<6){err.textContent='新密码至少 6 位';return}
    if(p1!==p2){err.textContent='两次输入的密码不一致';return}
    btn.disabled=true;
    try{
      const {error}=await sb.auth.updateUser({password:p1});
      if(error)throw error;
      await sb.auth.signOut();
      el.style.display='none';
      history.replaceState({},'',location.pathname);
      showAuth();setAuthMode('login');
      $('#authPassword').value='';
      $('#authError').textContent='密码已经重置。现在用新密码登录。';
    }catch(e){err.textContent=authMessage(e)}
    finally{btn.disabled=false}
  };
  return el;
}

function installAuthFix(){
  if(authFixInstalled)return;
  const passwordInput=document.querySelector('#authPassword');
  if(!passwordInput)return;
  authFixInstalled=true;

  const forgot=document.createElement('button');
  forgot.type='button';forgot.id='forgotPasswordBtn';forgot.textContent='忘记密码？';
  forgot.style.cssText='display:none;margin:10px 0 2px auto;padding:4px 0;border:0;background:transparent;color:#777;font-size:14px;line-height:1.4;cursor:pointer;';
  passwordInput.closest('label').insertAdjacentElement('afterend',forgot);

  confirmResendBtn=document.createElement('button');
  confirmResendBtn.type='button';confirmResendBtn.id='resendConfirmBtn';confirmResendBtn.textContent='重新发送确认邮件';
  confirmResendBtn.style.cssText='display:none;margin:8px 0 2px auto;padding:4px 0;border:0;background:transparent;color:#777;font-size:14px;line-height:1.4;cursor:pointer;';
  forgot.insertAdjacentElement('afterend',confirmResendBtn);
  confirmResendBtn.onclick=resendConfirmation;

  document.querySelectorAll('[data-auth-mode]').forEach(b=>b.addEventListener('click',()=>setTimeout(()=>setAuthMode(b.dataset.authMode),0)));
  setAuthMode(S.authMode||'register');

  forgot.onclick=async()=>{
    const email=$('#authUsername').value.trim().toLowerCase();
    const box=$('#authError');
    if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)){box.textContent='先填写你注册时使用的邮箱。';return}
    forgot.disabled=true;box.textContent='';
    try{
      const {error}=await sb.auth.resetPasswordForEmail(email,{redirectTo:`${location.origin}/?password_recovery=1`});
      if(error)throw error;
      box.textContent='如果这个邮箱注册过，我们已经发送了重置密码邮件。';
    }catch(e){box.textContent=authMessage(e)}
    finally{forgot.disabled=false}
  };

  const overlay=recoveryOverlay();
  const showReset=()=>{
    overlay.style.display='block';
    overlay.querySelector('#newPassword1').value='';
    overlay.querySelector('#newPassword2').value='';
    overlay.querySelector('#recoveryError').textContent='';
  };
  const wait=setInterval(()=>{
    if(typeof sb==='undefined'||!sb)return;
    clearInterval(wait);
    sb.auth.onAuthStateChange((event)=>{if(event==='PASSWORD_RECOVERY')showReset()});
    const q=new URLSearchParams(location.search);
    const recoveryInUrl=location.hash.includes('type=recovery')||q.get('password_recovery')==='1';
    if(recoveryInUrl){setTimeout(async()=>{const {data}=await sb.auth.getSession();if(data?.session)showReset();else{$('#authError').textContent='这个重置链接已经失效，请重新申请。';showAuth();setAuthMode('login')}},350)}
    if(q.get('email_confirmed')==='1'){
      history.replaceState({},'',location.pathname);
      setAuthMode('login');
      $('#authError').textContent='邮箱已经确认，可以登录了。';
    }
  },100);

  document.querySelector('#recoveryCodePanel')?.remove();
  document.querySelector('#forgotRecoveryPanel')?.remove();
  document.querySelector('#recoveryCodeBtn')?.remove();
}

installAuthFix();
