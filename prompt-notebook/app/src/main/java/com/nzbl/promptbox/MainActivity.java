package com.nzbl.promptbox;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String PREFS = "prompt_box_prefs";
    private static final String KEY_DATA = "prompts_json";
    private static final int REQ_EXPORT = 201;
    private static final int REQ_IMPORT = 202;

    private final ArrayList<PromptItem> items = new ArrayList<>();
    private LinearLayout listContainer;
    private EditText searchInput;
    private TextView countText;
    private TextView allFilter;
    private TextView favoriteFilter;
    private boolean favoritesOnly = false;

    private final int BG = Color.rgb(247,245,242);
    private final int CARD = Color.WHITE;
    private final int TEXT = Color.rgb(27,27,27);
    private final int MUTED = Color.rgb(112,108,102);
    private final int LINE = Color.rgb(231,227,221);
    private final int ACCENT = Color.rgb(32,31,29);
    private final int SOFT = Color.rgb(239,235,229);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        loadItems();
        setContentView(buildRoot());
        renderList();
    }

    private View buildRoot() {
        LinearLayout root = vertical();
        root.setBackgroundColor(BG);
        root.setPadding(dp(18), dp(12), dp(18), dp(16));

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleBox = vertical();
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        TextView title = text("提示词盒", 26, TEXT, Typeface.BOLD);
        TextView sub = text("专门收好反复要用的提示词", 13, MUTED, Typeface.NORMAL);
        sub.setPadding(0, dp(3), 0, 0);
        titleBox.addView(title);
        titleBox.addView(sub);
        header.addView(titleBox, titleLp);

        TextView backup = pill("备份", false);
        backup.setContentDescription("备份按钮");
        backup.setOnClickListener(v -> showBackupMenu());
        header.addView(backup, new LinearLayout.LayoutParams(dp(68), dp(42)));
        root.addView(header);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("搜索标题、分类、标签或提示词");
        searchInput.setHintTextColor(Color.rgb(155,151,145));
        searchInput.setTextColor(TEXT);
        searchInput.setTextSize(16);
        searchInput.setPadding(dp(14), 0, dp(14), 0);
        searchInput.setBackground(roundRect(CARD, 16, LINE, 1));
        searchInput.setContentDescription("搜索框");
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        searchLp.topMargin = dp(16);
        root.addView(searchInput, searchLp);
        searchInput.addTextChangedListener(simpleWatcher(this::renderList));

        LinearLayout filterRow = horizontal();
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        filterRow.setPadding(0, dp(13), 0, dp(10));
        allFilter = pill("全部", true);
        favoriteFilter = pill("收藏", false);
        allFilter.setOnClickListener(v -> { favoritesOnly = false; updateFilters(); renderList(); });
        favoriteFilter.setOnClickListener(v -> { favoritesOnly = true; updateFilters(); renderList(); });
        filterRow.addView(allFilter, new LinearLayout.LayoutParams(dp(70), dp(38)));
        LinearLayout.LayoutParams favLp = new LinearLayout.LayoutParams(dp(70), dp(38));
        favLp.leftMargin = dp(8);
        filterRow.addView(favoriteFilter, favLp);
        View spacer = new View(this);
        filterRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        countText = text("", 13, MUTED, Typeface.NORMAL);
        filterRow.addView(countText);
        root.addView(filterRow);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        listContainer = vertical();
        scroll.addView(listContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, scrollLp);

        Button add = new Button(this);
        add.setText("＋ 新建提示词");
        add.setTextSize(16);
        add.setTextColor(Color.WHITE);
        add.setAllCaps(false);
        add.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add.setBackground(roundRect(ACCENT, 16, ACCENT, 0));
        add.setContentDescription("新建提示词");
        add.setOnClickListener(v -> showEditor(null));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        addLp.topMargin = dp(12);
        root.addView(add, addLp);
        return root;
    }

    private void updateFilters() {
        allFilter.setBackground(roundRect(favoritesOnly ? CARD : ACCENT, 18, favoritesOnly ? LINE : ACCENT, 1));
        allFilter.setTextColor(favoritesOnly ? TEXT : Color.WHITE);
        favoriteFilter.setBackground(roundRect(favoritesOnly ? ACCENT : CARD, 18, favoritesOnly ? ACCENT : LINE, 1));
        favoriteFilter.setTextColor(favoritesOnly ? Color.WHITE : TEXT);
    }

    private void renderList() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        String q = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<PromptItem> visible = new ArrayList<>();
        for (PromptItem item : items) {
            if (favoritesOnly && !item.favorite) continue;
            String hay = (item.title + " " + item.category + " " + item.tags + " " + item.content).toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !hay.contains(q)) continue;
            visible.add(item);
        }
        if (countText != null) countText.setText(visible.size() + " 条");

        if (visible.isEmpty()) {
            LinearLayout empty = vertical();
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(72), dp(16), dp(72));
            TextView big = text(favoritesOnly ? "还没有收藏" : (q.isEmpty() ? "这里还没有提示词" : "没有搜到匹配内容"), 18, TEXT, Typeface.BOLD);
            TextView small = text(q.isEmpty() ? "把常用提示词放进来，需要时一搜就有。" : "换个关键词试试。", 14, MUTED, Typeface.NORMAL);
            small.setGravity(Gravity.CENTER);
            small.setPadding(0, dp(8), 0, 0);
            empty.addView(big);
            empty.addView(small);
            listContainer.addView(empty);
            return;
        }

        for (PromptItem item : visible) listContainer.addView(buildCard(item));
    }

    private View buildCard(PromptItem item) {
        LinearLayout card = vertical();
        card.setPadding(dp(15), dp(14), dp(15), dp(13));
        card.setBackground(roundRect(CARD, 18, LINE, 1));
        card.setOnClickListener(v -> showEditor(item));

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(item.title.isEmpty() ? "未命名提示词" : item.title, 18, TEXT, Typeface.BOLD);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView star = text(item.favorite ? "★" : "☆", 24, item.favorite ? Color.rgb(190,142,44) : MUTED, Typeface.NORMAL);
        star.setGravity(Gravity.CENTER);
        star.setContentDescription(item.favorite ? "取消收藏" : "收藏");
        star.setOnClickListener(v -> {
            item.favorite = !item.favorite;
            item.updatedAt = System.currentTimeMillis();
            saveItems();
            renderList();
        });
        top.addView(star, new LinearLayout.LayoutParams(dp(44), dp(44)));
        card.addView(top);

        LinearLayout meta = horizontal();
        if (!item.category.isEmpty()) {
            TextView cat = text(item.category, 12, TEXT, Typeface.NORMAL);
            cat.setGravity(Gravity.CENTER);
            cat.setPadding(dp(9), 0, dp(9), 0);
            cat.setBackground(roundRect(SOFT, 12, SOFT, 0));
            meta.addView(cat, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(27)));
        }
        if (!item.tags.isEmpty()) {
            TextView tags = text("  " + item.tags.replace(",", " · "), 12, MUTED, Typeface.NORMAL);
            tags.setMaxLines(1);
            tags.setEllipsize(TextUtils.TruncateAt.END);
            meta.addView(tags, new LinearLayout.LayoutParams(0, dp(27), 1f));
        }
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaLp.topMargin = dp(5);
        card.addView(meta, metaLp);

        TextView preview = text(item.content, 14, Color.rgb(74,71,67), Typeface.NORMAL);
        preview.setMaxLines(3);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        preview.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        previewLp.topMargin = dp(9);
        card.addView(preview, previewLp);

        LinearLayout actions = horizontal();
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView time = text("更新 " + formatTime(item.updatedAt), 11, MUTED, Typeface.NORMAL);
        actions.addView(time, new LinearLayout.LayoutParams(0, dp(40), 1f));

        TextView copy = pill("复制", false);
        copy.setContentDescription("复制提示词");
        copy.setOnClickListener(v -> {
            copyToClipboard(item.content);
            Toast.makeText(this, "已复制提示词", Toast.LENGTH_SHORT).show();
        });
        actions.addView(copy, new LinearLayout.LayoutParams(dp(68), dp(38)));
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        actionsLp.topMargin = dp(6);
        card.addView(actions, actionsLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);
        return card;
    }

    private void showEditor(PromptItem existing) {
        final PromptItem draft = existing == null ? new PromptItem() : existing.copy();

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(buildEditorContent(dialog, draft, existing));
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(roundRect(BG, 22, BG, 0));
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.setOnShowListener(d -> {
            Window ww = dialog.getWindow();
            if (ww != null) {
                ww.setLayout((int)(getResources().getDisplayMetrics().widthPixels * 0.94f),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private View buildEditorContent(Dialog dialog, PromptItem draft, PromptItem existing) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = vertical();
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        scroll.addView(box);

        TextView heading = text(existing == null ? "新建提示词" : "编辑提示词", 22, TEXT, Typeface.BOLD);
        box.addView(heading);

        EditText title = editorField("标题", false, "标题输入框");
        title.setText(draft.title);
        box.addView(label("名称"));
        box.addView(title, fieldLp(dp(50)));

        EditText category = editorField("分类（例如：软件 / 写作 / 图片）", false, "分类输入框");
        category.setText(draft.category);
        box.addView(label("分类"));
        box.addView(category, fieldLp(dp(50)));

        EditText tags = editorField("标签（用逗号分隔）", false, "标签输入框");
        tags.setText(draft.tags);
        box.addView(label("标签"));
        box.addView(tags, fieldLp(dp(50)));

        EditText content = editorField("把完整提示词放在这里", true, "提示词输入框");
        content.setText(draft.content);
        content.setGravity(Gravity.TOP);
        box.addView(label("完整提示词"));
        box.addView(content, fieldLp(dp(220)));

        LinearLayout actions = horizontal();
        actions.setPadding(0, dp(14), 0, 0);

        if (existing != null) {
            Button delete = actionButton("删除", false);
            delete.setTextColor(Color.rgb(178,57,48));
            delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("删除这条提示词？")
                    .setMessage("删除后无法恢复。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (dd, which) -> {
                        items.remove(existing);
                        saveItems();
                        renderList();
                        dialog.dismiss();
                    }).show());
            actions.addView(delete, new LinearLayout.LayoutParams(0, dp(48), 1f));
            View gap1 = new View(this);
            actions.addView(gap1, new LinearLayout.LayoutParams(dp(8), 1));
        }

        Button cancel = actionButton("取消", false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1f));

        View gap2 = new View(this);
        actions.addView(gap2, new LinearLayout.LayoutParams(dp(8), 1));

        Button save = actionButton("保存", true);
        save.setContentDescription("保存提示词");
        save.setOnClickListener(v -> {
            draft.title = title.getText().toString().trim();
            draft.category = category.getText().toString().trim();
            draft.tags = tags.getText().toString().trim();
            draft.content = content.getText().toString().trim();
            if (draft.content.isEmpty()) {
                Toast.makeText(this, "提示词内容还没填", Toast.LENGTH_SHORT).show();
                content.requestFocus();
                return;
            }
            if (draft.title.isEmpty()) draft.title = autoTitle(draft.content);
            draft.updatedAt = System.currentTimeMillis();
            if (existing == null) {
                draft.id = UUID.randomUUID().toString();
                draft.createdAt = draft.updatedAt;
                items.add(0, draft);
            } else {
                int index = items.indexOf(existing);
                if (index >= 0) items.set(index, draft);
            }
            saveItems();
            renderList();
            dialog.dismiss();
        });
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(48), 1f));

        box.addView(actions);
        return scroll;
    }

    private void showBackupMenu() {
        String[] choices = {"导出备份", "导入备份"};
        new AlertDialog.Builder(this)
                .setTitle("提示词备份")
                .setItems(choices, (d, which) -> {
                    if (which == 0) exportBackup();
                    else importBackup();
                })
                .show();
    }

    private void exportBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "提示词盒备份.json");
        startActivityForResult(intent, REQ_EXPORT);
    }

    private void importBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/json");
        startActivityForResult(intent, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_EXPORT) {
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os != null) os.write(toJson().toString(2).getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "备份已导出", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_IMPORT) {
                String text = readAll(getContentResolver().openInputStream(uri));
                JSONArray arr = new JSONArray(text);
                new AlertDialog.Builder(this)
                        .setTitle("导入这个备份？")
                        .setMessage("当前内容会被备份文件替换。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("导入", (d, w) -> {
                            try {
                                parseJson(arr);
                                saveItems();
                                renderList();
                                Toast.makeText(this, "备份已导入", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Toast.makeText(this, "导入失败", Toast.LENGTH_SHORT).show();
                            }
                        }).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "操作失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadItems() {
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_DATA, "[]");
        try { parseJson(new JSONArray(raw)); }
        catch (Exception e) { items.clear(); }
    }

    private void parseJson(JSONArray arr) throws Exception {
        items.clear();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            PromptItem p = new PromptItem();
            p.id = o.optString("id", UUID.randomUUID().toString());
            p.title = o.optString("title", "");
            p.category = o.optString("category", "");
            p.tags = o.optString("tags", "");
            p.content = o.optString("content", "");
            p.favorite = o.optBoolean("favorite", false);
            p.createdAt = o.optLong("createdAt", System.currentTimeMillis());
            p.updatedAt = o.optLong("updatedAt", p.createdAt);
            items.add(p);
        }
    }

    private JSONArray toJson() throws Exception {
        JSONArray arr = new JSONArray();
        for (PromptItem p : items) {
            JSONObject o = new JSONObject();
            o.put("id", p.id);
            o.put("title", p.title);
            o.put("category", p.category);
            o.put("tags", p.tags);
            o.put("content", p.content);
            o.put("favorite", p.favorite);
            o.put("createdAt", p.createdAt);
            o.put("updatedAt", p.updatedAt);
            arr.put(o);
        }
        return arr;
    }

    private void saveItems() {
        try {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_DATA, toJson().toString())
                    .apply();
        } catch (Exception ignored) {}
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("提示词", text));
    }

    private String autoTitle(String content) {
        String one = content.replace('\n', ' ').trim();
        if (one.length() > 18) one = one.substring(0, 18) + "…";
        return one.isEmpty() ? "未命名提示词" : one;
    }

    private String formatTime(long time) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(time));
    }

    private TextWatcher simpleWatcher(Runnable runnable) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) { runnable.run(); }
            public void afterTextChanged(Editable s) {}
        };
    }

    private LinearLayout vertical() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout horizontal() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        return v;
    }

    private TextView text(String s, int sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, style);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private TextView pill(String label, boolean selected) {
        TextView t = text(label, 14, selected ? Color.WHITE : TEXT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setBackground(roundRect(selected ? ACCENT : CARD, 18, selected ? ACCENT : LINE, 1));
        return t;
    }

    private TextView label(String label) {
        TextView t = text(label, 12, MUTED, Typeface.BOLD);
        t.setPadding(dp(2), dp(13), 0, dp(6));
        return t;
    }

    private EditText editorField(String hint, boolean multiline, String desc) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(160,156,150));
        e.setTextColor(TEXT);
        e.setTextSize(16);
        e.setPadding(dp(13), multiline ? dp(12) : 0, dp(13), multiline ? dp(12) : 0);
        e.setBackground(roundRect(CARD, 14, LINE, 1));
        e.setContentDescription(desc);
        if (!multiline) e.setSingleLine(true);
        return e;
    }

    private LinearLayout.LayoutParams fieldLp(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    }

    private Button actionButton(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.WHITE : TEXT);
        b.setBackground(roundRect(primary ? ACCENT : CARD, 14, primary ? ACCENT : LINE, 1));
        return b;
    }

    private GradientDrawable roundRect(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    static class PromptItem {
        String id = "";
        String title = "";
        String category = "";
        String tags = "";
        String content = "";
        boolean favorite = false;
        long createdAt = System.currentTimeMillis();
        long updatedAt = createdAt;

        PromptItem copy() {
            PromptItem p = new PromptItem();
            p.id = id;
            p.title = title;
            p.category = category;
            p.tags = tags;
            p.content = content;
            p.favorite = favorite;
            p.createdAt = createdAt;
            p.updatedAt = updatedAt;
            return p;
        }
    }
}
