package com.qxdnzbl.hotspotshare;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final int PICK_FILES = 1001;

    private LocalFileServer server;
    private TextView fileStatus;
    private TextView addressView;
    private TextView hintView;
    private Button pickButton;
    private Button copyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        buildUi();
        try {
            server = new LocalFileServer(this);
            server.start();
            refreshAddress();
        } catch (IOException e) {
            addressView.setText("服务器启动失败：" + e.getMessage());
            copyButton.setEnabled(false);
        }
    }

    private void buildUi() {
        int pad = dp(22);
        int gap = dp(14);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(28), pad, dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("热点传文件");
        title.setTextSize(28);
        title.setGravity(Gravity.START);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("OPPO 开个人热点，iPhone 连上后直接用 Safari 下载。\n不需要 iPhone 安装任何 App，也不走云端。");
        subtitle.setTextSize(16);
        subtitle.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = gap;
        root.addView(subtitle, subLp);

        pickButton = new Button(this);
        pickButton.setText("选择要传的文件");
        pickButton.setTextSize(17);
        pickButton.setMinHeight(dp(52));
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(-1, -2);
        buttonLp.topMargin = dp(24);
        root.addView(pickButton, buttonLp);
        pickButton.setOnClickListener(v -> chooseFiles());

        fileStatus = new TextView(this);
        fileStatus.setText("还没有选择文件");
        fileStatus.setTextSize(16);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = gap;
        root.addView(fileStatus, statusLp);

        TextView addressTitle = new TextView(this);
        addressTitle.setText("iPhone Safari 输入这个地址");
        addressTitle.setTextSize(18);
        LinearLayout.LayoutParams atLp = new LinearLayout.LayoutParams(-1, -2);
        atLp.topMargin = dp(30);
        root.addView(addressTitle, atLp);

        addressView = new TextView(this);
        addressView.setText("正在获取热点地址…");
        addressView.setTextSize(20);
        addressView.setTextIsSelectable(true);
        addressView.setPadding(0, dp(10), 0, dp(10));
        root.addView(addressView, new LinearLayout.LayoutParams(-1, -2));

        copyButton = new Button(this);
        copyButton.setText("复制地址");
        copyButton.setMinHeight(dp(48));
        root.addView(copyButton, new LinearLayout.LayoutParams(-1, -2));
        copyButton.setOnClickListener(v -> copyPrimaryAddress());

        hintView = new TextView(this);
        hintView.setText("传输时保持这个页面打开。\n如果显示多个地址：在 iPhone 的 Wi‑Fi 详情里看“路由器”，使用与它相同的 IP 地址，再加 :8080。\n进入网页后可下载单个文件；多选文件时也可以一键打包成 ZIP 下载。");
        hintView.setTextSize(15);
        hintView.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.topMargin = dp(20);
        root.addView(hintView, hintLp);

        setContentView(scroll);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
        if (requestCode != PICK_FILES || resultCode != RESULT_OK || data == null || server == null) {
            return;
        }

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

        List<SharedFile> files = new ArrayList<>();
        long total = 0L;
        boolean knownTotal = true;
        for (Uri uri : uris) {
            SharedFile file = readMetadata(uri);
            files.add(file);
            if (file.size >= 0) total += file.size;
            else knownTotal = false;
        }
        server.setFiles(files);

        if (files.isEmpty()) {
            fileStatus.setText("没有读取到文件");
        } else {
            String sizeText = knownTotal ? formatBytes(total) : "大小未知";
            fileStatus.setText("已选择 " + files.size() + " 个文件 · " + sizeText + "\n服务器已就绪，可以在 iPhone 打开下面地址。");
        }
        refreshAddress();
    }

    private SharedFile readMetadata(Uri uri) {
        String name = "file";
        long size = -1L;
        String mime = getContentResolver().getType(uri);
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null);
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
        return new SharedFile(uri, name, size, mime);
    }

    private void refreshAddress() {
        if (server == null) return;
        List<String> ips = getPrivateIpv4Addresses();
        if (ips.isEmpty()) {
            addressView.setText("没有检测到热点 IP。\n先开启 OPPO 个人热点，再回到这里。");
            copyButton.setEnabled(false);
            return;
        }
        List<String> urls = new ArrayList<>();
        for (String ip : ips) {
            urls.add("http://" + ip + ":" + server.getPort());
        }
        addressView.setText(joinLines(urls));
        copyButton.setEnabled(true);
    }

    private void copyPrimaryAddress() {
        if (server == null) return;
        List<String> ips = getPrivateIpv4Addresses();
        if (ips.isEmpty()) return;
        String url = "http://" + ips.get(0) + ":" + server.getPort();
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("热点传文件地址", url));
            Toast.makeText(this, "已复制：" + url, Toast.LENGTH_SHORT).show();
        }
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

    private static String joinLines(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(items.get(i));
        }
        return sb.toString();
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

    @Override
    protected void onDestroy() {
        if (server != null) server.stop();
        super.onDestroy();
    }

    static final class SharedFile {
        final Uri uri;
        final String name;
        final long size;
        final String mime;

        SharedFile(Uri uri, String name, long size, String mime) {
            this.uri = uri;
            this.name = name == null || name.trim().isEmpty() ? "file" : name;
            this.size = size;
            this.mime = mime;
        }
    }

    static final class LocalFileServer {
        private final Context context;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private volatile List<SharedFile> files = Collections.emptyList();
        private volatile boolean running;
        private ServerSocket serverSocket;
        private int port = 8080;

        LocalFileServer(Context context) {
            this.context = context.getApplicationContext();
        }

        void start() throws IOException {
            IOException last = null;
            for (int p = 8080; p <= 8090; p++) {
                try {
                    ServerSocket ss = new ServerSocket(p);
                    ss.setReuseAddress(true);
                    serverSocket = ss;
                    port = p;
                    running = true;
                    executor.execute(this::acceptLoop);
                    return;
                } catch (IOException e) {
                    last = e;
                }
            }
            throw last == null ? new IOException("无法打开端口") : last;
        }

        int getPort() {
            return port;
        }

        void setFiles(List<SharedFile> newFiles) {
            files = Collections.unmodifiableList(new ArrayList<>(newFiles));
        }

        void stop() {
            running = false;
            try {
                if (serverSocket != null) serverSocket.close();
            } catch (IOException ignored) {
            }
            executor.shutdownNow();
        }

        private void acceptLoop() {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    socket.setTcpNoDelay(true);
                    executor.execute(() -> handle(socket));
                } catch (IOException e) {
                    if (running) {
                        // Continue accepting unless the server was stopped.
                    }
                }
            }
        }

        private void handle(Socket socket) {
            try (Socket s = socket;
                 InputStream rawIn = new BufferedInputStream(s.getInputStream());
                 OutputStream rawOut = new BufferedOutputStream(s.getOutputStream())) {

                HttpRequest request = HttpRequest.read(rawIn);
                if (request == null) return;

                if (!("GET".equals(request.method) || "HEAD".equals(request.method))) {
                    sendText(rawOut, 405, "Method Not Allowed", "只支持 GET/HEAD");
                    return;
                }

                String path = request.path;
                if ("/".equals(path)) {
                    sendHome(rawOut, "HEAD".equals(request.method));
                    return;
                }
                if (path.startsWith("/download/")) {
                    int index = parseIndex(path.substring("/download/".length()));
                    List<SharedFile> snapshot = files;
                    if (index < 0 || index >= snapshot.size()) {
                        sendText(rawOut, 404, "Not Found", "文件不存在");
                        return;
                    }
                    sendFile(rawOut, request, snapshot.get(index), "HEAD".equals(request.method));
                    return;
                }
                if ("/download-all.zip".equals(path)) {
                    if ("HEAD".equals(request.method)) {
                        sendHeaders(rawOut, 200, "OK", headers(
                                "Content-Type", "application/zip",
                                "Content-Disposition", attachmentHeader("OPPO-transfer.zip"),
                                "Transfer-Encoding", "chunked",
                                "Cache-Control", "no-store",
                                "Connection", "close"
                        ));
                        rawOut.flush();
                    } else {
                        sendZip(rawOut);
                    }
                    return;
                }
                sendText(rawOut, 404, "Not Found", "页面不存在");
            } catch (Exception ignored) {
            }
        }

        private int parseIndex(String value) {
            try {
                int q = value.indexOf('?');
                if (q >= 0) value = value.substring(0, q);
                return Integer.parseInt(value);
            } catch (Exception e) {
                return -1;
            }
        }

        private void sendHome(OutputStream out, boolean headOnly) throws IOException {
            List<SharedFile> snapshot = files;
            StringBuilder body = new StringBuilder();
            body.append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">" +
                    "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\">" +
                    "<title>热点传文件</title><style>" +
                    "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;margin:0;background:#f6f7fb;color:#17181c}" +
                    ".wrap{max-width:680px;margin:0 auto;padding:28px 18px 40px}.card{background:#fff;border-radius:20px;padding:20px;box-shadow:0 8px 30px rgba(0,0,0,.06)}" +
                    "h1{font-size:28px;margin:0 0 8px}.sub{color:#666;margin-bottom:20px;line-height:1.55}.file{padding:14px 0;border-top:1px solid #eee}.name{font-weight:600;word-break:break-all}.size{color:#777;font-size:14px;margin-top:5px}" +
                    "a.btn{display:block;text-decoration:none;text-align:center;background:#3767e8;color:#fff;padding:14px 16px;border-radius:14px;margin-top:12px;font-weight:600}" +
                    "a.secondary{background:#17181c}.empty{padding:24px 0;color:#777;text-align:center}.tip{font-size:14px;color:#777;line-height:1.5;margin-top:16px}" +
                    "</style></head><body><div class=\"wrap\"><div class=\"card\"><h1>热点传文件</h1>" +
                    "<div class=\"sub\">文件从 OPPO 直接传到这台 iPhone，不经过云端。下载完成前请保持 OPPO 上的“热点传文件”页面打开。</div>");

            if (snapshot.isEmpty()) {
                body.append("<div class=\"empty\">OPPO 端还没有选择文件</div>");
            } else {
                if (snapshot.size() > 1) {
                    body.append("<a class=\"btn secondary\" href=\"/download-all.zip\">全部打包下载（ZIP）</a>");
                }
                for (int i = 0; i < snapshot.size(); i++) {
                    SharedFile f = snapshot.get(i);
                    body.append("<div class=\"file\"><div class=\"name\">")
                            .append(escapeHtml(f.name)).append("</div><div class=\"size\">")
                            .append(f.size >= 0 ? formatBytes(f.size) : "大小未知")
                            .append("</div><a class=\"btn\" href=\"/download/").append(i).append("\">下载这个文件</a></div>");
                }
                body.append("<div class=\"tip\">大文件支持分段下载；如果 Safari 中断，再点同一个文件可以重新下载。</div>");
            }
            body.append("</div></div></body></html>");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            Map<String, String> h = headers(
                    "Content-Type", "text/html; charset=utf-8",
                    "Content-Length", String.valueOf(bytes.length),
                    "Cache-Control", "no-store",
                    "Connection", "close"
            );
            sendHeaders(out, 200, "OK", h);
            if (!headOnly) out.write(bytes);
            out.flush();
        }

        private void sendFile(OutputStream out, HttpRequest request, SharedFile file, boolean headOnly) throws IOException {
            long size = file.size;
            long start = 0L;
            long end = size > 0 ? size - 1 : -1L;
            boolean partial = false;

            String range = request.headers.get("range");
            if (range != null && size > 0 && range.toLowerCase(Locale.US).startsWith("bytes=")) {
                String spec = range.substring(6).trim();
                int dash = spec.indexOf('-');
                if (dash >= 0) {
                    try {
                        String startPart = spec.substring(0, dash).trim();
                        String endPart = spec.substring(dash + 1).trim();
                        if (!startPart.isEmpty()) start = Long.parseLong(startPart);
                        if (!endPart.isEmpty()) end = Long.parseLong(endPart);
                        if (start < 0 || start >= size) {
                            sendHeaders(out, 416, "Range Not Satisfiable", headers(
                                    "Content-Range", "bytes */" + size,
                                    "Connection", "close"
                            ));
                            out.flush();
                            return;
                        }
                        if (end < start || end >= size) end = size - 1;
                        partial = true;
                    } catch (NumberFormatException ignored) {
                        start = 0L;
                        end = size - 1;
                        partial = false;
                    }
                }
            }

            long length = size >= 0 ? (end - start + 1) : -1L;
            Map<String, String> h = new HashMap<>();
            h.put("Content-Type", file.mime);
            h.put("Content-Disposition", attachmentHeader(file.name));
            h.put("Accept-Ranges", "bytes");
            h.put("Cache-Control", "no-store");
            h.put("Connection", "close");
            if (size >= 0) {
                h.put("Content-Length", String.valueOf(length));
                if (partial) h.put("Content-Range", "bytes " + start + "-" + end + "/" + size);
            }

            sendHeaders(out, partial ? 206 : 200, partial ? "Partial Content" : "OK", h);
            if (headOnly) {
                out.flush();
                return;
            }

            try (InputStream in = new BufferedInputStream(context.getContentResolver().openInputStream(file.uri), 256 * 1024)) {
                if (in == null) throw new IOException("无法读取文件");
                if (start > 0) skipFully(in, start);
                byte[] buffer = new byte[256 * 1024];
                long remaining = length;
                while (true) {
                    int maxRead = buffer.length;
                    if (remaining >= 0) {
                        if (remaining == 0) break;
                        maxRead = (int) Math.min(maxRead, remaining);
                    }
                    int read = in.read(buffer, 0, maxRead);
                    if (read < 0) break;
                    out.write(buffer, 0, read);
                    if (remaining >= 0) remaining -= read;
                }
                out.flush();
            }
        }

        private void sendZip(OutputStream out) throws IOException {
            List<SharedFile> snapshot = files;
            if (snapshot.isEmpty()) {
                sendText(out, 404, "Not Found", "没有可下载的文件");
                return;
            }
            sendHeaders(out, 200, "OK", headers(
                    "Content-Type", "application/zip",
                    "Content-Disposition", attachmentHeader("OPPO-transfer.zip"),
                    "Transfer-Encoding", "chunked",
                    "Cache-Control", "no-store",
                    "Connection", "close"
            ));
            out.flush();

            HttpChunkedOutputStream chunked = new HttpChunkedOutputStream(out, 256 * 1024);
            ZipOutputStream zip = new ZipOutputStream(chunked);
            zip.setLevel(Deflater.NO_COMPRESSION);
            Set<String> usedNames = new HashSet<>();
            byte[] buffer = new byte[256 * 1024];
            try {
                for (int i = 0; i < snapshot.size(); i++) {
                    SharedFile f = snapshot.get(i);
                    String entryName = uniqueZipName(sanitizeZipName(f.name), usedNames, i);
                    ZipEntry entry = new ZipEntry(entryName);
                    zip.putNextEntry(entry);
                    try (InputStream in = new BufferedInputStream(context.getContentResolver().openInputStream(f.uri), 256 * 1024)) {
                        if (in == null) throw new IOException("无法读取：" + f.name);
                        int read;
                        while ((read = in.read(buffer)) >= 0) {
                            if (read == 0) continue;
                            zip.write(buffer, 0, read);
                        }
                    }
                    zip.closeEntry();
                }
                zip.finish();
                zip.flush();
                chunked.finish();
            } finally {
                try { zip.close(); } catch (Exception ignored) {}
            }
        }

        private static String sanitizeZipName(String name) {
            String clean = name == null ? "file" : name.replace('\\', '_').replace('/', '_').replace('\r', '_').replace('\n', '_');
            if (clean.trim().isEmpty()) clean = "file";
            return clean;
        }

        private static String uniqueZipName(String base, Set<String> used, int index) {
            String name = base;
            int n = 2;
            while (used.contains(name)) {
                name = "(" + n + ") " + base;
                n++;
            }
            used.add(name);
            return name;
        }

        private static void skipFully(InputStream in, long bytes) throws IOException {
            long remaining = bytes;
            byte[] fallback = new byte[64 * 1024];
            while (remaining > 0) {
                long skipped = in.skip(remaining);
                if (skipped > 0) {
                    remaining -= skipped;
                    continue;
                }
                int read = in.read(fallback, 0, (int) Math.min(fallback.length, remaining));
                if (read < 0) throw new IOException("无法跳到文件指定位置");
                remaining -= read;
            }
        }

        private static String attachmentHeader(String filename) {
            String encoded;
            try {
                encoded = URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
            } catch (Exception e) {
                encoded = "file";
            }
            String asciiFallback = filename.replaceAll("[^A-Za-z0-9._ -]", "_").replace("\"", "_");
            if (asciiFallback.trim().isEmpty()) asciiFallback = "file";
            return "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded;
        }

        private static String escapeHtml(String value) {
            if (value == null) return "";
            return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
        }

        private static void sendText(OutputStream out, int code, String reason, String text) throws IOException {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            sendHeaders(out, code, reason, headers(
                    "Content-Type", "text/plain; charset=utf-8",
                    "Content-Length", String.valueOf(bytes.length),
                    "Connection", "close"
            ));
            out.write(bytes);
            out.flush();
        }

        private static Map<String, String> headers(String... pairs) {
            Map<String, String> map = new HashMap<>();
            for (int i = 0; i + 1 < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
            return map;
        }

        private static void sendHeaders(OutputStream out, int code, String reason, Map<String, String> headers) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n");
            sb.append("Server: HotspotShare/1.0\r\n");
            for (Map.Entry<String, String> e : headers.entrySet()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
            sb.append("\r\n");
            out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        }
    }

    static final class HttpRequest {
        final String method;
        final String path;
        final Map<String, String> headers;

        HttpRequest(String method, String path, Map<String, String> headers) {
            this.method = method;
            this.path = path;
            this.headers = headers;
        }

        static HttpRequest read(InputStream in) throws IOException {
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.trim().isEmpty()) return null;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return null;
            String method = parts[0].trim().toUpperCase(Locale.US);
            String rawPath = parts[1].trim();
            String path;
            try {
                path = URLDecoder.decode(rawPath, "UTF-8");
            } catch (Exception e) {
                path = rawPath;
            }
            Map<String, String> headers = new HashMap<>();
            while (true) {
                String line = readLine(in);
                if (line == null || line.isEmpty()) break;
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String key = line.substring(0, colon).trim().toLowerCase(Locale.US);
                    String value = line.substring(colon + 1).trim();
                    headers.put(key, value);
                }
            }
            return new HttpRequest(method, path, headers);
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int previous = -1;
            while (buffer.size() < 65536) {
                int b = in.read();
                if (b < 0) {
                    if (buffer.size() == 0) return null;
                    break;
                }
                if (previous == '\r' && b == '\n') {
                    byte[] data = buffer.toByteArray();
                    int len = Math.max(0, data.length - 1);
                    return new String(data, 0, len, StandardCharsets.ISO_8859_1);
                }
                buffer.write(b);
                previous = b;
            }
            return new String(buffer.toByteArray(), StandardCharsets.ISO_8859_1).trim();
        }
    }

    static final class HttpChunkedOutputStream extends OutputStream {
        private final OutputStream out;
        private final byte[] buffer;
        private int count;
        private boolean finished;

        HttpChunkedOutputStream(OutputStream out, int bufferSize) {
            this.out = out;
            this.buffer = new byte[Math.max(8192, bufferSize)];
        }

        @Override
        public void write(int b) throws IOException {
            ensureOpen();
            if (count == buffer.length) flushChunk();
            buffer[count++] = (byte) b;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            ensureOpen();
            while (len > 0) {
                int space = buffer.length - count;
                if (space == 0) {
                    flushChunk();
                    space = buffer.length;
                }
                int copy = Math.min(space, len);
                System.arraycopy(b, off, buffer, count, copy);
                count += copy;
                off += copy;
                len -= copy;
            }
        }

        @Override
        public void flush() throws IOException {
            ensureOpen();
            flushChunk();
            out.flush();
        }

        void finish() throws IOException {
            if (finished) return;
            flushChunk();
            out.write("0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            finished = true;
        }

        @Override
        public void close() throws IOException {
            finish();
        }

        private void flushChunk() throws IOException {
            if (count <= 0) return;
            String prefix = Integer.toHexString(count) + "\r\n";
            out.write(prefix.getBytes(StandardCharsets.ISO_8859_1));
            out.write(buffer, 0, count);
            out.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
            count = 0;
        }

        private void ensureOpen() throws IOException {
            if (finished) throw new IOException("chunked stream already finished");
        }
    }
}
