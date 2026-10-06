package com.qxdnzbl.shuangjichuan;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity implements NativeMessageAdapter.Callbacks {
  private static final int PICK_FILES=7070, PERMS=7071, PICK_BG=7072;
  private static final String SECRET="6686986c94d4a4d34fd705665b962491078a94688d3f730b568d36a2c526c470";

  private final ExecutorService io=Executors.newCachedThreadPool();
  private SharedPreferences prefs;
  private TransferDb db;

  private FrameLayout root;
  private ImageView wallpaper;
  private View wallpaperVeil;
  private AmbientView ambient;
  private TextView status;
  private RecyclerView list;
  private NativeMessageAdapter adapter;
  private EditText input;
  private LinearLayout searchPanel;
  private EditText searchInput;
  private TextView searchCount;
  private int searchCursor=0;
  private PopupWindow contextPopup;
  private Bitmap wallpaperBitmap;
  private long wallpaperVersion=-1L;

  private final BroadcastReceiver receiver=new BroadcastReceiver(){
    @Override public void onReceive(Context c,Intent i){
      if(TransferService.ACTION_CHANGED.equals(i.getAction())) reloadMessages(false);
    }
  };

  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    getWindow().setStatusBarColor(Color.rgb(249,249,251));
    getWindow().setNavigationBarColor(Color.rgb(247,248,251));
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

    prefs=getSharedPreferences("dual",MODE_PRIVATE);
    db=new TransferDb(this);

    String device=prefs.getString("device","");
    if(device.isEmpty()){
      device=UUID.randomUUID().toString();
      prefs.edit().putString("device",device).apply();
    }
    prefs.edit().putString("token",SECRET).putString("account","__private__").apply();

    if(isDebuggable()){
      prefs.edit().putBoolean("ci_nearby_only",getIntent().getBooleanExtra("ciNearbyOnly",false)).apply();
      if(getIntent().getBooleanExtra("ciSend",false)){
        db.addText("ci-local-message",true,"hello",System.currentTimeMillis(),"pending");
      }
      if(getIntent().getBooleanExtra("ciSendFile",false)){
        try{
          File dir=new File(getFilesDir(),"outgoing"); dir.mkdirs();
          File test=new File(dir,"ci-offline-file.txt");
          try(FileOutputStream out=new FileOutputStream(test)){
            out.write("offline-file".getBytes(StandardCharsets.UTF_8));
          }
          db.addFile("ci-local-file",true,"ci-offline-file.txt",test.getAbsolutePath(),test.length(),System.currentTimeMillis()+1,"pending");
        }catch(Exception e){ throw new RuntimeException(e); }
      }
    }

    buildNativeUi();
    applyBackground();
    reloadMessages(true);
    updateStatus();

    requestNearbyPermissions();
    if(hasNearbyPermissions()) TransferService.start(this);
  }

  @Override protected void onStart(){
    super.onStart();
    IntentFilter f=new IntentFilter(TransferService.ACTION_CHANGED);
    if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(receiver,f);
  }

  @Override protected void onStop(){
    try{unregisterReceiver(receiver);}catch(Exception ignored){}
    super.onStop();
  }

  @Override protected void onResume(){
    super.onResume();
    if(hasNearbyPermissions()) TransferService.wake(this);
    reloadMessages(false);
    updateStatus();
  }

  @Override protected void onDestroy(){
    io.shutdownNow();
    if(wallpaperBitmap!=null) wallpaperBitmap.recycle();
    super.onDestroy();
  }

  private void buildNativeUi(){
    root=new FrameLayout(this);
    root.setBackground(makeGradient(new int[]{Color.rgb(252,249,248),Color.rgb(246,248,252),Color.rgb(241,245,251)},GradientDrawable.Orientation.TL_BR,0));

    wallpaper=new ImageView(this);
    wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
    wallpaper.setVisibility(View.GONE);
    root.addView(wallpaper,new FrameLayout.LayoutParams(-1,-1));

    wallpaperVeil=new View(this);
    wallpaperVeil.setVisibility(View.GONE);
    root.addView(wallpaperVeil,new FrameLayout.LayoutParams(-1,-1));

    ambient=new AmbientView(this);
    root.addView(ambient,new FrameLayout.LayoutParams(-1,-1));

    LinearLayout page=new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    root.addView(page,new FrameLayout.LayoutParams(-1,-1));
    page.setOnApplyWindowInsetsListener((v,insets)->{
      int top=0,bottom=0;
      if(Build.VERSION.SDK_INT>=30){
        android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
        android.graphics.Insets ime=insets.getInsets(WindowInsets.Type.ime());
        top=bars.top;
        bottom=Math.max(bars.bottom,ime.bottom);
      }else{
        top=insets.getSystemWindowInsetTop();
        bottom=insets.getSystemWindowInsetBottom();
      }
      v.setPadding(0,top,0,bottom);
      return insets;
    });
    page.requestApplyInsets();

    page.addView(buildHeader(),new LinearLayout.LayoutParams(-1,-2));

    adapter=new NativeMessageAdapter(this,this);
    list=new RecyclerView(this);
    LinearLayoutManager lm=new LinearLayoutManager(this);
    lm.setStackFromEnd(false);
    list.setLayoutManager(lm);
    list.setAdapter(adapter);
    list.setClipToPadding(false);
    list.setPadding(0,dp(8),0,dp(10));
    list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
    list.setItemAnimator(null);
    page.addView(list,new LinearLayout.LayoutParams(-1,0,1f));

    page.addView(buildComposer(),new LinearLayout.LayoutParams(-1,-2));
    setContentView(root);

    root.getViewTreeObserver().addOnGlobalLayoutListener(()->{
      int[] loc=new int[2];
      input.getLocationOnScreen(loc);
      int imeBottom=0;
      if(Build.VERSION.SDK_INT>=30&&root.getRootWindowInsets()!=null){
        imeBottom=root.getRootWindowInsets().getInsets(WindowInsets.Type.ime()).bottom;
      }
      android.util.Log.i("DualPhoneNative","screen="+root.getHeight()+" ime="+imeBottom+" inputBottom="+(loc[1]+input.getHeight()));
    });
  }

  private View buildHeader(){
    LinearLayout header=new LinearLayout(this);
    header.setOrientation(LinearLayout.VERTICAL);
    header.setPadding(dp(20),dp(10),dp(16),dp(8));
    header.setBackground(makeColor(Color.argb(212,250,250,251),0));

    LinearLayout row=new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);

    LinearLayout titleBox=new LinearLayout(this);
    titleBox.setOrientation(LinearLayout.VERTICAL);

    TextView title=new TextView(this);
    title.setText("我的两台手机");
    title.setTextColor(Color.rgb(30,31,34));
    title.setTextSize(21.5f);
    title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
    titleBox.addView(title,new LinearLayout.LayoutParams(-2,-2));

    status=new TextView(this);
    status.setText("● 自动同步");
    status.setTextSize(11f);
    status.setTextColor(Color.rgb(76,133,106));
    status.setPadding(0,dp(3),0,0);
    status.setBackgroundColor(Color.TRANSPARENT);
    LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-2,-2);
    slp.topMargin=dp(2);
    titleBox.addView(status,slp);

    row.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1f));

    ImageButton search=iconButton(R.drawable.ic_search);
    search.setContentDescription("搜索");
    search.setOnClickListener(v->openSearch());
    row.addView(search,buttonLp());

    ImageButton more=iconButton(R.drawable.ic_more);
    more.setContentDescription("聊天背景");
    more.setOnClickListener(v->showBackgroundDialog());
    LinearLayout.LayoutParams mlp=buttonLp(); mlp.leftMargin=dp(6);
    row.addView(more,mlp);

    header.addView(row,new LinearLayout.LayoutParams(-1,-2));

    searchPanel=new LinearLayout(this);
    searchPanel.setGravity(Gravity.CENTER_VERTICAL);
    searchPanel.setPadding(dp(10),0,dp(4),0);
    searchPanel.setBackground(makeColor(Color.argb(170,255,255,255),dp(14)));
    searchPanel.setVisibility(View.GONE);

    searchInput=new EditText(this);
    searchInput.setSingleLine(true);
    searchInput.setTextSize(14);
    searchInput.setHint("搜索聊天");
    searchInput.setHintTextColor(Color.rgb(152,155,160));
    searchInput.setTextColor(Color.rgb(34,36,39));
    searchInput.setBackgroundColor(Color.TRANSPARENT);
    searchPanel.addView(searchInput,new LinearLayout.LayoutParams(0,dp(38),1f));

    searchCount=new TextView(this);
    searchCount.setTextSize(11.5f);
    searchCount.setTextColor(Color.rgb(127,131,137));
    searchCount.setGravity(Gravity.CENTER);
    searchPanel.addView(searchCount,new LinearLayout.LayoutParams(dp(42),dp(38)));

    TextView prev=smallAction("↑");
    prev.setOnClickListener(v->moveSearch(-1));
    searchPanel.addView(prev,new LinearLayout.LayoutParams(dp(34),dp(34)));

    TextView next=smallAction("↓");
    next.setOnClickListener(v->moveSearch(1));
    searchPanel.addView(next,new LinearLayout.LayoutParams(dp(34),dp(34)));

    TextView close=smallAction("×");
    close.setTextSize(20);
    close.setOnClickListener(v->closeSearch());
    searchPanel.addView(close,new LinearLayout.LayoutParams(dp(34),dp(34)));

    LinearLayout.LayoutParams splp=new LinearLayout.LayoutParams(-1,-2);
    splp.topMargin=dp(9);
    header.addView(searchPanel,splp);

    searchInput.addTextChangedListener(new TextWatcher(){
      public void beforeTextChanged(CharSequence s,int st,int c,int a){}
      public void onTextChanged(CharSequence s,int st,int before,int count){
        searchCursor=0;
        adapter.setSearch(s==null?"":s.toString(),null);
        updateSearchUi();
      }
      public void afterTextChanged(Editable e){}
    });
    return header;
  }

  private View buildComposer(){
    FrameLayout wrap=new FrameLayout(this);
    wrap.setPadding(dp(12),dp(6),dp(12),Math.max(dp(8),navInset()));

    LinearLayout bar=new LinearLayout(this);
    bar.setGravity(Gravity.BOTTOM);
    bar.setPadding(dp(5),dp(4),dp(5),dp(4));
    bar.setBackground(makeColor(Color.argb(220,252,252,253),dp(24)));
    bar.setElevation(dp(2));

    ImageButton attach=iconButton(R.drawable.ic_plus);
    attach.setBackground(makeColor(Color.argb(20,80,88,100),dp(99)));
    attach.setOnClickListener(v->pickFiles());
    bar.addView(attach,new LinearLayout.LayoutParams(dp(36),dp(36)));

    input=new EditText(this);
    input.setTextSize(15);
    input.setTextColor(Color.rgb(37,40,45));
    input.setHintTextColor(Color.rgb(150,153,158));
    input.setHint("消息");
    input.setGravity(Gravity.CENTER_VERTICAL);
    input.setMinLines(1);
    input.setMaxLines(5);
    input.setPadding(dp(8),dp(7),dp(8),dp(7));
    input.setBackgroundColor(Color.TRANSPARENT);
    View.OnFocusChangeListener imeFocus=(v,has)->{
      if(has) v.postDelayed(()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(v,InputMethodManager.SHOW_IMPLICIT),80);
    };
    input.setOnFocusChangeListener(imeFocus);
    input.setOnClickListener(v->v.post(()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(v,InputMethodManager.SHOW_IMPLICIT)));
    LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(0,-2,1f);
    ilp.leftMargin=dp(4); ilp.rightMargin=dp(4);
    bar.addView(input,ilp);

    ImageButton send=iconButton(R.drawable.ic_send);
    send.setColorFilter(Color.WHITE);
    send.setBackground(makeGradient(new int[]{Color.rgb(117,136,238),Color.rgb(134,119,233)},GradientDrawable.Orientation.TL_BR,dp(99)));
    send.setOnClickListener(v->sendText());
    bar.addView(send,new LinearLayout.LayoutParams(dp(36),dp(36)));

    wrap.addView(bar,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
    return wrap;
  }

  private ImageButton iconButton(int drawable){
    ImageButton b=new ImageButton(this);
    b.setImageResource(drawable);
    b.setColorFilter(Color.rgb(73,79,87));
    b.setPadding(dp(7),dp(7),dp(7),dp(7));
    b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    b.setBackgroundColor(Color.TRANSPARENT);
    b.setElevation(0);
    return b;
  }

  private LinearLayout.LayoutParams buttonLp(){ return new LinearLayout.LayoutParams(dp(32),dp(32)); }

  private TextView smallAction(String text){
    TextView v=new TextView(this);
    v.setText(text);
    v.setTextSize(16);
    v.setTextColor(Color.rgb(92,97,103));
    v.setGravity(Gravity.CENTER);
    v.setBackground(makeColor(Color.TRANSPARENT,dp(10)));
    return v;
  }

  private void reloadMessages(boolean initial){
    runOnUiThread(()->{
      boolean atBottom=true;
      RecyclerView.LayoutManager lm=list==null?null:list.getLayoutManager();
      if(lm instanceof LinearLayoutManager){
        int last=((LinearLayoutManager)lm).findLastVisibleItemPosition();
        atBottom=last>=Math.max(0,adapter.getItemCount()-3);
      }
      List<TransferDb.Msg> items=db.all();
      adapter.setItems(items);
      updateStatus();
      if(initial||atBottom){
        list.post(()->{ if(adapter.getItemCount()>0) list.scrollToPosition(adapter.getItemCount()-1); });
      }
      updateSearchUi();
      android.util.Log.i("DualPhoneNative","screen=chat messages="+items.size()+" link="+prefs.getString("link_state","searching"));
    });
  }

  private void updateStatus(){
    if(status==null)return;
    String s=prefs.getString("link_state","searching");
    String label;
    int fg;
    if("nearby".equals(s)){label="● 已直连";fg=Color.rgb(70,130,101);}
    else if("relay".equals(s)){label="● 已同步";fg=Color.rgb(70,130,101);}
    else if("connecting".equals(s)){label="● 连接中";fg=Color.rgb(84,122,159);}
    else if("permission".equals(s)){label="● 需要权限";fg=Color.rgb(158,112,53);}
    else {label="● 自动同步";fg=Color.rgb(80,132,108);}
    status.setText(label);
    status.setTextColor(fg);
  }

  private void sendText(){
    String s=input.getText().toString().trim();
    if(s.isEmpty())return;
    input.setText("");
    db.addText(UUID.randomUUID().toString(),true,s,System.currentTimeMillis(),"pending");
    reloadMessages(false);
    TransferService.wake(this);
  }

  private void openSearch(){
    searchPanel.setVisibility(View.VISIBLE);
    searchInput.requestFocus();
    ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(searchInput,InputMethodManager.SHOW_IMPLICIT);
  }

  private void closeSearch(){
    searchInput.setText("");
    searchPanel.setVisibility(View.GONE);
    adapter.setSearch("",null);
    ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(searchInput.getWindowToken(),0);
  }

  private void moveSearch(int delta){
    List<Integer> matches=adapter.getMatchPositions();
    if(matches.isEmpty()){ toast("没有找到"); return; }
    searchCursor=(searchCursor+delta+matches.size())%matches.size();
    String active=adapter.getItemIdAt(matches.get(searchCursor));
    adapter.setSearch(searchInput.getText().toString(),active);
    searchCount.setText((searchCursor+1)+"/"+matches.size());
    list.smoothScrollToPosition(matches.get(searchCursor));
  }

  private void updateSearchUi(){
    if(searchPanel==null||searchPanel.getVisibility()!=View.VISIBLE)return;
    List<Integer> m=adapter.getMatchPositions();
    if(m.isEmpty()) searchCount.setText(searchInput.getText().length()==0?"":"0/0");
    else{
      if(searchCursor>=m.size())searchCursor=0;
      searchCount.setText((searchCursor+1)+"/"+m.size());
      adapter.setSearch(searchInput.getText().toString(),adapter.getItemIdAt(m.get(searchCursor)));
    }
  }

  @Override public void onLongPress(View anchor,TransferDb.Msg msg){
    if(contextPopup!=null)contextPopup.dismiss();
    LinearLayout menu=new LinearLayout(this);
    menu.setPadding(dp(5),dp(5),dp(5),dp(5));
    menu.setGravity(Gravity.CENTER);
    menu.setBackground(makeColor(Color.argb(246,249,249,251),dp(15)));
    menu.setElevation(dp(10));

    if(!"file".equals(msg.kind)){
      menu.addView(menuButton("复制",v->{copy(msg.text);contextPopup.dismiss();}));
    }else{
      if(!msg.mine)menu.addView(menuButton("保存",v->{saveToDownloadsAsync(msg);contextPopup.dismiss();}));
      menu.addView(menuButton("复制文件名",v->{copy(msg.fileName);contextPopup.dismiss();}));
    }
    menu.addView(menuButton("搜索",v->{
      contextPopup.dismiss();openSearch();
      searchInput.setText("file".equals(msg.kind)?NativeMessageAdapter.friendlyFileLabel(msg.fileName):msg.text);
      searchInput.setSelection(searchInput.length());
    }));

    contextPopup=new PopupWindow(menu,-2,-2,true);
    contextPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    contextPopup.setOutsideTouchable(true);
    contextPopup.setElevation(dp(12));
    menu.measure(View.MeasureSpec.UNSPECIFIED,View.MeasureSpec.UNSPECIFIED);
    int x=Math.max(0,(anchor.getWidth()-menu.getMeasuredWidth())/2);
    contextPopup.showAsDropDown(anchor,x,-anchor.getHeight()-menu.getMeasuredHeight()-dp(6));
  }

  private TextView menuButton(String t,View.OnClickListener click){
    TextView v=new TextView(this);
    v.setText(t);v.setTextSize(13.5f);v.setTextColor(Color.rgb(42,45,49));v.setGravity(Gravity.CENTER);
    v.setPadding(dp(14),0,dp(14),0);
    v.setOnClickListener(click);
    v.setBackground(makeColor(Color.TRANSPARENT,dp(10)));
    return v;
  }

  @Override public void onFileClick(TransferDb.Msg msg){ if(!msg.mine)saveToDownloadsAsync(msg); }

  private void copy(String s){
    android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
    cm.setPrimaryClip(android.content.ClipData.newPlainText("双机传",s==null?"":s));
    toast("已复制");
  }

  private void pickFiles(){
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(i,PICK_FILES);
  }

  private void showBackgroundDialog(){
    final Dialog d=new Dialog(this);
    d.requestWindowFeature(Window.FEATURE_NO_TITLE);
    LinearLayout box=new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(20),dp(10),dp(20),dp(18));
    GradientDrawable bg=makeColor(Color.argb(248,250,250,252),dp(28));
    box.setBackground(bg);

    View grab=new View(this);grab.setBackground(makeColor(Color.rgb(205,208,214),dp(99)));
    LinearLayout.LayoutParams glp=new LinearLayout.LayoutParams(dp(36),dp(4));glp.gravity=Gravity.CENTER_HORIZONTAL;glp.bottomMargin=dp(16);
    box.addView(grab,glp);

    LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
    TextView title=new TextView(this);title.setText("聊天背景");title.setTextSize(20);title.setTypeface(Typeface.DEFAULT_BOLD);title.setTextColor(Color.rgb(31,32,35));
    head.addView(title,new LinearLayout.LayoutParams(0,-2,1f));
    TextView close=smallAction("×");close.setTextSize(21);close.setBackground(makeColor(Color.argb(18,80,85,95),dp(99)));close.setOnClickListener(v->d.dismiss());
    head.addView(close,new LinearLayout.LayoutParams(dp(32),dp(32)));
    box.addView(head,new LinearLayout.LayoutParams(-1,-2));

    box.addView(settingRow("照片","只使用你自己选择的照片","选择照片",v->{d.dismiss();pickBackground();}));
    File f=new File(getFilesDir(),"chat-background.jpg");
    if(f.isFile())box.addView(settingRow("清除背景","恢复默认界面","清除",v->{clearBackground();d.dismiss();}));

    box.addView(sliderRow("模糊","bg_blur",0,18,prefs.getInt("bg_blur",5),v->{prefs.edit().putInt("bg_blur",v).apply();applyBackgroundEffects();}));
    box.addView(sliderRow("柔化","bg_dim",6,42,prefs.getInt("bg_dim",18),v->{prefs.edit().putInt("bg_dim",v).apply();applyBackgroundEffects();}));

    TextView note=new TextView(this);note.setText("背景只保存在这台手机，没有预设背景。");note.setTextSize(11.5f);note.setTextColor(Color.rgb(126,129,135));note.setPadding(dp(11),dp(10),dp(11),dp(10));note.setBackground(makeColor(Color.argb(12,70,75,85),dp(13)));
    LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(-1,-2);nlp.topMargin=dp(10);box.addView(note,nlp);

    d.setContentView(box);
    Window w=d.getWindow();
    if(w!=null){
      w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
      w.setDimAmount(.16f);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      w.setLayout(-1,-2);w.setGravity(Gravity.BOTTOM);
    }
    d.setOnShowListener(x->{Window ww=d.getWindow();if(ww!=null)ww.setLayout(-1,-2);});
    d.show();
  }

  private View settingRow(String title,String sub,String action,View.OnClickListener l){
    LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(14),0,dp(14));
    LinearLayout txt=new LinearLayout(this);txt.setOrientation(LinearLayout.VERTICAL);
    TextView t=new TextView(this);t.setText(title);t.setTextSize(14.5f);t.setTextColor(Color.rgb(37,39,43));t.setTypeface(Typeface.DEFAULT_BOLD);txt.addView(t);
    TextView s=new TextView(this);s.setText(sub);s.setTextSize(11.5f);s.setTextColor(Color.rgb(135,138,144));LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-2,-2);slp.topMargin=dp(2);txt.addView(s,slp);
    row.addView(txt,new LinearLayout.LayoutParams(0,-2,1f));
    TextView a=new TextView(this);a.setText(action);a.setTextSize(13);a.setTextColor(Color.rgb(96,116,216));a.setGravity(Gravity.CENTER);a.setPadding(dp(13),0,dp(13),0);a.setBackground(makeColor(Color.argb(20,100,120,220),dp(13)));a.setOnClickListener(l);
    row.addView(a,new LinearLayout.LayoutParams(-2,dp(36)));
    return row;
  }

  private interface IntListener{void on(int v);}
  private View sliderRow(String title,String key,int min,int max,int current,IntListener listener){
    LinearLayout box=new LinearLayout(this);box.setGravity(Gravity.CENTER_VERTICAL);box.setPadding(0,dp(8),0,dp(4));
    TextView label=new TextView(this);label.setText(title);label.setTextSize(13.5f);label.setTextColor(Color.rgb(55,57,61));box.addView(label,new LinearLayout.LayoutParams(dp(48),-2));
    SeekBar seek=new SeekBar(this);seek.setMax(max-min);seek.setProgress(current-min);box.addView(seek,new LinearLayout.LayoutParams(0,-2,1f));
    TextView value=new TextView(this);value.setText(String.valueOf(current));value.setTextSize(11.5f);value.setGravity(Gravity.END);value.setTextColor(Color.rgb(130,133,139));box.addView(value,new LinearLayout.LayoutParams(dp(40),-2));
    seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar s,int p,boolean from){int v=p+min;value.setText(String.valueOf(v));listener.on(v);}
      public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
    });
    return box;
  }

  private void pickBackground(){
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_BG);
  }

  private void clearBackground(){
    File f=new File(getFilesDir(),"chat-background.jpg");if(f.exists())f.delete();
    wallpaperVersion=-1;applyBackground();
  }

  private void applyBackground(){
    File f=new File(getFilesDir(),"chat-background.jpg");
    if(!f.isFile()){
      wallpaper.setVisibility(View.GONE);wallpaperVeil.setVisibility(View.GONE);ambient.setAlpha(1f);return;
    }
    if(wallpaperVersion!=f.lastModified()){
      if(wallpaperBitmap!=null)wallpaperBitmap.recycle();
      wallpaperBitmap=BitmapFactory.decodeFile(f.getAbsolutePath());
      wallpaperVersion=f.lastModified();
      wallpaper.setImageBitmap(wallpaperBitmap);
    }
    wallpaper.setVisibility(View.VISIBLE);wallpaperVeil.setVisibility(View.VISIBLE);ambient.setAlpha(.16f);
    applyBackgroundEffects();
  }

  private void applyBackgroundEffects(){
    if(wallpaper.getVisibility()!=View.VISIBLE)return;
    int blur=prefs.getInt("bg_blur",5);
    int dim=prefs.getInt("bg_dim",18);
    if(Build.VERSION.SDK_INT>=31){
      float radius=Math.max(.1f,blur*1.8f);
      wallpaper.setRenderEffect(blur==0?null:RenderEffect.createBlurEffect(radius,radius,Shader.TileMode.CLAMP));
    }
    wallpaperVeil.setBackgroundColor(Color.argb(Math.min(230,Math.round(255f*dim/100f)),250,250,252));
  }

  @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
    super.onActivityResult(requestCode,resultCode,data);
    if(resultCode!=RESULT_OK||data==null)return;
    if(requestCode==PICK_BG&&data.getData()!=null){
      Uri uri=data.getData();
      io.execute(()->{try{saveChatBackground(uri);runOnUiThread(this::applyBackground);}catch(Exception e){runOnUiThread(()->toast("背景图片读取失败"));}});
      return;
    }
    if(requestCode!=PICK_FILES)return;
    ArrayList<Uri> uris=new ArrayList<>();
    if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());
    else if(data.getData()!=null)uris.add(data.getData());
    io.execute(()->{
      for(Uri u:uris){try{queueFile(u);}catch(Exception e){runOnUiThread(()->toast("文件读取失败"));}}
      runOnUiThread(()->reloadMessages(false));
      TransferService.wake(this);
    });
  }

  private void saveChatBackground(Uri uri)throws Exception{
    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
    try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
    int max=Math.max(bounds.outWidth,bounds.outHeight),sample=1;while(max/sample>1800)sample*=2;
    BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=sample;
    Bitmap bmp;try(InputStream in=getContentResolver().openInputStream(uri)){bmp=BitmapFactory.decodeStream(in,null,opts);}
    if(bmp==null)throw new IOException("decode failed");
    int w=bmp.getWidth(),h=bmp.getHeight();float scale=Math.min(1f,1800f/Math.max(w,h));Bitmap out=bmp;
    if(scale<1f)out=Bitmap.createScaledBitmap(bmp,Math.round(w*scale),Math.round(h*scale),true);
    File dst=new File(getFilesDir(),"chat-background.jpg"),tmp=new File(getFilesDir(),"chat-background.tmp");
    try(FileOutputStream o=new FileOutputStream(tmp)){if(!out.compress(Bitmap.CompressFormat.JPEG,88,o))throw new IOException();o.getFD().sync();}
    if(dst.exists()&&!dst.delete())throw new IOException();if(!tmp.renameTo(dst))throw new IOException();
    if(out!=bmp)out.recycle();bmp.recycle();wallpaperVersion=-1;
  }

  private void queueFile(Uri u)throws Exception{
    String name="文件";
    try(Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
      if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0&&c.getString(i)!=null)name=c.getString(i);}
    }
    String id=UUID.randomUUID().toString();File dir=new File(getFilesDir(),"outgoing");dir.mkdirs();
    File dst=new File(dir,id+"_"+name.replace("/","_").replace("\\","_"));long size=0;
    try(InputStream in=getContentResolver().openInputStream(u);OutputStream out=new FileOutputStream(dst)){
      byte[] b=new byte[65536];int n;while((n=in.read(b))>0){size+=n;if(size>500L*1024*1024)throw new IOException("too large");out.write(b,0,n);}
    }
    db.addFile(id,true,name,dst.getAbsolutePath(),size,System.currentTimeMillis(),"pending");
  }

  private void saveToDownloadsAsync(TransferDb.Msg m){io.execute(()->saveToDownloads(m));}
  private void saveToDownloads(TransferDb.Msg m){
    try{
      File src=new File(m.filePath);if(!src.isFile())throw new IOException();String name=m.fileName==null?"文件":m.fileName;
      if(Build.VERSION.SDK_INT>=29){
        ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,name);v.put(MediaStore.Downloads.IS_PENDING,1);
        Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException();
        try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(u)){copy(in,out);}
        ContentValues done=new ContentValues();done.put(MediaStore.Downloads.IS_PENDING,0);getContentResolver().update(u,done,null,null);
      }
      runOnUiThread(()->toast("已保存到下载"));
    }catch(Exception e){runOnUiThread(()->toast("保存失败"));}
  }

  private boolean isDebuggable(){return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;}

  private boolean hasNearbyPermissions(){
    if(Build.VERSION.SDK_INT>=33)return granted(Manifest.permission.BLUETOOTH_ADVERTISE)&&granted(Manifest.permission.BLUETOOTH_CONNECT)&&granted(Manifest.permission.BLUETOOTH_SCAN)&&granted(Manifest.permission.NEARBY_WIFI_DEVICES);
    if(Build.VERSION.SDK_INT>=31)return granted(Manifest.permission.BLUETOOTH_ADVERTISE)&&granted(Manifest.permission.BLUETOOTH_CONNECT)&&granted(Manifest.permission.BLUETOOTH_SCAN)&&granted(Manifest.permission.ACCESS_FINE_LOCATION);
    if(Build.VERSION.SDK_INT>=29)return granted(Manifest.permission.ACCESS_FINE_LOCATION);
    return granted(Manifest.permission.ACCESS_COARSE_LOCATION);
  }
  private boolean granted(String p){return checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
  private void addNeed(List<String> out,String p){if(!granted(p))out.add(p);}
  private void requestNearbyPermissions(){
    ArrayList<String> need=new ArrayList<>();
    if(Build.VERSION.SDK_INT>=33){addNeed(need,Manifest.permission.BLUETOOTH_ADVERTISE);addNeed(need,Manifest.permission.BLUETOOTH_CONNECT);addNeed(need,Manifest.permission.BLUETOOTH_SCAN);addNeed(need,Manifest.permission.NEARBY_WIFI_DEVICES);}
    else if(Build.VERSION.SDK_INT>=31){addNeed(need,Manifest.permission.BLUETOOTH_ADVERTISE);addNeed(need,Manifest.permission.BLUETOOTH_CONNECT);addNeed(need,Manifest.permission.BLUETOOTH_SCAN);addNeed(need,Manifest.permission.ACCESS_FINE_LOCATION);}
    else if(Build.VERSION.SDK_INT>=29)addNeed(need,Manifest.permission.ACCESS_FINE_LOCATION);else addNeed(need,Manifest.permission.ACCESS_COARSE_LOCATION);
    if(!need.isEmpty())requestPermissions(need.toArray(new String[0]),PERMS);
  }

  @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
    super.onRequestPermissionsResult(requestCode,permissions,grantResults);
    if(requestCode==PERMS&&hasNearbyPermissions()){
      stopService(new Intent(this,TransferService.class));
      root.postDelayed(()->TransferService.start(this),180);
      updateStatus();
    }
  }

  private int navInset(){return 0;}
  private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
  private GradientDrawable makeColor(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);return g;}
  private GradientDrawable makeGradient(int[] colors,GradientDrawable.Orientation o,float radius){GradientDrawable g=new GradientDrawable(o,colors);g.setCornerRadius(radius);return g;}
  private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
  private static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[65536];int n;while((n=in.read(b))>0)out.write(b,0,n);}

  private class AmbientView extends View{
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    AmbientView(Context c){super(c);}
    @Override protected void onDraw(Canvas c){
      int w=getWidth(),h=getHeight();
      p.setShader(new RadialGradient(w*.08f,h*.05f,w*.72f,new int[]{0x44FFE4D7,0x00FFE4D7},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);
      p.setShader(new RadialGradient(w*.94f,h*.20f,w*.78f,new int[]{0x3FD7E2FF,0x00D7E2FF},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);
      p.setShader(new LinearGradient(0,0,0,h,0x10FFFFFF,0x24F1F5FA,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);
      p.setShader(null);
    }
  }
}
