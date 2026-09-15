let authFixInstalled=false;

async function edgeCall(name,payload={}){
  const r=await fetch(`${SUPABASE_URL}/functions/v1/${name}`,{
    method:'POST',
    headers:{'Content-Type':'application/json','apikey':SUPABASE_KEY,'Authorization':`Bearer ${SUPABASE_KEY}`},
    body:JSON.stringify(payload)
  });
  let out={};
  try{out=await r.json()}catch{}
  if(!r.ok)throw new Error(out.error||'暂时无法完成，请稍后再试');
  return out;
}

function setAuthMode(mode){
  S.authMode=mode;
  $$('[data-auth-mode]').forEach(x=>x.classList.toggle('active',x.dataset.authMode===mode));
  $('#authSubmit').textContent=mode==='login'?'登录':'进去';
  const b=document.querySelector('#forgotPasswordBtn');
  if(b)b.style.display=mode==='login'?'block':'none';
}

async function auth(mode){
  const email=$('#authUsername').value.trim().toLowerCase();
  const password=$('#authPassword').value;
  const box=$('#authError');
  const submit=$('#authSubmit');
  box.textContent='';
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)||password.length<6){box.textContent='请填写有效邮箱，密码至少 6 位';return}
  submit.disabled=true;
  try{
    if(mode==='register'){
      const out=await edgeCall('kehua-register',{email,password});
      const {error}=await sb.auth.signInWithPassword({email,password});
      if(error){
        const msg=String(error.message||'').toLowerCase();
        if(msg.includes('invalid login credentials')){
          setAuthMode('login');
          throw new Error(out.created===false?'这个邮箱已经有账号了。请直接登录；忘记密码就点“忘记密码？”。':'注册完成，请重新输入密码登录。');
        }
        throw error;
      }
    }else{
      const {error}=await sb.auth.signInWithPassword({email,password});
      if(error){
        const m=String(error.message||'').toLowerCase();
        if(m.includes('invalid login credentials'))throw new Error('邮箱或密码不对。忘记密码可以点下面的“忘记密码？”。');
        throw error;
      }
    }
    await boot();
  }catch(e){box.textContent=String(e?.message||'暂时进不去').replace('Email not confirmed','账号状态异常，请重新登录')}
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
    }catch(e){err.textContent=e?.message||'密码修改失败，请重新打开重置链接'}
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
      box.textContent='重置密码邮件已经发送，请打开邮箱里的链接设置新密码。';
    }catch(e){
      const m=String(e?.message||'').toLowerCase();
      box.textContent=m.includes('rate')?'发送太频繁了，请稍后再试。':'重置邮件发送失败，请稍后再试。';
    }finally{forgot.disabled=false}
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
    const recoveryInUrl=location.hash.includes('type=recovery')||new URLSearchParams(location.search).get('password_recovery')==='1';
    if(recoveryInUrl){setTimeout(async()=>{const {data}=await sb.auth.getSession();if(data?.session)showReset()},300)}
  },100);

  const oldBtn=document.querySelector('#recoveryCodeBtn');if(oldBtn)oldBtn.remove();
}

installAuthFix();
