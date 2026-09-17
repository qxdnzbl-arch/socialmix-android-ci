const { app, BrowserWindow, shell, session } = require('electron');
const path = require('path');

const APP_URL = 'https://kehua-revival-live.onrender.com/';
const APP_ORIGIN = new URL(APP_URL).origin;
const SUPABASE_ORIGIN = 'https://nvwdtfnhsyfdopaxdylx.supabase.co';

function allowedNavigation(url) {
  try {
    const origin = new URL(url).origin;
    return origin === APP_ORIGIN || origin === SUPABASE_ORIGIN;
  } catch {
    return false;
  }
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1180,
    height: 820,
    minWidth: 390,
    minHeight: 640,
    show: false,
    backgroundColor: '#f4f3f8',
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
    if (allowedNavigation(url)) return { action: 'allow' };
    shell.openExternal(url).catch(() => {});
    return { action: 'deny' };
  });

  win.webContents.on('will-navigate', (event, url) => {
    if (!allowedNavigation(url)) {
      event.preventDefault();
      shell.openExternal(url).catch(() => {});
    }
  });

  win.webContents.on('did-fail-load', (_event, errorCode, _errorDescription, validatedURL, isMainFrame) => {
    if (!isMainFrame || errorCode === -3) return;
    win.loadFile(path.join(__dirname, 'offline.html'), { query: { retry: validatedURL || APP_URL } }).catch(() => {});
  });

  win.webContents.session.setPermissionRequestHandler((_webContents, permission, callback) => {
    callback(permission === 'clipboard-sanitized-write');
  });

  win.loadURL(APP_URL).catch(() => {
    win.loadFile(path.join(__dirname, 'offline.html')).catch(() => {});
  });
}

app.setAppUserModelId('com.qxdnzbl.kehua.desktop');
app.whenReady().then(() => {
  session.defaultSession.setPermissionCheckHandler((_webContents, permission) => permission === 'clipboard-sanitized-write');
  createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
