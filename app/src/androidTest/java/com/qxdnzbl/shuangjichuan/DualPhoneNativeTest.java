package com.qxdnzbl.shuangjichuan;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import android.os.ParcelFileDescriptor;
import java.io.FileInputStream;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;

@RunWith(AndroidJUnit4.class)
public class DualPhoneNativeTest {
    private void waitForVisible(int id, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        Throwable last = null;
        while (System.currentTimeMillis() < end) {
            try {
                onView(withId(id)).check(matches(isDisplayed()));
                return;
            } catch (Throwable t) {
                last = t;
                Thread.sleep(500);
            }
        }
        throw new AssertionError("Timed out waiting for view " + id, last);
    }

    private void waitForText(String text, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        Throwable last = null;
        while (System.currentTimeMillis() < end) {
            try {
                onView(withText(text)).check(matches(isDisplayed()));
                return;
            } catch (Throwable t) {
                last = t;
                Thread.sleep(500);
            }
        }
        throw new AssertionError("Timed out waiting for text " + text, last);
    }

    private void shell(String command) throws Exception {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
        try (FileInputStream in = new FileInputStream(pfd.getFileDescriptor())) {
            byte[] b = new byte[1024];
            while (in.read(b) != -1) {}
        }
        pfd.close();
    }

    @Test public void register_send_text_send_file_and_keyboard() throws Exception {
        InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSharedPreferences("dual_phone_transfer", 0).edit().clear().commit();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            waitForVisible(MainActivity.ID_EMAIL, 10000);
            String email = "nativeqa" + System.currentTimeMillis() + "@example.com";
            onView(withId(MainActivity.ID_EMAIL)).perform(typeText(email), closeSoftKeyboard());
            onView(withId(MainActivity.ID_PASSWORD)).perform(typeText("QaPass1234"), closeSoftKeyboard());
            onView(withId(MainActivity.ID_REGISTER)).perform(click());

            waitForVisible(MainActivity.ID_INPUT, 30000);
            onView(withId(MainActivity.ID_INPUT)).perform(replaceText("native-qa-message"), closeSoftKeyboard());
            onView(withId(MainActivity.ID_SEND)).perform(click());
            waitForText("native-qa-message", 20000);

            scenario.onActivity(a -> a.sendQaFileForTest("qa-file.txt", "dual-phone-file".getBytes(StandardCharsets.UTF_8)));
            waitForText("📎  qa-file.txt", 30000);
            shell("screencap -p /sdcard/native-02-chat.png");

            scenario.onActivity(MainActivity::showKeyboardForTest);
            Thread.sleep(1000);
            scenario.onActivity(a -> assertTrue("Composer is clipped by keyboard", a.isComposerAboveVisibleFrameForTest()));
            shell("screencap -p /sdcard/native-03-keyboard.png");
        }
    }
}
