package com.nzbl.pocketclean;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

public class MainActivity extends Activity {
    private static final int PICK_IMAGES=101, EXPORT_BACKUP=102, IMPORT_BACKUP=103;
    private final int BG=Color.rgb(250,250,247), CARD=Color.WHITE, TEXT=Color.rgb(30,30,30), MUTED=Color.rgb(116,116,116), LINE=Color.rgb(231,231,226);
    private Store store;
    private LinearLayout page;
    private String filterCategory="";
    private Store.Note editing;
    private final ArrayList<String> pendingPhotos=new ArrayList<>();

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        store=new Store(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        showHome();
    }

    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
    private TextView tv(String s,int sp,int color){ TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private GradientDrawable bg(int color,int radius){ GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g; }
    private GradientDrawable strokeBg(int color,int radius,int strokeColor){ GradientDrawable g=bg(color,radius); g.setStroke(dp(1),strokeColor); return g; }
    private LinearLayout row(){ LinearLayout x=new LinearLayout(this); x.setOrientation(LinearLayout.HORIZONTAL); x.setGravity(Gravity.CENTER_VERTICAL); return x; }
    private Button btn(String s){
        Button b=new Button(this); b.setText(s); b.setTextSize(14); b.setTextColor(TEXT); b.setAllCaps(false); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(14),dp(9),dp(14),dp(9)); b.setBackground(strokeBg(Color.WHITE,14,LINE)); return b;
    }
    private Button darkBtn(String s){ Button b=btn(s); b.setTextColor(Color.WHITE); b.setBackground(bg(Color.rgb(35,35,35),14)); return b; }
    private Space space(int w){ Space s=new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(w,1)); return s; }
    private View divider(){ View v=new View(this); v.setBackgroundColor(LINE); v.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(1))); return v; }

    private void shell(){
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true); sv.setBackgroundColor(BG);
        page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(18),dp(18),dp(18),dp(30)); page.setBackgroundColor(BG);
        sv.addView(page,new ScrollView.LayoutParams(-1,-2)); setContentView(sv);
    }

    private void topTitle(String title,String sub, boolean back){
        LinearLayout r=row();
        if(back){
            Button b=btn("返回"); b.setContentDescription("返回"); b.setOnClickListener(v->showHome()); r.addView(b);
            r.addView(space(dp(10)));
        }
        LinearLayout texts=new LinearLayout(this); texts.setOrientation(LinearLayout.VERTICAL);
        TextView t=tv(title,26,TEXT); t.setTypeface(null,1); texts.addView(t);
        if(sub!=null && !sub.isEmpty()){ TextView s=tv(sub,13,MUTED); s.setPadding(0,dp(3),0,0); texts.addView(s); }
        r.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        page.addView(r);
    }

    private void showHome(){
        editing=null; pendingPhotos.clear(); shell();
        LinearLayout header=row();
        LinearLayout tt=new LinearLayout(this); tt.setOrientation(LinearLayout.VERTICAL);
        TextView title=tv("随手存",28,TEXT); title.setTypeface(null,1); tt.addView(title);
        TextView sub=tv("把需要的，先收好。",13,MUTED); sub.setPadding(0,dp(4),0,0); tt.addView(sub);
        header.addView(tt,new LinearLayout.LayoutParams(0,-2,1));
        Button search=btn("搜索"); search.setOnClickListener(v->showSearch()); header.addView(search);
        header.addView(space(dp(8)));
        Button more=btn("更多"); more.setOnClickListener(v->showMore()); header.addView(more);
        page.addView(header);
        page.addView(space(dp(18)));

        HorizontalScrollView hsv=new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout chips=row();
        Button all=chip("全部",filterCategory.isEmpty()); all.setOnClickListener(v->{filterCategory="";showHome();}); chips.addView(all);
        chips.addView(space(dp(8)));
        Button fav=chip("★ 常用","__fav".equals(filterCategory)); fav.setOnClickListener(v->{filterCategory="__fav";showHome();}); chips.addView(fav);
        for(Store.Category c:store.categories){
            chips.addView(space(dp(8)));
            Button cb=chip(c.name,c.id.equals(filterCategory)); cb.setOnClickListener(v->{filterCategory=c.id;showHome();}); chips.addView(cb);
        }
        hsv.addView(chips); page.addView(hsv);
        page.addView(space(dp(18)));

        List<Store.Note> notes=store.activeNotes();
        ArrayList<Store.Note> visible=new ArrayList<>();
        for(Store.Note n:notes){
            if("__fav".equals(filterCategory) && !n.favorite) continue;
            if(!filterCategory.isEmpty() && !"__fav".equals(filterCategory) && !filterCategory.equals(n.categoryId)) continue;
            visible.add(n);
        }
        if(visible.isEmpty()){
            LinearLayout empty=new LinearLayout(this); empty.setOrientation(LinearLayout.VERTICAL); empty.setGravity(Gravity.CENTER); empty.setPadding(0,dp(58),0,dp(48));
            TextView e=tv(filterCategory.isEmpty()?"还没有记录":"这里还没有记录",16,MUTED); empty.addView(e);
            page.addView(empty);
        } else {
            for(Store.Note n:visible){ page.addView(noteCard(n)); page.addView(space(dp(10))); }
        }
        LinearLayout actions=row(); actions.setPadding(0,dp(8),0,0);
        Button add=darkBtn("＋  记一条"); add.setContentDescription("新记录"); add.setOnClickListener(v->showEditor(null)); actions.addView(add,new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(space(dp(10)));
        Button cat=btn("＋ 新建分类"); cat.setOnClickListener(v->categoryNameDialog(null)); actions.addView(cat,new LinearLayout.LayoutParams(0,-2,1));
        page.addView(actions);
    }

    private Button chip(String s, boolean on){
        Button b=btn(s); if(on){ b.setTextColor(Color.WHITE); b.setBackground(bg(Color.rgb(50,50,50),18)); } else b.setBackground(strokeBg(BG,18,LINE));
        return b;
    }

    private View noteCard(Store.Note n){
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(14),dp(16),dp(14)); card.setBackground(strokeBg(CARD,18,LINE));
        String title=n.title.trim(); if(title.isEmpty()) title=n.text.trim().isEmpty()?(n.photos.isEmpty()?"未命名记录":"图片记录"):firstLine(n.text);
        TextView t=tv((n.favorite?"★  ":"")+title,17,TEXT); t.setTypeface(null,1); card.addView(t);
        if(!n.text.trim().isEmpty()){ TextView body=tv(n.text.trim(),14,MUTED); body.setMaxLines(3); body.setPadding(0,dp(8),0,0); card.addView(body); }
        LinearLayout meta=row(); meta.setPadding(0,dp(10),0,0);
        Store.Category c=store.findCategory(n.categoryId);
        String m=(c==null?"未分类":c.name)+(n.photos.isEmpty()?"":"  ·  "+n.photos.size()+" 张图")+"  ·  "+formatDate(n.updatedAt);
        meta.addView(tv(m,12,MUTED),new LinearLayout.LayoutParams(0,-2,1));
        card.addView(meta);
        card.setOnClickListener(v->showDetail(n));
        return card;
    }

    private String firstLine(String s){ String x=s.replace('\n',' ').trim(); return x.length()>28?x.substring(0,28)+"…":x; }
    private String formatDate(long t){ return new SimpleDateFormat("M月d日",Locale.CHINA).format(new Date(t)); }

    private void showEditor(Store.Note old){
        shell(); pendingPhotos.clear();
        editing=old;
        if(old!=null) pendingPhotos.addAll(old.photos);
        LinearLayout r=row();
        Button back=btn("取消"); back.setOnClickListener(v->showHome()); r.addView(back);
        TextView ttl=tv(old==null?"新记录":"编辑记录",22,TEXT); ttl.setGravity(Gravity.CENTER); ttl.setTypeface(null,1); r.addView(ttl,new LinearLayout.LayoutParams(0,-2,1));
        Button save=darkBtn("保存"); r.addView(save); page.addView(r); page.addView(space(dp(20)));

        EditText title=new EditText(this); title.setHint("给这条记录起个名字"); title.setText(old==null?"":old.title); title.setTextSize(20); title.setTextColor(TEXT); title.setHintTextColor(Color.rgb(165,165,165)); title.setSingleLine(true); title.setBackgroundColor(Color.TRANSPARENT); title.setPadding(0,dp(8),0,dp(10)); page.addView(title);
        page.addView(divider());
        EditText body=new EditText(this); body.setHint("写下想留住的内容…"); body.setText(old==null?"":old.text); body.setTextSize(17); body.setTextColor(TEXT); body.setHintTextColor(Color.rgb(165,165,165)); body.setGravity(Gravity.TOP); body.setMinLines(8); body.setBackgroundColor(Color.TRANSPARENT); body.setPadding(0,dp(14),0,dp(14)); page.addView(body,new LinearLayout.LayoutParams(-1,dp(260)));

        LinearLayout controls=row();
        Button cat=btn(categoryLabel(old==null?"":old.categoryId)); controls.addView(cat,new LinearLayout.LayoutParams(0,-2,1));
        controls.addView(space(dp(8)));
        Button photo=btn("＋ 添加图片"); photo.setOnClickListener(v->pickImages()); controls.addView(photo,new LinearLayout.LayoutParams(0,-2,1)); page.addView(controls);
        LinearLayout photoBox=new LinearLayout(this); photoBox.setOrientation(LinearLayout.VERTICAL); photoBox.setPadding(0,dp(12),0,0); page.addView(photoBox);
        renderPhotos(photoBox,true);

        final String[] selected={old==null?"":old.categoryId};
        cat.setOnClickListener(v->pickCategory(selected[0],id->{ selected[0]=id; cat.setText(categoryLabel(id)); }));
        save.setOnClickListener(v->{
            String ti=title.getText().toString().trim(), tx=body.getText().toString().trim();
            if(ti.isEmpty() && tx.isEmpty() && pendingPhotos.isEmpty()){ toast("写点文字或添加图片再保存"); return; }
            Store.Note n=old==null?new Store.Note():old;
            n.title=ti; n.text=tx; n.categoryId=selected[0]; n.photos.clear(); n.photos.addAll(pendingPhotos);
            store.upsert(n); toast("已保存"); showHome();
        });
    }

    private String categoryLabel(String id){ Store.Category c=store.findCategory(id); return "分类 · "+(c==null?"未分类":c.name)+" ▾"; }

    private interface CategoryPick { void chosen(String id); }
    private void pickCategory(String current, CategoryPick cb){
        ArrayList<String> names=new ArrayList<>(), ids=new ArrayList<>(); names.add("未分类"); ids.add("");
        for(Store.Category c:store.categories){ names.add(c.name); ids.add(c.id); }
        new AlertDialog.Builder(this).setTitle("放在哪个分类").setItems(names.toArray(new String[0]),(d,w)->cb.chosen(ids.get(w))).setNegativeButton("取消",null).show();
    }

    private void pickImages(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); i.addCategory(Intent.CATEGORY_OPENABLE);
        try{ startActivityForResult(i,PICK_IMAGES); }catch(Exception e){ toast("手机未提供图片选择器。"); }
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data); if(result!=RESULT_OK || data==null) return;
        if(request==PICK_IMAGES){
            ArrayList<Uri> uris=new ArrayList<>();
            if(data.getClipData()!=null) for(int i=0;i<data.getClipData().getItemCount();i++) uris.add(data.getClipData().getItemAt(i).getUri());
            else if(data.getData()!=null) uris.add(data.getData());
            int ok=0; for(Uri u:uris){ String p=copyPhoto(u); if(p!=null){ pendingPhotos.add(p); ok++; } }
            if(ok>0) showEditorFromCurrentState();
        } else if(request==EXPORT_BACKUP) exportBackup(data.getData());
        else if(request==IMPORT_BACKUP) importBackup(data.getData());
    }

    private void showEditorFromCurrentState(){
        // Image picker returns after editor fields may contain unsaved text. We avoid silently destroying it:
        // this method only refreshes photo chips if editor is still visible.
        LinearLayout root=page;
        if(root==null) return;
        View last=root.getChildAt(root.getChildCount()-1);
        if(last instanceof LinearLayout) renderPhotos((LinearLayout)last,true);
    }

    private String copyPhoto(Uri u){
        try{
            File dir=new File(getFilesDir(),"photos"); if(!dir.exists()) dir.mkdirs();
            String ext=".img"; String name=displayName(u); int dot=name.lastIndexOf('.'); if(dot>=0 && dot>name.length()-7) ext=name.substring(dot);
            File out=new File(dir,UUID.randomUUID()+ext);
            try(InputStream in=getContentResolver().openInputStream(u); OutputStream os=new FileOutputStream(out)){
                if(in==null) return null; byte[] buf=new byte[8192]; int n; while((n=in.read(buf))>0) os.write(buf,0,n);
            }
            return out.getAbsolutePath();
        }catch(Exception e){ return null; }
    }
    private String displayName(Uri u){
        try(Cursor c=getContentResolver().query(u,null,null,null,null)){ if(c!=null && c.moveToFirst()){ int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if(i>=0) return c.getString(i); } }catch(Exception ignored){}
        return "image";
    }

    private void renderPhotos(LinearLayout box, boolean removable){
        box.removeAllViews();
        for(String p:new ArrayList<>(pendingPhotos)){
            File f=new File(p); if(!f.exists()) continue;
            LinearLayout r=row(); ImageView iv=new ImageView(this); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); iv.setImageURI(Uri.fromFile(f));
            r.addView(iv,new LinearLayout.LayoutParams(dp(74),dp(74))); r.addView(space(dp(10)));
            TextView label=tv("参考图片",14,TEXT); r.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            if(removable){ Button rm=btn("移除"); rm.setOnClickListener(v->{pendingPhotos.remove(p); renderPhotos(box,true);}); r.addView(rm); }
            box.addView(r); box.addView(space(dp(8)));
        }
    }

    private void showDetail(Store.Note n){
        shell(); topTitle("记录","",true); page.addView(space(dp(18)));
        String title=n.title.trim().isEmpty()?(n.text.trim().isEmpty()?"图片记录":firstLine(n.text)):n.title.trim();
        TextView t=tv(title,24,TEXT); t.setTypeface(null,1); page.addView(t);
        Store.Category c=store.findCategory(n.categoryId);
        TextView meta=tv((c==null?"未分类":c.name)+"  ·  "+formatDate(n.updatedAt),12,MUTED); meta.setPadding(0,dp(8),0,0); page.addView(meta);
        if(!n.text.trim().isEmpty()){ TextView b=tv(n.text,17,TEXT); b.setPadding(0,dp(20),0,dp(12)); b.setTextIsSelectable(true); page.addView(b); }
        for(String p:n.photos){ File f=new File(p); if(!f.exists()) continue; ImageView iv=new ImageView(this); iv.setAdjustViewBounds(true); iv.setImageURI(Uri.fromFile(f)); iv.setPadding(0,dp(8),0,dp(8)); page.addView(iv,new LinearLayout.LayoutParams(-1,-2)); }
        page.addView(space(dp(18)));
        LinearLayout actions=row();
        Button edit=darkBtn("编辑"); edit.setOnClickListener(v->showEditor(n)); actions.addView(edit,new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(space(dp(8)));
        Button fav=btn(n.favorite?"取消常用":"设为常用"); fav.setOnClickListener(v->{n.favorite=!n.favorite;store.save();showDetail(n);}); actions.addView(fav,new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(space(dp(8)));
        Button del=btn("删除"); del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("移到回收站？").setMessage("之后可以在「更多 · 回收站」找回。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{n.trashed=true;store.save();showHome();}).show()); actions.addView(del,new LinearLayout.LayoutParams(0,-2,1));
        page.addView(actions);
    }

    private void showSearch(){
        shell(); topTitle("搜索记录","",true); page.addView(space(dp(14)));
        EditText q=new EditText(this); q.setHint("搜索文字、标题"); q.setSingleLine(true); q.setTextSize(17); q.setPadding(dp(14),dp(12),dp(14),dp(12)); q.setBackground(strokeBg(Color.WHITE,16,LINE)); page.addView(q);
        LinearLayout results=new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); results.setPadding(0,dp(14),0,0); page.addView(results);
        Runnable render=()->{
            results.removeAllViews(); String s=q.getText().toString().trim().toLowerCase(Locale.ROOT);
            if(s.isEmpty()){ results.addView(tv("输入文字开始搜索",14,MUTED)); return; }
            int count=0;
            for(Store.Note n:store.activeNotes()){
                Store.Category c=store.findCategory(n.categoryId);
                String hay=(n.title+" "+n.text+" "+(c==null?"":c.name)).toLowerCase(Locale.ROOT);
                if(hay.contains(s)){ results.addView(noteCard(n)); results.addView(space(dp(10))); count++; }
            }
            if(count==0) results.addView(tv("没有找到记录",14,MUTED));
        };
        q.addTextChangedListener(new android.text.TextWatcher(){ public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void onTextChanged(CharSequence s,int a,int b,int c){render.run();} public void afterTextChanged(android.text.Editable e){} });
        q.requestFocus(); q.postDelayed(()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(q,InputMethodManager.SHOW_IMPLICIT),200);
    }

    private void showMore(){
        String[] items={"管理分类","回收站","导出备份（含图片）","导入备份","关于"};
        new AlertDialog.Builder(this).setTitle("更多").setItems(items,(d,w)->{
            if(w==0) showCategories();
            else if(w==1) showTrash();
            else if(w==2) chooseExport();
            else if(w==3) chooseImport();
            else new AlertDialog.Builder(this).setTitle("随手存 1.1").setMessage("文字和图片放在一起，下次直接打开就能用。\n\n完全离线，没有账号和广告。\n\n首次打开为空白；分类由你自己创建。").setPositiveButton("知道了",null).show();
        }).show();
    }

    private void showCategories(){
        shell(); topTitle("管理分类","分类由你自己创建",true); page.addView(space(dp(14)));
        Button add=darkBtn("＋ 新建分类"); add.setOnClickListener(v->categoryNameDialog(null)); page.addView(add); page.addView(space(dp(14)));
        if(store.categories.isEmpty()){ page.addView(tv("还没有分类",15,MUTED)); return; }
        for(Store.Category c:new ArrayList<>(store.categories)){
            LinearLayout r=row(); r.setPadding(dp(4),dp(9),dp(4),dp(9));
            r.addView(tv(c.name,17,TEXT),new LinearLayout.LayoutParams(0,-2,1));
            Button rename=btn("重命名"); rename.setOnClickListener(v->categoryNameDialog(c)); r.addView(rename); r.addView(space(dp(8)));
            Button del=btn("删除"); del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("删除分类？").setMessage("分类里的记录会移到「未分类」，不会删除。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{store.deleteCategory(c); if(c.id.equals(filterCategory)) filterCategory=""; showCategories();}).show()); r.addView(del);
            page.addView(r); page.addView(divider());
        }
    }

    private void categoryNameDialog(Store.Category c){
        final EditText input=new EditText(this); input.setHint("分类名称"); if(c!=null) input.setText(c.name); input.setSingleLine(true); input.setPadding(dp(18),dp(6),dp(18),dp(6));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(c==null?"新建分类":"重命名").setView(input).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String name=input.getText().toString().trim();
            if(name.isEmpty()){ input.setError("请输入名称"); return; }
            if(store.categoryNameExists(name,c==null?null:c.id)){ input.setError("已经有这个分类"); return; }
            if(c==null) store.addCategory(name); else store.renameCategory(c,name);
            dialog.dismiss(); showHome();
        })); dialog.show();
    }

    private void showTrash(){
        shell(); topTitle("回收站","",true); page.addView(space(dp(14)));
        boolean any=false;
        for(Store.Note n:new ArrayList<>(store.notes)) if(n.trashed){
            any=true; LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14),dp(12),dp(14),dp(12)); card.setBackground(strokeBg(CARD,16,LINE));
            String title=n.title.trim().isEmpty()?(n.text.trim().isEmpty()?"图片记录":firstLine(n.text)):n.title.trim(); card.addView(tv(title,16,TEXT));
            LinearLayout a=row(); a.setPadding(0,dp(10),0,0);
            Button restore=btn("恢复"); restore.setOnClickListener(v->{n.trashed=false;store.save();showTrash();}); a.addView(restore,new LinearLayout.LayoutParams(0,-2,1)); a.addView(space(dp(8)));
            Button del=btn("彻底删除"); del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("彻底删除？").setMessage("这条记录将无法恢复。").setNegativeButton("取消",null).setPositiveButton("彻底删除",(d,w)->{deleteFiles(n);store.notes.remove(n);store.save();showTrash();}).show()); a.addView(del,new LinearLayout.LayoutParams(0,-2,1));
            card.addView(a); page.addView(card); page.addView(space(dp(10)));
        }
        if(!any) page.addView(tv("这里还没有删除的记录",15,MUTED));
    }
    private void deleteFiles(Store.Note n){ for(String p:n.photos) try{new File(p).delete();}catch(Exception ignored){} }

    private void chooseExport(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.setType("application/zip"); i.putExtra(Intent.EXTRA_TITLE,"随手存备份-"+new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date())+".zip");
        try{startActivityForResult(i,EXPORT_BACKUP);}catch(Exception e){toast("手机未提供文件保存入口。");}
    }
    private void chooseImport(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("application/zip"); i.addCategory(Intent.CATEGORY_OPENABLE);
        try{startActivityForResult(i,IMPORT_BACKUP);}catch(Exception e){toast("手机未提供文件选择器。");}
    }
    private void exportBackup(Uri uri){
        if(uri==null)return;
        try(OutputStream raw=getContentResolver().openOutputStream(uri); ZipOutputStream zos=new ZipOutputStream(new BufferedOutputStream(raw))){
            zos.putNextEntry(new ZipEntry("data.json")); zos.write(store.exportJson().getBytes(StandardCharsets.UTF_8)); zos.closeEntry();
            HashSet<String> done=new HashSet<>();
            for(Store.Note n:store.notes) for(String p:n.photos){ File f=new File(p); if(!f.exists())continue; String name="photos/"+f.getName(); if(!done.add(name))continue; zos.putNextEntry(new ZipEntry(name)); try(InputStream in=new FileInputStream(f)){byte[] buf=new byte[8192];int k;while((k=in.read(buf))>0)zos.write(buf,0,k);} zos.closeEntry(); }
            toast("备份已保存");
        }catch(Exception e){toast("备份没有保存成功。");}
    }
    private void importBackup(Uri uri){
        if(uri==null)return;
        new AlertDialog.Builder(this).setTitle("导入这份备份？").setMessage("会合并到当前软件中。").setNegativeButton("取消",null).setPositiveButton("导入",(d,w)->doImport(uri)).show();
    }
    private void doImport(Uri uri){
        File temp=new File(getCacheDir(),"pocket_import_"+System.currentTimeMillis()); temp.mkdirs();
        String json=null;
        try(InputStream raw=getContentResolver().openInputStream(uri); ZipInputStream zis=new ZipInputStream(new BufferedInputStream(raw))){
            ZipEntry e; byte[] buf=new byte[8192];
            while((e=zis.getNextEntry())!=null){
                String name=e.getName();
                if("data.json".equals(name)){ ByteArrayOutputStream bo=new ByteArrayOutputStream();int k;while((k=zis.read(buf))>0)bo.write(buf,0,k);json=bo.toString("UTF-8"); }
                else if(name.startsWith("photos/")){ String base=new File(name).getName(); if(base.isEmpty())continue; File out=new File(temp,base); try(OutputStream os=new FileOutputStream(out)){int k;while((k=zis.read(buf))>0)os.write(buf,0,k);} }
                zis.closeEntry();
            }
        }catch(Exception e){toast("导入失败");return;}
        if(json==null || !store.importJson(json)){toast("不是有效的随手存备份");return;}
        File dir=new File(getFilesDir(),"photos"); dir.mkdirs();
        File[] fs=temp.listFiles(); if(fs!=null) for(File f:fs){ File out=new File(dir,f.getName()); if(!out.exists()) f.renameTo(out); }
        // Repair photo paths by basename after restoring on a different device.
        for(Store.Note n:store.notes){ for(int i=0;i<n.photos.size();i++){ File old=new File(n.photos.get(i)); File now=new File(dir,old.getName()); if(now.exists())n.photos.set(i,now.getAbsolutePath()); } }
        store.save(); toast("备份已导入"); showHome();
    }

    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }

    @Override public void onBackPressed(){ showHome(); }
}
