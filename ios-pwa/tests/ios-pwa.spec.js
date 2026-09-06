const { test, expect } = require('@playwright/test');

test('iPhone 7 core music flow works and persists imported track', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByText('心动')).toBeVisible();

  await page.getByRole('button', { name: '音乐库' }).last().click();
  await expect(page.getByText('音乐库').first()).toBeVisible();

  const input = page.locator('#fileInput');
  await input.setInputFiles({
    name: '夜航测试.mp3',
    mimeType: 'audio/mpeg',
    buffer: Buffer.from('ID3\u0004\u0000\u0000\u0000\u0000\u0000\u0000fake-audio')
  });

  await expect(page.getByText('夜航测试')).toBeVisible();
  await expect(page.getByText('本地音乐')).toBeVisible();

  await page.getByText('夜航测试').click();
  await expect(page.locator('#songTitle')).toHaveText('夜航测试');
  await expect(page.locator('#homePage')).toHaveClass(/active/);

  await page.locator('#queueBtn').click();
  await expect(page.getByText('播放列表')).toBeVisible();
  await expect(page.locator('#queueList').getByText('夜航测试')).toBeVisible();
  await page.locator('#queueScrim').click({ position: { x: 5, y: 5 } });

  await page.locator('#searchBtn').click();
  await page.locator('#searchInput').fill('夜航');
  await expect(page.locator('#searchList').getByText('夜航测试')).toBeVisible();

  await page.reload();
  await page.getByRole('button', { name: '音乐库' }).last().click();
  await expect(page.getByText('夜航测试')).toBeVisible();
});

test('manifest and service worker assets are reachable', async ({ request }) => {
  const manifest = await request.get('/manifest.webmanifest');
  expect(manifest.ok()).toBeTruthy();
  const data = await manifest.json();
  expect(data.display).toBe('standalone');
  expect(data.name).toBe('沉浸音乐');

  const sw = await request.get('/sw.js');
  expect(sw.ok()).toBeTruthy();
  expect(await sw.text()).toContain('immersive-music-ios-v1');
});