(()=>{
  const main=document.createElement('script');
  main.src='/app.js?v=firebase-prod-1';
  main.onload=()=>{
    const fix=document.createElement('script');
    fix.src='/auth-fix.js?v=firebase-prod-1';
    document.body.appendChild(fix);
  };
  main.onerror=()=>{
    const box=document.getElementById('authError');
    if(box) box.textContent='页面加载失败，请刷新重试';
  };
  document.head.appendChild(main);
})();
