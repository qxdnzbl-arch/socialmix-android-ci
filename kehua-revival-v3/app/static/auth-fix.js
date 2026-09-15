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
      const r=await fetch(`${SUPABASE_URL}/functions/v1/kehua-register`,{
        method:'POST',
        headers:{'Content-Type':'application/json','apikey':SUPABASE_KEY,'Authorization':`Bearer ${SUPABASE_KEY}`},
        body:JSON.stringify({email,password})
      });
      let out={};
      try{out=await r.json()}catch{}
      if(!r.ok && r.status!==409) throw new Error(out.error||'注册暂时失败，请稍后重试');
      const {error}=await sb.auth.signInWithPassword({email,password});
      if(error){
        const msg=String(error.message||'').toLowerCase();
        if(msg.includes('invalid login credentials')){
          S.authMode='login';
          $$('[data-auth-mode]').forEach(x=>x.classList.toggle('active',x.dataset.authMode==='login'));
          $('#authSubmit').textContent='登录';
          syncForgotPasswordVisibility();
          throw new Error('这个邮箱已经有账号了。请用原密码登录；如果忘了，点“忘记密码？”。');
        }
        throw error;
      }
    }else{
      let {error}=await sb.auth.signInWithPassword({email,password});
      if(error){
        const msg=String(error.message||'').toLowerCase();
        if(error.code==='email_not_confirmed'||msg.includes('email not confirmed')||msg.includes('not confirmed')){
          const r=await fetch(`${SUPABASE_URL}/functions/v1/kehua-register`,{
            method:'POST',
            headers:{'Content-Type':'application/json','apikey':SUPABASE_KEY,'Authorization':`Bearer ${SUPABASE_KEY}`},
            body:JSON.stringify({email,password})
          });
          if(r.ok)({error}=await sb.auth.signInWithPassword({email,password}));
        }
        if(error){
          const m=String(error.message||'').toLowerCase();
          if(m.includes('invalid login credentials')) throw new Error('邮箱或密码不对。忘记密码可以点下面的“忘记密码？”。');
          throw error;
        }
      }
    }
    await boot();
  }catch(e){
    box.textContent=String(e?.message||'暂时进不去').replace('Email not confirmed','账号状态异常，请重新登录');
  }finally{
    submit.disabled=false;
  }
}

let recoveryInstalled=false;
function syncForgotPasswordVisibility(){
  const b=document.querySelector('#forgotPasswordBtn');
  if(!b)return;
  b.style.display=(typeof S!=='undefined'&&S.authMode==='login')?'block':'none';
}

function installPasswordRecovery(){
  if(recoveryInstalled)return;
  const passwordInput=document.querySelector('#authPassword');
  const form=document.querySelector('#authForm');
  if(!passwordInput||!form)return;
  recoveryInstalled=true;

  const forgot=document.createElement('button');
  forgot.type='button';
  forgot.id='forgotPasswordBtn';
  forgot.textContent='忘记密码？';
  forgot.style.cssText='display:none;margin:10px 0 2px auto;padding:4px 0;border:0;background:transparent;color:#777;font-size:14px;line-height:1.4;cursor:pointer;';
  passwordInput.closest('label').insertAdjacentElement('afterend',forgot);

  document.querySelectorAll('[data-auth-mode]').forEach(b=>b.addEventListener('click',()=>setTimeout(syncForgotPasswordVisibility,0)));
  syncForgotPasswordVisibility();

  const overlay=document.createElement('div');
  overlay.id='passwordRecoveryPanel';
  overlay.style.cssText='display:none;position:fixed;inset:0;z-index:99999;background:#f7f7f8;padding:env(safe-area-inset-top) 22px env(safe-area-inset-bottom);box-sizing:border-box;';
  overlay.innerHTML=`
    <div style="max-width:520px;margin:0 auto;padding-top:22px;">
      <button type="button" id="recoveryBack" style="border:0;background:transparent;font-size:28px;line-height:1;padding:8px 8px 8px 0;color:#333;">‹</button>
      <h2 style="font-size:24px;margin:34px 0 8px;color:#222;">设置新密码</h2>
      <p style="margin:0 0 30px;color:#8b8b91;font-size:15px;line-height:1.65;">请输入新的登录密码。</p>
      <label style="display:block;border-bottom:1px solid #e3e3e7;padding:12px 0;"><input id="newPassword1" type="password" autocomplete="new-password" placeholder="新密码，至少 6 位" style="width:100%;border:0;outline:0;background:transparent;font-size:17px;color:#222;box-sizing:border-box;"></label>
      <label style="display:block;border-bottom:1px solid #e3e3e7;padding:12px 0;"><input id="newPassword2" type="password" autocomplete="new-password" placeholder="再输入一次" style="width:100%;border:0;outline:0;background:transparent;font-size:17px;color:#222;box-sizing:border-box;"></label>
      <p id="recoveryError" style="min-height:24px;margin:16px 0;color:#d8576b;font-size:14px;"></p>
      <button type="button" id="saveNewPassword" style="width:100%;height:52px;border:0;border-radius:26px;background:#ff3e68;color:white;font-size:17px;font-weight:600;">保存新密码</button>
    </div>`;
  document.body.appendChild(overlay);

  const showReset=()=>{
    overlay.style.display='block';
    document.querySelector('#newPassword1').value='';
    document.querySelector('#newPassword2').value='';
    document.querySelector('#recoveryError').textContent='';
  };
  const hideReset=()=>overlay.style.display='none';
  window.__showPasswordRecovery=showReset;

  document.querySelector('#recoveryBack').onclick=()=>{
    hideReset();
    history.replaceState({},'',location.pathname);
  };

  document.querySelector('#saveNewPassword').onclick=async()=>{
    const p1=document.querySelector('#newPassword1').value;
    const p2=document.querySelector('#newPassword2').value;
    const err=document.querySelector('#recoveryError');
    const btn=document.querySelector('#saveNewPassword');
    err.textContent='';
    if(p1.length<6){err.textContent='新密码至少 6 位';return}
    if(p1!==p2){err.textContent='两次输入的密码不一致';return}
    btn.disabled=true;
    try{
      const {error}=await sb.auth.updateUser({password:p1});
      if(error)throw error;
      await sb.auth.signOut();
      hideReset();
      history.replaceState({},'',location.pathname);
      showAuth();
      S.authMode='login';
      document.querySelectorAll('[data-auth-mode]').forEach(x=>x.classList.toggle('active',x.dataset.authMode==='login'));
      document.querySelector('#authSubmit').textContent='登录';
      syncForgotPasswordVisibility();
      document.querySelector('#authPassword').value='';
      document.querySelector('#authError').textContent='密码已经重置。现在用新密码登录。';
    }catch(e){
      err.textContent=e?.message||'密码修改失败，请重新打开重置链接';
    }finally{btn.disabled=false}
  };

  forgot.onclick=async()=>{
    const email=document.querySelector('#authUsername').value.trim().toLowerCase();
    const box=document.querySelector('#authError');
    if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)){
      box.textContent='先填写你注册时使用的邮箱。';
      return;
    }
    forgot.disabled=true;
    box.textContent='';
    try{
      const redirectTo=`${location.origin}/?password_recovery=1`;
      const {error}=await sb.auth.resetPasswordForEmail(email,{redirectTo});
      if(error)throw error;
      box.textContent='如果这个邮箱注册过，我们已经发送了重置密码邮件。打开邮件里的重置链接，就可以设置新密码。';
    }catch(e){
      const m=String(e?.message||'');
      box.textContent=m.toLowerCase().includes('rate')?'发送太频繁了，请稍后再试。':'重置邮件暂时发送失败，请稍后再试。';
    }finally{forgot.disabled=false}
  };

  const wait=setInterval(()=>{
    if(typeof sb==='undefined'||!sb)return;
    clearInterval(wait);
    sb.auth.onAuthStateChange((event)=>{
      if(event==='PASSWORD_RECOVERY')showReset();
    });
    const recoveryInUrl=location.hash.includes('type=recovery')||new URLSearchParams(location.search).get('password_recovery')==='1';
    if(recoveryInUrl){
      setTimeout(async()=>{
        const {data}=await sb.auth.getSession();
        if(data?.session)showReset();
      },300);
    }
  },100);
}

installPasswordRecovery();
