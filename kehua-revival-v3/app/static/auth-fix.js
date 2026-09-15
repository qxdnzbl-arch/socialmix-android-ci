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
          submit.textContent='登录';
          throw new Error('这个邮箱已经有账号。我已切到登录，请输入第一次注册时的密码。');
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
          if(r.ok){
            ({error}=await sb.auth.signInWithPassword({email,password}));
          }
        }
        if(error){
          const m=String(error.message||'').toLowerCase();
          if(m.includes('invalid login credentials')) throw new Error('邮箱或密码不对。');
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
