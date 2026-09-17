const { app, BrowserWindow, shell, session } = require('electron');
const path = require('path');

const SUPABASE_ORIGIN = 'https://nvwdtfnhsyfdopaxdylx.supabase.co';
const CDN_ORIGINS = new Set(['https://cdn.jsdelivr.net']);

function allowedRemote(url) {
  try {
    const u = new URL(url);
    return u.origin === SUPABASE_ORIGIN || CDN_ORIGINS.has(u.origin);
  } catch {
    return false;
  }
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1100,
    height: 820,
    minWidth: 390,
    minHeight: 640,
    show: false,
    backgroundColor: '#f4f4f7',
    title: '可话',
    autoHideMenuBar: true,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
      webSecurity: true,
      allowRunningInsecureContent: false,
      spellcheck: false
    }
  });

  win.once('ready-to-show', () => win.show());
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (allowedRemote(url)) return { action: 'allow' };
    shell.openExternal(url).catch(() => {});
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (event, url) => {
    if (url.startsWith('file://') || allowedRemote(url)) return;
    event.preventDefault();
    shell.openExternal(url).catch(() => {});
  });
  win.webContents.on('did-fail-load', (_event, errorCode, _description, _url, isMainFrame) => {
    if (!isMainFrame || errorCode === -3) return;
    win.loadFile(path.join(__dirname, 'offline.html')).catch(() => {});
  });
  win.webContents.session.setPermissionRequestHandler((_wc, permission, callback) => {
    callback(permission === 'clipboard-sanitized-write');
  });
  win.loadFile(path.join(__dirname, 'client', 'index.html')).catch(() => {
    win.loadFile(path.join(__dirname, 'offline.html')).catch(() => {});
  });
}

app.setAppUserModelId('com.qxdnzbl.kehua.desktop');
app.whenReady().then(() => {
  session.defaultSession.setPermissionCheckHandler((_wc, permission) => permission === 'clipboard-sanitized-write');
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });