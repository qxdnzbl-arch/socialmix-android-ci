async function auth(mode){
  const email=$('#authUsername').value.trim();
  const password=$('#authPassword').value;
  const box=$('#authError');
  box.textContent='';
  if(!email.includes('@')||password.length<6){box.textContent='请填写邮箱，密码至少 6 位';return}
  try{
    if(mode==='register'){
      const {data,error}=await sb.auth.signUp({email,password,options:{data:{nickname:email.split('@')[0].slice(0,20)||'可话用户'}}});
      chk(error);
      if(!data.session){
        box.textContent='账号已经注册成功，但还没确认邮箱。确认邮件已经发到这个邮箱，点邮件里的确认链接后再回来登录。';
        return;
      }
    }else{
      const {error}=await sb.auth.signInWithPassword({email,password});
      if(error){
        const msg=String(error.message||'').toLowerCase();
        if(error.code==='email_not_confirmed'||msg.includes('email not confirmed')||msg.includes('not confirmed')){
          try{await sb.auth.resend({type:'signup',email})}catch{}
          box.textContent='这个账号已经注册，但邮箱还没确认。我刚重新发了一封确认邮件；点里面的确认链接后，再回来登录。';
          return;
        }
        if(msg.includes('invalid login credentials')){box.textContent='邮箱或密码不对。';return}
        throw error;
      }
    }
    await boot();
  }catch(e){
    const msg=String(e?.message||'');
    if(msg.toLowerCase().includes('rate limit')) box.textContent='确认邮件发得太频繁了，直接打开之前收到的那封确认邮件即可。';
    else box.textContent=msg||'暂时进不去';
  }
}
