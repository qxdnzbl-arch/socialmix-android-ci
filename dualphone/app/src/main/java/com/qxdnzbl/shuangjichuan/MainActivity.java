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
  private AmbientView ambient;
  private TextView status;
  private View statusDot;
  private RecyclerView list;
  private NativeMessageAdapter adapter;
  private EditText input;
  private ImageButton sendButton;
  private TextView latestButton;
  private boolean followLatest=true,imeWasVisible=false;
  private AppUpdater updater;
  private Dialog photoDialog;
  private LinearLayout searchPanel;
  private EditText searchInput;
  private TextView searchCount;
  private int searchCursor=0;
  private PopupWindow contextPopup;
  private Bitmap wallpaperBitmap;
  private long wallpaperVersion=-1L;
  private long wallpaperLoadingKey=-1L;
  private View headerView;
  private final Handler mainHandler=new Handler(Looper.getMainLooper());
  private boolean reloadQueued=false;
  private boolean reloadInFlight=false;
  private boolean reloadInitialPending=false;
  private int lastLoggedIme=-1;
  private int lastLoggedInputBottom=-1;
  private int ciLoadCount=0;

  private final BroadcastReceiver receiver=new BroadcastReceiver(){
    @Override public void onReceive(Context c,Intent i){
      if(TransferService.ACTION_CHANGED.equals(i.getAction())){
        updateStatus();
        scheduleReload(false);
      }
    }
  };

  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    getWindow().setStatusBarColor(Color.TRANSPARENT);
    getWindow().setNavigationBarColor(Color.rgb(247,248,251));
    int sys=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
    if(Build.VERSION.SDK_INT>=26)sys|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    getWindow().getDecorView().setSystemUiVisibility(sys);
    if(Build.VERSION.SDK_INT>=30){
      WindowInsetsController wc=getWindow().getInsetsController();
      if(wc!=null) wc.setSystemBarsAppearance(
        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
      );
    }
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
      ciLoadCount=Math.min(600,Math.max(0,getIntent().getIntExtra("ciLoadMessages",0)));
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
    loadBackgroundAsync();
    scheduleReload(true);
    updateStatus();
    updater=new AppUpdater(this);
    if(ciLoadCount>0)loadCiMessagesAsync(ciLoadCount);

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
    scheduleReload(false);
    updateStatus();
    if(updater!=null)updater.onResume();
  }

  @Override protected void onPause(){
    if(input!=null)prefs.edit().putString("draft_text",input.getText().toString()).apply();
    super.onPause();
  }

  @Override protected void onDestroy(){
    mainHandler.removeCallbacksAndMessages(null);
    io.shutdownNow();
    if(photoDialog!=null)photoDialog.dismiss();
    if(adapter!=null)adapter.close();
    if(updater!=null)updater.close();
    if(wallpaperBitmap!=null) wallpaperBitmap.recycle();
    super.onDestroy();
  }

  private void buildNativeUi(){
    root=new FrameLayout(this);
    root.setFocusableInTouchMode(true);
    root.setBackground(makeGradient(new int[]{Color.rgb(252,249,248),Color.rgb(246,248,252),Color.rgb(241,245,251)},GradientDrawable.Orientation.TL_BR,0));

    wallpaper=new ImageView(this);
    wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
    wallpaper.setVisibility(View.GONE);
    int wallpaperHeight=Build.VERSION.SDK_INT>=30?getWindowManager().getCurrentWindowMetrics().getBounds().height():getResources().getDisplayMetrics().heightPixels;
    root.addView(wallpaper,new FrameLayout.LayoutParams(-1,wallpaperHeight,Gravity.TOP));

    ambient=new AmbientView(this);
    root.addView(ambient,new FrameLayout.LayoutParams(-1,-1));

    LinearLayout page=new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    root.addView(page,new FrameLayout.LayoutParams(-1,-1));

    headerView=buildHeader();
    page.addView(headerView,new LinearLayout.LayoutParams(-1,-2));
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
      v.setPadding(0,0,0,bottom);
      if(headerView!=null)headerView.setPadding(dp(18),top+dp(7),dp(14),dp(7));
      return insets;
    });
    page.requestApplyInsets();

    adapter=new NativeMessageAdapter(this,this);
    list=new RecyclerView(this);
    LinearLayoutManager lm=new LinearLayoutManager(this);
    lm.setStackFromEnd(true);
    list.setLayoutManager(lm);
    list.setAdapter(adapter);
    list.setClipToPadding(false);
    list.setPadding(0,dp(8),0,dp(10));
    list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
    list.setItemAnimator(null);
    FrameLayout messages=new FrameLayout(this);
    messages.addView(list,new FrameLayout.LayoutParams(-1,-1));
    latestButton=smallAction("回到最新");latestButton.setTextSize(12);latestButton.setContentDescription("回到最新");
    latestButton.setPadding(dp(14),0,dp(14),0);latestButton.setBackground(solidPanel(Color.rgb(238,240,243),dp(12)));
    latestButton.setVisibility(View.GONE);latestButton.setOnClickListener(v->{endInput();scrollToLatest();});
    FrameLayout.LayoutParams latestLp=new FrameLayout.LayoutParams(-2,dp(40),Gravity.END|Gravity.BOTTOM);
    latestLp.rightMargin=dp(14);latestLp.bottomMargin=dp(10);messages.addView(latestButton,latestLp);
    page.addView(messages,new LinearLayout.LayoutParams(-1,0,1f));
    list.addOnScrollListener(new RecyclerView.OnScrollListener(){
      @Override public void onScrollStateChanged(RecyclerView r,int state){
        if(state==RecyclerView.SCROLL_STATE_DRAGGING){followLatest=false;endInput();}
        if(state==RecyclerView.SCROLL_STATE_IDLE){followLatest=isAtLatest();updateLatestButton();}
      }
      @Override public void onScrolled(RecyclerView r,int dx,int dy){updateLatestButton();}
    });

    page.addView(buildComposer(),new LinearLayout.LayoutParams(-1,-2));
    setContentView(root);
    root.requestFocus();

    root.getViewTreeObserver().addOnGlobalLayoutListener(()->{
      int[] loc=new int[2];
      input.getLocationOnScreen(loc);
      int imeBottom=0;
      if(Build.VERSION.SDK_INT>=30&&root.getRootWindowInsets()!=null){
        imeBottom=root.getRootWindowInsets().getInsets(WindowInsets.Type.ime()).bottom;
      }
      boolean imeVisible=imeBottom>0;
      if(Build.VERSION.SDK_INT<30){Rect visible=new Rect();root.getWindowVisibleDisplayFrame(visible);imeVisible=root.getHeight()-visible.height()>dp(150);}
      if(imeWasVisible&&!imeVisible)clearInputFocus();
      if(imeVisible!=imeWasVisible&&followLatest&&searchPanel.getVisibility()!=View.VISIBLE)scrollToLatest();
      imeWasVisible=imeVisible;
      int inputBottom=loc[1]+input.getHeight();
      if(imeBottom!=lastLoggedIme||Math.abs(inputBottom-lastLoggedInputBottom)>dp(8)){
        lastLoggedIme=imeBottom;
        lastLoggedInputBottom=inputBottom;
        android.util.Log.i("DualPhoneNative","screen="+root.getHeight()+" ime="+imeBottom+" inputBottom="+inputBottom);
      }
    });
  }

  private View buildHeader(){
    LinearLayout header=new LinearLayout(this);
    header.setOrientation(LinearLayout.VERTICAL);
    header.setPadding(dp(12),dp(4),dp(12),dp(7));
    header.setBackground(solidPanel(Color.rgb(244,245,247),0));

    FrameLayout top=new FrameLayout(this);
    top.setMinimumHeight(dp(52));

    LinearLayout titleBox=new LinearLayout(this);
    titleBox.setOrientation(LinearLayout.VERTICAL);
    titleBox.setGravity(Gravity.CENTER_HORIZONTAL);

    TextView title=new TextView(this);
    title.setText("我的两台手机");
    title.setTextColor(Color.rgb(29,29,31));
    title.setTextSize(17.5f);
    title.setGravity(Gravity.CENTER);
    title.setIncludeFontPadding(false);
    title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
    titleBox.addView(title,new LinearLayout.LayoutParams(-2,-2));

    LinearLayout statusRow=new LinearLayout(this);
    statusRow.setGravity(Gravity.CENTER);
    statusDot=new View(this);
    statusDot.setBackground(makeColor(Color.rgb(91,145,116),dp(99)));
    statusRow.addView(statusDot,new LinearLayout.LayoutParams(dp(6),dp(6)));
    status=new TextView(this);
    status.setText("自动同步");
    status.setTextSize(10.5f);
    status.setTextColor(Color.rgb(88,129,109));
    status.setIncludeFontPadding(false);
    LinearLayout.LayoutParams stlp=new LinearLayout.LayoutParams(-2,-2);
    stlp.leftMargin=dp(5);
    statusRow.addView(status,stlp);
    LinearLayout.LayoutParams srlp=new LinearLayout.LayoutParams(-2,-2);
    srlp.gravity=Gravity.CENTER_HORIZONTAL;
    srlp.topMargin=dp(5);
    titleBox.addView(statusRow,srlp);

    FrameLayout.LayoutParams tlp=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER);
    top.addView(titleBox,tlp);

    LinearLayout actions=new LinearLayout(this);
    actions.setGravity(Gravity.CENTER);
    ImageButton search=iconButton(R.drawable.ic_search);
    search.setContentDescription("搜索");
    search.setOnClickListener(v->openSearch());
    actions.addView(search,new LinearLayout.LayoutParams(dp(34),dp(34)));
    ImageButton more=iconButton(R.drawable.ic_more);
    more.setContentDescription("聊天背景");
    more.setOnClickListener(v->showBackgroundDialog());
    LinearLayout.LayoutParams mlp=new LinearLayout.LayoutParams(dp(34),dp(34));mlp.leftMargin=dp(6);
    actions.addView(more,mlp);
    FrameLayout.LayoutParams alp=new FrameLayout.LayoutParams(-2,-2,Gravity.END|Gravity.CENTER_VERTICAL);
    top.addView(actions,alp);

    header.addView(top,new LinearLayout.LayoutParams(-1,dp(52)));

    searchPanel=new LinearLayout(this);
    searchPanel.setGravity(Gravity.CENTER_VERTICAL);
    searchPanel.setPadding(dp(11),0,dp(4),0);
    searchPanel.setBackground(solidPanel(Color.rgb(231,233,237),dp(12)));
    searchPanel.setVisibility(View.GONE);

    ImageView searchGlyph=new ImageView(this);
    searchGlyph.setImageResource(R.drawable.ic_search);
    searchGlyph.setColorFilter(Color.rgb(126,128,133));
    searchGlyph.setPadding(dp(3),dp(3),dp(3),dp(3));
    searchPanel.addView(searchGlyph,new LinearLayout.LayoutParams(dp(26),dp(38)));

    searchInput=new EditText(this);
    searchInput.setSingleLine(true);
    searchInput.setTextSize(14);
    searchInput.setHint("搜索聊天");
    searchInput.setHintTextColor(Color.rgb(151,153,158));
    searchInput.setTextColor(Color.rgb(34,35,38));
    searchInput.setBackgroundColor(Color.TRANSPARENT);
    searchInput.setPadding(dp(3),0,dp(4),0);
    configureInput(searchInput);
    searchPanel.addView(searchInput,new LinearLayout.LayoutParams(0,dp(38),1f));

    searchCount=new TextView(this);
    searchCount.setTextSize(11.5f);
    searchCount.setTextColor(Color.rgb(127,130,136));
    searchCount.setGravity(Gravity.CENTER);
    searchPanel.addView(searchCount,new LinearLayout.LayoutParams(dp(40),dp(38)));

    TextView prev=smallAction("↑");prev.setOnClickListener(v->moveSearch(-1));
    TextView next=smallAction("↓");next.setOnClickListener(v->moveSearch(1));
    TextView close=smallAction("×");close.setTextSize(19);close.setOnClickListener(v->closeSearch());
    searchPanel.addView(prev,new LinearLayout.LayoutParams(dp(32),dp(32)));
    searchPanel.addView(next,new LinearLayout.LayoutParams(dp(32),dp(32)));
    searchPanel.addView(close,new LinearLayout.LayoutParams(dp(32),dp(32)));

    LinearLayout.LayoutParams splp=new LinearLayout.LayoutParams(-1,-2);
    splp.topMargin=dp(5);
    header.addView(searchPanel,splp);

    searchInput.addTextChangedListener(new TextWatcher(){
      public void beforeTextChanged(CharSequence s,int st,int c,int a){}
      public void onTextChanged(CharSequence s,int st,int before,int count){
        searchCursor=0;
        adapter.setSearch(s==null?"":s.toString(),null);
        updateSearchCountOnly();
      }
      public void afterTextChanged(Editable e){}
    });
    return header;
  }

  private View buildComposer(){
    FrameLayout wrap=new FrameLayout(this);
    wrap.setPadding(dp(10),dp(5),dp(10),dp(7));
    LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.BOTTOM);
    row.setPadding(dp(2),dp(2),dp(2),dp(2));
    row.setBackground(solidPanel(Color.rgb(236,238,241),dp(14)));
    ImageButton attach=iconButton(R.drawable.ic_plus);attach.setContentDescription("添加文件");attach.setId(R.id.attach);
    attach.setBackgroundColor(Color.TRANSPARENT);attach.setOnClickListener(v->pickFiles());
    row.addView(attach,new LinearLayout.LayoutParams(dp(42),dp(42)));
    input=new EditText(this);input.setId(R.id.message);
    input.setTextSize(15);input.setTextColor(Color.rgb(37,39,43));input.setHintTextColor(Color.rgb(118,123,133));
    input.setHint("消息");input.setGravity(Gravity.CENTER_VERTICAL);input.setMinLines(1);input.setMaxLines(5);
    input.setPadding(dp(3),dp(9),dp(5),dp(9));input.setBackgroundColor(Color.TRANSPARENT);
    configureInput(input);row.addView(input,new LinearLayout.LayoutParams(0,-2,1f));
    sendButton=iconButton(R.drawable.ic_send);sendButton.setId(R.id.send);sendButton.setContentDescription("发送");
    sendButton.setBackgroundColor(Color.TRANSPARENT);sendButton.setColorFilter(Color.rgb(48,51,58));
    sendButton.setOnClickListener(v->sendText());
    row.addView(sendButton,new LinearLayout.LayoutParams(dp(42),dp(42)));
    input.addTextChangedListener(new TextWatcher(){
      public void beforeTextChanged(CharSequence s,int start,int count,int after){}
      public void onTextChanged(CharSequence s,int start,int before,int count){updateSendButton();}
      public void afterTextChanged(Editable e){}
    });
    input.setText(prefs.getString("draft_text",""));
    updateSendButton();
    wrap.addView(row,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));return wrap;
  }
  private void updateSendButton(){boolean enabled=input.getText().toString().trim().length()>0;sendButton.setEnabled(enabled);sendButton.setAlpha(enabled?1f:.32f);}
  private void configureInput(EditText edit){
    edit.setCursorVisible(false);
    edit.setOnFocusChangeListener((v,has)->{
      edit.setCursorVisible(has);
      if(has)v.post(()->{if(v.hasFocus())((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(v,InputMethodManager.SHOW_IMPLICIT);});
    });
    edit.setOnClickListener(v->{edit.setCursorVisible(true);edit.requestFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(edit,InputMethodManager.SHOW_IMPLICIT);});
  }
  private void clearInputFocus(){
    if(input!=null){input.clearFocus();input.setCursorVisible(false);}
    if(searchInput!=null){searchInput.clearFocus();searchInput.setCursorVisible(false);}
    if(root!=null)root.requestFocus();
  }
  private void endInput(){
    View focus=getCurrentFocus();
    if(focus!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);
    clearInputFocus();
  }
  @Override public boolean dispatchTouchEvent(MotionEvent event){
    if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
      View focus=getCurrentFocus();
      if(focus instanceof EditText){Rect rect=new Rect();focus.getGlobalVisibleRect(rect);if(!rect.contains((int)event.getRawX(),(int)event.getRawY()))endInput();}
    }
    return super.dispatchTouchEvent(event);
  }
  private boolean isAtLatest(){return list==null||adapter==null||adapter.getItemCount()==0||!list.canScrollVertically(1);}
  private void updateLatestButton(){
    if(latestButton!=null)latestButton.setVisibility(!isAtLatest()&&searchPanel.getVisibility()!=View.VISIBLE?View.VISIBLE:View.GONE);
  }
  private void scrollToLatest(){
    followLatest=true;
    list.post(()->{
      if(isFinishing()||isDestroyed()||adapter.getItemCount()==0)return;
      int position=adapter.getItemCount()-1;list.scrollToPosition(position);
      list.post(()->{
        LinearLayoutManager lm=(LinearLayoutManager)list.getLayoutManager();View last=lm.findViewByPosition(position);
        if(last!=null)list.scrollBy(0,lm.getDecoratedBottom(last)-(list.getHeight()-list.getPaddingBottom()));
        followLatest=true;updateLatestButton();
      });
    });
  }

  private ImageButton iconButton(int drawable){
    ImageButton b=new ImageButton(this);
    b.setImageResource(drawable);
    b.setColorFilter(Color.rgb(73,79,87));
    b.setPadding(dp(7),dp(7),dp(7),dp(7));
    b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    b.setBackground(solidPanel(Color.rgb(228,231,236),dp(11)));
    b.setElevation(0);
    return b;
  }

  private LinearLayout.LayoutParams buttonLp(){ return new LinearLayout.LayoutParams(dp(30),dp(30)); }

  private TextView smallAction(String text){
    TextView v=new TextView(this);
    v.setText(text);
    v.setTextSize(16);
    v.setTextColor(Color.rgb(92,97,103));
    v.setGravity(Gravity.CENTER);
    v.setBackground(makeColor(Color.TRANSPARENT,dp(10)));
    return v;
  }

  private void scheduleReload(boolean initial){
    reloadInitialPending=reloadInitialPending||initial;
    if(reloadQueued||reloadInFlight){
      reloadQueued=true;
      return;
    }
    reloadQueued=true;
    mainHandler.postDelayed(()->{
      reloadQueued=false;
      reloadInFlight=true;
      final boolean forceInitial=reloadInitialPending;
      reloadInitialPending=false;

      io.execute(()->{
        final List<TransferDb.Msg> items=db.all();
        mainHandler.post(()->{
          if(isFinishing()||isDestroyed())return;
          adapter.setItems(items);
          updateStatus();
          if(forceInitial||(followLatest&&searchPanel.getVisibility()!=View.VISIBLE))scrollToLatest();
          else list.post(this::updateLatestButton);
          updateSearchUi();
          android.util.Log.i("DualPhoneNative","screen=chat messages="+items.size()+" link="+prefs.getString("link_state","searching"));
          reloadInFlight=false;
          if(reloadQueued){
            reloadQueued=false;
            scheduleReload(false);
          }
        });
      });
    },initial?0:90);
  }

  private void loadCiMessagesAsync(int count){
    io.execute(()->{
      long base=System.currentTimeMillis()-count*1000L;
      for(int i=0;i<count;i++){
        String text;
        if(i%17==0){
          text="这是用于真实负载验收的长消息。\n第二行用于测试滚动、换行和触摸响应。\n第 "+(i+1)+" 条。";
        }else{
          text="性能测试消息 "+(i+1)+" · 双机传原生界面";
        }
        db.addText("ci-load-"+i,(i%3)!=0,text,base+i*1000L,"sent");
      }
      mainHandler.post(()->{
        android.util.Log.i("DualPhoneNative","ci_load_done="+count);
        scheduleReload(true);
      });
    });
  }

  private void updateStatus(){
    if(status==null)return;
    String s=prefs.getString("link_state","searching");
    if(adapter!=null)adapter.setLinkState(s);
    String label;
    int fg;
    if("nearby".equals(s)){label="已直连";fg=Color.rgb(70,130,101);}
    else if("relay".equals(s)){label="已同步";fg=Color.rgb(70,130,101);}
    else if("connecting".equals(s)){label="连接中";fg=Color.rgb(84,122,159);}
    else if("permission".equals(s)){label="需要权限";fg=Color.rgb(158,112,53);}
    else {label="自动同步";fg=Color.rgb(80,132,108);}
    status.setText(label);
    status.setTextColor(fg);
    if(statusDot!=null)statusDot.setBackground(makeColor(fg,dp(99)));
  }

  private void sendText(){
    String s=input.getText().toString().trim();
    if(s.isEmpty())return;
    input.setText("");
    final String id=UUID.randomUUID().toString();
    final long now=System.currentTimeMillis();
    io.execute(()->{
      db.addText(id,true,s,now,"pending");
      mainHandler.post(()->scheduleReload(true));
      TransferService.wake(this);
    });
  }

  private void openSearch(){
    endInput();
    searchPanel.setVisibility(View.VISIBLE);
    searchInput.requestFocus();
    updateLatestButton();
    ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(searchInput,InputMethodManager.SHOW_IMPLICIT);
  }

  private void closeSearch(){
    endInput();
    searchInput.setText("");
    searchPanel.setVisibility(View.GONE);
    adapter.setSearch("",null);
    updateLatestButton();
    ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(searchInput.getWindowToken(),0);
  }

  private void moveSearch(int delta){
    List<Integer> matches=adapter.getMatchPositions();
    if(matches.isEmpty()){ toast("没有找到"); return; }
    searchCursor=(searchCursor+delta+matches.size())%matches.size();
    String active=adapter.getItemIdAt(matches.get(searchCursor));
    adapter.setSearch(searchInput.getText().toString(),active);
    searchCount.setText((searchCursor+1)+"/"+matches.size());
    followLatest=false;
    list.smoothScrollToPosition(matches.get(searchCursor));
  }

  private void updateSearchCountOnly(){
    if(searchPanel==null||searchPanel.getVisibility()!=View.VISIBLE)return;
    List<Integer> m=adapter.getMatchPositions();
    if(m.isEmpty()) searchCount.setText(searchInput.getText().length()==0?"":"0/0");
    else{
      if(searchCursor>=m.size())searchCursor=0;
      searchCount.setText((searchCursor+1)+"/"+m.size());
    }
  }

  private void updateSearchUi(){
    updateSearchCountOnly();
  }

  @Override public void onLongPress(View anchor,TransferDb.Msg msg){
    endInput();
    if(contextPopup!=null)contextPopup.dismiss();
    LinearLayout menu=new LinearLayout(this);
    menu.setPadding(dp(5),dp(5),dp(5),dp(5));
    menu.setGravity(Gravity.CENTER);
    menu.setBackground(solidPanel(Color.rgb(244,245,247),dp(13)));
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

  @Override public void onFileClick(TransferDb.Msg msg){
    endInput();
    if(PhotoImages.isPhoto(msg.fileName)){showPhoto(msg);return;}
    if(!msg.mine){saveToDownloadsAsync(msg);return;}
    new AlertDialog.Builder(this).setTitle(msg.fileName).setPositiveButton("保存到下载",(d,w)->saveToDownloadsAsync(msg)).setNegativeButton("取消",null).show();
  }
  private void showPhoto(TransferDb.Msg msg){
    if(photoDialog!=null)photoDialog.dismiss();
    final Dialog dialog=new Dialog(this,android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);photoDialog=dialog;
    LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(Color.rgb(31,33,38));
    LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(14),dp(8),dp(10),dp(8));
    TextView title=new TextView(this);title.setText(msg.fileName);title.setTextColor(Color.WHITE);title.setTextSize(14);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.MIDDLE);toolbar.addView(title,new LinearLayout.LayoutParams(0,dp(40),1));
    TextView save=smallAction("保存到下载");save.setTextSize(13);save.setTextColor(Color.WHITE);save.setPadding(dp(12),0,dp(12),0);save.setOnClickListener(v->saveToDownloadsAsync(msg));toolbar.addView(save,new LinearLayout.LayoutParams(-2,dp(44)));
    TextView close=smallAction("×");close.setContentDescription("关闭图片");close.setTextColor(Color.WHITE);close.setTextSize(24);close.setOnClickListener(v->dialog.dismiss());toolbar.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));
    page.addView(toolbar,new LinearLayout.LayoutParams(-1,-2));
    FrameLayout content=new FrameLayout(this);ZoomImageView photo=new ZoomImageView(this);content.addView(photo,new FrameLayout.LayoutParams(-1,-1));
    TextView loading=new TextView(this);loading.setText("加载中");loading.setTextColor(Color.WHITE);loading.setGravity(Gravity.CENTER);content.addView(loading,new FrameLayout.LayoutParams(-1,-1));page.addView(content,new LinearLayout.LayoutParams(-1,0,1));
    final Bitmap[] shown={null};dialog.setContentView(page);dialog.setOnDismissListener(d->{photo.setImageDrawable(null);if(shown[0]!=null)shown[0].recycle();});dialog.show();
    if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-1);
    io.execute(()->{
      Bitmap decoded=PhotoImages.decode(msg.filePath,2048);
      mainHandler.post(()->{
        if(!dialog.isShowing()||isDestroyed()){if(decoded!=null)decoded.recycle();return;}
        if(decoded==null){loading.setText("图片无法预览");return;}
        shown[0]=decoded;photo.setImageBitmap(decoded);loading.setVisibility(View.GONE);
      });
    });
  }

  private void copy(String s){
    android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
    cm.setPrimaryClip(android.content.ClipData.newPlainText("双机传",s==null?"":s));
    toast("已复制");
  }

  private void pickFiles(){
    endInput();
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(i,PICK_FILES);
  }

  private void showBackgroundDialog(){
    endInput();
    final Dialog d=new Dialog(this);
    d.requestWindowFeature(Window.FEATURE_NO_TITLE);
    LinearLayout box=new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(20),dp(10),dp(20),dp(18));
    GradientDrawable bg=solidPanel(Color.rgb(244,245,247),dp(22));
    box.setBackground(bg);

    View grab=new View(this);grab.setBackground(makeColor(Color.rgb(205,208,214),dp(99)));
    LinearLayout.LayoutParams glp=new LinearLayout.LayoutParams(dp(36),dp(4));glp.gravity=Gravity.CENTER_HORIZONTAL;glp.bottomMargin=dp(16);
    box.addView(grab,glp);

    LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
    TextView title=new TextView(this);title.setText("聊天背景");title.setTextSize(20);title.setTypeface(Typeface.DEFAULT_BOLD);title.setTextColor(Color.rgb(31,32,35));
    head.addView(title,new LinearLayout.LayoutParams(0,-2,1f));
    TextView close=smallAction("×");close.setTextSize(21);close.setBackground(makeColor(Color.rgb(224,227,232),dp(10)));close.setOnClickListener(v->d.dismiss());
    head.addView(close,new LinearLayout.LayoutParams(dp(32),dp(32)));
    box.addView(head,new LinearLayout.LayoutParams(-1,-2));

    box.addView(settingRow("照片","只使用你自己选择的照片","选择照片",v->{d.dismiss();pickBackground();}));
    File f=new File(getFilesDir(),"chat-background.jpg");
    if(f.isFile())box.addView(settingRow("清除背景","恢复默认界面","清除",v->{clearBackground();d.dismiss();}));

    box.addView(settingRow("应用更新", "当前版本 "+BuildConfig.VERSION_NAME, "检查更新", v->{d.dismiss();updater.check(true);}));

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
    TextView a=new TextView(this);a.setText(action);a.setTextSize(13);a.setTextColor(Color.WHITE);a.setGravity(Gravity.CENTER);a.setPadding(dp(13),0,dp(13),0);a.setBackground(makeColor(Color.rgb(48,51,58),dp(10)));a.setOnClickListener(l);
    row.addView(a,new LinearLayout.LayoutParams(-2,dp(36)));
    return row;
  }

  private void pickBackground(){
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_BG);
  }

  private void clearBackground(){
    File f=new File(getFilesDir(),"chat-background.jpg");if(f.exists())f.delete();
    wallpaperVersion=-1;loadBackgroundAsync();
  }

  private void loadBackgroundAsync(){
    File f=new File(getFilesDir(),"chat-background.jpg");
    if(!f.isFile()){
      wallpaperLoadingKey=-1L;wallpaperVersion=-1L;wallpaper.setImageDrawable(null);
      if(wallpaperBitmap!=null&&!wallpaperBitmap.isRecycled())wallpaperBitmap.recycle();
      wallpaperBitmap=null;wallpaper.setVisibility(View.GONE);ambient.setVisibility(View.VISIBLE);return;
    }
    final long version=f.lastModified();
    wallpaper.setVisibility(View.VISIBLE);ambient.setVisibility(View.GONE);
    if(wallpaperVersion==version&&wallpaperBitmap!=null)return;
    if(wallpaperLoadingKey==version)return;wallpaperLoadingKey=version;
    io.execute(()->{
      Bitmap decoded=PhotoImages.decode(f.getAbsolutePath(),2048);
      mainHandler.post(()->{
        if(isFinishing()||isDestroyed()){if(decoded!=null)decoded.recycle();return;}
        File current=new File(getFilesDir(),"chat-background.jpg");
        if(!current.isFile()||current.lastModified()!=version){if(decoded!=null)decoded.recycle();wallpaperLoadingKey=-1L;return;}
        wallpaperLoadingKey=-1L;if(decoded==null)return;
        Bitmap old=wallpaperBitmap;wallpaperBitmap=decoded;wallpaperVersion=version;wallpaper.setImageBitmap(decoded);
        if(old!=null&&old!=decoded&&!old.isRecycled())old.recycle();
      });
    });
  }

  @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
    super.onActivityResult(requestCode,resultCode,data);
    if(resultCode!=RESULT_OK||data==null)return;
    if(requestCode==PICK_BG&&data.getData()!=null){
      Uri uri=data.getData();
      io.execute(()->{try{saveChatBackground(uri);mainHandler.post(this::loadBackgroundAsync);}catch(Exception e){runOnUiThread(()->toast("背景图片读取失败"));}});
      return;
    }
    if(requestCode!=PICK_FILES)return;
    ArrayList<Uri> uris=new ArrayList<>();
    if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());
    else if(data.getData()!=null)uris.add(data.getData());
    io.execute(()->{
      for(Uri u:uris){try{queueFile(u);}catch(Exception e){runOnUiThread(()->toast("文件读取失败"));}}
      mainHandler.post(()->scheduleReload(true));
      TransferService.wake(this);
    });
  }

  private void saveChatBackground(Uri uri)throws Exception{
    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
    try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
    int max=Math.max(bounds.outWidth,bounds.outHeight),sample=1;while(max/sample>1400)sample*=2;
    BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=sample;
    Bitmap bmp;try(InputStream in=getContentResolver().openInputStream(uri)){bmp=BitmapFactory.decodeStream(in,null,opts);}
    if(bmp==null)throw new IOException("decode failed");
    int w=bmp.getWidth(),h=bmp.getHeight();float scale=Math.min(1f,1400f/Math.max(w,h));Bitmap out=bmp;
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

  private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
  private GradientDrawable solidPanel(int color,float radius){GradientDrawable g=makeColor(color,radius);g.setStroke(dp(1),Color.rgb(194,199,207));return g;}

  private GradientDrawable makeColor(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);return g;}
  private GradientDrawable makeGlass(int color,float radius,int stroke){GradientDrawable g=makeColor(color,radius);g.setStroke(1,stroke);return g;}
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
