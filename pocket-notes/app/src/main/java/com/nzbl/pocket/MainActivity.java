package com.nzbl.pocket;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    public static final int BG=0xfff4f7fa,INK=0xff22343e,MUTED=0xff667987,ACCENT=0xff22646d,LINE=0xffdce5ec,WHITE=0xffffffff;
    Store store;
    LinearLayout root,list,photoStrip;
    EditText titleInput,bodyInput,searchInput;
    Store.Note editing,current;
    String screen="home",filter="all",query="";
    boolean changing=false,showing=false,draftWarning=false;
    TextView categoryButton;
    final Handler handler=new Handler();
    Runnable draftTask;
    int scrollPosition=0;
    ListView homeList;
    public int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        store=new Store(this);home();
        if(saved!=null){filter=saved.getString("filter","all");query=saved.getString("query","");String s=saved.getString("screen","home");if("edit".equals(s)&&store.draft!=null)editor(store.draft.copy());else if("detail".equals(s)){Store.Note n=store.find(saved.getString("id",""));if(n!=null)detail(n,false);}else home();}
        if(!store.ready)new AlertDialog.Builder(this).setTitle("记录读取异常").setMessage(store.loadError).setPositiveButton("导出原文件",(d,w)->exportPicker()).setNegativeButton("关闭",null).show();
    }
    @Override public void onSaveInstanceState(Bundle out){captureDraft();out.putString("screen",screen);out.putString("filter",filter);out.putString("query",query);if(current!=null)out.putString("id",current.id);super.onSaveInstanceState(out);}
    @Override public void onPause(){captureDraft();super.onPause();}
    @Override public void onBackPressed(){
        if("photo".equals(screen)){detail(current,showing);return;}
        if("edit".equals(screen)){leaveEditor();return;}
        if("detail".equals(screen)){if(showing)detail(current,false);else home();return;}
        if("trash".equals(screen)){home();return;}
        if(!query.isEmpty()){query="";home();return;}
        super.onBackPressed();
    }
    void base(String name){
        restoreSystemBars();screen=name;root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setFitsSystemWindows(true);
        root.setFocusableInTouchMode(true);setContentView(root);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    void gap(LinearLayout target,int h){View v=new View(this);target.addView(v,lp(1,h));}
    TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);t.setLineSpacing(dp(4),1);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    GradientDrawable bg(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    Drawable ripple(int color,int radius,int stroke){return new RippleDrawable(android.content.res.ColorStateList.valueOf(0x2022646d),bg(color,radius,stroke),bg(WHITE,radius,0));}
    TextView button(String label,boolean primary,Runnable action){TextView b=text(label,16,primary?WHITE:INK,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(16),dp(12),dp(16),dp(12));b.setMinHeight(dp(50));b.setBackground(ripple(primary?ACCENT:WHITE,16,primary?0:LINE));b.setOnClickListener(v->action.run());return b;}
    TextView icon(String kind,String label,Runnable action){final Glyph glyph=new Glyph(kind,INK,24);TextView b=new TextView(this){protected void onDraw(Canvas c){super.onDraw(c);c.save();c.translate((getWidth()-dp(24))/2f,(getHeight()-dp(24))/2f);glyph.draw(c);c.restore();}};b.setGravity(Gravity.CENTER);b.setContentDescription(label);b.setBackground(ripple(0x00000000,14,0));b.setOnClickListener(v->action.run());b.setMinWidth(dp(48));b.setMinHeight(dp(48));return b;}
    void top(String heading,Runnable back,String right,Runnable action){LinearLayout bar=row();bar.setPadding(dp(8),dp(7),dp(8),dp(7));bar.addView(icon("back","返回",back),lp(48,48));TextView t=text(heading,18,INK,true);t.setSingleLine(true);t.setEllipsize(TextUtils.TruncateAt.END);bar.addView(t,new LinearLayout.LayoutParams(0,dp(48),1));t.setGravity(Gravity.CENTER_VERTICAL);if(right!=null){TextView r=text(right,16,ACCENT,true);r.setGravity(Gravity.CENTER);r.setMinWidth(dp(64));r.setMinHeight(dp(48));r.setBackground(ripple(0,12,0));r.setOnClickListener(v->action.run());bar.addView(r);}root.addView(bar);}
    ScrollView scroller(LinearLayout target){ScrollView s=new ScrollView(this);s.setFillViewport(true);s.setClipToPadding(false);s.setVerticalScrollBarEnabled(false);s.addView(target,new ScrollView.LayoutParams(-1,-2));root.addView(s,new LinearLayout.LayoutParams(-1,0,1));return s;}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    void error(String s){new AlertDialog.Builder(this).setTitle("没有完成").setMessage(s).setPositiveButton("知道了",null).show();}
    void hideKeyboard(){View v=getCurrentFocus();if(v!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);root.requestFocus();}
    boolean commit(Runnable r){if(store.change(r))return true;error("保存失败，原记录仍保留。请检查手机剩余空间后再试。");return false;}

    void home(){
        editing=null;showing=false;base("home");
        LinearLayout header=row();header.setPadding(dp(22),dp(18),dp(12),dp(8));
        LinearLayout names=column();names.addView(text("随手存",30,INK,true));gap(names,8);names.addView(text("想说的话，想用的图。",14,MUTED,false));header.addView(names,new LinearLayout.LayoutParams(0,-2,1));header.addView(icon("more","更多",this::more),lp(48,48));root.addView(header);
        LinearLayout search=row();search.setPadding(dp(15),0,dp(8),0);search.setBackground(bg(WHITE,18,LINE));TextView lens=text("",16,MUTED,false);lens.setCompoundDrawablesWithIntrinsicBounds(new Glyph("search",MUTED,21),null,null,null);search.addView(lens,lp(28,48));
        searchInput=new EditText(this);searchInput.setTextSize(16);searchInput.setTextColor(INK);searchInput.setHintTextColor(MUTED);searchInput.setHint("搜索文字、标题");searchInput.setSingleLine(true);searchInput.setBackgroundColor(Color.TRANSPARENT);searchInput.setPadding(dp(4),0,dp(6),0);searchInput.setContentDescription("搜索记录");searchInput.setText(query);search.addView(searchInput,new LinearLayout.LayoutParams(0,dp(50),1));search.addView(icon("close","清空搜索",()->searchInput.setText("")),lp(40,46));LinearLayout.LayoutParams sl=lp(-1,52);sl.setMargins(dp(20),dp(12),dp(20),dp(14));root.addView(search,sl);
        HorizontalScrollView tabs=new HorizontalScrollView(this);tabs.setHorizontalScrollBarEnabled(false);LinearLayout chips=row();chips.setPadding(dp(20),0,dp(12),dp(4));addFilter(chips,"all","全部");addFilter(chips,"pinned","常用");for(Store.Category c:store.categories)addFilter(chips,c.id,c.name);tabs.addView(chips);root.addView(tabs,lp(-1,48));
        list=column();list.setPadding(dp(20),dp(14),dp(20),dp(4));homeList=new ListView(this);homeList.setDivider(null);homeList.setVerticalScrollBarEnabled(false);homeList.setBackgroundColor(BG);homeList.setClipToPadding(false);homeList.addHeaderView(list,null,false);root.addView(homeList,new LinearLayout.LayoutParams(-1,0,1));renderList();homeList.post(()->homeList.setSelection(scrollPosition));
        LinearLayout footer=row();footer.setPadding(dp(20),dp(10),dp(20),dp(12));TextView categories=button("分类",false,this::categoriesDialog);footer.addView(categories,lp(76,54));TextView add=button("＋  记一条",true,()->newNote(null));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(54),1);ap.leftMargin=dp(12);footer.addView(add,ap);root.addView(footer);
        searchInput.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();renderList();}public void afterTextChanged(Editable e){}});
    }
    void addFilter(LinearLayout chips,String id,String label){boolean active=filter.equals(id);TextView t=text(label,15,active?WHITE:MUTED,active);t.setGravity(Gravity.CENTER);t.setPadding(dp(17),dp(10),dp(17),dp(10));t.setBackground(ripple(active?ACCENT:0,14,0));t.setOnClickListener(v->{filter=id;scrollPosition=0;hideKeyboard();home();});LinearLayout.LayoutParams p=lp(-2,42);p.rightMargin=dp(6);chips.addView(t,p);}
    void renderList(){
        if(list==null||!"home".equals(screen))return;homeList.setAdapter(null);list.removeAllViews();
        if(store.draft!=null&&store.draft.hasContent()&&query.isEmpty()){
            TextView d=button("继续上次没写完的记录",false,()->editor(store.draft.copy()));d.setTextColor(ACCENT);list.addView(d,lp(-1,-2));gap(list,14);
        }
        ArrayList<Store.Note> found=new ArrayList<>();String q=query.toLowerCase(Locale.ROOT);
        for(Store.Note n:store.notes)if(!n.deleted&&(filter.equals("all")||(filter.equals("pinned")&&n.pinned)||filter.equals(n.category))&&(n.title+"\n"+n.body).toLowerCase(Locale.ROOT).contains(q))found.add(n);
        Collections.sort(found,(a,b)->{if(a.pinned!=b.pinned)return a.pinned?-1:1;return Long.compare(b.updated,a.updated);});
        LinearLayout label=row();TextView kind=text(filter.equals("all")?"所有记录":filter.equals("pinned")?"常用记录":store.categoryName(filter),14,MUTED,true);label.addView(kind,new LinearLayout.LayoutParams(0,-2,1));label.addView(text(found.size()+" 条",14,MUTED,false));list.addView(label);gap(list,14);
        if(found.isEmpty()){
            LinearLayout empty=column();empty.setPadding(dp(22),dp(26),dp(22),dp(26));empty.setBackground(bg(WHITE,24,LINE));
            empty.addView(text(query.isEmpty()?"把需要的，先收好。":"没有找到这条记录",23,INK,true));gap(empty,12);
            TextView hint=text(query.isEmpty()?"文字和图片放在一起，\n下次直接打开就能用。":"换个词试试，或清空搜索。",16,MUTED,false);empty.addView(hint);gap(empty,24);
            if(query.isEmpty()){empty.addView(button("记理发要求",false,()->newNote(Store.HAIR)));gap(empty,10);empty.addView(button("存一段提示词",false,()->newNote(Store.PROMPT)));}else empty.addView(button("清空搜索",false,()->searchInput.setText("")));
            list.addView(empty,lp(-1,-2));return;
        }
        homeList.setAdapter(new BaseAdapter(){public int getCount(){return found.size();}public Object getItem(int p){return found.get(p);}public long getItemId(int p){return p;}public View getView(int p,View recycled,ViewGroup parent){LinearLayout wrap=column();wrap.setPadding(dp(20),0,dp(20),dp(12));wrap.addView(noteCard(found.get(p)),lp(-1,-2));return wrap;}});
    }
    int categoryColor(String id){if(Store.HAIR.equals(id))return 0xff9d642c;if(Store.PROMPT.equals(id))return 0xff5568a1;if(Store.DAILY.equals(id))return ACCENT;return ACCENT;}
    int categoryTint(String id){if(Store.HAIR.equals(id))return 0xfffcf1e4;if(Store.PROMPT.equals(id))return 0xffeef0fb;return 0xffe6f2f3;}
    LinearLayout noteCard(Store.Note n){
        LinearLayout c=column();c.setPadding(dp(18),dp(18),dp(18),dp(15));c.setBackground(ripple(WHITE,22,LINE));
        LinearLayout meta=row();TextView tag=text(store.categoryName(n.category),13,categoryColor(n.category),true);tag.setPadding(dp(10),dp(5),dp(10),dp(5));tag.setBackground(bg(categoryTint(n.category),8,0));meta.addView(tag);View spacer=new View(this);meta.addView(spacer,new LinearLayout.LayoutParams(0,1,1));if(n.pinned)meta.addView(text("★ 常用",13,categoryColor(n.category),true));c.addView(meta);gap(c,13);
        TextView h=text(n.heading(),19,INK,true);h.setMaxLines(2);h.setEllipsize(TextUtils.TruncateAt.END);c.addView(h);
        if(!n.body.trim().isEmpty()){gap(c,9);TextView b=text(n.body,16,MUTED,false);b.setMaxLines(3);b.setEllipsize(TextUtils.TruncateAt.END);c.addView(b);}
        if(!n.images.isEmpty()){gap(c,13);LinearLayout images=row();for(int i=0;i<Math.min(n.images.size(),3);i++){ImageView image=thumbnail(n.images.get(i),300);image.setScaleType(ImageView.ScaleType.CENTER_CROP);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(92),1);if(i>0)p.leftMargin=dp(7);images.addView(image,p);}c.addView(images);}
        gap(c,14);LinearLayout bottom=row();String date=new SimpleDateFormat("M月d日",Locale.CHINA).format(new Date(n.updated));bottom.addView(text(date+(n.images.isEmpty()?"":"  ·  "+n.images.size()+" 张图"),13,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));
        TextView copy=text("复制文字",14,ACCENT,true);copy.setGravity(Gravity.CENTER);copy.setMinHeight(dp(38));copy.setPadding(dp(10),0,dp(10),0);copy.setBackground(ripple(categoryTint(Store.DAILY),10,0));copy.setOnClickListener(v->copy(n));bottom.addView(copy);c.addView(bottom);
        c.setOnClickListener(v->{scrollPosition=homeList.getFirstVisiblePosition();hideKeyboard();detail(n,false);});return c;
    }
    void copy(Store.Note n){String value=n.body.trim().isEmpty()?n.title:n.body;if(value.isEmpty()){toast("这条记录只有图片");return;}((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(n.heading(),value));if(Build.VERSION.SDK_INT<33)toast("已复制");}
    void newNote(String category){
        if(store.draft!=null&&store.draft.hasContent()){new AlertDialog.Builder(this).setTitle("还有一条草稿").setMessage("先保存草稿，再开始新记录。") .setPositiveButton("继续草稿",(d,w)->editor(store.draft.copy())).setNegativeButton("取消",null).show();return;}
        Store.Note n=new Store.Note();n.category=category!=null?category:(filter.equals("all")||filter.equals("pinned")?Store.HAIR:filter);editor(n);
    }
    EditText input(String hint,int size,boolean multiline){EditText e=new EditText(this);e.setTextSize(size);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setBackgroundColor(Color.TRANSPARENT);e.setPadding(0,dp(5),0,dp(5));e.setGravity(Gravity.TOP);e.setHint(hint);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|(multiline?android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE:android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));if(!multiline)e.setSingleLine();e.setSelectAllOnFocus(false);e.setLineSpacing(dp(6),1);return e;}
    void editor(Store.Note note){
        editing=note;current=null;showing=false;changing=true;base("edit");top(store.find(note.id)==null?"新记录":"编辑记录",this::leaveEditor,null,null);
        LinearLayout content=column();content.setPadding(dp(20),dp(4),dp(20),dp(24));scroller(content);
        LinearLayout field=column();field.setPadding(dp(20),dp(18),dp(20),dp(20));field.setBackground(bg(WHITE,24,LINE));
        categoryButton=text(store.categoryName(note.category)+"  ▾",14,categoryColor(note.category),true);categoryButton.setPadding(dp(12),dp(10),dp(12),dp(10));categoryButton.setBackground(ripple(categoryTint(note.category),10,0));categoryButton.setOnClickListener(v->pickCategory());field.addView(categoryButton,lp(-2,42));gap(field,14);
        titleInput=input("给这条记录起个名字",23,false);titleInput.setTypeface(Typeface.create("sans-serif-medium",0));titleInput.setContentDescription("记录标题");titleInput.setText(note.title);field.addView(titleInput,lp(-1,-2));gap(field,9);
        View divider=new View(this);divider.setBackgroundColor(LINE);field.addView(divider,lp(-1,1));gap(field,12);
        bodyInput=input(Store.HAIR.equals(note.category)?"把要说的话写在这里…":Store.PROMPT.equals(note.category)?"把提示词粘贴在这里…":"写下想留住的内容…",18,true);bodyInput.setContentDescription("记录内容");bodyInput.setMinLines(6);bodyInput.setText(note.body);field.addView(bodyInput,lp(-1,-2));content.addView(field);
        gap(content,22);LinearLayout photosHeading=row();photosHeading.addView(text("参考图片",16,INK,true),new LinearLayout.LayoutParams(0,-2,1));TextView add=text("＋ 添加图片",15,ACCENT,true);add.setGravity(Gravity.CENTER);add.setMinHeight(dp(46));add.setPadding(dp(10),0,0,0);add.setOnClickListener(v->photoPicker());photosHeading.addView(add);content.addView(photosHeading);
        photoStrip=column();content.addView(photoStrip);renderEditorPhotos();
        LinearLayout footer=row();footer.setPadding(dp(20),dp(10),dp(20),dp(12));TextView cancel=button("取消",false,this::leaveEditor);cancel.setContentDescription("取消编辑");footer.addView(cancel,lp(88,54));TextView save=button("保存",true,this::saveNote);save.setContentDescription("保存记录");LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(54),1);p.leftMargin=dp(12);footer.addView(save,p);root.addView(footer);
        changing=false;TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){if(!changing){if(draftTask!=null)handler.removeCallbacks(draftTask);draftTask=()->captureDraft();handler.postDelayed(draftTask,350);}}public void afterTextChanged(Editable e){}};titleInput.addTextChangedListener(watcher);bodyInput.addTextChangedListener(watcher);root.requestFocus();
    }
    void updateEditing(){if(editing!=null&&"edit".equals(screen)&&titleInput!=null){editing.title=titleInput.getText().toString();editing.body=bodyInput.getText().toString();}}
    void captureDraft(){if(editing==null||!"edit".equals(screen)||changing||!store.ready)return;updateEditing();Store.Note d=editing.hasContent()?editing.copy():null;if(!store.change(()->store.draft=d)&&!draftWarning){draftWarning=true;toast("草稿没有保存成功，请检查手机剩余空间。");}}
    void pickCategory(){updateEditing();String[] names=new String[store.categories.size()];for(int i=0;i<names.length;i++)names[i]=store.categories.get(i).name;new AlertDialog.Builder(this).setTitle("放在哪个分类").setItems(names,(d,w)->{editing.category=store.categories.get(w).id;categoryButton.setText(names[w]+"  ▾");categoryButton.setTextColor(categoryColor(editing.category));categoryButton.setBackground(ripple(categoryTint(editing.category),10,0));bodyInput.setHint(Store.HAIR.equals(editing.category)?"把要说的话写在这里…":Store.PROMPT.equals(editing.category)?"把提示词粘贴在这里…":"写下想留住的内容…");captureDraft();}).show();}
    void saveNote(){
        updateEditing();if(!editing.hasContent()){toast("写点文字或添加图片再保存");return;}Store.Note n=editing.copy();n.updated=System.currentTimeMillis();if(commit(()->{Store.Note old=store.find(n.id);if(old!=null)store.notes.remove(old);store.notes.add(n);store.draft=null;})){editing=null;hideKeyboard();query="";filter=n.category;scrollPosition=0;home();}
    }
    void leaveEditor(){updateEditing();hideKeyboard();if(editing==null||!editing.hasContent()){if(commit(()->store.draft=null)){editing=null;home();}return;}
        new AlertDialog.Builder(this).setTitle("保留这次编辑吗？").setItems(new String[]{"保存记录","保留草稿","放弃这次编辑"},(d,w)->{if(w==0)saveNote();else if(w==1){Store.Note note=editing.copy();if(commit(()->store.draft=note)){editing=null;home();}}else if(commit(()->store.draft=null)){editing=null;home();}}).setNegativeButton("继续写",null).show();
    }
    ImageView thumbnail(String filename,int max){ImageView i=new ImageView(this);i.setBackground(bg(0xffeaf0f4,14,0));i.setClipToOutline(true);i.setAdjustViewBounds(true);try{Bitmap b=decode(new File(store.photos,filename),max);if(b!=null)i.setImageBitmap(b);else i.setContentDescription("图片无法读取");}catch(Exception e){i.setContentDescription("图片无法读取");}return i;}
    static Bitmap decode(File f,int max){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.toString(),o);if(o.outWidth<=0||o.outHeight<=0)return null;int sample=1;while(Math.max(o.outWidth,o.outHeight)/sample>max)sample*=2;o.inSampleSize=sample;o.inJustDecodeBounds=false;Bitmap b=BitmapFactory.decodeFile(f.toString(),o);if(b==null)return null;try{android.media.ExifInterface exif=new android.media.ExifInterface(f.toString());int orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);Matrix m=new Matrix();switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;}if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(rotated!=b)b.recycle();b=rotated;}}catch(Exception ignored){}return b;}
    void renderEditorPhotos(){photoStrip.removeAllViews();if(editing.images.isEmpty()){TextView hint=text("从相册添加，文字和图片一起保存。",14,MUTED,false);hint.setPadding(0,dp(5),0,dp(6));photoStrip.addView(hint);return;}
        for(String name:new ArrayList<>(editing.images)){LinearLayout r=row();r.setPadding(0,dp(8),0,dp(8));ImageView im=thumbnail(name,500);im.setScaleType(ImageView.ScaleType.CENTER_CROP);r.addView(im,new LinearLayout.LayoutParams(0,dp(128),1));TextView remove=icon("close","移除图片",()->new AlertDialog.Builder(this).setTitle("移除这张图片？").setMessage("只从这条记录移除，相册原图不受影响。").setPositiveButton("移除",(d,w)->{editing.images.remove(name);captureDraft();renderEditorPhotos();}).setNegativeButton("取消",null).show());r.addView(remove,lp(52,52));photoStrip.addView(r);}}
    void photoPicker(){captureDraft();Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);try{startActivityForResult(i,10);}catch(ActivityNotFoundException e){error("手机未提供图片选择器。");}}
    void detail(Store.Note n,boolean presentation){
        editing=null;current=n;showing=presentation;base("detail");if(presentation){getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);top("给理发师看",()->detail(n,false),"退出",()->detail(n,false));}else top("记录",this::home,"编辑",()->editor(n.copy()));
        LinearLayout content=column();content.setPadding(dp(20),dp(6),dp(20),dp(24));scroller(content);LinearLayout card=column();card.setPadding(dp(22),dp(23),dp(22),dp(22));card.setBackground(bg(WHITE,24,LINE));
        TextView tag=text(store.categoryName(n.category),14,categoryColor(n.category),true);card.addView(tag);gap(card,16);TextView h=text(n.heading(),presentation?27:25,INK,true);card.addView(h);if(!n.body.trim().isEmpty()){gap(card,20);TextView b=text(n.body,presentation?23:18,INK,false);b.setTextIsSelectable(true);b.setLineSpacing(dp(presentation?9:7),1);card.addView(b);}content.addView(card);
        if(!n.images.isEmpty()){gap(content,25);content.addView(text("参考图片 · 点击放大",14,MUTED,true));gap(content,12);for(int i=0;i<n.images.size();i++){final int index=i;ImageView image=thumbnail(n.images.get(i),1200);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription("参考图片 "+(i+1));image.setOnClickListener(v->fullPhoto(index));content.addView(image,lp(-1,-2));gap(content,12);}}
        if(!presentation){LinearLayout footer=row();footer.setPadding(dp(20),dp(10),dp(20),dp(12));TextView more=icon("more","记录操作",()->noteMore(n));footer.addView(more,lp(48,54));TextView copy=button("复制",false,()->copy(n));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(54),1);cp.leftMargin=dp(8);footer.addView(copy,cp);if(Store.HAIR.equals(n.category)){TextView show=button("给理发师看",true,()->detail(n,true));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(54),1.5f);p.leftMargin=dp(10);footer.addView(show,p);}root.addView(footer);}
    }
    void fullPhoto(int index){boolean wasShowing=showing;base("photo");showing=wasShowing;root.setBackgroundColor(0xff18232b);getWindow().setStatusBarColor(0xff18232b);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);LinearLayout bar=row();bar.setPadding(dp(12),dp(8),dp(12),dp(8));TextView back=button("返回",false,()->{restoreSystemBars();detail(current,showing);});bar.addView(back,lp(70,48));TextView number=text((index+1)+" / "+current.images.size(),16,WHITE,true);number.setGravity(Gravity.CENTER);bar.addView(number,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(bar);Bitmap bitmap=decode(new File(store.photos,current.images.get(index)),2800);if(bitmap!=null)root.addView(new ZoomImage(bitmap),new LinearLayout.LayoutParams(-1,0,1));else{TextView unavailable=text("图片无法读取",18,WHITE,false);unavailable.setGravity(Gravity.CENTER);root.addView(unavailable,new LinearLayout.LayoutParams(-1,0,1));}LinearLayout nav=row();nav.setPadding(dp(20),dp(10),dp(20),dp(16));if(index>0)nav.addView(button("上一张",false,()->fullPhoto(index-1)),new LinearLayout.LayoutParams(0,dp(50),1));else nav.addView(new View(this),new LinearLayout.LayoutParams(0,dp(50),1));gapHorizontal(nav,12);if(index+1<current.images.size())nav.addView(button("下一张",false,()->fullPhoto(index+1)),new LinearLayout.LayoutParams(0,dp(50),1));else nav.addView(new View(this),new LinearLayout.LayoutParams(0,dp(50),1));root.addView(nav);if(showing)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    void gapHorizontal(LinearLayout row,int w){row.addView(new View(this),lp(w,1));}
    void restoreSystemBars(){getWindow().setStatusBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
    void noteMore(Store.Note n){new AlertDialog.Builder(this).setTitle(n.heading()).setItems(new String[]{n.pinned?"取消常用":"设为常用","删除记录"},(d,w)->{if(w==0){if(commit(()->{n.pinned=!n.pinned;n.updated=System.currentTimeMillis();}))detail(n,false);}else new AlertDialog.Builder(this).setTitle("移到回收站？").setMessage("之后可以在「更多 · 回收站」找回。").setPositiveButton("删除",(a,b)->{if(commit(()->{n.deleted=true;n.updated=System.currentTimeMillis();})){current=null;home();}}).setNegativeButton("取消",null).show();}).show();}
    void more(){new AlertDialog.Builder(this).setTitle("随手存").setItems(new String[]{"管理分类","导出备份（含图片）","导入备份","回收站","关于"},(d,w)->{if(w==0)categoriesDialog();else if(w==1)exportPicker();else if(w==2)importPicker();else if(w==3)trash();else new AlertDialog.Builder(this).setTitle("随手存 1.0").setMessage("文字、图片、提示词，按你的分类收好。\n\n完全离线，没有账号和广告。\n\n记录保存在这台手机。换手机或卸载前，请导出备份；备份包含文字、图片和分类。").setPositiveButton("知道了",null).show();}).show();}
    void categoriesDialog(){String[] options=new String[store.categories.size()+1];for(int i=0;i<store.categories.size();i++)options[i]=store.categories.get(i).name;options[options.length-1]="＋ 新建分类";new AlertDialog.Builder(this).setTitle("管理分类").setItems(options,(d,w)->{if(w==options.length-1)categoryNameDialog(null);else{Store.Category c=store.categories.get(w);ArrayList<String> ops=new ArrayList<>();ops.add("重命名");if(!Arrays.asList(Store.HAIR,Store.PROMPT,Store.DAILY).contains(c.id))ops.add("删除分类");new AlertDialog.Builder(this).setTitle(c.name).setItems(ops.toArray(new String[0]),(dd,ww)->{if(ww==0)categoryNameDialog(c);else new AlertDialog.Builder(this).setTitle("删除分类？").setMessage("分类里的记录会移到「"+store.categoryName(Store.DAILY)+"」，不会删除。").setPositiveButton("删除分类",(a,b)->{if(commit(()->{for(Store.Note n:store.notes)if(n.category.equals(c.id))n.category=Store.DAILY;if(store.draft!=null&&store.draft.category.equals(c.id))store.draft.category=Store.DAILY;store.categories.remove(c);})){filter="all";home();}}).setNegativeButton("取消",null).show();}).show();}}).setNegativeButton("关闭",null).show();}
    void categoryNameDialog(Store.Category category){EditText e=input("分类名称",18,false);e.setPadding(dp(22),dp(14),dp(22),dp(14));e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});if(category!=null)e.setText(category.name);AlertDialog d=new AlertDialog.Builder(this).setTitle(category==null?"新建分类":"重命名").setView(e).setPositiveButton("保存",null).setNegativeButton("取消",null).create();d.setOnShowListener(v->{d.getButton(-1).setOnClickListener(b->{String name=e.getText().toString().trim();if(name.isEmpty()){e.setError("请输入名称");return;}for(Store.Category c:store.categories)if(c!=category&&c.name.equals(name)){e.setError("已经有这个分类");return;}if(commit(()->{if(category==null)store.categories.add(new Store.Category(UUID.randomUUID().toString(),name));else category.name=name;})){d.dismiss();home();}});});d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);d.show();}
    void trash(){editing=null;current=null;showing=false;base("trash");top("回收站",this::home,null,null);LinearLayout content=column();content.setPadding(dp(20),dp(10),dp(20),dp(20));scroller(content);ArrayList<Store.Note> removed=new ArrayList<>();for(Store.Note n:store.notes)if(n.deleted)removed.add(n);if(removed.isEmpty()){TextView e=text("这里还没有删除的记录",18,MUTED,false);e.setPadding(0,dp(26),0,0);content.addView(e);}for(Store.Note n:removed){LinearLayout c=column();c.setPadding(dp(20),dp(18),dp(20),dp(18));c.setBackground(bg(WHITE,20,LINE));c.addView(text(n.heading(),19,INK,true));gap(c,14);LinearLayout actions=row();actions.addView(button("恢复",true,()->{if(commit(()->{n.deleted=false;n.updated=System.currentTimeMillis();}))trash();}),new LinearLayout.LayoutParams(0,dp(48),1));gapHorizontal(actions,10);actions.addView(button("彻底删除",false,()->new AlertDialog.Builder(this).setTitle("彻底删除？").setMessage("这条记录将无法恢复。").setPositiveButton("彻底删除",(d,w)->{if(commit(()->store.notes.remove(n)))trash();}).setNegativeButton("取消",null).show()),new LinearLayout.LayoutParams(0,dp(48),1));c.addView(actions);content.addView(c);gap(content,12);}}
    void exportPicker(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,"随手存备份-"+new SimpleDateFormat("yyyyMMdd-HHmm",Locale.ROOT).format(new Date())+".zip");try{startActivityForResult(i,20);}catch(Exception e){error("手机未提供文件保存入口。");}}
    void importPicker(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/octet-stream","application/x-zip-compressed"});try{startActivityForResult(i,21);}catch(Exception e){error("手机未提供文件选择器。");}}
    void busy(String label,Runnable background){ProgressDialog p=new ProgressDialog(this);p.setMessage(label);p.setCancelable(false);p.show();new Thread(()->{try{background.run();}finally{runOnUiThread(()->{if(!isFinishing())p.dismiss();});}}).start();}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;
        if(request==10&&editing!=null){ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());Store.Note note=editing;
            busy("正在保存图片…",()->{int failed=0;ArrayList<String> success=new ArrayList<>();for(Uri uri:uris){File target=new File(store.photos,UUID.randomUUID()+".img");try{InputStream in=getContentResolver().openInputStream(uri);if(in==null)throw new IOException();Store.copy(in,new FileOutputStream(target),64*1024*1024);BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(target.toString(),o);if(o.outWidth<=0||o.outHeight<=0)throw new IOException();success.add(target.getName());}catch(Exception e){target.delete();failed++;}}int errors=failed;runOnUiThread(()->{note.images.addAll(success);captureDraft();renderEditorPhotos();if(errors>0)error(errors+" 张图片没能读取，其他图片已保存。");});});
        }else if(request==20&&data.getData()!=null){Uri uri=data.getData();busy("正在导出文字和图片…",()->{try{OutputStream out=getContentResolver().openOutputStream(uri);if(out==null)throw new IOException();store.exportZip(out);runOnUiThread(()->toast("备份已保存"));}catch(Exception e){runOnUiThread(()->error("备份没有保存成功。请检查可用空间后重试。"));}});
        }else if(request==21&&data.getData()!=null){Uri uri=data.getData();new AlertDialog.Builder(this).setTitle("导入这份备份？").setMessage("合并到现有记录，不清空手机上的内容。相同记录保留更新的一份。").setPositiveButton("导入",(d,w)->busy("正在导入…",()->{try{InputStream in=getContentResolver().openInputStream(uri);if(in==null)throw new IOException();int count=store.importZip(in);runOnUiThread(()->{filter="all";query="";home();toast("已导入 "+count+" 条记录");});}catch(Exception e){runOnUiThread(()->error("导入失败，原记录没有改变。请使用本软件导出的完整 ZIP 备份。"));}})).setNegativeButton("取消",null).show();}
    }
    class Glyph extends Drawable {
        String kind;int color,size;Paint p=new Paint(3);
        Glyph(String k,int c,int s){kind=k;color=c;size=dp(s);setBounds(0,0,size,size);}
        @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}
        public void draw(Canvas canvas){canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(size/24f,size/24f);p.setColor(color);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStyle(Paint.Style.STROKE);
            if(kind.equals("back")){canvas.drawLine(16,5,9,12,p);canvas.drawLine(9,12,16,19,p);}else if(kind.equals("close")){canvas.drawLine(7,7,17,17,p);canvas.drawLine(17,7,7,17,p);}else if(kind.equals("search")){canvas.drawCircle(10.5f,10.5f,6.2f,p);canvas.drawLine(15,15,20,20,p);}else{p.setStyle(Paint.Style.FILL);for(int x=5;x<=19;x+=7)canvas.drawCircle(x,12,1.7f,p);}canvas.restore();}
        public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    class ZoomImage extends View {
        Bitmap b;Paint p=new Paint(3);Matrix matrix=new Matrix();float zoom=1,baseScale=1,dx=0,dy=0,lastX,lastY;ScaleGestureDetector scale;GestureDetector gesture;
        ZoomImage(Bitmap bitmap){super(MainActivity.this);b=bitmap;setContentDescription("放大的参考图片，双指缩放，双击还原");scale=new ScaleGestureDetector(MainActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){zoom=Math.max(1,Math.min(5,zoom*d.getScaleFactor()));invalidate();return true;}});gesture=new GestureDetector(MainActivity.this,new GestureDetector.SimpleOnGestureListener(){public boolean onDown(MotionEvent e){return true;}public boolean onDoubleTap(MotionEvent e){zoom=zoom>1?1:2.5f;dx=dy=0;invalidate();return true;}});}
        protected void onDraw(Canvas c){super.onDraw(c);baseScale=Math.min((float)getWidth()/b.getWidth(),(float)getHeight()/b.getHeight());float s=baseScale*zoom;float maxX=Math.max(0,(b.getWidth()*s-getWidth())/2),maxY=Math.max(0,(b.getHeight()*s-getHeight())/2);dx=Math.max(-maxX,Math.min(maxX,dx));dy=Math.max(-maxY,Math.min(maxY,dy));matrix.reset();matrix.postScale(s,s);matrix.postTranslate((getWidth()-b.getWidth()*s)/2+dx,(getHeight()-b.getHeight()*s)/2+dy);c.drawBitmap(b,matrix,p);}
        public boolean onTouchEvent(MotionEvent e){scale.onTouchEvent(e);gesture.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();}else if(e.getActionMasked()==MotionEvent.ACTION_MOVE){if(!scale.isInProgress()&&e.getPointerCount()==1&&zoom>1){dx+=e.getX()-lastX;dy+=e.getY()-lastY;invalidate();}lastX=e.getX();lastY=e.getY();}return true;}
    }
}
