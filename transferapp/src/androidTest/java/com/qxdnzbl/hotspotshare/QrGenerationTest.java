package com.qxdnzbl.hotspotshare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class QrGenerationTest {
    @Test
    public void qrBitmapIsGeneratedWithBlackAndWhiteModules() throws Exception {
        Bitmap bitmap = QrMainActivity.makeQr("http://192.168.97.3:8080", 300);
        assertEquals(300, bitmap.getWidth());
        assertEquals(300, bitmap.getHeight());

        boolean hasBlack = false;
        boolean hasWhite = false;
        for (int y = 0; y < bitmap.getHeight(); y += 5) {
            for (int x = 0; x < bitmap.getWidth(); x += 5) {
                int pixel = bitmap.getPixel(x, y);
                if (pixel == 0xFF000000) hasBlack = true;
                if (pixel == 0xFFFFFFFF) hasWhite = true;
            }
        }
        assertTrue(hasBlack);
        assertTrue(hasWhite);
    }
}
