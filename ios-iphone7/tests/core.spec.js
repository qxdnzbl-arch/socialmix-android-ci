const {test,expect}=require('@playwright/test');

test('iPhone 7 import library player queue search and persistence',async({page})=>{
  const errors=[];page.on('pageerror',e=>{errors.push(String(e));console.log('PAGEERROR',String(e))});page.on('console',m=>console.log('BROWSER',m.type(),m.text()));
  await page.goto('/');
  await expect(page.locator('#home')).toHaveClass(/active/);
  await expect(page.getByText('心动')).toBeVisible();
  await page.getByRole('button',{name:'音乐库'}).last().click();
  await expect(page.locator('#library')).toHaveClass(/active/);
  const input=page.locator('#fileInput');
  await input.setInputFiles({name:'夜航测试.mp3',mimeType:'audio/mpeg',buffer:Buffer.from('fake-audio-data')});
  await page.waitForTimeout(900);
  console.log('FILES',await input.evaluate(el=>el.files.length));
  console.log('LIBRARY',await page.locator('#libraryList').innerHTML());
  console.log('ERRORS',JSON.stringify(errors));
  await expect(page.locator('#libraryList').getByText('夜航测试')).toBeVisible();
  await expect(page.locator('#libraryList').getByText('本地音乐')).toBeVisible();
  await page.locator('#libraryList .row-main').click();
  await expect(page.locator('#home')).toHaveClass(/active/);
  await expect(page.locator('#nowTitle')).toHaveText('夜航测试');
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

test('delete removes app copy and empty state returns',async({page})=>{
  page.on('pageerror',e=>console.log('DELETE PAGEERROR',String(e)));page.on('console',m=>console.log('DELETE BROWSER',m.type(),m.text()));
  await page.goto('/');await page.getByRole('button',{name:'音乐库'}).last().click();
  await page.locator('#fileInput').setInputFiles({name:'删除测试.m4a',mimeType:'audio/mp4',buffer:Buffer.from('fake')});
  await page.waitForTimeout(900);console.log('DELETE LIBRARY',await page.locator('#libraryList').innerHTML());
  await expect(page.locator('#libraryList').getByText('删除测试')).toBeVisible();
  await page.locator('#libraryList .more').click();
  await expect(page.locator('#libraryList .del')).toBeVisible();
  await page.locator('#libraryList .del').click();
  await expect(page.locator('#libraryList').getByText('删除测试')).toHaveCount(0);
  await expect(page.locator('#libraryList')).toContainText('还没有音乐');
});

test('PWA install assets and mobile viewport are valid',async({page,request})=>{
  await page.goto('/');
  expect(await page.evaluate(()=>({w:innerWidth,sw:'serviceWorker'in navigator,idb:'indexedDB'in window}))).toMatchObject({w:375,sw:true,idb:true});
  const manifest=await request.get('/manifest.webmanifest');expect(manifest.ok()).toBeTruthy();const m=await manifest.json();expect(m.name).toBe('沉浸音乐');expect(m.display).toBe('standalone');
  const sw=await request.get('/sw.js');expect(sw.ok()).toBeTruthy();expect(await sw.text()).toContain('immersive-music-iphone7-v1');
  const app=await request.get('/app.js');expect(app.ok()).toBeTruthy();
});