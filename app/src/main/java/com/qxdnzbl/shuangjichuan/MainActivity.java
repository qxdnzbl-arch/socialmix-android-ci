package com.qxdnzbl.shuangjichuan;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import org.json.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    public static final int ID_EMAIL = 1101;
    public static final int ID_PASSWORD = 1102;
    public static final int ID_REGISTER = 1103;
    public static final int ID_LOGIN = 1104;
    public static final int ID_INPUT = 1201;
    public static final int ID_ATTACH = 1202;
    public static final int ID_SEND = 1203;

    private static final String BASE = "https://shuangji-chuan.floot.app";
    private static final int PICK_FILES = 7001;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private String cookie = "";
    private String deviceId = "";
    private LinearLayout messagesBox;
    private ScrollView scroll;
    private EditText messageInput;
    private TextView statusText;
    private Button attachButton;
    private Button sendButton;
    private boolean loading;
    private int lastMessageCount = -1;

    private final Runnable poller = new Runnable() {
        @Override public void run() {
            if (!cookie.isEmpty()) loadMessages(false);
            handler.postDelayed(this, 1500);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        prefs = getSharedPreferences("dual_phone_transfer", MODE_PRIVATE);
        cookie = prefs.getString("cookie", "");
        deviceId = prefs.getString("device_id", "");
        if (deviceId.isEmpty()) {
            deviceId = UUID.randomUUID().toString();
            prefs.edit().putString("device_id", deviceId).apply();
        }
        if (cookie.isEmpty()) showLogin();
        else validateSession();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(poller);
        io.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private TextView label(String value, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private void showLogin() {
        handler.removeCallbacks(poller);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(50), dp(22), dp(24));
        root.setBackgroundColor(Color.WHITE);

        TextView title = label("双机传", 32, Color.rgb(23, 32, 42));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView lead = label("两台手机只登录一次。以后打开就是同一个聊天框，直接发消息和文件。", 14, Color.rgb(108, 119, 130));
        lead.setPadding(0, dp(10), 0, dp(24));
        root.addView(lead);

        EditText email = new EditText(this);
        email.setId(ID_EMAIL);
        email.setHint("邮箱");
        email.setSingleLine(true);
        email.setTextSize(15);
        email.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        email.setPadding(dp(14), 0, dp(14), 0);
        email.setBackground(rounded(Color.rgb(244, 246, 248), 12));
        root.addView(email, new LinearLayout.LayoutParams(-1, dp(52)));

        EditText password = new EditText(this);
        password.setId(ID_PASSWORD);
        password.setHint("密码");
        password.setSingleLine(true);
        password.setTextSize(15);
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setPadding(dp(14), 0, dp(14), 0);
        password.setBackground(rounded(Color.rgb(244, 246, 248), 12));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(52));
        pp.topMargin = dp(12);
        root.addView(password, pp);

        TextView error = label("", 13, Color.rgb(198, 67, 67));
        error.setPadding(0, dp(8), 0, 0);
        root.addView(error);

        Button login = new Button(this);
        login.setId(ID_LOGIN);
        login.setText("登录并进入");
        login.setAllCaps(false);
        login.setTextColor(Color.WHITE);
        login.setTextSize(15);
        login.setBackground(rounded(Color.rgb(36, 107, 219), 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.topMargin = dp(16);
        root.addView(login, lp);

        Button register = new Button(this);
        register.setId(ID_REGISTER);
        register.setText("第一次使用：创建账号");
        register.setAllCaps(false);
        register.setTextColor(Color.rgb(36, 107, 219));
        register.setTextSize(14);
        register.setBackground(rounded(Color.rgb(233, 239, 248), 12));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(48));
        rp.topMargin = dp(10);
        root.addView(register, rp);

        TextView note = label("两台手机使用同一个邮箱和密码。登录状态会长期保留。", 12, Color.rgb(108, 119, 130));
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note);

        login.setOnClickListener(v -> doAuth(false, email, password, error, login, register));
        register.setOnClickListener(v -> doAuth(true, email, password, error, login, register));
        setContentView(root);
    }

    private void doAuth(boolean register, EditText emailField, EditText passwordField, TextView error, Button loginBtn, Button registerBtn) {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString();
        if (email.isEmpty() || !email.contains("@")) {
            error.setText("请输入正确的邮箱");
            return;
        }
        if (password.length() < (register ? 8 : 1)) {
            error.setText(register ? "密码至少 8 位" : "请输入密码");
            return;
        }
        loginBtn.setEnabled(false);
        registerBtn.setEnabled(false);
        error.setText("");
        io.execute(() -> {
            try {
                JSONObject p = new JSONObject();
                p.put("email", email);
                p.put("password", password);
                if (register) p.put("displayName", "我");
                HttpResult result = request("POST", register ? "/_api/auth/register_with_password" : "/_api/auth/login_with_password", wrap(p), "");
                if (result.code < 200 || result.code >= 300) throw new IOException(extractError(result.body));
                if (result.setCookie == null || result.setCookie.isEmpty()) throw new IOException("登录状态保存失败");
                cookie = result.setCookie.split(";")[0];
                prefs.edit().putString("cookie", cookie).apply();
                runOnUiThread(this::showChat);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    error.setText(e.getMessage() == null ? "登录失败，请重试" : e.getMessage());
                    loginBtn.setEnabled(true);
                    registerBtn.setEnabled(true);
                });
            }
        });
    }

    private void validateSession() {
        io.execute(() -> {
            try {
                HttpResult result = request("GET", "/_api/auth/session", null, cookie);
                if (result.code >= 200 && result.code < 300) {
                    runOnUiThread(this::showChat);
                } else {
                    cookie = "";
                    prefs.edit().remove("cookie").apply();
                    runOnUiThread(this::showLogin);
                }
            } catch (Exception e) {
                runOnUiThread(this::showChat);
            }
        });
    }

    private void showChat() {
        handler.removeCallbacks(poller);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(244, 246, 248));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(14), dp(18), dp(12));
        header.setBackgroundColor(Color.WHITE);

        TextView title = label("我的两台手机", 18, Color.rgb(23, 32, 42));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title);

        statusText = label("● 自动同步", 12, Color.rgb(35, 133, 91));
        statusText.setPadding(0, dp(4), 0, 0);
        header.addView(statusText);
        root.addView(header);

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(223, 229, 234));
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        messagesBox = new LinearLayout(this);
        messagesBox.setOrientation(LinearLayout.VERTICAL);
        messagesBox.setPadding(dp(14), dp(14), dp(14), dp(18));
        scroll.addView(messagesBox, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        View divider2 = new View(this);
        divider2.setBackgroundColor(Color.rgb(223, 229, 234));
        root.addView(divider2, new LinearLayout.LayoutParams(-1, dp(1)));

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(8), dp(8), dp(8), dp(8));
        composer.setBackgroundColor(Color.WHITE);

        attachButton = new Button(this);
        attachButton.setId(ID_ATTACH);
        attachButton.setText("＋");
        attachButton.setContentDescription("发送文件");
        attachButton.setTextSize(24);
        attachButton.setAllCaps(false);
        attachButton.setTextColor(Color.rgb(23, 32, 42));
        attachButton.setBackground(rounded(Color.TRANSPARENT, 12));
        composer.addView(attachButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        messageInput = new EditText(this);
        messageInput.setId(ID_INPUT);
        messageInput.setHint("输入消息");
        messageInput.setTextSize(15);
        messageInput.setMinLines(1);
        messageInput.setMaxLines(4);
        messageInput.setPadding(dp(12), dp(9), dp(12), dp(9));
        messageInput.setBackground(rounded(Color.rgb(238, 241, 244), 14));
        messageInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, -2, 1f);
        ip.leftMargin = dp(4);
        ip.rightMargin = dp(7);
        composer.addView(messageInput, ip);

        sendButton = new Button(this);
        sendButton.setId(ID_SEND);
        sendButton.setText("发送");
        sendButton.setContentDescription("发送");
        sendButton.setTextSize(14);
        sendButton.setTextColor(Color.WHITE);
        sendButton.setAllCaps(false);
        sendButton.setBackground(rounded(Color.rgb(36, 107, 219), 12));
        composer.addView(sendButton, new LinearLayout.LayoutParams(dp(64), dp(48)));
        root.addView(composer);

        attachButton.setOnClickListener(v -> pickFiles());
        sendButton.setOnClickListener(v -> sendText());
        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendText();
                return true;
            }
            return false;
        });

        setContentView(root);
        lastMessageCount = -1;
        loadMessages(true);
        handler.postDelayed(poller, 1500);
    }

    private void pickFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_FILES);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FILES || resultCode != RESULT_OK || data == null) return;
        ArrayList<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) return;
        attachButton.setEnabled(false);
        io.execute(() -> {
            try {
                for (Uri uri : uris) uploadUri(uri);
                runOnUiThread(() -> Toast.makeText(this, "文件已发送", Toast.LENGTH_SHORT).show());
                loadMessages(true);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, e.getMessage() == null ? "文件发送失败" : e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                runOnUiThread(() -> attachButton.setEnabled(true));
            }
        });
    }

    private void uploadUri(Uri uri) throws Exception {
        String name = "file";
        String type = getContentResolver().getType(uri);
        if (type == null) type = "application/octet-stream";
        Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
            cursor.close();
        }
        File temp = new File(getCacheDir(), "upload-" + UUID.randomUUID());
        try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(temp)) {
            if (in == null) throw new IOException("无法读取文件");
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
        try {
            sendFile(temp, name, type);
        } finally {
            temp.delete();
        }
    }

    private void sendText() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;
        messageInput.setText("");
        sendButton.setEnabled(false);
        io.execute(() -> {
            try {
                JSONObject p = new JSONObject();
                p.put("action", "sendText");
                p.put("text", text);
                p.put("deviceId", deviceId);
                HttpResult r = request("POST", "/_api/messages_action", wrap(p), cookie);
                if (r.code < 200 || r.code >= 300) throw new IOException(extractError(r.body));
                loadMessages(true);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    messageInput.setText(text);
                    Toast.makeText(this, e.getMessage() == null ? "发送失败" : e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                runOnUiThread(() -> sendButton.setEnabled(true));
            }
        });
    }

    private void sendFile(File file, String name, String type) throws Exception {
        long size = file.length();
        if (size <= 0 || size > 100L * 1024L * 1024L) throw new IOException("单个文件需小于 100MB");

        JSONObject presign = new JSONObject();
        presign.put("action", "presignFile");
        presign.put("fileName", name);
        presign.put("fileType", type);
        presign.put("fileSize", size);
        HttpResult pr = request("POST", "/_api/messages_action", wrap(presign), cookie);
        if (pr.code < 200 || pr.code >= 300) throw new IOException(extractError(pr.body));
        JSONObject pd = unwrap(pr.body);
        String uploadUrl = pd.getString("uploadUrl");
        String fileKey = pd.getString("fileKey");
        JSONObject headers = pd.getJSONObject("headers");

        HttpURLConnection upload = (HttpURLConnection) new URL(uploadUrl).openConnection();
        upload.setRequestMethod("PUT");
        upload.setDoOutput(true);
        upload.setConnectTimeout(20000);
        upload.setReadTimeout(120000);
        upload.setFixedLengthStreamingMode(size);
        Iterator<String> keys = headers.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            upload.setRequestProperty(key, headers.getString(key));
        }
        try (InputStream in = new FileInputStream(file); OutputStream out = upload.getOutputStream()) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
        int uploadCode = upload.getResponseCode();
        upload.disconnect();
        if (uploadCode < 200 || uploadCode >= 300) throw new IOException("文件上传失败");

        JSONObject complete = new JSONObject();
        complete.put("action", "sendFile");
        complete.put("deviceId", deviceId);
        complete.put("fileKey", fileKey);
        complete.put("fileName", name);
        complete.put("fileType", type);
        complete.put("fileSize", size);
        HttpResult cr = request("POST", "/_api/messages_action", wrap(complete), cookie);
        if (cr.code < 200 || cr.code >= 300) throw new IOException(extractError(cr.body));
    }

    private void loadMessages(boolean scrollBottom) {
        if (loading) return;
        loading = true;
        io.execute(() -> {
            try {
                HttpResult r = request("GET", "/_api/messages", null, cookie);
                if (r.code == 401) {
                    cookie = "";
                    prefs.edit().remove("cookie").apply();
                    runOnUiThread(this::showLogin);
                    return;
                }
                if (r.code < 200 || r.code >= 300) throw new IOException("同步失败");
                JSONArray messages = unwrap(r.body).getJSONArray("messages");
                if (messages.length() == lastMessageCount && !scrollBottom) return;
                lastMessageCount = messages.length();
                runOnUiThread(() -> {
                    renderMessages(messages);
                    statusText.setText("● 自动同步");
                    statusText.setTextColor(Color.rgb(35, 133, 91));
                    if (scrollBottom) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (statusText != null) {
                        statusText.setText("● 正在重连");
                        statusText.setTextColor(Color.rgb(165, 107, 24));
                    }
                });
            } finally {
                loading = false;
            }
        });
    }

    private void renderMessages(JSONArray messages) {
        messagesBox.removeAllViews();
        if (messages.length() == 0) {
            TextView empty = label("直接发就行\n从任意一台手机发文字、图片或文件，另一台会自动收到。", 14, Color.rgb(108, 119, 130));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(28), dp(80), dp(28), dp(24));
            messagesBox.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (int i = 0; i < messages.length(); i++) {
            try { addMessage(messages.getJSONObject(i)); } catch (Exception ignored) {}
        }
    }

    private void addMessage(JSONObject m) throws Exception {
        boolean mine = deviceId.equals(m.optString("senderDeviceId"));
        String kind = m.optString("kind");
        LinearLayout row = new LinearLayout(this);
        row.setGravity(mine ? Gravity.RIGHT : Gravity.LEFT);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(8);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(12), dp(9), dp(12), dp(7));
        bubble.setBackground(rounded(kind.equals("text") && mine ? Color.rgb(36, 107, 219) : Color.WHITE, 16));

        if ("text".equals(kind)) {
            TextView body = label(m.optString("textContent"), 15, mine ? Color.WHITE : Color.rgb(23, 32, 42));
            body.setMaxWidth(dp(300));
            bubble.addView(body);
        } else {
            String id = m.getString("id");
            String fileName = m.optString("fileName", "文件");
            TextView file = label("📎  " + fileName, 14, Color.rgb(23, 32, 42));
            file.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            file.setMaxWidth(dp(280));
            bubble.addView(file);
            TextView meta = label(formatSize(m.optLong("fileSize", 0)) + "   点击下载", 12, Color.rgb(108, 119, 130));
            meta.setPadding(0, dp(5), 0, 0);
            bubble.addView(meta);
            bubble.setOnClickListener(v -> downloadFile(id, fileName));
        }
        TextView time = label(formatTime(m.optString("createdAt")), 10, kind.equals("text") && mine ? Color.rgb(220, 232, 250) : Color.rgb(130, 140, 150));
        time.setGravity(Gravity.RIGHT);
        time.setPadding(0, dp(4), 0, 0);
        bubble.addView(time);
        row.addView(bubble);
        messagesBox.addView(row, rowParams);
    }

    private String formatTime(String iso) {
        if (iso == null || iso.length() < 16) return "";
        return iso.substring(11, 16);
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private void downloadFile(String messageId, String fileName) {
        try {
            Uri uri = Uri.parse(BASE + "/_api/file_download?messageId=" + URLEncoder.encode(messageId, "UTF-8"));
            DownloadManager.Request req = new DownloadManager.Request(uri);
            req.setTitle(fileName);
            req.setDescription("双机传");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.addRequestHeader("Cookie", cookie);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName(fileName));
            ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
            Toast.makeText(this, "开始下载到 Downloads", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "下载失败", Toast.LENGTH_LONG).show();
        }
    }

    private String safeName(String name) {
        String clean = name.replace("/", "_").replace("\\", "_").trim();
        return clean.isEmpty() ? "download" : clean;
    }

    private String wrap(JSONObject payload) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("json", payload);
        return root.toString();
    }

    private JSONObject unwrap(String body) throws JSONException {
        return new JSONObject(body).getJSONObject("json");
    }

    private String extractError(String body) {
        try {
            JSONObject j = unwrap(body);
            if (j.has("error")) return j.optString("error", "操作失败");
            if (j.has("message")) return j.optString("message", "操作失败");
        } catch (Exception ignored) {}
        return "操作失败，请重试";
    }

    private HttpResult request(String method, String path, String body, String sessionCookie) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + path).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Accept", "application/json");
        if (sessionCookie != null && !sessionCookie.isEmpty()) conn.setRequestProperty("Cookie", sessionCookie);
        if (body != null) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = conn.getOutputStream()) { out.write(bytes); }
        }
        int code = conn.getResponseCode();
        InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String response = "";
        if (in != null) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] bytes = new byte[8192];
            int n;
            while ((n = in.read(bytes)) != -1) buffer.write(bytes, 0, n);
            in.close();
            response = buffer.toString(StandardCharsets.UTF_8.name());
        }
        String setCookie = conn.getHeaderField("Set-Cookie");
        conn.disconnect();
        return new HttpResult(code, response, setCookie);
    }

    public void sendQaFileForTest(String name, byte[] bytes) {
        io.execute(() -> {
            try {
                File f = new File(getCacheDir(), "qa-" + UUID.randomUUID());
                try (FileOutputStream out = new FileOutputStream(f)) { out.write(bytes); }
                try { sendFile(f, name, "text/plain"); } finally { f.delete(); }
                loadMessages(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public boolean isComposerAboveVisibleFrameForTest() {
        if (sendButton == null) return false;
        Rect visible = new Rect();
        getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
        int[] location = new int[2];
        sendButton.getLocationOnScreen(location);
        return location[1] + sendButton.getHeight() <= visible.bottom + dp(2);
    }

    public void showKeyboardForTest() {
        if (messageInput == null) return;
        messageInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private static class HttpResult {
        final int code;
        final String body;
        final String setCookie;
        HttpResult(int code, String body, String setCookie) {
            this.code = code;
            this.body = body;
            this.setCookie = setCookie;
        }
    }
}
