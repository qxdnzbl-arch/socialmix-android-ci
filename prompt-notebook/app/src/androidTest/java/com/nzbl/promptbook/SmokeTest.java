package com.nzbl.promptbook;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

public class SmokeTest extends InstrumentationTestCase {
    public void testCreateSaveAndPersist() throws Exception {
        Context target = getInstrumentation().getTargetContext();
        target.getSharedPreferences("prompt_book_prefs", Context.MODE_PRIVATE).edit().clear().commit();

        Intent intent = new Intent(target, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();

        assertNotNull(activity);
        assertNotNull(findByText(activity.getWindow().getDecorView(), "提示词本"));

        View add = findByText(activity.getWindow().getDecorView(), "＋  新建提示词");
        assertNotNull(add);
        getInstrumentation().runOnMainSync(add::performClick);
        getInstrumentation().waitForIdleSync();

        assertNotNull(findByText(activity.getWindow().getDecorView(), "新建提示词"));
        EditText first = findFirstEditText(activity.getWindow().getDecorView());
        assertNotNull(first);
        getInstrumentation().runOnMainSync(() -> first.setText("PromptTest"));

        View save = findByText(activity.getWindow().getDecorView(), "保存");
        assertNotNull(save);
        getInstrumentation().runOnMainSync(save::performClick);
        getInstrumentation().waitForIdleSync();

        assertNotNull(findByText(activity.getWindow().getDecorView(), "PromptTest"));
        String raw = target.getSharedPreferences("prompt_book_prefs", Context.MODE_PRIVATE)
                .getString("prompts_json_v1", "");
        assertTrue(raw.contains("PromptTest"));

        activity.finish();
    }

    private View findByText(View root, String expected) {
        if (root instanceof TextView) {
            CharSequence text = ((TextView) root).getText();
            if (text != null && expected.contentEquals(text)) return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = findByText(g.getChildAt(i), expected);
                if (found != null) return found;
            }
        }
        return null;
    }

    private EditText findFirstEditText(View root) {
        if (root instanceof EditText) return (EditText) root;
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                EditText found = findFirstEditText(g.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
