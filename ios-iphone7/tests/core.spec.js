const {test,expect}=require('@playwright/test');

function wav(seconds=3,sampleRate=8000){
  const samples=Math.floor(seconds*sampleRate),dataSize=samples*2,b=Buffer.alloc(44+dataSize);
  b.write('RIFF',0);b.writeUInt32LE(36+dataSize,4);b.write('WAVE',8);b.write('fmt ',12);b.writeUInt32LE(16,16);b.writeUInt16LE(1,20);b.writeUInt16LE(1,22);b.writeUInt32LE(sampleRate,24);b.writeUInt32LE(sampleRate*2,28);b.writeUInt16LE(2,32);b.writeUInt16LE(16,34);b.write('data',36);b.writeUInt32LE(dataSize,40);
  for(let i=0;i<samples;i++){const v=Math.round(Math.sin(i/sampleRate*2*Math.PI*220)*4500);b.writeInt16LE(v,44+i*2)}
  return b;
}

test('iPhone 7 import, playback, queue, search and reload persistence',async({page})=>{
  const errors=[];page.on('pageerror',e=>errors.push(String(e)));
  await page.goto('/');
  await expect(page.locator('#home')).toHaveClass(/active/);
  await expect(page.getByText('心动')).toBeVisible();

  await page.getByRole('button',{name:'音乐库'}).last().click();
  await expect(page.locator('#library')).toHaveClass(/active/);
  await page.locator('#fileInput').setInputFiles({name:'夜航测试.wav',mimeType:'audio/wav',buffer:wav()});
  await expect(page.locator('#libraryList').getByText('夜航测试')).toBeVisible();
  await expect(page.locator('#libraryList').getByText('本地音乐')).toBeVisible();

  await page.locator('#libraryList .row-main').click();
  await expect(page.locator('#home')).toHaveClass(/active/);
  await expect(page.locator('#nowTitle')).toHaveText('夜航测试');
  await expect(page.locator('#dur')).toHaveText('0:03',{timeout:5000});
  await expect(page.locator('#disc')).toHaveClass(/playing/,{timeout:3000});

  await page.locator('#queueBtn').click();
  await expect(page.locator('#queueSheet')).toHaveClass(/show/);
  await expect(page.locator('#queueList').getByText('夜航测试')).toBeVisible();
  await page.locator('#scrim').click({position:{x:4,y:4}});

  await page.locator('#openSearch').click();
  await page.locator('#searchInput').fill('夜航');
  await expect(page.locator('#searchList').getByText('夜航测试')).toBeVisible();

  await page.reload();
  await page.getByRole('button',{name:'音乐库'}).last().click();
  await expect(page.locator('#libraryList').getByText('夜航测试')).toBeVisible();
  expect(errors).toEqual([]);
});

test('delete removes only the app copy and returns empty state',async({page})=>{
  await page.goto('/');
  await page.getByRole('button',{name:'音乐库'}).last().click();
  await page.locator('#fileInput').setInputFiles({name:'删除测试.wav',mimeType:'audio/wav',buffer:wav(1)});
  await expect(page.locator('#libraryList').getByText('删除测试')).toBeVisible();
  await page.locator('#libraryList .more').click();
  await expect(page.locator('#libraryList .del')).toBeVisible();
  await page.locator('#libraryList .del').click();
  await expect(page.locator('#libraryList').getByText('删除测试')).toHaveCount(0);
  await expect(page.locator('#libraryList')).toContainText('还没有音乐');
});

test('PWA install assets and iPhone 7 viewport are valid',async({page,request})=>{
  await page.goto('/');
  expect(await page.evaluate(()=>({w:innerWidth,sw:'serviceWorker'in navigator,idb:'indexedDB'in window}))).toMatchObject({w:375,sw:true,idb:true});
  const manifest=await request.get('/manifest.webmanifest');expect(manifest.ok()).toBeTruthy();const m=await manifest.json();expect(m.name).toBe('沉浸音乐');expect(m.display).toBe('standalone');
  const sw=await request.get('/sw.js');expect(sw.ok()).toBeTruthy();expect(await sw.text()).toContain('immersive-music-iphone7-v1');
  const app=await request.get('/app.js');expect(app.ok()).toBeTruthy();
});