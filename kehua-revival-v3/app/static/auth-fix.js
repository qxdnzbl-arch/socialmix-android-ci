let recoveryUiInstalled=false;
let pendingRecoveryCode=null;

async function edgeCall(name,payload={},useSession=false){
  let bearer=SUPABASE_KEY;
  if(useSession){
    const {data}=await sb.auth.getSession();
    bearer=data?.session?.access_token||'';
    if(!bearer)throw new Error('请先登录');
  }
  const r=await fetch(`${SUPABASE_URL}/functions/v1/${name}`,{
    method:'POST',
    headers:{'Content-Type':'application/json','apikey':SUPABASE_KEY,'Authorization':`Bearer ${bearer}`},
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
  syncForgotPasswordVisibility();
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
      const out=await edgeCall('kehua-register',{email,password},false);
      const {error}=await sb.auth.signInWithPassword({email,password});
      if(error){
        const msg=String(error.message||'').toLowerCase();
        if(msg.includes('invalid login credentials')){
          setAuthMode('login');
          throw new Error(out.created===false?'这个邮箱已经有账号了。请用原密码登录；忘记密码就点“忘记密码？”。':'注册完成，但暂时无法登录，请重新输入密码。');
        }
        throw error;
      }
      pendingRecoveryCode=out.recovery_code||null;
    }else{
      let {error}=await sb.auth.signInWithPassword({email,password});
      if(error){
        const msg=String(error.message||'').toLowerCase();
        if(error.code==='email_not_confirmed'||msg.includes('email not confirmed')||msg.includes('not confirmed')){
          const r=await edgeCall('kehua-register',{email,password},false);
          if(r?.ok)({error}=await sb.auth.signInWithPassword({email,password}));
        }
        if(error){
          const m=String(error.message||'').toLowerCase();
          if(m.includes('invalid login credentials'))throw new Error('邮箱或密码不对。忘记密码可以点“忘记密码？”。');
          throw error;
        }
      }
    }
    await boot();
    installRecoveryUi();
    if(pendingRecoveryCode){
      const code=pendingRecoveryCode;pendingRecoveryCode=null;
      showRecoveryCode(code,'保存账号恢复码','以后忘记密码时，用它找回账号。');
    }else{
      await ensureRecoveryCode(false);
    }
  }catch(e){box.textContent=String(e?.message||'暂时进不去').replace('Email not confirmed','账号状态异常，请重新登录')}
  finally{submit.disabled=false}
}

function syncForgotPasswordVisibility(){
  const b=document.querySelector('#forgotPasswordBtn');
  if(!b)return;
  b.style.display=(typeof S!=='undefined'&&S.authMode==='login')?'block':'none';
}

function recoveryPanelBase(id){
  let el=document.getElementById(id);
  if(el)return el;
  el=document.createElement('div');
  el.id=id;
  el.style.cssText='display:none;position:fixed;inset:0;z-index:99999;background:#f7f7f8;padding:env(safe-area-inset-top) 22px env(safe-area-inset-bottom);box-sizing:border-box;overflow:auto;';
  document.body.appendChild(el);
  return el;
}

function showRecoveryCode(code,title='账号恢复码',note='请把它保存好。'){
  const overlay=recoveryPanelBase('recoveryCodePanel');
  overlay.innerHTML=`<div style="max-width:520px;margin:0 auto;padding-top:44px;">
    <h2 style="font-size:24px;margin:0 0 10px;color:#222;">${title}</h2>
    <p style="margin:0 0 28px;color:#8b8b91;font-size:15px;line-height:1.65;">${note}</p>
    <div id="recoveryCodeValue" style="padding:18px 16px;border:1px solid #e1e1e6;border-radius:16px;background:white;color:#222;font:600 17px/1.7 ui-monospace,SFMono-Regular,Menlo,monospace;letter-spacing:.6px;word-break:break-all;text-align:center;">${code}</div>
    <button type="button" id="copyRecoveryCode" style="width:100%;height:48px;margin-top:18px;border:1px solid #dedee4;border-radius:24px;background:white;color:#333;font-size:16px;">复制恢复码</button>
    <button type="button" id="closeRecoveryCode" style="width:100%;height:52px;margin-top:12px;border:0;border-radius:26px;background:#ff3e68;color:white;font-size:17px;font-weight:600;">我已保存</button>
  </div>`;
  overlay.style.display='block';
  overlay.querySelector('#copyRecoveryCode').onclick=async()=>{try{await navigator.clipboard.writeText(code);toast('已复制')}catch{toast('请长按复制恢复码')}};
  overlay.querySelector('#closeRecoveryCode').onclick=()=>overlay.style.display='none';
}

async function ensureRecoveryCode(rotate=false){
  try{
    const out=await edgeCall('kehua-recovery-code',{mode:rotate?'rotate':'ensure'},true);
    if(out?.created&&out?.recovery_code){
      showRecoveryCode(out.recovery_code,rotate?'新的账号恢复码':'保存账号恢复码',rotate?'旧恢复码已经失效，请保存新的这一串。':'以后忘记密码时，用它找回账号。');
    }
    return out;
  }catch(e){if(rotate)toast(e.message||'暂时无法生成恢复码');return null}
}

function openForgotPanel(){
  const overlay=recoveryPanelBase('forgotRecoveryPanel');
  const email=$('#authUsername').value.trim().toLowerCase();
  overlay.innerHTML=`<div style="max-width:520px;margin:0 auto;padding-top:22px;">
    <button type="button" id="forgotBack" style="border:0;background:transparent;font-size:28px;line-height:1;padding:8px 8px 8px 0;color:#333;">‹</button>
    <h2 style="font-size:24px;margin:34px 0 8px;color:#222;">找回账号</h2>
    <p style="margin:0 0 26px;color:#8b8b91;font-size:15px;line-height:1.65;">输入注册邮箱和你保存的账号恢复码。</p>
    <label style="display:block;border-bottom:1px solid #e3e3e7;padding:12px 0;"><input id="recoverEmail" type="email" autocomplete="email" value="${email.replace(/"/g,'&quot;')}" placeholder="注册邮箱" style="width:100%;border:0;outline:0;background:transparent;font-size:17px;color:#222;box-sizing:border-box;"></label>
    <label style="display:block;border-bottom:1px solid #e3e3e7;padding:12px 0;"><input id="recoverCode" autocomplete="off" autocapitalize="characters" placeholder="账号恢复码" style="width:100%;border:0;outline:0;background:transparent;font-size:17px;color:#222;box-sizing:border-box;"></label>
    <p id="forgotError" style="min-height:24px;margin:16px 0;color:#d8576b;font-size:14px;"></p>
    <button type="button" id="recoverContinue" style="width:100%;height:52px;border:0;border-radius:26px;background:#ff3e68;color:white;font-size:17px;font-weight:600;">继续</button>
  </div>`;
  overlay.style.display='block';
  overlay.querySelector('#forgotBack').onclick=()=>overlay.style.display='none';
  overlay.querySelector('#recoverContinue').onclick=async()=>{
    const btn=overlay.querySelector('#recoverContinue');
    const err=overlay.querySelector('#forgotError');
    const mail=overlay.querySelector('#recoverEmail').value.trim().toLowerCase();
    const code=overlay.querySelector('#recoverCode').value.trim();
    err.textContent='';
    if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(mail)||code.replace(/[^A-Za-z0-9]/g,'').length<16){err.textContent='请填写注册邮箱和完整恢复码';return}
    btn.disabled=true;
    try{
      const out=await edgeCall('kehua-recovery-login',{email:mail,recovery_code:code},false);
      location.href=out.action_link;
    }catch(e){err.textContent=e?.message||'无法找回账号'}
    finally{btn.disabled=false}
  };
}

function showNewPasswordPanel(){
  const overlay=recoveryPanelBase('passwordRecoveryPanel');
  overlay.innerHTML=`<div style="max-width:520px;margin:0 auto;padding-top:44px;">
    <h2 style="font-size:24px;margin:0 0 8px;color:#222;">设置新密码</h2>
    <p style="margin:0 0 30px;color:#8b8b91;font-size:15px;line-height:1.65;">设置完成后，旧密码会立即失效。</p>
    <label style="display:block;border-bottom:1px solid #e3e3e7;padding:12px 0;"><input id="newPassword1" type="password" autocomplete="new-password" placeholder="新密码，至少 6 位" style="width:100%;border:0;outline:0;background:transparent;font-size:17px;color:#222;box-sizing:border-box;"></label>
    <label style="display:block;border-bottom:1px solid #e3e3e7;padding:12px 0;"><input id="newPassword2" type="password" autocomplete="new-password" placeholder="再输入一次" style="width:100%;border:0;outline:0;background:transparent;font-size:17px;color:#222;box-sizing:border-box;"></label>
    <p id="recoveryError" style="min-height:24px;margin:16px 0;color:#d8576b;font-size:14px;"></p>
    <button type="button" id="saveNewPassword" style="width:100%;height:52px;border:0;border-radius:26px;background:#ff3e68;color:white;font-size:17px;font-weight:600;">保存新密码</button>
  </div>`;
  overlay.style.display='block';
  overlay.querySelector('#saveNewPassword').onclick=async()=>{
    const p1=overlay.querySelector('#newPassword1').value;
    const p2=overlay.querySelector('#newPassword2').value;
    const err=overlay.querySelector('#recoveryError');
    const btn=overlay.querySelector('#saveNewPassword');
    err.textContent='';
    if(p1.length<6){err.textContent='新密码至少 6 位';return}
    if(p1!==p2){err.textContent='两次输入的密码不一致';return}
    btn.disabled=true;
    try{
      const {error}=await sb.auth.updateUser({password:p1});
      if(error)throw error;
      const rotated=await edgeCall('kehua-recovery-code',{mode:'rotate'},true);
      await sb.auth.signOut();
      S.me=null;
      overlay.style.display='none';
      history.replaceState({},'',location.pathname);
      showAuth();setAuthMode('login');
      $('#authPassword').value='';
      $('#authError').textContent='密码已经重置。现在用新密码登录。';
      if(rotated?.recovery_code)showRecoveryCode(rotated.recovery_code,'密码已重置','这是新的账号恢复码，旧恢复码已经失效。');
    }catch(e){err.textContent=e?.message||'密码修改失败，请重新开始找回账号'}
    finally{btn.disabled=false}
  };
}

function installRecoveryUi(){
  if(recoveryUiInstalled)return;
  const passwordInput=document.querySelector('#authPassword');
  if(!passwordInput)return;
  recoveryUiInstalled=true;
  const forgot=document.createElement('button');
  forgot.type='button';forgot.id='forgotPasswordBtn';forgot.textContent='忘记密码？';
  forgot.style.cssText='display:none;margin:10px 0 2px auto;padding:4px 0;border:0;background:transparent;color:#777;font-size:14px;line-height:1.4;cursor:pointer;';
  passwordInput.closest('label').insertAdjacentElement('afterend',forgot);
  forgot.onclick=openForgotPanel;
  document.querySelectorAll('[data-auth-mode]').forEach(b=>b.addEventListener('click',()=>setTimeout(syncForgotPasswordVisibility,0)));
  syncForgotPasswordVisibility();

  const settings=document.querySelector('#settingsPage .settings-list');
  if(settings&&!document.querySelector('#recoveryCodeBtn')){
    const b=document.createElement('button');b.id='recoveryCodeBtn';b.textContent='更换账号恢复码';
    const logout=document.querySelector('#logoutBtn');settings.insertBefore(b,logout||settings.firstChild);
    b.onclick=async()=>{if(confirm('生成新的恢复码后，旧恢复码会立即失效。继续吗？'))await ensureRecoveryCode(true)};
  }
}

installRecoveryUi();

(async()=>{
  for(let i=0;i<30;i++){
    if(typeof sb!=='undefined'&&sb)break;
    await new Promise(r=>setTimeout(r,100));
  }
  installRecoveryUi();
  const recovery=new URLSearchParams(location.search).get('recovery_session')==='1';
  if(recovery){
    for(let i=0;i<30;i++){
      const {data}=await sb.auth.getSession();
      if(data?.session){showNewPasswordPanel();return}
      await new Promise(r=>setTimeout(r,100));
    }
    showAuth();setAuthMode('login');$('#authError').textContent='找回链接已经失效，请重新使用恢复码。';
    return;
  }
  const {data}=await sb.auth.getSession();
  if(data?.session)await ensureRecoveryCode(false);
})();
