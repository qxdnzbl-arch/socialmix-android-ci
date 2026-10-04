package com.qxdnzbl.shuangjichuan;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

public class MainActivity extends Activity {
    private static final int PICK = 7070;

    private SharedPreferences prefs;
    private TransferDb db;
    private LinearLayout messages;
    private ScrollView scroll;
    private TextView status;
    private EditText input;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (TransferService.ACTION_STATE.equals(intent.getAction())) {
                boolean connected = intent.getBooleanExtra("connected", false);
                showState(connected);
            } else if (TransferService.ACTION_CHANGED.equals(intent.getAction())) {
                renderMessages();
            }
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
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

        if (prefs.getString("token", "").isEmpty()) showSetup();
        else showChat();
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

    int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    GradientDrawable box(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    TextView tv(String text, int sp, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        return v;
    }

    private void showSetup() {
        ScrollView outer = new ScrollView(this);
        outer.setBackgroundColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(62), dp(24), dp(32));
        outer.addView(root);

        TextView icon = tv("▯  ▯", 30, Color.rgb(38, 112, 216));
        root.addView(icon);

        TextView title = tv("双机传", 36, Color.rgb(28, 36, 44));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, dp(16), 0, 0);
        root.addView(title);

        TextView sub = tv("不用联网，不用 VPN。两台手机只要连同一个 Wi‑Fi，或者一台开热点另一台连上，就能直接传消息和文件。", 16, Color.rgb(99, 110, 121));
        sub.setLineSpacing(0, 1.35f);
        sub.setPadding(0, dp(12), 0, dp(28));
        root.addView(sub);

        EditText account = new EditText(this);
        account.setId(R.id.account);
        account.setHint("两台手机填同一个账号");
        account.setSingleLine(true);
        account.setTextSize(16);
        account.setPadding(dp(14), 0, dp(14), 0);
        account.setBackground(box(Color.rgb(244, 246, 248), 13));
        root.addView(account, new LinearLayout.LayoutParams(-1, dp(56)));

        EditText password = new EditText(this);
        password.setId(R.id.password);
        password.setHint("两台手机填同一个密码");
        password.setSingleLine(true);
        password.setInputType(129);
        password.setTextSize(16);
        password.setPadding(dp(14), 0, dp(14), 0);
        password.setBackground(box(Color.rgb(244, 246, 248), 13));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(56));
        pp.topMargin = dp(12);
        root.addView(password, pp);

        TextView err = tv("", 13, Color.rgb(190, 60, 60));
        err.setPadding(0, dp(8), 0, 0);
        root.addView(err);

        Button enter = new Button(this);
        enter.setId(R.id.enter);
        enter.setText("进入双机传");
        enter.setTextSize(16);
        enter.setTextColor(Color.WHITE);
        enter.setAllCaps(false);
        enter.setBackground(box(Color.rgb(77, 139, 224), 14));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(54));
        ep.topMargin = dp(16);
        root.addView(enter, ep);

        TextView note = tv("账号和密码不会上传到任何服务器，只在本机用来让两台手机认出彼此。以后不用再登录。", 13, Color.rgb(125, 135, 145));
        note.setLineSpacing(0, 1.3f);
        note.setPadding(0, dp(18), 0, 0);
        root.addView(note);

        enter.setOnClickListener(v -> {
            String a = account.getText().toString().trim();
            String p = password.getText().toString();
            if (a.isEmpty() || p.length() < 4) {
                err.setText("账号不能为空，密码至少 4 位");
                return;
            }
            String token = sha256(a.toLowerCase(Locale.ROOT) + "\n" + p);
            prefs.edit().putString("token", token).putString("account", a).apply();
            showChat();
        });

        setContentView(outer);
    }

    private void showChat() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 247, 249));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(18), dp(13), dp(18), dp(11));
        head.setBackgroundColor(Color.WHITE);

        TextView title = tv("我的两台手机", 19, Color.rgb(28, 36, 44));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        head.addView(title);

        status = tv("● 正在寻找另一台手机", 12, Color.rgb(172, 112, 30));
        status.setId(R.id.status);
        status.setPadding(0, dp(4), 0, 0);
        head.addView(status);
        root.addView(head);

        View line = new View(this);
        line.setBackgroundColor(Color.rgb(224, 229, 234));
        root.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(14), dp(14), dp(14), dp(18));
        scroll.addView(messages);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.BOTTOM);
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));
        bar.setBackgroundColor(Color.WHITE);

        Button attach = new Button(this);
        attach.setId(R.id.attach);
        attach.setText("+");
        attach.setTextSize(24);
        attach.setAllCaps(false);
        attach.setBackgroundColor(Color.TRANSPARENT);
        bar.addView(attach, new LinearLayout.LayoutParams(dp(48), dp(48)));

        input = new EditText(this);
        input.setId(R.id.message);
        input.setHint("输入消息");
        input.setTextSize(15);
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setPadding(dp(12), dp(9), dp(12), dp(9));
        input.setBackground(box(Color.rgb(238, 241, 244), 14));
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);

        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, -2, 1f);
        ip.leftMargin = dp(4);
        ip.rightMargin = dp(7);
        bar.addView(input, ip);

        Button send = new Button(this);
        send.setId(R.id.send);
        send.setText("发送");
        send.setTextColor(Color.WHITE);
        send.setAllCaps(false);
        send.setBackground(box(Color.rgb(66, 126, 214), 12));
        bar.addView(send, new LinearLayout.LayoutParams(dp(68), dp(48)));
        root.addView(bar);

        // Android 15+ may draw edge-to-edge and let the IME cover bottom controls even
        // with adjustResize. Keep the composer physically above the visible window.
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect visible = new Rect();
            root.getWindowVisibleDisplayFrame(visible);
            int fullBottom = root.getRootView().getHeight();
            int covered = Math.max(0, fullBottom - visible.bottom);
            float shift = covered > dp(120) ? -covered : 0f;
            if (bar.getTranslationY() != shift) bar.setTranslationY(shift);
            if (covered > dp(120)) {
                Log.i("DualPhoneLayout",
                    "visibleBottom=" + visible.bottom +
                    " barY=" + (int)bar.getY() +
                    " barBottom=" + (int)(bar.getY() + bar.getHeight()) +
                    " covered=" + covered);
            }
        });

        attach.setOnClickListener(v -> pickFiles());
        send.setOnClickListener(v -> sendText());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendText();
                return true;
            }
            return false;
        });

        setContentView(root);
        renderMessages();
        TransferService.start(this);
    }

    private void showState(boolean connected) {
        if (status == null) return;
        if (connected) {
            status.setText("● 已连接 · 本地直传");
            status.setTextColor(Color.rgb(36, 137, 92));
        } else {
            status.setText("● 等待另一台手机 · 连同一 Wi‑Fi 或热点");
            status.setTextColor(Color.rgb(172, 112, 30));
        }
    }

    private void sendText() {
        String s = input.getText().toString().trim();
        if (s.isEmpty()) return;

        input.setText("");
        String id = UUID.randomUUID().toString();
        db.addText(id, true, s, System.currentTimeMillis(), "pending");
        renderMessages();
        TransferService.wake(this);
    }

    private void pickFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK || resultCode != RESULT_OK || data == null) return;

        ArrayList<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                uris.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }

        new Thread(() -> {
            for (Uri uri : uris) {
                try { queueFile(uri); }
                catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "文件读取失败", Toast.LENGTH_SHORT).show());
                }
            }
            runOnUiThread(this::renderMessages);
            TransferService.wake(this);
        }).start();
    }

    private void queueFile(Uri uri) throws Exception {
        String name = "文件";
        long declared = -1;

        try (Cursor c = getContentResolver().query(
            uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
            null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int si = c.getColumnIndex(OpenableColumns.SIZE);
                if (ni >= 0 && c.getString(ni) != null) name = c.getString(ni);
                if (si >= 0 && !c.isNull(si)) declared = c.getLong(si);
            }
        }

        String id = UUID.randomUUID().toString();
        File dir = new File(getFilesDir(), "outgoing");
        dir.mkdirs();
        File out = new File(dir, id + "_" + name.replace("/", "_").replace("\\", "_"));

        long size = 0;
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream fout = new FileOutputStream(out)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                fout.write(buf, 0, n);
                size += n;
                if (size > 500L * 1024 * 1024) throw new IOException("too large");
            }
        }

        if (declared > 500L * 1024 * 1024 || size > 500L * 1024 * 1024) {
            out.delete();
            throw new IOException("too large");
        }

        db.addFile(id, true, name, out.getAbsolutePath(), size, System.currentTimeMillis(), "pending");
    }

    private void renderMessages() {
        if (messages == null) return;
        messages.removeAllViews();

        List<TransferDb.Msg> all = db.all();
        if (all.isEmpty()) {
            TextView e = tv("直接发就行\n两台手机连同一个 Wi‑Fi 或热点后，会自动发现彼此。", 14, Color.rgb(112, 123, 134));
            e.setGravity(Gravity.CENTER);
            e.setLineSpacing(0, 1.35f);
            e.setPadding(dp(26), dp(80), dp(26), 0);
            messages.addView(e);
        }

        for (TransferDb.Msg m : all) addBubble(m);
        if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addBubble(TransferDb.Msg m) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(m.mine ? Gravity.RIGHT : Gravity.LEFT);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.bottomMargin = dp(9);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(12), dp(9), dp(12), dp(8));
        bubble.setBackground(box(
            m.mine ? Color.rgb(67, 128, 216) : Color.WHITE, 16));

        int mainColor = m.mine ? Color.WHITE : Color.rgb(28, 36, 44);

        if ("text".equals(m.kind)) {
            TextView t = tv(m.text == null ? "" : m.text, 15, mainColor);
            t.setMaxWidth(dp(310));
            bubble.addView(t);
        } else {
            TextView n = tv("📎  " + (m.fileName == null ? "文件" : m.fileName), 14, mainColor);
            n.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            n.setMaxWidth(dp(300));
            bubble.addView(n);

            TextView s = tv(formatSize(m.fileSize), 12,
                m.mine ? Color.rgb(225, 237, 253) : Color.rgb(112, 123, 134));
            s.setPadding(0, dp(4), 0, 0);
            bubble.addView(s);

            if (!m.mine) {
                TextView save = tv("点一下保存到下载", 12, Color.rgb(55, 116, 205));
                save.setPadding(0, dp(5), 0, 0);
                bubble.addView(save);
                bubble.setOnClickListener(v -> saveToDownloads(m));
            }
        }

        if (m.mine) {
            String state = "sent".equals(m.status) ? "已送达" : "等待另一台手机";
            TextView st = tv(state, 11, Color.rgb(222, 235, 252));
            st.setGravity(Gravity.RIGHT);
            st.setPadding(0, dp(5), 0, 0);
            bubble.addView(st);
        }

        row.addView(bubble);
        messages.addView(row, rp);
    }

    private void saveToDownloads(TransferDb.Msg m) {
        if (m.filePath == null) return;

        new Thread(() -> {
            try {
                File src = new File(m.filePath);
                if (!src.isFile()) throw new IOException();

                String name = m.fileName == null ? "文件" : m.fileName;

                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    v.put(MediaStore.Downloads.IS_PENDING, 1);

                    Uri uri = getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) throw new IOException();

                    try (InputStream in = new FileInputStream(src);
                         OutputStream out = getContentResolver().openOutputStream(uri)) {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }

                    ContentValues done = new ContentValues();
                    done.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, done, null, null);
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    dir.mkdirs();
                    File dst = new File(dir, name);
                    copy(src, dst);
                }

                runOnUiThread(() -> Toast.makeText(this, "已保存到下载", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private static void copy(File a, File b) throws IOException {
        try (InputStream in = new FileInputStream(a);
             OutputStream out = new FileOutputStream(b)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format(Locale.US, "%02x", x & 255));
            return b.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String formatSize(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format(Locale.US, "%.1f KB", n / 1024.0);
        return String.format(Locale.US, "%.1f MB", n / 1024.0 / 1024.0);
    }
}
