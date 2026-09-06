package com.qxdnzbl.hotspotshare;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RunWith(AndroidJUnit4.class)
public class LocalFileServerTest {
    private Context context;
    private MainActivity.LocalFileServer server;
    private byte[] first;
    private byte[] second;
    private File firstFile;
    private File secondFile;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        first = new byte[1024 * 1024 + 333];
        second = new byte[700 * 1024 + 17];
        for (int i = 0; i < first.length; i++) first[i] = (byte) ((i * 31 + 7) & 0xff);
        for (int i = 0; i < second.length; i++) second[i] = (byte) ((i * 17 + 11) & 0xff);

        firstFile = new File(context.getCacheDir(), "传输测试一.bin");
        secondFile = new File(context.getCacheDir(), "transfer-test-two.bin");
        write(firstFile, first);
        write(secondFile, second);

        server = new MainActivity.LocalFileServer(context);
        server.start();
        server.setFiles(Arrays.asList(
                new MainActivity.SharedFile(Uri.fromFile(firstFile), "传输测试一.bin", first.length, "application/octet-stream"),
                new MainActivity.SharedFile(Uri.fromFile(secondFile), "transfer-test-two.bin", second.length, "application/octet-stream")
        ));
    }

    @After
    public void tearDown() {
        if (server != null) server.stop();
        if (firstFile != null) firstFile.delete();
        if (secondFile != null) secondFile.delete();
    }

    @Test
    public void homePageListsFiles() throws Exception {
        HttpURLConnection c = open("/");
        assertEquals(200, c.getResponseCode());
        String html = new String(readAll(c.getInputStream()), StandardCharsets.UTF_8);
        assertTrue(html.contains("热点传文件"));
        assertTrue(html.contains("传输测试一.bin"));
        assertTrue(html.contains("全部打包下载"));
        c.disconnect();
    }

    @Test
    public void directDownloadStreamsExactBytes() throws Exception {
        HttpURLConnection c = open("/download/0");
        assertEquals(200, c.getResponseCode());
        assertEquals(String.valueOf(first.length), c.getHeaderField("Content-Length"));
        assertEquals("bytes", c.getHeaderField("Accept-Ranges"));
        byte[] body = readAll(c.getInputStream());
        assertArrayEquals(first, body);
        c.disconnect();
    }

    @Test
    public void rangeDownloadReturns206AndExactSlice() throws Exception {
        int start = 12345;
        int end = 54321;
        HttpURLConnection c = open("/download/0");
        c.setRequestProperty("Range", "bytes=" + start + "-" + end);
        assertEquals(206, c.getResponseCode());
        assertEquals("bytes " + start + "-" + end + "/" + first.length, c.getHeaderField("Content-Range"));
        byte[] body = readAll(c.getInputStream());
        assertArrayEquals(Arrays.copyOfRange(first, start, end + 1), body);
        c.disconnect();
    }

    @Test
    public void zipDownloadContainsAllFilesExactly() throws Exception {
        HttpURLConnection c = open("/download-all.zip");
        assertEquals(200, c.getResponseCode());
        assertEquals("application/zip", c.getContentType());

        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zin = new ZipInputStream(c.getInputStream())) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                entries.put(entry.getName(), readAll(zin));
                zin.closeEntry();
            }
        }
        assertEquals(2, entries.size());
        assertArrayEquals(first, entries.get("传输测试一.bin"));
        assertArrayEquals(second, entries.get("transfer-test-two.bin"));
        c.disconnect();
    }

    private HttpURLConnection open(String path) throws Exception {
        URL url = new URL("http://127.0.0.1:" + server.getPort() + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(15000);
        c.setUseCaches(false);
        return c;
    }

    private static void write(File file, byte[] data) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            if (read > 0) out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
