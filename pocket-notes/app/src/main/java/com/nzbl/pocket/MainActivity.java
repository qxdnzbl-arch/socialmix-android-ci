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
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
    public static final int BG=0xfff4f7fa,INK=0xff22343e,MUTED=0xff667987,ACCENT=0xff22646d,LINE=0xffdce5ec,WHITE=0xffffffff;
    Store store;
    File backgroundFile,downloadedUpdate;
    CloudSync cloudSync;
    ImageView backdropView;
    LinearLayout root,list,photoStrip;
    View searchBox;
    EditText titleInput,bodyInput,searchInput;
    Store.Note editing,current;
    String screen="home",filter="all",query="",editorReturn="home",searchReturnFilter="all";
    int albumIndex=0;
    static class AlbumItem {Store.Note note;String file;int imageIndex;long time;AlbumItem(Store.Note n,String f,int i,long t){note=n;file=f;imageIndex=i;time=t;}}
    boolean changing=false,draftWarning=false,searchOpen=false,categoryDragMoved=false,suppressCategoryClick=false;
    String draggingCategoryId=null;
    View categoryDragView=null;
    ArrayList<Store.Category> categoryDragOriginal=null;
    TextView categoryButton;
    final Handler handler=new Handler();
    Runnable draftTask,syncTask;
    final java.util.concurrent.atomic.AtomicBoolean syncInFlight=new java.util.concurrent.atomic.AtomicBoolean(false);
    int scrollPosition=0;
    ListView homeList;
    public int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN&&"home".equals(screen)&&searchInput!=null&&searchInput.hasFocus()&&searchBox!=null){
            Rect r=new Rect();searchBox.getGlobalVisibleRect(r);
            if(!r.contains((int)e.getRawX(),(int)e.getRawY())){
                searchInput.clearFocus();searchInput.setCursorVisible(false);hideKeyboard();
            }
        }
        return super.dispatchTouchEvent(e);
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        backgroundFile=new File(getFilesDir(),"background.img");downloadedUpdate=new File(getCacheDir(),"update.apk");store=new Store(this);cloudSync=new CloudSync(this,store,backgroundFile);home();handler.postDelayed(this::maybeAutoCheckUpdate,1800);if(cloudSync.enabled())handler.postDelayed(()->syncCloud(false),900);
        if(saved!=null){filter=saved.getString("filter","all");query=saved.getString("query","");searchOpen=saved.getBoolean("searchOpen",false);searchReturnFilter=saved.getString("searchReturnFilter","all");String s=saved.getString("screen","home");if("edit".equals(s)&&store.draft!=null)editor(store.draft.copy());else if("album".equals(s))album();else home();}
        if(!store.ready)new AlertDialog.Builder(this).setTitle("记录读取异常").setMessage(store.loadError).setPositiveButton("导出原文件",(d,w)->exportPicker()).setNegativeButton("关闭",null).show();
    }
    @Override public void onSaveInstanceState(Bundle out){captureDraft();out.putString("screen",screen);out.putString("filter",filter);out.putString("query",query);out.putBoolean("searchOpen",searchOpen);out.putString("searchReturnFilter",searchReturnFilter);if(current!=null)out.putString("id",current.id);super.onSaveInstanceState(out);}
    @Override protected void onResume(){super.onResume();if(cloudSync!=null&&cloudSync.enabled())handler.postDelayed(()->syncCloud(false),500);}
    @Override public void onPause(){captureDraft();if(cloudSync!=null&&cloudSync.enabled()&&cloudSync.dirty())syncCloud(false);super.onPause();}
    @Override public void onBackPressed(){
        if("albumPhoto".equals(screen)){album();return;}
        if("photo".equals(screen)){if(editing!=null)editor(editing);else if(current!=null)detail(current,false);else home();return;}
        if("edit".equals(screen)){autoSaveAndLeave();return;}
        if("detail".equals(screen)){current=null;if("album".equals(editorReturn))album();else home();return;}
        if("trash".equals(screen)){home();return;}
        if("home".equals(screen)&&searchOpen){closeSearch();return;}
        if(!query.isEmpty()){query="";home();return;}
        super.onBackPressed();
    }
    void base(String name){
        restoreSystemBars();screen=name;
        getWindow().setSoftInputMode("home".equals(name)?WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING:WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setFitsSystemWindows(true);root.setFocusableInTouchMode(true);
        boolean customBackground=backgroundFile!=null&&backgroundFile.isFile();root.setBackgroundColor(customBackground?Color.TRANSPARENT:BG);backdropView=null;
        if(customBackground){
            FrameLayout shell=new FrameLayout(this);shell.setClipChildren(true);ImageView backdrop=new ImageView(this);backdropView=backdrop;backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try{Bitmap image=decode(backgroundFile,1800);if(image!=null)backdrop.setImageBitmap(image);}catch(Exception ignored){}
            FrameLayout.LayoutParams backdropParams=new FrameLayout.LayoutParams(-1,getResources().getDisplayMetrics().heightPixels);backdropParams.gravity=Gravity.TOP;
            shell.addView(backdrop,backdropParams);shell.addView(root,new FrameLayout.LayoutParams(-1,-1));setContentView(shell);
        }else setContentView(root);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    int homePanelColor(){return backgroundFile!=null&&backgroundFile.isFile()?0xf2ffffff:WHITE;}
    int homeSearchColor(){return backgroundFile!=null&&backgroundFile.isFile()?0xf2ffffff:WHITE;}
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
    boolean commit(Runnable r){if(store.change(r)){markSyncDirty(false);return true;}error("保存失败，原记录仍保留。请检查手机剩余空间后再试。");return false;}
    void markSyncDirty(boolean background){
        if(cloudSync==null||!cloudSync.enabled())return;cloudSync.markDirty(background);
        if(syncTask!=null)handler.removeCallbacks(syncTask);syncTask=()->syncCloud(false);handler.postDelayed(syncTask,2500);
    }
    void syncCloud(boolean manual){
        if(cloudSync==null||!cloudSync.enabled())return;
        if(!syncInFlight.compareAndSet(false,true)){if(manual)toast("正在同步中…");return;}
        if(manual)toast("正在同步…");
        final long initialChanges=cloudSync.prefs.getLong("change_seq",0);
        new Thread(()->{
            CloudSync.Result result;
            try{result=cloudSync.syncNow(false);}catch(Exception e){result=new CloudSync.Result(false,false,"同步暂时遇到问题，原记录仍在本机。",cloudSync.revision());}
            syncInFlight.set(false);
            final CloudSync.Result delivered=result;
            runOnUiThread(()->{
                if(isFinishing())return;
                if(delivered.ok){if(manual)toast(cloudSync.dirty()?"部分已同步，其余正在继续…":"同步完成");if(delivered.changed){if("home".equals(screen))home();else if("album".equals(screen))album();}}
                else if(manual)error(delivered.message);
                if(cloudSync.dirty()&&cloudSync.prefs.getLong("change_seq",0)!=initialChanges)
                    handler.postDelayed(()->syncCloud(false),1200);
            });
        }).start();
    }

    void mainHeader(String mode){
        LinearLayout switcher=row();switcher.setPadding(dp(20),dp(8),dp(12),dp(4));
        boolean homeActive="home".equals(mode),albumActive="album".equals(mode);
        TextView notes=text("记录",homeActive?18:17,homeActive?ACCENT:MUTED,homeActive);notes.setGravity(Gravity.CENTER);notes.setPadding(dp(13),dp(8),dp(13),dp(8));notes.setContentDescription("切换到记录");notes.setOnClickListener(v->{searchOpen=false;query="";if(!"home".equals(screen))home();});switcher.addView(notes,lp(-2,44));
        TextView album=text("相册",albumActive?18:17,albumActive?ACCENT:MUTED,albumActive);album.setGravity(Gravity.CENTER);album.setPadding(dp(13),dp(8),dp(13),dp(8));album.setContentDescription("切换到相册");album.setOnClickListener(v->{searchOpen=false;query="";if(!"album".equals(screen))album();});switcher.addView(album,lp(-2,44));
        switcher.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        if(homeActive)switcher.addView(icon("search","打开搜索",()->{searchReturnFilter=filter;filter="all";query="";searchOpen=true;home();}),lp(48,44));
        switcher.addView(icon("more","更多",this::more),lp(48,44));root.addView(switcher,lp(-1,56));
    }
    void closeSearch(){query="";searchOpen=false;hideKeyboard();filter=searchReturnFilter;normalizeFilter();home();}
    void searchHeader(){
        LinearLayout search=row();searchBox=search;search.setContentDescription("搜索栏");search.setPadding(dp(2),0,dp(6),0);search.setBackground(bg(homeSearchColor(),16,LINE));
        search.addView(icon("back","关闭搜索",this::closeSearch),lp(44,46));
        TextView lens=text("",16,MUTED,false);lens.setCompoundDrawablesWithIntrinsicBounds(new Glyph("search",MUTED,19),null,null,null);search.addView(lens,lp(25,46));
        searchInput=new EditText(this);searchInput.setTextSize(16);searchInput.setTextColor(INK);searchInput.setHintTextColor(MUTED);searchInput.setHint("搜索文字、标题");searchInput.setSingleLine(true);searchInput.setBackgroundColor(Color.TRANSPARENT);searchInput.setPadding(dp(3),0,dp(3),0);searchInput.setContentDescription("搜索记录");searchInput.setText(query);searchInput.setSelection(searchInput.length());searchInput.setCursorVisible(true);searchInput.setOnFocusChangeListener((v,hasFocus)->searchInput.setCursorVisible(hasFocus));search.addView(searchInput,new LinearLayout.LayoutParams(0,dp(46),1));
        TextView clear=icon("close","清空搜索",()->searchInput.setText(""));clear.setVisibility(query.isEmpty()?View.GONE:View.VISIBLE);search.addView(clear,lp(38,42));
        LinearLayout.LayoutParams p=lp(-1,48);p.setMargins(dp(20),dp(10),dp(20),dp(6));root.addView(search,p);
        searchInput.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence value,int a,int c,int f){}public void onTextChanged(CharSequence value,int a,int b,int c){query=value.toString();clear.setVisibility(query.isEmpty()?View.GONE:View.VISIBLE);renderList();}public void afterTextChanged(Editable e){}});
        searchInput.post(()->{searchInput.requestFocus();searchInput.setCursorVisible(true);((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(searchInput,InputMethodManager.SHOW_IMPLICIT);});
    }
    void normalizeFilter(){if(!filter.equals("all")){boolean exists=false;for(Store.Category c:store.categories)if(c.id.equals(filter))exists=true;if(!exists)filter="all";}}
    void home(){
        editing=null;normalizeFilter();if(!searchOpen)query="";base("home");searchBox=null;searchInput=null;if(searchOpen)searchHeader();else mainHeader("home");
        if(!searchOpen)categoryTabs();
        list=column();list.setPadding(dp(20),dp(searchOpen?6:10),dp(20),dp(4));homeList=new ListView(this);homeList.setDivider(null);homeList.setVerticalScrollBarEnabled(false);homeList.setBackgroundColor(backgroundFile!=null&&backgroundFile.isFile()?Color.TRANSPARENT:BG);homeList.setClipToPadding(false);homeList.addHeaderView(list,null,false);root.addView(homeList,new LinearLayout.LayoutParams(-1,0,1));renderList();homeList.post(()->homeList.setSelection(scrollPosition));
        if(!searchOpen){LinearLayout footer=row();footer.setPadding(dp(20),dp(10),dp(20),dp(12));TextView categories=button("分类",false,this::categoriesDialog);footer.addView(categories,lp(76,54));TextView add=button("＋  记一条",true,()->newNote(null));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(54),1);ap.leftMargin=dp(12);footer.addView(add,ap);root.addView(footer);root.requestFocus();}
    }
    void categoryTabs(){
        if(store.categories.isEmpty())return;
        HorizontalScrollView tabs=new HorizontalScrollView(this);tabs.setHorizontalScrollBarEnabled(false);tabs.setFillViewport(false);
        LinearLayout chips=row();chips.setPadding(dp(20),0,dp(12),dp(4));for(Store.Category c:store.categories)addFilter(chips,c.id,c.name);
        tabs.addView(chips);enableCategoryDrag(chips,tabs);root.addView(tabs,lp(-1,48));
    }
    int categoryIndex(String id){for(int i=0;i<store.categories.size();i++)if(store.categories.get(i).id.equals(id))return i;return -1;}
    boolean moveCategoryInMemory(String id,int to){
        int from=categoryIndex(id);if(from<0||store.categories.size()<2)return false;to=Math.max(0,Math.min(to,store.categories.size()-1));if(from==to)return false;
        Store.Category c=store.categories.remove(from);store.categories.add(to,c);return true;
    }
    boolean persistCategoryOrder(){try{store.save();markSyncDirty(false);return true;}catch(Exception e){return false;}}
    void beginCategoryDrag(TextView source,String id){
        if(categoryIndex(id)<0||store.categories.size()<2)return;
        categoryDragOriginal=new ArrayList<>(store.categories);draggingCategoryId=id;categoryDragView=source;categoryDragMoved=false;suppressCategoryClick=true;
        source.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);source.setAlpha(.68f);
        ClipData data=ClipData.newPlainText("分类",id);View.DragShadowBuilder shadow=new View.DragShadowBuilder(source);
        boolean started=Build.VERSION.SDK_INT>=24?source.startDragAndDrop(data,shadow,id,0):source.startDrag(data,shadow,id,0);
        if(!started){source.setAlpha(1f);categoryDragOriginal=null;draggingCategoryId=null;categoryDragView=null;suppressCategoryClick=false;}
    }
    void finishCategoryDrag(boolean keep){
        ArrayList<Store.Category> original=categoryDragOriginal;boolean moved=categoryDragMoved;
        if(categoryDragView!=null)categoryDragView.setAlpha(1f);
        categoryDragOriginal=null;draggingCategoryId=null;categoryDragView=null;categoryDragMoved=false;
        if(original!=null&&(!keep||(moved&&!persistCategoryOrder()))){
            store.categories.clear();store.categories.addAll(original);
            handler.post(()->{if("album".equals(screen))album();else home();});
            if(keep&&moved)error("分类顺序没有保存成功，请检查手机剩余空间后再试。");
        }
        suppressCategoryClick=true;handler.postDelayed(()->suppressCategoryClick=false,320);
    }
    void enableCategoryDrag(LinearLayout chips,HorizontalScrollView tabs){
        chips.setOnDragListener((v,event)->{
            Object local=event.getLocalState();if(!(local instanceof String))return false;String id=(String)local;
            if(event.getAction()==DragEvent.ACTION_DRAG_STARTED)return categoryIndex(id)>=0;
            if(event.getAction()==DragEvent.ACTION_DRAG_LOCATION){
                if(!id.equals(draggingCategoryId))return false;
                float visibleX=event.getX()-tabs.getScrollX();int edge=dp(58);
                if(visibleX<edge)tabs.scrollBy(-dp(18),0);else if(visibleX>tabs.getWidth()-edge)tabs.scrollBy(dp(18),0);
                int from=categoryIndex(id),to=0;View dragged=null;
                for(int i=0;i<chips.getChildCount();i++){View child=chips.getChildAt(i);Object tag=child.getTag();if(id.equals(tag)){dragged=child;continue;}if(event.getX()>child.getLeft()+child.getWidth()/2f)to++;}
                if(from>=0&&dragged!=null&&to!=from&&moveCategoryInMemory(id,to)){
                    ViewGroup.LayoutParams params=dragged.getLayoutParams();chips.removeView(dragged);chips.addView(dragged,to,params);categoryDragMoved=true;
                }
                return true;
            }
            if(event.getAction()==DragEvent.ACTION_DROP){finishCategoryDrag(true);return true;}
            if(event.getAction()==DragEvent.ACTION_DRAG_ENDED){if(categoryDragOriginal!=null)finishCategoryDrag(event.getResult());return true;}
            return true;
        });
    }
    void addFilter(LinearLayout chips,String id,String label){
        boolean active=filter.equals(id),albumMode="album".equals(screen);TextView t=text(label,15,active?WHITE:MUTED,active);t.setGravity(Gravity.CENTER);t.setPadding(dp(17),dp(10),dp(17),dp(10));t.setBackground(ripple(active?ACCENT:0,14,0));t.setTag(id);t.setContentDescription("分类："+label+"，长按拖动排序");
        t.setOnClickListener(v->{if(suppressCategoryClick)return;filter=active?"all":id;scrollPosition=0;hideKeyboard();if(albumMode)album();else home();});
        final float[] down=new float[2];final Runnable[] hold=new Runnable[1];final boolean[] started={false};int slop=ViewConfiguration.get(this).getScaledTouchSlop();
        hold[0]=()->{started[0]=true;beginCategoryDrag(t,id);};
        t.setOnTouchListener((v,e)->{int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){down[0]=e.getX();down[1]=e.getY();started[0]=false;handler.postDelayed(hold[0],400);}else if(action==MotionEvent.ACTION_MOVE&&!started[0]){if(Math.abs(e.getX()-down[0])>slop||Math.abs(e.getY()-down[1])>slop)handler.removeCallbacks(hold[0]);}else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)handler.removeCallbacks(hold[0]);return false;});
        LinearLayout.LayoutParams p=lp(-2,42);p.rightMargin=dp(6);chips.addView(t,p);
    }
    ArrayList<AlbumItem> albumItems(){
        ArrayList<AlbumItem> items=new ArrayList<>();
        for(Store.Note n:store.notes)if(!n.deleted&&(filter.equals("all")||filter.equals(n.category))){
            n.normalizeImageTimes();for(int i=0;i<n.images.size();i++)items.add(new AlbumItem(n,n.images.get(i),i,n.imageTimes.get(i)));
        }
        Collections.sort(items,(a,b)->Long.compare(b.time,a.time));return items;
    }
    void album(){
        editing=null;current=null;normalizeFilter();base("album");mainHeader("album");
        categoryTabs();
        ArrayList<AlbumItem> items=albumItems();LinearLayout content=column();content.setPadding(dp(12),dp(8),dp(12),dp(12));ScrollView scroll=scroller(content);
        if(!filter.equals("all")){LinearLayout label=row();label.setPadding(dp(8),0,dp(8),dp(10));label.addView(text(store.categoryName(filter),14,MUTED,true),new LinearLayout.LayoutParams(0,-2,1));content.addView(label);}
        if(items.isEmpty()){
            LinearLayout empty=column();empty.setPadding(dp(20),dp(18),dp(20),dp(18));empty.setBackground(bg(WHITE,20,LINE));empty.addView(text("这里还没有照片",18,INK,true));content.addView(empty,lp(-1,-2));
        }else{
            String lastDay="";LinearLayout row=null;int cells=0;SimpleDateFormat dayFormat=new SimpleDateFormat("yyyy年M月d日",Locale.CHINA);
            for(int p=0;p<items.size();p++){AlbumItem item=items.get(p);String day=dayFormat.format(new Date(item.time));
                if(!day.equals(lastDay)){if(row!=null&&cells>0){while(cells++<3)row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(104),1));content.addView(row);}if(!lastDay.isEmpty())gap(content,14);TextView dayLabel=text(day,14,INK,true);dayLabel.setPadding(dp(4),0,0,dp(8));content.addView(dayLabel);row=row();cells=0;lastDay=day;}
                final int position=p;ImageView image=thumbnail(item.file,700);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription("相册图片："+item.note.heading()+"："+(item.imageIndex+1));image.setOnClickListener(v->albumPhoto(position));LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(104),1);if(cells>0)cell.leftMargin=dp(3);row.addView(image,cell);cells++;
                if(cells==3){content.addView(row);gap(content,3);row=row();cells=0;}
            }
            if(row!=null&&cells>0){while(cells++<3)row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(104),1));content.addView(row);}
        }
        LinearLayout footer=row();footer.setPadding(dp(20),dp(10),dp(20),dp(12));TextView categories=button("分类",false,this::categoriesDialog);footer.addView(categories,lp(76,54));TextView add=button("＋  添加照片",true,this::albumPicker);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(54),1);ap.leftMargin=dp(12);footer.addView(add,ap);root.addView(footer);
    }
    void albumPicker(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);try{startActivityForResult(i,11);}catch(ActivityNotFoundException e){error("手机未提供图片选择器。");}}
    void albumPhoto(int index){
        ArrayList<AlbumItem> items=albumItems();if(items.isEmpty())return;index=Math.max(0,Math.min(index,items.size()-1));albumIndex=index;AlbumItem item=items.get(index);current=item.note;editing=null;base("albumPhoto");root.setBackgroundColor(0xff18232b);getWindow().setStatusBarColor(0xff18232b);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout bar=row();bar.setPadding(dp(8),dp(4),dp(10),dp(4));TextView back=text("‹",32,WHITE,false);back.setGravity(Gravity.CENTER);back.setContentDescription("返回");back.setOnClickListener(v->{restoreSystemBars();album();});bar.addView(back,lp(48,48));TextView number=text((index+1)+" / "+items.size(),14,WHITE,true);number.setGravity(Gravity.CENTER);bar.addView(number,new LinearLayout.LayoutParams(0,dp(48),1));bar.addView(new View(this),lp(48,48));root.addView(bar);
        Bitmap bitmap=decode(new File(store.photos,item.file),2800);if(bitmap!=null){Runnable previous=index>0?()->albumPhoto(albumIndex-1):null,next=index+1<items.size()?()->albumPhoto(albumIndex+1):null;root.addView(new ZoomImage(bitmap,previous,next,this::album),new LinearLayout.LayoutParams(-1,0,1));}else{TextView unavailable=text("图片无法读取",18,WHITE,false);unavailable.setGravity(Gravity.CENTER);root.addView(unavailable,new LinearLayout.LayoutParams(-1,0,1));}
        LinearLayout bottom=row();bottom.setPadding(dp(16),dp(6),dp(16),dp(10));String info=new SimpleDateFormat("M月d日",Locale.CHINA).format(new Date(item.time))+"  ·  "+store.categoryName(item.note.category);TextView meta=text(info,13,0xffdbe6ec,false);bottom.addView(meta,new LinearLayout.LayoutParams(0,dp(44),1));TextView open=text("打开记录",14,WHITE,true);open.setGravity(Gravity.CENTER);open.setPadding(dp(10),0,dp(10),0);open.setContentDescription("打开记录");open.setOnClickListener(v->{restoreSystemBars();editorReturn="album";detail(item.note,false);});bottom.addView(open,lp(-2,44));root.addView(bottom);
    }
    void renderList(){
        if(list==null||!"home".equals(screen))return;homeList.setAdapter(null);list.removeAllViews();
        if(!searchOpen&&store.draft!=null&&store.draft.hasContent()&&query.isEmpty()){
            TextView d=button("继续上次没写完的记录",false,()->editor(store.draft.copy()));d.setTextColor(ACCENT);list.addView(d,lp(-1,-2));gap(list,14);
        }
        ArrayList<Store.Note> found=new ArrayList<>();String q=query.toLowerCase(Locale.ROOT);
        for(Store.Note n:store.notes)if(!n.deleted&&(filter.equals("all")||filter.equals(n.category))&&(n.title+"\n"+n.body).toLowerCase(Locale.ROOT).contains(q))found.add(n);
        Collections.sort(found,(a,b)->Long.compare(b.updated,a.updated));
        if(found.isEmpty()){
            LinearLayout empty=column();empty.setPadding(dp(20),dp(18),dp(20),dp(18));empty.setBackground(bg(homePanelColor(),20,LINE));
            empty.addView(text(query.isEmpty()?"把需要的，先收好。":"没有找到这条记录",18,INK,true));
            if(!query.isEmpty()){gap(empty,12);TextView hint=text("换个词试试，或清空搜索。",14,MUTED,false);empty.addView(hint);gap(empty,14);empty.addView(button("清空搜索",false,()->searchInput.setText("")));}
            list.addView(empty,lp(-1,-2));return;
        }
        homeList.setAdapter(new BaseAdapter(){public int getCount(){return found.size();}public Object getItem(int p){return found.get(p);}public long getItemId(int p){return p;}public View getView(int p,View recycled,ViewGroup parent){LinearLayout wrap=column();wrap.setPadding(dp(20),0,dp(20),dp(10));wrap.addView(noteCard(found.get(p)),lp(-1,-2));return wrap;}});
    }
    int categoryColor(String id){if(id.isEmpty())return ACCENT;int color=Math.floorMod(id.hashCode(),3);return color==0?0xff9d642c:color==1?0xff5568a1:ACCENT;}
    int categoryTint(String id){if(id.isEmpty())return 0xffe6f2f3;int color=Math.floorMod(id.hashCode(),3);return color==0?0xfffcf1e4:color==1?0xffeef0fb:0xffe6f2f3;}
    boolean showCardCategory(){return filter.equals("all");}
    boolean showCardBody(Store.Note n){String body=n.body.trim(),heading=n.heading().trim();return !body.isEmpty()&&!body.equals(heading);}
    LinearLayout noteCard(Store.Note n){
        boolean hasImage=!n.images.isEmpty();LinearLayout c=column();c.setPadding(dp(13),dp(hasImage?11:9),dp(13),dp(hasImage?9:7));c.setBackground(ripple(homePanelColor(),18,LINE));
        if(showCardCategory()){LinearLayout meta=row();TextView tag=text(store.categoryName(n.category),12,categoryColor(n.category),true);tag.setPadding(dp(9),dp(4),dp(9),dp(4));tag.setBackground(bg(categoryTint(n.category),8,0));meta.addView(tag);c.addView(meta);gap(c,6);}
        TextView h=text(n.heading(),17,INK,true);h.setMaxLines(1);h.setEllipsize(TextUtils.TruncateAt.END);c.addView(h);
        if(showCardBody(n)){gap(c,4);TextView body=text(n.body,15,MUTED,false);body.setMaxLines(2);body.setEllipsize(TextUtils.TruncateAt.END);c.addView(body);}
        if(hasImage){gap(c,7);ImageView image=thumbnail(n.images.get(0),1200);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription("首页预览图："+n.heading());c.addView(image,lp(-1,64));}
        gap(c,hasImage?7:5);LinearLayout bottom=row();String date=new SimpleDateFormat("M月d日",Locale.CHINA).format(new Date(n.updated));bottom.addView(text(date+(n.images.isEmpty()?"":"  ·  "+n.images.size()+" 张图"),13,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));
        TextView copy=text("复制文字",13,ACCENT,true);copy.setGravity(Gravity.CENTER);copy.setPadding(dp(11),dp(7),dp(11),dp(7));copy.setMinHeight(dp(36));copy.setBackground(ripple(0xffedf6f7,11,0));copy.setOnClickListener(v->copy(n));bottom.addView(copy);c.addView(bottom);
        c.setContentDescription("记录卡片："+n.heading());c.setOnLongClickListener(v->{hideKeyboard();confirmDelete(n);return true;});
        c.setOnClickListener(v->{scrollPosition=homeList.getFirstVisiblePosition();hideKeyboard();editorReturn="home";detail(n,false);});return c;
    }
    void copy(Store.Note n){String value=n.body.trim().isEmpty()?n.title:n.body;if(value.isEmpty()){toast("这条记录只有图片");return;}((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(n.heading(),value));if(Build.VERSION.SDK_INT<33)toast("已复制");}
    void confirmDelete(Store.Note n){new AlertDialog.Builder(this).setTitle("移到回收站？").setMessage("之后可以在「更多 · 回收站」找回。").setPositiveButton("删除",(d,w)->{if(commit(()->{n.deleted=true;n.updated=System.currentTimeMillis();}))home();}).setNegativeButton("取消",null).show();}
    void newNote(String category){
        if(store.draft!=null&&store.draft.hasContent()){new AlertDialog.Builder(this).setTitle("还有一条草稿").setMessage("先保存草稿，再开始新记录。") .setPositiveButton("继续草稿",(d,w)->editor(store.draft.copy())).setNegativeButton("取消",null).show();return;}
        Store.Note n=new Store.Note();n.category=category!=null?category:(filter.equals("all")?Store.UNFILED:filter);editorReturn="home";editor(n);
    }
    EditText input(String hint,int size,boolean multiline){EditText e=new EditText(this);e.setTextSize(size);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setBackgroundColor(Color.TRANSPARENT);e.setPadding(0,dp(5),0,dp(5));e.setGravity(Gravity.TOP);e.setHint(hint);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|(multiline?android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE:android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));if(!multiline)e.setSingleLine();e.setSelectAllOnFocus(false);e.setLineSpacing(dp(6),1);return e;}
    void editor(Store.Note note){
        editing=note;current=null;changing=true;base("edit");top(store.find(note.id)==null?"新记录":"编辑记录",this::autoSaveAndLeave,null,null);
        LinearLayout content=column();content.setPadding(dp(20),dp(4),dp(20),dp(24));scroller(content);
        LinearLayout field=column();field.setPadding(dp(20),dp(18),dp(20),dp(20));field.setBackground(bg(WHITE,24,LINE));
        categoryButton=text(categoryLabel(note.category)+"  ▾",14,categoryColor(note.category),true);categoryButton.setPadding(dp(12),dp(10),dp(12),dp(10));categoryButton.setBackground(ripple(categoryTint(note.category),10,0));categoryButton.setContentDescription("选择记录分类");categoryButton.setOnClickListener(v->pickCategory());field.addView(categoryButton,lp(-2,42));gap(field,10);
        titleInput=input("标题",18,false);titleInput.setTypeface(Typeface.create("sans-serif-medium",0));titleInput.setContentDescription("记录标题");titleInput.setText(note.title);field.addView(titleInput,lp(-1,-2));gap(field,4);
        View divider=new View(this);divider.setBackgroundColor(LINE);field.addView(divider,lp(-1,1));gap(field,8);
        bodyInput=input("写下想留住的内容…",17,true);bodyInput.setContentDescription("记录内容");bodyInput.setMinLines(6);bodyInput.setText(note.body);field.addView(bodyInput,lp(-1,-2));content.addView(field);
        gap(content,22);LinearLayout photosHeading=row();photosHeading.addView(text("参考图片",16,INK,true),new LinearLayout.LayoutParams(0,-2,1));TextView add=text("＋ 添加图片",15,ACCENT,true);add.setGravity(Gravity.CENTER);add.setMinHeight(dp(46));add.setPadding(dp(10),0,0,0);add.setOnClickListener(v->photoPicker());photosHeading.addView(add);content.addView(photosHeading);
        photoStrip=column();content.addView(photoStrip);renderEditorPhotos();
        LinearLayout footer=row();footer.setPadding(dp(20),dp(10),dp(20),dp(12));TextView cancel=button("取消",false,this::leaveEditor);cancel.setContentDescription("取消编辑");footer.addView(cancel,lp(88,54));TextView save=button("保存",true,this::saveNote);save.setContentDescription("保存记录");LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(54),1);p.leftMargin=dp(12);footer.addView(save,p);root.addView(footer);
        changing=false;TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){if(!changing){if(draftTask!=null)handler.removeCallbacks(draftTask);draftTask=()->captureDraft();handler.postDelayed(draftTask,350);}}public void afterTextChanged(Editable e){}};titleInput.addTextChangedListener(watcher);bodyInput.addTextChangedListener(watcher);root.requestFocus();
    }
    void updateEditing(){if(editing!=null&&"edit".equals(screen)&&titleInput!=null){editing.title=titleInput.getText().toString();editing.body=bodyInput.getText().toString();}}
    void captureDraft(){if(editing==null||!"edit".equals(screen)||changing||!store.ready)return;updateEditing();if(editing.hasContent())editing.updated=System.currentTimeMillis();Store.Note d=editing.hasContent()?editing.copy():null;if(store.change(()->store.draft=d))markSyncDirty(false);else if(!draftWarning){draftWarning=true;toast("草稿没有保存成功，请检查手机剩余空间。");}}
    String categoryLabel(String id){return Store.UNFILED.equals(id)?"选择分类":store.categoryName(id);}
    void selectCategory(String id){editing.category=id;categoryButton.setText(categoryLabel(id)+"  ▾");categoryButton.setTextColor(categoryColor(id));categoryButton.setBackground(ripple(categoryTint(id),10,0));captureDraft();}
    void pickCategory(){updateEditing();String[] names=new String[store.categories.size()+2];names[0]="暂不分类";for(int i=0;i<store.categories.size();i++)names[i+1]=store.categories.get(i).name;names[names.length-1]="＋ 新建分类";new AlertDialog.Builder(this).setTitle("放在哪个分类").setItems(names,(d,w)->{if(w==names.length-1)categoryNameDialog(null,c->selectCategory(c.id));else selectCategory(w==0?Store.UNFILED:store.categories.get(w-1).id);}).setNegativeButton("取消",null).show();}
    void saveNote(){
        updateEditing();if(!editing.hasContent()){toast("写点文字或添加图片再保存");return;}Store.Note n=editing.copy();n.updated=System.currentTimeMillis();n.normalizeImageTimes();if(commit(()->{Store.Note old=store.find(n.id);if(old!=null)store.notes.remove(old);store.notes.add(n);store.draft=null;})){editing=null;hideKeyboard();query="";filter=n.category;scrollPosition=0;current=n;detail(n,false);}
    }
    void autoSaveAndLeave(){
        updateEditing();hideKeyboard();if(editing==null)return;
        Store.Note original=store.find(editing.id);
        if(!editing.hasContent()){
            if(commit(()->store.draft=null)){editing=null;if("album".equals(editorReturn))album();else home();}
            return;
        }
        Store.Note n=editing.copy();n.updated=System.currentTimeMillis();n.normalizeImageTimes();
        if(commit(()->{Store.Note old=store.find(n.id);if(old!=null)store.notes.remove(old);store.notes.add(n);store.draft=null;})){
            editing=null;query="";filter=n.category;scrollPosition=0;if("detail".equals(editorReturn))detail(n,false);else if("album".equals(editorReturn))album();else home();
        }
    }
    void leaveEditor(){updateEditing();hideKeyboard();if(editing==null||!editing.hasContent()){if(commit(()->store.draft=null)){editing=null;if("album".equals(editorReturn))album();else home();}return;}
        new AlertDialog.Builder(this).setTitle("保留这次编辑吗？").setItems(new String[]{"保存记录","保留草稿","放弃这次编辑"},(d,w)->{if(w==0)saveNote();else if(w==1){Store.Note note=editing.copy();if(commit(()->store.draft=note)){editing=null;if("album".equals(editorReturn))album();else home();}}else if(commit(()->store.draft=null)){editing=null;if("album".equals(editorReturn))album();else home();}}).setNegativeButton("继续写",null).show();
    }
    ImageView thumbnail(String filename,int max){ImageView i=new ImageView(this);i.setBackground(bg(0xffeaf0f4,14,0));i.setClipToOutline(true);i.setAdjustViewBounds(true);try{Bitmap b=decode(new File(store.photos,filename),max);if(b!=null)i.setImageBitmap(b);else i.setContentDescription("图片无法读取");}catch(Exception e){i.setContentDescription("图片无法读取");}return i;}
    static Bitmap decode(File f,int max){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.toString(),o);if(o.outWidth<=0||o.outHeight<=0)return null;int sample=1;while(Math.max(o.outWidth,o.outHeight)/sample>max)sample*=2;o.inSampleSize=sample;o.inJustDecodeBounds=false;Bitmap b=BitmapFactory.decodeFile(f.toString(),o);if(b==null)return null;try{android.media.ExifInterface exif=new android.media.ExifInterface(f.toString());int orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);Matrix m=new Matrix();switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;}if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(rotated!=b)b.recycle();b=rotated;}}catch(Exception ignored){}return b;}
    void renderEditorPhotos(){photoStrip.removeAllViews();if(editing.images.isEmpty()){TextView hint=text("从相册添加，文字和图片一起保存。",14,MUTED,false);hint.setPadding(0,dp(5),0,dp(6));photoStrip.addView(hint);return;}
        editing.normalizeImageTimes();for(int index=0;index<editing.images.size();index++){String name=editing.images.get(index);final int photoIndex=index;LinearLayout r=row();r.setPadding(0,dp(8),0,dp(8));ImageView im=thumbnail(name,1000);im.setScaleType(ImageView.ScaleType.CENTER_CROP);im.setContentDescription("参考图片 "+(index+1));im.setOnClickListener(v->{updateEditing();captureDraft();fullEditorPhoto(photoIndex);});r.addView(im,new LinearLayout.LayoutParams(0,dp(112),1));TextView remove=icon("close","移除图片",()->new AlertDialog.Builder(this).setTitle("移除这张图片？").setMessage("只从这条记录移除，相册原图不受影响。").setPositiveButton("移除",(d,w)->{int at=editing.images.indexOf(name);if(at>=0){editing.images.remove(at);if(at<editing.imageTimes.size())editing.imageTimes.remove(at);}captureDraft();renderEditorPhotos();}).setNegativeButton("取消",null).show());r.addView(remove,lp(52,52));photoStrip.addView(r);}}
    void photoPicker(){captureDraft();Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);try{startActivityForResult(i,10);}catch(ActivityNotFoundException e){error("手机未提供图片选择器。");}}
    void fullEditorPhoto(int index){if(editing==null||index<0||index>=editing.images.size())return;base("photo");root.setBackgroundColor(0xff18232b);getWindow().setStatusBarColor(0xff18232b);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);LinearLayout bar=row();bar.setPadding(dp(12),dp(8),dp(12),dp(8));TextView back=button("返回",false,()->{restoreSystemBars();editor(editing);});bar.addView(back,lp(70,48));TextView number=text((index+1)+" / "+editing.images.size(),16,WHITE,true);number.setGravity(Gravity.CENTER);bar.addView(number,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(bar);Bitmap bitmap=decode(new File(store.photos,editing.images.get(index)),2800);if(bitmap!=null)root.addView(new ZoomImage(bitmap),new LinearLayout.LayoutParams(-1,0,1));else{TextView unavailable=text("图片无法读取",18,WHITE,false);unavailable.setGravity(Gravity.CENTER);root.addView(unavailable,new LinearLayout.LayoutParams(-1,0,1));}LinearLayout nav=row();nav.setPadding(dp(20),dp(10),dp(20),dp(16));if(index>0)nav.addView(button("上一张",false,()->fullEditorPhoto(index-1)),new LinearLayout.LayoutParams(0,dp(50),1));else nav.addView(new View(this),new LinearLayout.LayoutParams(0,dp(50),1));gapHorizontal(nav,12);if(index+1<editing.images.size())nav.addView(button("下一张",false,()->fullEditorPhoto(index+1)),new LinearLayout.LayoutParams(0,dp(50),1));else nav.addView(new View(this),new LinearLayout.LayoutParams(0,dp(50),1));root.addView(nav);}
    void detail(Store.Note n,boolean ignored){
        editing=null;current=n;base("detail");top("记录",()->{current=null;if("album".equals(editorReturn))album();else home();},"编辑",()->{editorReturn="detail";editor(n.copy());});
        LinearLayout content=column();content.setPadding(dp(20),dp(6),dp(20),dp(24));scroller(content);LinearLayout card=column();card.setPadding(dp(22),dp(23),dp(22),dp(22));card.setBackground(bg(WHITE,24,LINE));
        TextView tag=text(store.categoryName(n.category),13,categoryColor(n.category),true);card.addView(tag);gap(card,10);TextView h=text(n.heading(),20,INK,true);h.setTextIsSelectable(true);card.addView(h);if(!n.body.trim().isEmpty()){gap(card,12);TextView body=text(n.body,16,INK,false);body.setTextIsSelectable(true);body.setLineSpacing(dp(5),1);card.addView(body);}content.addView(card);
        if(!n.images.isEmpty()){gap(content,25);content.addView(text("参考图片 · 点击放大",14,MUTED,true));gap(content,12);for(int i=0;i<n.images.size();i++){final int index=i;ImageView image=thumbnail(n.images.get(i),1200);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription("参考图片 "+(i+1));image.setOnClickListener(v->fullPhoto(index));content.addView(image,lp(-1,-2));gap(content,12);}}
    }
    void fullPhoto(int index){base("photo");root.setBackgroundColor(0xff18232b);getWindow().setStatusBarColor(0xff18232b);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);LinearLayout bar=row();bar.setPadding(dp(12),dp(8),dp(12),dp(8));TextView back=button("返回",false,()->{restoreSystemBars();detail(current,false);});bar.addView(back,lp(70,48));TextView number=text((index+1)+" / "+current.images.size(),16,WHITE,true);number.setGravity(Gravity.CENTER);bar.addView(number,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(bar);Bitmap bitmap=decode(new File(store.photos,current.images.get(index)),2800);if(bitmap!=null)root.addView(new ZoomImage(bitmap),new LinearLayout.LayoutParams(-1,0,1));else{TextView unavailable=text("图片无法读取",18,WHITE,false);unavailable.setGravity(Gravity.CENTER);root.addView(unavailable,new LinearLayout.LayoutParams(-1,0,1));}LinearLayout nav=row();nav.setPadding(dp(20),dp(10),dp(20),dp(16));if(index>0)nav.addView(button("上一张",false,()->fullPhoto(index-1)),new LinearLayout.LayoutParams(0,dp(50),1));else nav.addView(new View(this),new LinearLayout.LayoutParams(0,dp(50),1));gapHorizontal(nav,12);if(index+1<current.images.size())nav.addView(button("下一张",false,()->fullPhoto(index+1)),new LinearLayout.LayoutParams(0,dp(50),1));else nav.addView(new View(this),new LinearLayout.LayoutParams(0,dp(50),1));root.addView(nav);}
    void gapHorizontal(LinearLayout row,int w){row.addView(new View(this),lp(w,1));}
    void restoreSystemBars(){getWindow().setStatusBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
    String[] moreMenuItems(){return new String[]{"管理分类","背景图","多端同步","导出备份（含图片）","导入备份","回收站","关于","检查更新"};}
    void more(){new AlertDialog.Builder(this).setTitle("随手存").setItems(moreMenuItems(),(d,w)->{if(w==0)categoriesDialog();else if(w==1)backgroundDialog();else if(w==2)cloudSyncDialog();else if(w==3)exportPicker();else if(w==4)importPicker();else if(w==5)trash();else if(w==6)new AlertDialog.Builder(this).setTitle("随手存 "+appVersionName()).setMessage("文字、图片、分类和背景都保存在本机。\n\n开启「多端同步」后，可在另一台手机用同一个同步码取回并继续同步。\n\n导出备份仍可作为额外的本地备份。").setPositiveButton("知道了",null).show();else checkUpdate(true);}).show();}
    void cloudSyncDialog(){
        if(cloudSync==null)return;
        if(!cloudSync.enabled()){
            new AlertDialog.Builder(this).setTitle("多端同步")
                .setItems(new String[]{"开启多端同步","连接已有同步"},(d,w)->{if(w==0)enableCloudSync();else connectCloudSyncDialog();})
                .setNegativeButton("取消",null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("多端同步 · "+cloudSync.lastSyncLabel())
            .setItems(new String[]{"立即同步","查看同步码","断开这台手机"},(d,w)->{
                if(w==0)syncCloud(true);else if(w==1)showSyncCode();else new AlertDialog.Builder(this).setTitle("断开这台手机？")
                    .setMessage("只会停止这台手机同步，云端数据和其他手机不会删除。")
                    .setPositiveButton("断开",(x,y)->{cloudSync.disconnect();toast("这台手机已断开同步");})
                    .setNegativeButton("取消",null).show();
            }).setNegativeButton("关闭",null).show();
    }
    void enableCloudSync(){
        cloudSync.beginNew();
        busySync("正在开启多端同步…",()->{CloudSync.Result result=cloudSync.syncNow(false);runOnUiThread(()->{if(result.ok)showSyncCode();else error(result.message);});});
    }
    void connectCloudSyncDialog(){
        EditText e=input("粘贴同步码",16,false);e.setPadding(dp(22),dp(14),dp(22),dp(14));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("连接已有同步").setMessage("如果这台手机已经有记录，会和云端内容合并，不会直接清空。").setView(e).setPositiveButton("连接",null).setNegativeButton("取消",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(b->{String code=e.getText().toString();if(!CloudSync.validCode(code)){e.setError("同步码不正确");return;}d.dismiss();busySync("正在同步数据…",()->{CloudSync.Result result=cloudSync.connectExisting(code);runOnUiThread(()->{if(result.ok){filter="all";query="";home();toast("已连接并同步完成");}else error(result.message);});});}));
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);d.show();
    }
    void showSyncCode(){
        String raw=cloudSync.code();TextView code=text(CloudSync.displayCode(raw),16,INK,true);code.setTextIsSelectable(true);code.setPadding(dp(22),dp(18),dp(22),dp(18));
        new AlertDialog.Builder(this).setTitle("同步码").setMessage("在另一台手机安装随手存后：··· → 多端同步 → 连接已有同步，再粘贴这个码。\n\n同步码相当于这份数据的钥匙，不要公开发给别人。")
            .setView(code).setPositiveButton("复制同步码",(d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("随手存同步码",raw));toast("同步码已复制");})
            .setNegativeButton("关闭",null).show();
    }

    static class UpdateInfo {long code;String name,url;UpdateInfo(long c,String n,String u){code=c;name=n;url=u;}}
    String appVersionName(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "";}}
    long appVersionCode(){try{android.content.pm.PackageInfo p=getPackageManager().getPackageInfo(getPackageName(),0);return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}catch(Exception e){return 0;}}
    UpdateInfo parseUpdate(String json)throws Exception{
        JSONObject release=new JSONObject(json);String tag=release.optString("tag_name","");long code=0;if(tag.startsWith("v"))try{code=Long.parseLong(tag.substring(1));}catch(Exception ignored){}
        String name=release.optString("name",tag);String url=null;JSONArray assets=release.optJSONArray("assets");if(assets!=null)for(int i=0;i<assets.length();i++){JSONObject a=assets.getJSONObject(i);if("Suishoucun.apk".equals(a.optString("name"))){url=a.optString("browser_download_url");break;}}
        if(code<=0||url==null||!url.startsWith("https://github.com/"))throw new IOException("更新信息无效");return new UpdateInfo(code,name,url);
    }
    String readUrl(String address)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("User-Agent","Suishoucun-Android");int status=c.getResponseCode();if(status<200||status>=300)throw new IOException("HTTP "+status);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0){out.write(buf,0,n);if(out.size()>2*1024*1024)throw new IOException("响应过大");}return out.toString("UTF-8");}finally{c.disconnect();}
    }
    void maybeAutoCheckUpdate(){long now=System.currentTimeMillis();android.content.SharedPreferences p=getSharedPreferences("update",MODE_PRIVATE);long last=p.getLong("last_check",0);if(now-last<24L*60*60*1000)return;p.edit().putLong("last_check",now).apply();checkUpdate(false);}
    void checkUpdate(boolean manual){
        if(manual)toast("正在检查更新…");
        new Thread(()->{try{UpdateInfo info=parseUpdate(readUrl("https://api.github.com/repos/qxdnzbl-arch/socialmix-android-ci/releases/latest"));long local=appVersionCode();runOnUiThread(()->{if(info.code>local)showUpdate(info);else if(manual)toast("已经是最新版");});}catch(Exception e){if(manual)runOnUiThread(()->error("暂时无法检查更新，请稍后再试。"));}}).start();
    }
    void showUpdate(UpdateInfo info){new AlertDialog.Builder(this).setTitle("发现新版本").setMessage((info.name==null||info.name.isEmpty()?"新版":info.name)+"\n\n点更新后会自动下载，下载完成后只需要确认系统安装。").setPositiveButton("更新",(d,w)->downloadUpdate(info)).setNegativeButton("稍后",null).show();}
    void downloadUpdate(UpdateInfo info){
        busy("正在下载更新…",()->{File temp=new File(getCacheDir(),"update.tmp");try{HttpURLConnection c=(HttpURLConnection)new URL(info.url).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","Suishoucun-Android");int status=c.getResponseCode();if(status<200||status>=400)throw new IOException("HTTP "+status);try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(temp)){byte[] buf=new byte[64*1024];int n;long total=0;while((n=in.read(buf))>0){out.write(buf,0,n);total+=n;if(total>100L*1024*1024)throw new IOException("文件过大");}}finally{c.disconnect();}if(temp.length()<20*1024)throw new IOException("下载不完整");if(downloadedUpdate.exists()&&!downloadedUpdate.delete())throw new IOException();if(!temp.renameTo(downloadedUpdate))throw new IOException();runOnUiThread(this::installDownloadedUpdate);}catch(Exception e){temp.delete();runOnUiThread(()->error("更新下载失败，请稍后再试。"));}});
    }
    void installDownloadedUpdate(){
        if(downloadedUpdate==null||!downloadedUpdate.isFile()){error("更新文件不存在，请重新检查更新。");return;}
        if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){try{Intent permission=new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName()));startActivityForResult(permission,40);toast("请允许「安装未知应用」，返回后会继续更新");}catch(Exception e){error("无法打开安装权限设置。");}return;}
        try{Uri uri=Uri.parse("content://"+getPackageName()+".update/update.apk");Intent install=new Intent(Intent.ACTION_VIEW);install.setDataAndType(uri,"application/vnd.android.package-archive");install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(install);}catch(Exception e){error("无法打开系统安装器。");}
    }
    void backgroundDialog(){if(backgroundFile!=null&&backgroundFile.isFile())new AlertDialog.Builder(this).setTitle("背景图").setItems(new String[]{"更换背景图","恢复默认背景"},(d,w)->{if(w==0)backgroundPicker();else{backgroundFile.delete();markSyncDirty(true);home();}}).setNegativeButton("取消",null).show();else backgroundPicker();}
    void backgroundPicker(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(i,30);}catch(ActivityNotFoundException e){error("手机未提供图片选择器。");}}
    void categoriesDialog(){boolean albumMode="album".equals(screen);Runnable refresh=albumMode?this::album:this::home;String[] options=new String[store.categories.size()+1];for(int i=0;i<store.categories.size();i++)options[i]=store.categories.get(i).name;options[options.length-1]="＋ 新建分类";new AlertDialog.Builder(this).setTitle("管理分类").setItems(options,(d,w)->{if(w==options.length-1)categoryNameDialog(null,c->refresh.run());else{Store.Category c=store.categories.get(w);new AlertDialog.Builder(this).setTitle(c.name).setItems(new String[]{"重命名","删除分类"},(dd,ww)->{if(ww==0)categoryNameDialog(c,x->refresh.run());else new AlertDialog.Builder(this).setTitle("删除分类？").setMessage("分类里的记录会保留为未分类，图片和草稿也会保留。").setPositiveButton("删除分类",(a,b)->{if(commit(()->{for(Store.Note n:store.notes)if(n.category.equals(c.id))n.category=Store.UNFILED;if(store.draft!=null&&store.draft.category.equals(c.id))store.draft.category=Store.UNFILED;store.categories.remove(c);})){filter="all";refresh.run();}}).setNegativeButton("取消",null).show();}).show();}}).setNegativeButton("关闭",null).show();}
    void categoryNameDialog(Store.Category category){categoryNameDialog(category,c->home());}
    void categoryNameDialog(Store.Category category,java.util.function.Consumer<Store.Category> after){EditText e=input("分类名称",18,false);e.setPadding(dp(22),dp(14),dp(22),dp(14));e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});if(category!=null)e.setText(category.name);AlertDialog d=new AlertDialog.Builder(this).setTitle(category==null?"新建分类":"重命名").setView(e).setPositiveButton("保存",null).setNegativeButton("取消",null).create();d.setOnShowListener(v->{d.getButton(-1).setOnClickListener(b->{String name=e.getText().toString().trim();if(name.isEmpty()){e.setError("请输入名称");return;}for(Store.Category c:store.categories)if(c!=category&&c.name.equals(name)){e.setError("已经有这个分类");return;}Store.Category target=category==null?new Store.Category(UUID.randomUUID().toString(),name):category;if(commit(()->{if(category==null)store.categories.add(target);else category.name=name;})){d.dismiss();after.accept(target);}});});d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);d.show();}
    void trash(){editing=null;current=null;base("trash");top("回收站",this::home,null,null);LinearLayout content=column();content.setPadding(dp(20),dp(10),dp(20),dp(20));scroller(content);ArrayList<Store.Note> removed=new ArrayList<>();for(Store.Note n:store.notes)if(n.deleted)removed.add(n);if(removed.isEmpty()){TextView e=text("这里还没有删除的记录",18,MUTED,false);e.setPadding(0,dp(26),0,0);content.addView(e);}for(Store.Note n:removed){LinearLayout c=column();c.setPadding(dp(20),dp(18),dp(20),dp(18));c.setBackground(bg(WHITE,20,LINE));c.addView(text(n.heading(),19,INK,true));gap(c,14);LinearLayout actions=row();actions.addView(button("恢复",true,()->{if(commit(()->{n.deleted=false;n.updated=System.currentTimeMillis();}))trash();}),new LinearLayout.LayoutParams(0,dp(48),1));gapHorizontal(actions,10);actions.addView(button("彻底删除",false,()->new AlertDialog.Builder(this).setTitle("彻底删除？").setMessage("这条记录将无法恢复。").setPositiveButton("彻底删除",(d,w)->{if(commit(()->store.notes.remove(n)))trash();}).setNegativeButton("取消",null).show()),new LinearLayout.LayoutParams(0,dp(48),1));c.addView(actions);content.addView(c);gap(content,12);}}
    void exportPicker(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,"随手存备份-"+new SimpleDateFormat("yyyyMMdd-HHmm",Locale.ROOT).format(new Date())+".zip");try{startActivityForResult(i,20);}catch(Exception e){error("手机未提供文件保存入口。");}}
    void importPicker(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/octet-stream","application/x-zip-compressed"});try{startActivityForResult(i,21);}catch(Exception e){error("手机未提供文件选择器。");}}
    void busy(String label,Runnable background){ProgressDialog p=new ProgressDialog(this);p.setMessage(label);p.setCancelable(false);p.show();new Thread(()->{try{background.run();}finally{runOnUiThread(()->{if(!isFinishing())p.dismiss();});}}).start();}
    void busySync(String label,Runnable background){
        ProgressDialog p=new ProgressDialog(this);p.setMessage(label);p.setCancelable(false);p.show();
        new Thread(()->{
            cloudSync.progress=stage->runOnUiThread(()->{if(!isFinishing()&&p.isShowing())p.setMessage(stage);});
            try{background.run();}
            finally{cloudSync.progress=null;runOnUiThread(()->{if(!isFinishing())p.dismiss();});}
        }).start();
    }
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==40){if(Build.VERSION.SDK_INT<26||getPackageManager().canRequestPackageInstalls())installDownloadedUpdate();return;}if(result!=RESULT_OK||data==null)return;
        if(request==10&&editing!=null){ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());Store.Note note=editing;
            busy("正在保存图片…",()->{int failed=0;ArrayList<String> success=new ArrayList<>();for(Uri uri:uris){File target=new File(store.photos,UUID.randomUUID()+".img");try{InputStream in=getContentResolver().openInputStream(uri);if(in==null)throw new IOException();Store.copy(in,new FileOutputStream(target),64*1024*1024);BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(target.toString(),o);if(o.outWidth<=0||o.outHeight<=0)throw new IOException();success.add(target.getName());}catch(Exception e){target.delete();failed++;}}int errors=failed;runOnUiThread(()->{long now=System.currentTimeMillis();for(int i=0;i<success.size();i++){note.images.add(success.get(i));note.imageTimes.add(now+i);}captureDraft();renderEditorPhotos();if(errors>0)error(errors+" 张图片没能读取，其他图片已保存。");});});
        }else if(request==11){ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());String targetCategory=filter.equals("all")?Store.UNFILED:filter;
            busy("正在加入相册…",()->{int failed=0;ArrayList<Store.Note> added=new ArrayList<>();ArrayList<File> copied=new ArrayList<>();long now=System.currentTimeMillis();for(int u=0;u<uris.size();u++){Uri uri=uris.get(u);File target=new File(store.photos,UUID.randomUUID()+".img");try{InputStream in=getContentResolver().openInputStream(uri);if(in==null)throw new IOException();Store.copy(in,new FileOutputStream(target),64*1024*1024);BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(target.toString(),o);if(o.outWidth<=0||o.outHeight<=0)throw new IOException();Store.Note n=new Store.Note();n.category=targetCategory;n.updated=now+u;n.images.add(target.getName());n.imageTimes.add(now+u);added.add(n);copied.add(target);}catch(Exception e){target.delete();failed++;}}int errors=failed;runOnUiThread(()->{if(added.isEmpty()){if(errors>0)error("图片没有加入，请换一张再试。");return;}if(store.change(()->store.notes.addAll(added))){markSyncDirty(false);album();toast("已加入 "+added.size()+" 张照片");if(errors>0)error(errors+" 张图片没能读取，其他图片已加入。");}else{for(File file:copied)file.delete();error("照片没有保存成功，请检查手机剩余空间。");}});});
        }else if(request==30&&data.getData()!=null){Uri uri=data.getData();busy("正在设置背景图…",()->{File temp=new File(getFilesDir(),"background.tmp");try{InputStream in=getContentResolver().openInputStream(uri);if(in==null)throw new IOException();Store.copy(in,new FileOutputStream(temp),64*1024*1024);BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(temp.toString(),o);if(o.outWidth<=0||o.outHeight<=0)throw new IOException();if(backgroundFile.exists()&&!backgroundFile.delete())throw new IOException();if(!temp.renameTo(backgroundFile))throw new IOException();runOnUiThread(()->{markSyncDirty(true);home();toast("背景图已设置");});}catch(Exception e){temp.delete();runOnUiThread(()->error("背景图没有设置成功。"));}});
        }else if(request==20&&data.getData()!=null){Uri uri=data.getData();busy("正在导出文字和图片…",()->{try{OutputStream out=getContentResolver().openOutputStream(uri);if(out==null)throw new IOException();store.exportZip(out);runOnUiThread(()->toast("备份已保存"));}catch(Exception e){runOnUiThread(()->error("备份没有保存成功。请检查可用空间后重试。"));}});
        }else if(request==21&&data.getData()!=null){Uri uri=data.getData();new AlertDialog.Builder(this).setTitle("导入这份备份？").setMessage("合并到现有记录，不清空手机上的内容。相同记录保留更新的一份。").setPositiveButton("导入",(d,w)->busy("正在导入…",()->{try{InputStream in=getContentResolver().openInputStream(uri);if(in==null)throw new IOException();int count=store.importZip(in);runOnUiThread(()->{markSyncDirty(false);filter="all";query="";home();toast("已导入 "+count+" 条记录");});}catch(Exception e){runOnUiThread(()->error("导入失败，原记录没有改变。请使用本软件导出的完整 ZIP 备份。"));}})).setNegativeButton("取消",null).show();}
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
        Bitmap b;Paint p=new Paint(3);Matrix matrix=new Matrix();float zoom=1,baseScale=1,dx=0,dy=0,lastX,lastY,downX,downY;ScaleGestureDetector scale;GestureDetector gesture;Runnable previous,next,dismiss;
        ZoomImage(Bitmap bitmap){this(bitmap,null,null,null);}
        ZoomImage(Bitmap bitmap,Runnable prev,Runnable nxt,Runnable close){super(MainActivity.this);b=bitmap;previous=prev;next=nxt;dismiss=close;setContentDescription("放大的图片，左右滑动切换，双指缩放，双击还原，下滑返回");scale=new ScaleGestureDetector(MainActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){zoom=Math.max(1,Math.min(5,zoom*d.getScaleFactor()));invalidate();return true;}});gesture=new GestureDetector(MainActivity.this,new GestureDetector.SimpleOnGestureListener(){public boolean onDown(MotionEvent e){return true;}public boolean onDoubleTap(MotionEvent e){zoom=zoom>1?1:2.5f;dx=dy=0;invalidate();return true;}});}
        protected void onDraw(Canvas c){super.onDraw(c);baseScale=Math.min((float)getWidth()/b.getWidth(),(float)getHeight()/b.getHeight());float s=baseScale*zoom;float maxX=Math.max(0,(b.getWidth()*s-getWidth())/2),maxY=Math.max(0,(b.getHeight()*s-getHeight())/2);dx=Math.max(-maxX,Math.min(maxX,dx));dy=Math.max(-maxY,Math.min(maxY,dy));matrix.reset();matrix.postScale(s,s);matrix.postTranslate((getWidth()-b.getWidth()*s)/2+dx,(getHeight()-b.getHeight()*s)/2+dy);c.drawBitmap(b,matrix,p);}
        public boolean onTouchEvent(MotionEvent e){scale.onTouchEvent(e);gesture.onTouchEvent(e);int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){downX=lastX=e.getX();downY=lastY=e.getY();}else if(action==MotionEvent.ACTION_MOVE){if(!scale.isInProgress()&&e.getPointerCount()==1&&zoom>1){dx+=e.getX()-lastX;dy+=e.getY()-lastY;invalidate();}lastX=e.getX();lastY=e.getY();}else if(action==MotionEvent.ACTION_UP&&zoom<=1.01f){float sx=e.getX()-downX,sy=e.getY()-downY;if(Math.abs(sx)>dp(64)&&Math.abs(sx)>Math.abs(sy)*1.25f){if(sx<0&&next!=null)next.run();else if(sx>0&&previous!=null)previous.run();}else if(sy>dp(90)&&Math.abs(sy)>Math.abs(sx)*1.2f&&dismiss!=null)dismiss.run();}return true;}
    }
}
