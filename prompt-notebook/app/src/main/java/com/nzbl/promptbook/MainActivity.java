package com.nzbl.promptbook;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String PREFS = "prompt_book_prefs";
    private static final String KEY_DATA = "prompts_json_v1";
    private static final int REQ_EXPORT = 201;
    private static final int REQ_IMPORT = 202;

    private final List<PromptItem> items = new ArrayList<>();
    private LinearLayout root;
    private LinearLayout listContainer;
    private EditText searchInput;
    private TextView countText;
    private String activeFilter = "全部";

    private final int BG = Color.rgb(246,247,244);
    private final int SURFACE = Color.WHITE;
    private final int TEXT = Color.rgb(26,29,26);
    private final int MUTED = Color.rgb(110,116,111);
    private final int ACCENT = Color.rgb(60,90,70);
    private final int ACCENT_SOFT = Color.rgb(231,238,233);
    private final int LINE = Color.rgb(228,232,228);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        loadItems();
        showHome();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable bg(int color, float radiusDp, Integer strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp((int) radiusDp));
        if (strokeColor != null) d.setStroke(dp(1), strokeColor);
        return d;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLineSpacing(0f, 1.1f);
        return t;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setMinHeight(dp(46));
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setTextColor(primary ? Color.WHITE : TEXT);
        b.setBackground(bg(primary ? ACCENT : SURFACE, 14, primary ? null : LINE));
        return b;
    }

    private EditText field(String hint, boolean multiline) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(153,158,154));
        e.setTextColor(TEXT);
        e.setTextSize(16);
        e.setBackground(bg(SURFACE, 14, LINE));
        e.setPadding(dp(14), dp(multiline ? 12 : 0), dp(14), dp(multiline ? 12 : 0));
        if (multiline) {
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setMinLines(6);
            e.setMaxLines(20);
        } else {
            e.setSingleLine(true);
            e.setMinHeight(dp(48));
        }
        return e;
    }

    private void showHome() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(16), dp(18), dp(10));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("提示词本", 26, TEXT, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button more = button("⋯", false);
        titleRow.addView(more, new LinearLayout.LayoutParams(dp(52), dp(46)));
        more.setOnClickListener(v -> showTopMenu(more));
        header.addView(titleRow);

        TextView sub = text("专门存提示词。离线、清楚、随时复制。", 13, MUTED, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(2);
        header.addView(sub, subLp);

        searchInput = field("搜索名称、分类、内容、备注", false);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        searchLp.topMargin = dp(14);
        header.addView(searchInput, searchLp);
        searchInput.addTextChangedListener(new SimpleWatcher(this::renderList));

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams frLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        frLp.topMargin = dp(10);
        header.addView(filterRow, frLp);
        addFilter(filterRow, "全部");
        addFilter(filterRow, "收藏");
        addFilter(filterRow, "最近");

        countText = text("", 12, MUTED, false);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countLp.topMargin = dp(10);
        header.addView(countText, countLp);

        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(14), dp(4), dp(14), dp(110));
        scroll.addView(listContainer, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button add = button("＋  新建提示词", true);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        addLp.setMargins(dp(18), dp(8), dp(18), dp(16));
        root.addView(add, addLp);
        add.setOnClickListener(v -> showEditor(null));

        setContentView(root);
        renderList();
    }

    private void addFilter(LinearLayout row, String label) {
        TextView chip = text(label, 14, activeFilter.equals(label) ? Color.WHITE : TEXT, false);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setBackground(bg(activeFilter.equals(label) ? ACCENT : SURFACE, 18, activeFilter.equals(label) ? null : LINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        lp.rightMargin = dp(8);
        row.addView(chip, lp);
        chip.setOnClickListener(v -> {
            activeFilter = label;
            showHome();
        });
    }

    private void renderList() {
        if (listContainer == null) return;
        listContainer.removeAllViews();
        String q = searchInput == null ? "" : searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);

        List<PromptItem> display = new ArrayList<>();
        for (PromptItem item : items) {
            if (activeFilter.equals("收藏") && !item.favorite) continue;
            String hay = (item.title + " " + item.category + " " + item.prompt + " " + item.note).toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !hay.contains(q)) continue;
            display.add(item);
        }
        Collections.sort(display, (a,b) -> Long.compare(b.updatedAt, a.updatedAt));
        if (activeFilter.equals("最近") && display.size() > 10) display = new ArrayList<>(display.subList(0,10));

        countText.setText(display.size() + " 条" + (activeFilter.equals("全部") ? "" : " · " + activeFilter));

        if (display.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(dp(18), dp(70), dp(18), dp(40));
            TextView e1 = text(q.isEmpty() ? "还没有提示词" : "没找到匹配内容", 18, TEXT, true);
            TextView e2 = text(q.isEmpty() ? "点下面“新建提示词”开始。" : "换个关键词试试。", 14, MUTED, false);
            LinearLayout.LayoutParams e2lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            e2lp.topMargin = dp(8);
            empty.addView(e1);
            empty.addView(e2, e2lp);
            listContainer.addView(empty);
            return;
        }

        for (PromptItem item : display) listContainer.addView(promptCard(item));
    }

    private View promptCard(PromptItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(14));
        card.setBackground(bg(SURFACE, 18, LINE));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(10);
        card.setLayoutParams(cardLp);
        card.setOnClickListener(v -> showEditor(item));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(item.title.isEmpty() ? "未命名提示词" : item.title, 18, TEXT, true);
        title.setMaxLines(1);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView star = text(item.favorite ? "★" : "☆", 24, item.favorite ? ACCENT : MUTED, false);
        star.setGravity(Gravity.CENTER);
        star.setPadding(dp(8), 0, dp(4), 0);
        top.addView(star, new LinearLayout.LayoutParams(dp(44), dp(44)));
        star.setOnClickListener(v -> {
            item.favorite = !item.favorite;
            item.updatedAt = System.currentTimeMillis();
            saveItems();
            renderList();
        });
        card.addView(top);

        if (!item.category.trim().isEmpty()) {
            TextView cat = text(item.category.trim(), 12, ACCENT, false);
            cat.setGravity(Gravity.CENTER);
            cat.setPadding(dp(10), 0, dp(10), 0);
            cat.setBackground(bg(ACCENT_SOFT, 12, null));
            LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26));
            catLp.topMargin = dp(4);
            card.addView(cat, catLp);
        }

        String preview = item.prompt.trim().replace("\n", " ");
        if (preview.length() > 140) preview = preview.substring(0,140) + "…";
        TextView body = text(preview.isEmpty() ? "还没有填写提示词内容" : preview, 14, preview.isEmpty() ? MUTED : TEXT, false);
        body.setMaxLines(4);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = dp(10);
        card.addView(body, bodyLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(12);
        card.addView(actions, actionsLp);

        TextView edited = text("修改于 " + formatTime(item.updatedAt), 12, MUTED, false);
        actions.addView(edited, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button copy = button("复制", false);
        actions.addView(copy, new LinearLayout.LayoutParams(dp(76), dp(42)));
        copy.setOnClickListener(v -> copyPrompt(item.prompt));
        return card;
    }

    private void showEditor(PromptItem existing) {
        boolean isNew = existing == null;
        PromptItem item = isNew ? new PromptItem() : existing;
        if (isNew) {
            item.id = UUID.randomUUID().toString();
            item.createdAt = System.currentTimeMillis();
            item.updatedAt = item.createdAt;
        }

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(12), dp(14), dp(8));
        Button back = button("‹", false);
        bar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(46)));
        TextView title = text(isNew ? "新建提示词" : "编辑提示词", 20, TEXT, true);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(46), 1f));
        Button menu = button("⋯", false);
        bar.addView(menu, new LinearLayout.LayoutParams(dp(52), dp(46)));
        page.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(6), dp(18), dp(28));

        form.addView(label("名称"));
        EditText titleInput = field("给这条提示词起个好找的名字", false);
        titleInput.setText(item.title);
        form.addView(titleInput, gapLp(dp(50), 6));

        form.addView(label("分类"), gapTop(12));
        EditText categoryInput = field("例如：Claude / 生图 / 写作 / 代码", false);
        categoryInput.setText(item.category);
        form.addView(categoryInput, gapLp(dp(50), 6));

        form.addView(label("完整提示词"), gapTop(12));
        EditText promptInput = field("把完整提示词放这里。长提示词也可以。", true);
        promptInput.setText(item.prompt);
        promptInput.setMinHeight(dp(260));
        form.addView(promptInput, gapLp(ViewGroup.LayoutParams.WRAP_CONTENT, 6));

        form.addView(label("备注"), gapTop(12));
        EditText noteInput = field("用途、版本、使用时机、注意事项……", true);
        noteInput.setText(item.note);
        noteInput.setMinHeight(dp(120));
        form.addView(noteInput, gapLp(ViewGroup.LayoutParams.WRAP_CONTENT, 6));

        LinearLayout favRow = new LinearLayout(this);
        favRow.setGravity(Gravity.CENTER_VERTICAL);
        favRow.setPadding(dp(4), dp(6), 0, dp(6));
        TextView favLabel = text("收藏这条提示词", 15, TEXT, false);
        favRow.addView(favLabel, new LinearLayout.LayoutParams(0, dp(46), 1f));
        Button fav = button(item.favorite ? "★ 已收藏" : "☆ 收藏", false);
        favRow.addView(fav, new LinearLayout.LayoutParams(dp(112), dp(44)));
        form.addView(favRow, gapTop(10));

        scroll.addView(form);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(dp(18), dp(10), dp(18), dp(16));
        Button copy = button("复制", false);
        bottom.addView(copy, new LinearLayout.LayoutParams(dp(94), dp(52)));
        Button save = button("保存", true);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        saveLp.leftMargin = dp(10);
        bottom.addView(save, saveLp);
        page.addView(bottom);

        setContentView(page);

        final boolean[] favorite = {item.favorite};
        fav.setOnClickListener(v -> {
            favorite[0] = !favorite[0];
            fav.setText(favorite[0] ? "★ 已收藏" : "☆ 收藏");
        });

        Runnable persist = () -> {
            item.title = titleInput.getText().toString().trim();
            item.category = categoryInput.getText().toString().trim();
            item.prompt = promptInput.getText().toString();
            item.note = noteInput.getText().toString();
            item.favorite = favorite[0];
            item.updatedAt = System.currentTimeMillis();
            if (isNew && !items.contains(item)) items.add(item);
            saveItems();
        };

        save.setOnClickListener(v -> {
            persist.run();
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
            hideKeyboard();
            showHome();
        });

        copy.setOnClickListener(v -> copyPrompt(promptInput.getText().toString()));

        back.setOnClickListener(v -> {
            if (hasAnyContent(titleInput, categoryInput, promptInput, noteInput) || !isNew) persist.run();
            hideKeyboard();
            showHome();
        });

        menu.setOnClickListener(v -> showEditorMenu(menu, item, isNew, persist));
    }

    private boolean hasAnyContent(EditText... fields) {
        for (EditText e : fields) if (!e.getText().toString().trim().isEmpty()) return true;
        return false;
    }

    private TextView label(String s) {
        TextView t = text(s, 13, MUTED, true);
        t.setPadding(dp(2), 0, 0, 0);
        return t;
    }

    private LinearLayout.LayoutParams gapTop(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(top);
        return lp;
    }

    private LinearLayout.LayoutParams gapLp(int height, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        lp.topMargin = dp(top);
        return lp;
    }

    private void showEditorMenu(View anchor, PromptItem item, boolean isNew, Runnable persist) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("复制一份");
        if (!isNew) popup.getMenu().add("删除");
        popup.setOnMenuItemClickListener(mi -> {
            if (mi.getTitle().toString().equals("复制一份")) {
                persist.run();
                PromptItem copy = item.copy();
                copy.id = UUID.randomUUID().toString();
                copy.title = (copy.title.isEmpty() ? "未命名提示词" : copy.title) + " · 副本";
                copy.createdAt = System.currentTimeMillis();
                copy.updatedAt = copy.createdAt;
                items.add(copy);
                saveItems();
                showEditor(copy);
                return true;
            }
            if (mi.getTitle().toString().equals("删除")) {
                confirmDelete(item);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void confirmDelete(PromptItem item) {
        new AlertDialog.Builder(this)
                .setTitle("删除这条提示词？")
                .setMessage(item.title.isEmpty() ? "删除后无法撤销。" : "“" + item.title + "”删除后无法撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d,w) -> {
                    items.remove(item);
                    saveItems();
                    showHome();
                })
                .show();
    }

    private void showTopMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("导出备份");
        popup.getMenu().add("导入备份");
        popup.setOnMenuItemClickListener(mi -> {
            String t = mi.getTitle().toString();
            if (t.equals("导出备份")) { exportBackup(); return true; }
            if (t.equals("导入备份")) { importBackup(); return true; }
            return false;
        });
        popup.show();
    }

    private void copyPrompt(String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("提示词", value == null ? "" : value));
        Toast.makeText(this, "提示词已复制", Toast.LENGTH_SHORT).show();
    }

    private void exportBackup() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "提示词本备份-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(new Date()) + ".json");
        startActivityForResult(i, REQ_EXPORT);
    }

    private void importBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("application/json");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_EXPORT) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new Exception("无法写入文件");
                    out.write(toJson().toString(2).getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "备份已导出", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_IMPORT) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line).append('\n');
                }
                JSONArray arr = new JSONArray(sb.toString());
                List<PromptItem> imported = parseItems(arr);
                new AlertDialog.Builder(this)
                        .setTitle("导入 " + imported.size() + " 条提示词？")
                        .setMessage("导入会替换当前全部内容。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("导入", (d,w) -> {
                            items.clear();
                            items.addAll(imported);
                            saveItems();
                            showHome();
                            Toast.makeText(this, "导入完成", Toast.LENGTH_SHORT).show();
                        }).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "操作失败：文件格式不正确", Toast.LENGTH_LONG).show();
        }
    }

    private void hideKeyboard() {
        View v = getCurrentFocus();
        if (v != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    private String formatTime(long time) {
        long delta = System.currentTimeMillis() - time;
        if (delta < 60_000) return "刚刚";
        if (delta < 3_600_000) return (delta / 60_000) + " 分钟前";
        if (delta < 86_400_000) return (delta / 3_600_000) + " 小时前";
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(time));
    }

    private void loadItems() {
        items.clear();
        try {
            String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_DATA, "[]");
            items.addAll(parseItems(new JSONArray(raw)));
        } catch (Exception ignored) {}
    }

    private void saveItems() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_DATA, toJson().toString()).apply();
    }

    private JSONArray toJson() {
        JSONArray arr = new JSONArray();
        for (PromptItem p : items) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", p.id);
                o.put("title", p.title);
                o.put("category", p.category);
                o.put("prompt", p.prompt);
                o.put("note", p.note);
                o.put("favorite", p.favorite);
                o.put("createdAt", p.createdAt);
                o.put("updatedAt", p.updatedAt);
                arr.put(o);
            } catch (Exception ignored) {}
        }
        return arr;
    }

    private List<PromptItem> parseItems(JSONArray arr) {
        List<PromptItem> out = new ArrayList<>();
        for (int i=0; i<arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            PromptItem p = new PromptItem();
            p.id = o.optString("id", UUID.randomUUID().toString());
            p.title = o.optString("title", "");
            p.category = o.optString("category", "");
            p.prompt = o.optString("prompt", "");
            p.note = o.optString("note", "");
            p.favorite = o.optBoolean("favorite", false);
            p.createdAt = o.optLong("createdAt", System.currentTimeMillis());
            p.updatedAt = o.optLong("updatedAt", p.createdAt);
            out.add(p);
        }
        return out;
    }

    private static class PromptItem {
        String id = "";
        String title = "";
        String category = "";
        String prompt = "";
        String note = "";
        boolean favorite = false;
        long createdAt = System.currentTimeMillis();
        long updatedAt = createdAt;

        PromptItem copy() {
            PromptItem c = new PromptItem();
            c.id = id;
            c.title = title;
            c.category = category;
            c.prompt = prompt;
            c.note = note;
            c.favorite = favorite;
            c.createdAt = createdAt;
            c.updatedAt = updatedAt;
            return c;
        }
    }

    private static class SimpleWatcher implements TextWatcher {
        private final Runnable onChange;
        SimpleWatcher(Runnable onChange) { this.onChange = onChange; }
        public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
        public void onTextChanged(CharSequence s, int st, int before, int count) { onChange.run(); }
        public void afterTextChanged(Editable s) {}
    }
}
