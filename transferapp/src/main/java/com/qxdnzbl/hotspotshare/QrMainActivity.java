package com.qxdnzbl.hotspotshare;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class QrMainActivity extends Activity {
    private static final int PICK_FILES = 1001;

    private MainActivity.LocalFileServer server;
    private TextView fileStatus;
    private TextView addressView;
    private ImageView qrView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        try {
            server = new MainActivity.LocalFileServer(this);
            server.start();
            refreshAddress();
        } catch (Exception e) {
            addressView.setText("服务器启动失败：" + e.getMessage());
            qrView.setImageDrawable(null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (server != null) refreshAddress();
    }

    private void buildUi() {
        int pad = dp(22);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, dp(28), pad, dp(34));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("热点传文件");
        title.setTextSize(28);
        title.setGravity(Gravity.START);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("OPPO 保持个人热点开启，iPhone 连上热点。\n这次不用手输网址，也不要在 OPPO 浏览器打开地址。");
        subtitle.setTextSize(16);
        subtitle.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(12);
        root.addView(subtitle, subLp);

        Button pickButton = new Button(this);
        pickButton.setText("选择要传的文件");
        pickButton.setTextSize(17);
        pickButton.setMinHeight(dp(52));
        LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(-1, -2);
        pickLp.topMargin = dp(22);
        root.addView(pickButton, pickLp);
        pickButton.setOnClickListener(v -> chooseFiles());

        fileStatus = new TextView(this);
        fileStatus.setText("还没有选择文件");
        fileStatus.setTextSize(16);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(12);
        root.addView(fileStatus, statusLp);

        TextView qrTitle = new TextView(this);
        qrTitle.setText("iPhone 用相机扫二维码");
        qrTitle.setTextSize(20);
        qrTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams qrTitleLp = new LinearLayout.LayoutParams(-1, -2);
        qrTitleLp.topMargin = dp(28);
        root.addView(qrTitle, qrTitleLp);

        TextView qrHint = new TextView(this);
        qrHint.setText("iPhone：打开系统“相机” → 对准下面二维码 → 点顶部出现的网址。");
        qrHint.setTextSize(15);
        qrHint.setGravity(Gravity.CENTER);
        qrHint.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams qhLp = new LinearLayout.LayoutParams(-1, -2);
        qhLp.topMargin = dp(8);
        root.addView(qrHint, qhLp);

        qrView = new ImageView(this);
        qrView.setAdjustViewBounds(true);
        qrView.setContentDescription("iPhone 扫码二维码");
        int qrSize = dp(250);
        LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(qrSize, qrSize);
        qrLp.topMargin = dp(14);
        root.addView(qrView, qrLp);

        addressView = new TextView(this);
        addressView.setText("正在获取热点地址…");
        addressView.setTextSize(14);
        addressView.setGravity(Gravity.CENTER);
        addressView.setTextIsSelectable(true);
        LinearLayout.LayoutParams addressLp = new LinearLayout.LayoutParams(-1, -2);
        addressLp.topMargin = dp(10);
        root.addView(addressView, addressLp);

        TextView finalHint = new TextView(this);
        finalHint.setText("传输期间保持这个页面打开。iPhone 扫码进入后，直接点文件下载即可。文件只在两台手机之间传，不上传云端。");
        finalHint.setTextSize(15);
        finalHint.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams finalLp = new LinearLayout.LayoutParams(-1, -2);
        finalLp.topMargin = dp(20);
        root.addView(finalHint, finalLp);

        setContentView(scroll);
    }

    private void chooseFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_FILES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FILES || resultCode != RESULT_OK || data == null || server == null) return;

        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            ClipData clip = data.getClipData();
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) uris.add(uri);
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }

        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        for (Uri uri : uris) {
            try {
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) {
            }
        }

        List<MainActivity.SharedFile> files = new ArrayList<>();
        long total = 0L;
        boolean knownTotal = true;
        for (Uri uri : uris) {
            MainActivity.SharedFile file = readMetadata(uri);
            files.add(file);
            if (file.size >= 0) total += file.size;
            else knownTotal = false;
        }
        server.setFiles(files);

        if (files.isEmpty()) {
            fileStatus.setText("没有读取到文件");
        } else {
            String sizeText = knownTotal ? formatBytes(total) : "大小未知";
            fileStatus.setText("已选择 " + files.size() + " 个文件 · " + sizeText + "\n现在用 iPhone 扫下面二维码。");
        }
        refreshAddress();
    }

    private MainActivity.SharedFile readMetadata(Uri uri) {
        String name = "file";
        long size = -1L;
        String mime = getContentResolver().getType(uri);
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        if (mime == null || mime.trim().isEmpty()) mime = "application/octet-stream";
        return new MainActivity.SharedFile(uri, name, size, mime);
    }

    private void refreshAddress() {
        if (server == null) return;
        List<String> ips = getPrivateIpv4Addresses();
        if (ips.isEmpty()) {
            addressView.setText("没有检测到热点地址。请先开启 OPPO 个人热点。");
            qrView.setImageDrawable(null);
            return;
        }
        String url = "http://" + ips.get(0) + ":" + server.getPort();
        addressView.setText(url);
        try {
            qrView.setImageBitmap(makeQr(url, 900));
        } catch (Exception e) {
            qrView.setImageDrawable(null);
            addressView.setText(url + "\n二维码生成失败：" + e.getMessage());
        }
    }

    static Bitmap makeQr(String text, int size) throws Exception {
        BitMatrix matrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return bitmap;
    }

    private List<String> getPrivateIpv4Addresses() {
        List<String> preferred = new ArrayList<>();
        List<String> other = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return Collections.emptyList();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                try {
                    if (!nif.isUp() || nif.isLoopback()) continue;
                } catch (SocketException ignored) {
                }
                Enumeration<InetAddress> addresses = nif.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) continue;
                    String ip = address.getHostAddress();
                    if (ip == null) continue;
                    if (ip.startsWith("192.168.")) preferred.add(ip);
                    else if (address.isSiteLocalAddress()) other.add(ip);
                }
            }
        } catch (Exception ignored) {
        }
        preferred.addAll(other);
        return preferred;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int i = -1;
        do {
            value /= 1024.0;
            i++;
        } while (value >= 1024 && i < units.length - 1);
        return new DecimalFormat("0.##").format(value) + " " + units[i];
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (server != null) server.stop();
        super.onDestroy();
    }
}
