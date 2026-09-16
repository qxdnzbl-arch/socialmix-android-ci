let authFixInstalled=false;
let fbCtx=null;
let fbReadyPromise=null;

function setAuthMode(mode){
  S.authMode=mode;
  $$('[data-auth-mode]').forEach(x=>x.classList.toggle('active',x.dataset.authMode===mode));
  $('#authSubmit').textContent=mode==='login'?'登录':'进去';
  const forgot=document.querySelector('#forgotPasswordBtn');
  if(forgot)forgot.style.display=mode==='login'?'block':'none';
  const p=$('#authPassword');
  if(p)p.autocomplete=mode==='login'?'current-password':'new-password';
}

function authMessage(e){
  const code=String(e?.code||'');
  const raw=String(e?.message||e||'').toLowerCase();
  if(code.includes('too-many-requests')||raw.includes('too many')||raw.includes('rate'))return '操作太频繁了，请稍后再试。';
  if(code.includes('invalid-email'))return '邮箱格式不正确。';
  if(code.includes('weak-password'))return '密码至少需要 6 位。';
  if(code.includes('network-request-failed'))return '网络连接失败，请稍后再试。';
  return '邮箱或密码不对。';
}

async function firebaseReady(){
  if(fbCtx)return fbCtx;
  if(fbReadyPromise)return fbReadyPromise;
  fbReadyPromise=(async()=>{
    const [{FIREBASE_CONFIG},appMod,authMod]=await Promise.all([
      import('/firebase-config.js?v=firebase-prod-1'),
      import('https://www.gstatic.com/firebasejs/12.19.0/firebase-app.js'),
      import('https://www.gstatic.com/firebasejs/12.19.0/firebase-auth.js')
    ]);
    if(!FIREBASE_CONFIG||!FIREBASE_CONFIG.apiKey||!FIREBASE_CONFIG.projectId)throw new Error('identity_not_configured');
    const app=appMod.getApps().length?appMod.getApp():appMod.initializeApp(FIREBASE_CONFIG);
    const fbauth=authMod.getAuth(app);
    fbauth.languageCode='zh-CN';
    await authMod.setPersistence(fbauth,authMod.browserLocalPersistence);
    fbCtx={appMod,authMod,fbauth};
    return fbCtx;
  })();
  try{return await fbReadyPromise}catch(e){fbReadyPromise=null;throw e}
}

async function waitFirebaseUser(fbauth,authMod){
  await fbauth.authStateReady?.();
  if(typeof fbauth.authStateReady==='function')return fbauth.currentUser;
  return await new Promise(resolve=>{
    const off=authMod.onAuthStateChanged(fbauth,u=>{off();resolve(u)},()=>{off();resolve(null)});
  });
}

async function bridgeIdentity(user,password){
  const token=await user.getIdToken(true);
  const r=await fetch(`${SUPABASE_URL}/functions/v1/kehua-auth-bridge`,{
    method:'POST',
    headers:{'Content-Type':'application/json','apikey':SUPABASE_KEY,'Authorization':`Bearer ${token}`},
    body:JSON.stringify({password})
  });
  let out={};
  try{out=await r.json()}catch{}
  if(!r.ok){
    const err=new Error(out.error||'account_bridge_failed');
    err.code=out.error||'account_bridge_failed';
    throw err;
  }
  return out;
}

async function migrateLegacyIfNeeded(email,password){
  const {authMod,fbauth}=await firebaseReady();
  const temp=window.supabase.createClient(SUPABASE_URL,SUPABASE_KEY,{auth:{persistSession:false,autoRefreshToken:false,detectSessionInUrl:false}});
  const {error:legacyError}=await temp.auth.signInWithPassword({email,password});
  if(legacyError)return null;
  try{await temp.auth.signOut()}catch{}
  try{
    const cred=await authMod.createUserWithEmailAndPassword(fbauth,email,password);
    await authMod.sendEmailVerification(cred.user,{url:`${location.origin}/?email_confirmed=1`});
    await authMod.signOut(fbauth);
    return 'verification_sent';
  }catch(e){
    if(String(e?.code||'').includes('email-already-in-use'))return null;
    throw e;
  }
}

async function productionAuth(mode){
  const email=$('#authUsername').value.trim().toLowerCase();
  const password=$('#authPassword').value;
  const box=$('#authError');
  const submit=$('#authSubmit');
  box.textContent='';
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)||password.length<6){box.textContent='请填写有效邮箱，密码至少 6 位';return}
  submit.disabled=true;
  try{
    const {authMod,fbauth}=await firebaseReady();
    if(mode==='register'){
      try{
        const cred=await authMod.createUserWithEmailAndPassword(fbauth,email,password);
        await authMod.sendEmailVerification(cred.user,{url:`${location.origin}/?email_confirmed=1`});
        await authMod.signOut(fbauth);
        try{await sb.auth.signOut()}catch{}
        setAuthMode('login');
        box.textContent='确认邮件已经发送。打开邮件里的链接后，再回来登录。';
      }catch(e){
        if(String(e?.code||'').includes('email-already-in-use')){
          setAuthMode('login');
          box.textContent='这个邮箱已经有账号了。请直接登录。';
          return;
        }
        throw e;
      }
      return;
    }

    let cred;
    try{
      cred=await authMod.signInWithEmailAndPassword(fbauth,email,password);
    }catch(e){
      const migrated=await migrateLegacyIfNeeded(email,password);
      if(migrated==='verification_sent'){
        setAuthMode('login');
        box.textContent='为了保护原账号，确认邮件已经发送。确认后就可以继续使用原账号。';
        return;
      }
      throw e;
    }

    if(!cred.user.emailVerified){
      try{await authMod.sendEmailVerification(cred.user,{url:`${location.origin}/?email_confirmed=1`})}catch{}
      await authMod.signOut(fbauth);
      try{await sb.auth.signOut()}catch{}
      box.textContent='这个邮箱还没完成验证。验证邮件已重新发送。';
      return;
    }

    await bridgeIdentity(cred.user,password);
    const {error}=await sb.auth.signInWithPassword({email,password});
    if(error)throw error;
    await boot();
  }catch(e){
    if(String(e?.message||'')==='identity_not_configured')box.textContent='登录服务正在初始化，请稍后再试。';
    else if(String(e?.code||'')==='email_conflict')box.textContent='这个邮箱已被其他账号占用，请联系客服处理。';
    else box.textContent=authMessage(e);
    try{const {authMod,fbauth}=await firebaseReady();await authMod.signOut(fbauth)}catch{}
  }finally{submit.disabled=false}
}

async function productionLogout(){
  try{const {authMod,fbauth}=await firebaseReady();await authMod.signOut(fbauth)}catch{}
  try{await sb.auth.signOut()}catch{}
  S.me=null;
  showAuth();
  setAuthMode('login');
}

async function installAuthFix(){
  if(authFixInstalled)return;
  const passwordInput=document.querySelector('#authPassword');
  if(!passwordInput)return;
  authFixInstalled=true;

  const forgot=document.createElement('button');
  forgot.type='button';forgot.id='forgotPasswordBtn';forgot.textContent='忘记密码？';
  forgot.style.cssText='display:none;margin:10px 0 2px auto;padding:4px 0;border:0;background:transparent;color:#777;font-size:14px;line-height:1.4;cursor:pointer;';
  passwordInput.closest('label').insertAdjacentElement('afterend',forgot);

  auth=productionAuth;
  logout=productionLogout;
  document.querySelectorAll('[data-auth-mode]').forEach(b=>b.addEventListener('click',()=>setTimeout(()=>setAuthMode(b.dataset.authMode),0)));
  setAuthMode(S.authMode||'register');

  forgot.onclick=async()=>{
    const email=$('#authUsername').value.trim().toLowerCase();
    const box=$('#authError');
    if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)){box.textContent='先填写你注册时使用的邮箱。';return}
    forgot.disabled=true;box.textContent='';
    try{
      const {authMod,fbauth}=await firebaseReady();
      await authMod.sendPasswordResetEmail(fbauth,email,{url:`${location.origin}/?password_recovery=1`});
      box.textContent='如果这个邮箱注册过，我们已经发送了重置密码邮件。';
    }catch(e){
      if(String(e?.message||'')==='identity_not_configured')box.textContent='登录服务正在初始化，请稍后再试。';
      else if(String(e?.code||'').includes('too-many-requests'))box.textContent='发送太频繁了，请稍后再试。';
      else box.textContent='如果这个邮箱注册过，我们已经发送了重置密码邮件。';
    }finally{forgot.disabled=false}
  };

  document.querySelector('#recoveryCodePanel')?.remove();
  document.querySelector('#forgotRecoveryPanel')?.remove();
  document.querySelector('#recoveryCodeBtn')?.remove();
  document.querySelector('#passwordRecoveryPanel')?.remove();

  const q=new URLSearchParams(location.search);
  if(q.get('email_confirmed')==='1'){
    history.replaceState({},'',location.pathname);
    setAuthMode('login');
    $('#authError').textContent='邮箱已经确认，可以登录了。';
  }else if(q.get('password_recovery')==='1'){
    history.replaceState({},'',location.pathname);
    setAuthMode('login');
    $('#authError').textContent='密码已经修改。现在用新密码登录。';
  }

  try{
    const {authMod,fbauth}=await firebaseReady();
    const fuser=await waitFirebaseUser(fbauth,authMod);
    const {data:{session}}=await sb.auth.getSession();
    if(session&&(!fuser||!fuser.emailVerified)){
      await sb.auth.signOut();
      showAuth();setAuthMode('login');
    }else if(fuser?.emailVerified&&!session){
      await authMod.signOut(fbauth);
      showAuth();setAuthMode('login');
    }
  }catch{}
}

installAuthFix();
