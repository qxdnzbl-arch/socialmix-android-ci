package com.qxdnzbl.shuangjichuan;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.*;
import android.webkit.*;
import android.widget.Toast;
import org.json.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int PICK = 7070;
    private static final String UI_FILE = "ui-current.html";
    private static final String PREF_UI_VERSION = "ui_version";
    private static final int BUNDLED_UI_VERSION = 2;

    private final ExecutorService io = Executors.newCachedThreadPool();
    private SharedPreferences prefs;
    private TransferDb db;
    private WebView web;
    private volatile boolean connected = false;
    private String debugManifestOverride;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (TransferService.ACTION_STATE.equals(intent.getAction())) {
                connected = intent.getBooleanExtra("connected", false);
                notifyWeb();
            } else if (TransferService.ACTION_CHANGED.equals(intent.getAction())) {
                notifyWeb();
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        prefs = getSharedPreferences("dual", MODE_PRIVATE);
        db = new TransferDb(this);

        String device = prefs.getString("device", "");
        if (device.isEmpty()) {
            device = UUID.randomUUID().toString();
            prefs.edit().putString("device", device).apply();
        }

        if (isDebuggable() && getIntent().getBooleanExtra("ciAuto", false)) {
            prefs.edit()
                .putString("token", sha256("testlocal\npass1234"))
                .putString("account", "testlocal")
                .apply();
        }
        if (isDebuggable()) debugManifestOverride = getIntent().getStringExtra("ciManifest");

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.setBackgroundColor(Color.WHITE);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                Log.i("DualPhoneUi", "LOCAL_UI_READY " + url);
            }
        });

        setContentView(web);
        ensureLocalUi();
        loadLocalUi();

        if (!prefs.getString("token", "").isEmpty()) TransferService.start(this);
        checkForUiUpdate();
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter();
        f.addAction(TransferService.ACTION_STATE);
        f.addAction(TransferService.ACTION_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
    }

    @Override protected void onStop() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        super.onStop();
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private void ensureLocalUi() {
        File dst = new File(getFilesDir(), UI_FILE);
        if (dst.isFile()) return;
        try (InputStream in = getAssets().open("ui.html");
             OutputStream out = new FileOutputStream(dst)) {
            copy(in, out);
            prefs.edit().putInt(PREF_UI_VERSION, BUNDLED_UI_VERSION).apply();
        } catch (Exception e) {
            throw new RuntimeException("UI init failed", e);
        }
    }

    private void loadLocalUi() {
        web.loadUrl(Uri.fromFile(new File(getFilesDir(), UI_FILE)).toString());
    }

    private void notifyWeb() {
        if (web == null) return;
        runOnUiThread(() -> web.evaluateJavascript(
            "window.nativeRefresh&&window.nativeRefresh()", null));
    }

    private void checkForUiUpdate() {
        io.execute(() -> {
            try {
                String[] manifests;
                if (isDebuggable() && debugManifestOverride != null && !debugManifestOverride.isEmpty()) {
                    manifests = new String[]{debugManifestOverride};
                } else {
                    manifests = new String[]{
                        "https://cdn.jsdelivr.net/gh/qxdnzbl-arch/socialmix-android-ci@dual-phone-transfer-build/dualphone/ota/manifest.json",
                        "https://fastly.jsdelivr.net/gh/qxdnzbl-arch/socialmix-android-ci@dual-phone-transfer-build/dualphone/ota/manifest.json",
                        "https://raw.githubusercontent.com/qxdnzbl-arch/socialmix-android-ci/dual-phone-transfer-build/dualphone/ota/manifest.json"
                    };
                }

                JSONObject mf = null;
                for (String url : manifests) {
                    try { mf = new JSONObject(fetchText(url)); break; }
                    catch (Exception ignored) {}
                }
                if (mf == null) return;

                int remoteVersion = mf.optInt("version", 0);
                int currentVersion = prefs.getInt(PREF_UI_VERSION, BUNDLED_UI_VERSION);
                if (remoteVersion <= currentVersion) return;

                JSONArray urls = mf.optJSONArray("urls");
                if (urls == null || urls.length() == 0) return;

                byte[] html = null;
                for (int i = 0; i < urls.length(); i++) {
                    try {
                        html = fetchBytes(urls.getString(i));
                        String probe = new String(html, StandardCharsets.UTF_8);
                        if (!probe.contains("<html") || !probe.contains("Android.")) {
                            html = null;
                            continue;
                        }
                        break;
                    } catch (Exception ignored) {}
                }
                if (html == null) return;

                String expected = mf.optString("sha256", "");
                if (!expected.isEmpty() && !expected.equalsIgnoreCase(hex(sha256Bytes(html)))) return;

                File dst = new File(getFilesDir(), UI_FILE);
                File tmp = new File(getFilesDir(), UI_FILE + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(html);
                    out.getFD().sync();
                }
                if (dst.exists() && !dst.delete()) return;
                if (!tmp.renameTo(dst)) return;

                prefs.edit().putInt(PREF_UI_VERSION, remoteVersion).apply();
                Log.i("DualPhoneOta", "OTA_APPLIED " + remoteVersion);
                runOnUiThread(this::loadLocalUi);
            } catch (Exception e) {
                Log.i("DualPhoneOta", "OTA_SKIPPED");
            }
        });
    }

    private static byte[] fetchBytes(String url) throws Exception {
        URLConnection c = new URL(url).openConnection();
        c.setConnectTimeout(4500);
        c.setReadTimeout(7000);
        c.setUseCaches(false);
        try (InputStream in = c.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            copy(in, out);
            return out.toByteArray();
        }
    }

    private static String fetchText(String url) throws Exception {
        return new String(fetchBytes(url), StandardCharsets.UTF_8);
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private static byte[] sha256Bytes(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static String hex(byte[] data) {
        StringBuilder b = new StringBuilder();
        for (byte x : data) b.append(String.format(Locale.US, "%02x", x & 255));
        return b.toString();
    }

    private boolean isDebuggable() {
        return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private static String sha256(String s) {
        try { return hex(sha256Bytes(s.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private class Bridge {
        @JavascriptInterface public String getState() {
            JSONObject root = new JSONObject();
            try {
                root.put("setupNeeded", prefs.getString("token", "").isEmpty());
                root.put("connected", connected);
                root.put("account", prefs.getString("account", ""));
                root.put("uiVersion", prefs.getInt(PREF_UI_VERSION, BUNDLED_UI_VERSION));
                JSONArray arr = new JSONArray();
                for (TransferDb.Msg m : db.all()) {
                    JSONObject x = new JSONObject();
                    x.put("id", m.id);
                    x.put("mine", m.mine);
                    x.put("kind", m.kind);
                    x.put("text", m.text == null ? "" : m.text);
                    x.put("fileName", m.fileName == null ? "" : m.fileName);
                    x.put("fileSize", m.fileSize);
                    x.put("status", m.status == null ? "" : m.status);
                    x.put("createdAt", m.createdAt);
                    arr.put(x);
                }
                root.put("messages", arr);
            } catch (Exception ignored) {}
            return root.toString();
        }

        @JavascriptInterface public void setCredentials(String account, String password) {
            String a = account == null ? "" : account.trim();
            String p = password == null ? "" : password;
            if (a.isEmpty() || p.length() < 4) return;
            prefs.edit()
                .putString("token", sha256(a.toLowerCase(Locale.ROOT) + "\n" + p))
                .putString("account", a)
                .apply();
            TransferService.start(MainActivity.this);
            notifyWeb();
        }

        @JavascriptInterface public void sendText(String text) {
            String s = text == null ? "" : text.trim();
            if (s.isEmpty()) return;
            db.addText(UUID.randomUUID().toString(), true, s,
                System.currentTimeMillis(), "pending");
            TransferService.wake(MainActivity.this);
            notifyWeb();
        }

        @JavascriptInterface public void pickFiles() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(i, PICK);
            });
        }

        @JavascriptInterface public void saveFile(String id) {
            io.execute(() -> {
                TransferDb.Msg target = null;
                for (TransferDb.Msg m : db.all()) if (m.id.equals(id)) { target = m; break; }
                if (target != null && !target.mine && target.filePath != null) saveToDownloads(target);
            });
        }

        @JavascriptInterface public void reportUi(String marker) {
            Log.i("DualPhoneUi", marker == null ? "" : marker);
        }

        @JavascriptInterface public void reportLayout(double visible, double bottom) {
            Log.i("DualPhoneLayout", "visible=" + Math.round(visible) + " bottom=" + Math.round(bottom));
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK || resultCode != RESULT_OK || data == null) return;

        ArrayList<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++)
                uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) uris.add(data.getData());

        io.execute(() -> {
            for (Uri uri : uris) {
                try { queueFile(uri); }
                catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "文件读取失败", Toast.LENGTH_SHORT).show());
                }
            }
            TransferService.wake(MainActivity.this);
            notifyWeb();
        });
    }

    private void queueFile(Uri uri) throws Exception {
        String name = "文件";
        try (Cursor c = getContentResolver().query(uri,
            new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (ni >= 0 && c.getString(ni) != null) name = c.getString(ni);
            }
        }
        String id = UUID.randomUUID().toString();
        File dir = new File(getFilesDir(), "outgoing");
        dir.mkdirs();
        File out = new File(dir, id + "_" + name.replace("/", "_").replace("\\", "_"));

        long size = 0;
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream fout = new FileOutputStream(out)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                fout.write(buf, 0, n);
                size += n;
                if (size > 500L * 1024 * 1024) throw new IOException("too large");
            }
        }
        db.addFile(id, true, name, out.getAbsolutePath(), size,
            System.currentTimeMillis(), "pending");
    }

    private void saveToDownloads(TransferDb.Msg m) {
        try {
            File src = new File(m.filePath);
            if (!src.isFile()) throw new IOException();
            String name = m.fileName == null || m.fileName.isEmpty() ? "文件" : m.fileName;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new IOException();
                try (InputStream in = new FileInputStream(src);
                     OutputStream out = getContentResolver().openOutputStream(uri)) {
                    copy(in, out);
                }
                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, done, null, null);
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                dir.mkdirs();
                try (InputStream in = new FileInputStream(src);
                     OutputStream out = new FileOutputStream(new File(dir, name))) {
                    copy(in, out);
                }
            }
            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                "已保存到下载", Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                "保存失败", Toast.LENGTH_SHORT).show());
        }
    }
}
