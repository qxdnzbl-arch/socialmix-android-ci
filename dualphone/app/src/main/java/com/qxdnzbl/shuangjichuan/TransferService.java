package com.qxdnzbl.shuangjichuan;

import android.app.*;
import android.content.*;
import android.net.wifi.*;
import android.os.*;
import android.util.Log;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class TransferService extends Service {
    public static final String ACTION_STATE = "com.qxdnzbl.shuangjichuan.STATE";
    public static final String ACTION_CHANGED = "com.qxdnzbl.shuangjichuan.CHANGED";
    private static final int NOTIFY_ID = 31021;
    private static final int DISCOVERY_PORT = 39731;
    private static final int TRANSFER_PORT = 39732;
    private static final int MAGIC = 0x534A4331;
    private static final String GROUP = "239.255.42.99";

    private final ExecutorService io = Executors.newCachedThreadPool();
    private final ScheduledExecutorService timer = Executors.newScheduledThreadPool(2);
    private volatile boolean running = true;
    private volatile Peer peer;
    private volatile long peerSeenAt = 0;
    private ServerSocket server;
    private MulticastSocket discovery;
    private WifiManager.MulticastLock multicastLock;
    private TransferDb db;
    private String token, deviceId, prefix;
    private int serverPort;

    static class Peer {
        final InetAddress host;
        final int port;
        Peer(InetAddress host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        db = new TransferDb(this);

        SharedPreferences p = getSharedPreferences("dual", MODE_PRIVATE);
        token = p.getString("token", "");
        deviceId = p.getString("device", "");

        createChannel();
        startForeground(NOTIFY_ID, notification("正在寻找另一台手机"));

        if (token.isEmpty() || deviceId.isEmpty()) {
            stopSelf();
            return;
        }

        prefix = token.substring(0, Math.min(12, token.length()));
        startTcpServer();
        startDiscovery();

        timer.scheduleWithFixedDelay(this::announce, 200, 900, TimeUnit.MILLISECONDS);
        timer.scheduleWithFixedDelay(this::maintenance, 300, 700, TimeUnit.MILLISECONDS);
    }

    public static void start(Context c) {
        Intent i = new Intent(c, TransferService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void wake(Context c) {
        start(c);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                "transfer", "双机传后台接收", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("保持两台手机在局域网内可互相发现");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification notification(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, "transfer")
            : new Notification.Builder(this);
        return b.setContentTitle("双机传")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .build();
    }

    private void setState(boolean connected) {
        Intent i = new Intent(ACTION_STATE).setPackage(getPackageName());
        i.putExtra("connected", connected);
        sendBroadcast(i);

        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFY_ID, notification(
            connected ? "已连接另一台手机" : "等待另一台手机 · 同一 Wi‑Fi 或热点"));
    }

    private void changed() {
        sendBroadcast(new Intent(ACTION_CHANGED).setPackage(getPackageName()));
    }

    private void startTcpServer() {
        io.execute(() -> {
            try {
                server = new ServerSocket(TRANSFER_PORT);
                server.setReuseAddress(true);
                serverPort = TRANSFER_PORT;
                Log.i("DualPhone", "SERVER port=" + serverPort + " prefix=" + prefix);
                while (running) {
                    Socket s = server.accept();
                    io.execute(() -> receive(s));
                }
            } catch (Exception e) {
                if (running) Log.e("DualPhone", "server", e);
            }
        });
    }

    private void startDiscovery() {
        try {
            WifiManager wm = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            multicastLock = wm.createMulticastLock("shuangjichuan");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        } catch (Exception ignored) {}

        io.execute(() -> {
            try {
                discovery = new MulticastSocket(null);
                discovery.setReuseAddress(true);
                discovery.bind(new InetSocketAddress(DISCOVERY_PORT));
                discovery.setBroadcast(true);
                try {
                    discovery.joinGroup(InetAddress.getByName(GROUP));
                } catch (Exception ignored) {}

                byte[] buf = new byte[512];
                while (running) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    discovery.receive(p);
                    String msg = new String(
                        p.getData(), p.getOffset(), p.getLength(), StandardCharsets.UTF_8);
                    String[] a = msg.split("\\|");
                    if (a.length != 5 || !"SJC3".equals(a[0])) continue;
                    if (!prefix.equals(a[1])) continue;
                    if (deviceId.equals(a[2])) continue;

                    int port;
                    try {
                        port = Integer.parseInt(a[3]);
                    } catch (Exception e) {
                        continue;
                    }
                    if (port <= 0 || port > 65535) continue;

                    peer = new Peer(p.getAddress(), port);
                    peerSeenAt = System.currentTimeMillis();
                    setState(true);
                    flushPending();
                }
            } catch (Exception e) {
                if (running) Log.e("DualPhone", "discovery", e);
            }
        });
    }

    private void announce() {
        if (!running || serverPort <= 0) return;
        String msg = "SJC3|" + prefix + "|" + deviceId + "|" + serverPort + "|1";
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket s = new DatagramSocket()) {
            s.setBroadcast(true);
            s.send(new DatagramPacket(
                bytes, bytes.length, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT));
            try {
                s.send(new DatagramPacket(
                    bytes, bytes.length, InetAddress.getByName(GROUP), DISCOVERY_PORT));
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private void maintenance() {
        if (!running) return;
        if (peer != null && System.currentTimeMillis() - peerSeenAt > 5000) {
            peer = null;
            setState(false);
        }
        if (peer != null) flushPending();
    }

    private synchronized void flushPending() {
        Peer target = peer;
        if (target == null) return;

        for (TransferDb.Msg m : db.pending()) {
            if (!running || peer == null) return;
            if (send(target, m)) {
                db.markSent(m.id);
                changed();
            } else {
                peer = null;
                setState(false);
                return;
            }
        }
    }

    private boolean send(Peer p, TransferDb.Msg m) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(p.host, p.port), 2000);
            s.setSoTimeout(5000);

            DataOutputStream out =
                new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            DataInputStream in =
                new DataInputStream(new BufferedInputStream(s.getInputStream()));

            out.writeInt(MAGIC);
            writeString(out, token);
            writeString(out, m.id);
            writeString(out, deviceId);
            out.writeLong(m.createdAt);

            if ("text".equals(m.kind)) {
                out.writeByte(1);
                writeString(out, m.text == null ? "" : m.text);
            } else {
                File file = new File(m.filePath == null ? "" : m.filePath);
                if (!file.isFile()) return false;

                out.writeByte(2);
                writeString(out, m.fileName == null ? "文件" : m.fileName);
                out.writeLong(file.length());

                try (FileInputStream fin = new FileInputStream(file)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = fin.read(buf)) > 0) out.write(buf, 0, n);
                }
            }

            out.flush();
            return in.readInt() == 1;
        } catch (Exception e) {
            Log.w("DualPhone", "send failed " + e);
            return false;
        }
    }

    private void receive(Socket s) {
        try (Socket socket = s) {
            socket.setSoTimeout(30000);

            DataInputStream in =
                new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out =
                new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            if (in.readInt() != MAGIC) return;

            String incomingToken = readString(in, 512);
            if (!token.equals(incomingToken)) return;

            String id = readString(in, 1024);
            readString(in, 1024); // sender device id
            long createdAt = in.readLong();
            int type = in.readUnsignedByte();

            if (type == 1) {
                String text = readString(in, 1024 * 1024);
                db.addText(id, false, text, createdAt, "received");
            } else if (type == 2) {
                String name = safeName(readString(in, 4096));
                long size = in.readLong();
                if (size < 0 || size > 500L * 1024 * 1024) return;

                File dir = new File(getFilesDir(), "incoming");
                dir.mkdirs();
                File file = new File(dir, id + "_" + name);

                try (FileOutputStream fout = new FileOutputStream(file)) {
                    byte[] buf = new byte[64 * 1024];
                    long left = size;
                    while (left > 0) {
                        int n = in.read(buf, 0, (int)Math.min(buf.length, left));
                        if (n < 0) throw new EOFException();
                        fout.write(buf, 0, n);
                        left -= n;
                    }
                }

                db.addFile(id, false, name, file.getAbsolutePath(), size, createdAt, "received");
            } else {
                return;
            }

            out.writeInt(1);
            out.flush();
            peerSeenAt = System.currentTimeMillis();
            changed();
        } catch (Exception e) {
            Log.w("DualPhone", "receive failed " + e);
        }
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(b.length);
        out.write(b);
    }

    private static String readString(DataInputStream in, int max) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > max) throw new IOException("bad length");
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static String safeName(String s) {
        String v = s.replace("/", "_").replace("\\", "_").trim();
        return v.isEmpty() ? "文件" : v;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {}
        try {
            if (discovery != null) discovery.close();
        } catch (Exception ignored) {}
        try {
            if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        } catch (Exception ignored) {}

        timer.shutdownNow();
        io.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
