package com.qxdnzbl.shuangjichuan;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
  static final String BASE="https://shuangji-chuan.floot.app";
  static final int PICK=7070;
  final ExecutorService io=Executors.newSingleThreadExecutor();
  final Handler ui=new Handler(Looper.getMainLooper());
  SharedPreferences prefs; String cookie="",deviceId="";
  LinearLayout messages; ScrollView scroll; EditText input; TextView sync;
  boolean loading=false, demo=false;
  final Runnable poll=new Runnable(){ public void run(){ if(!cookie.isEmpty()&&!demo) loadMessages(false); ui.postDelayed(this,1800);}};

  public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setStatusBarColor(Color.WHITE);
    getWindow().setNavigationBarColor(Color.WHITE);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    prefs=getSharedPreferences("dual",MODE_PRIVATE);
    cookie=prefs.getString("cookie","");
    deviceId=prefs.getString("device","");
    if(deviceId.isEmpty()){deviceId=UUID.randomUUID().toString();prefs.edit().putString("device",deviceId).apply();}
    demo=getIntent().getBooleanExtra("demo",false);
    if(demo) showChat(true); else if(cookie.isEmpty()) showLogin(); else showChat(false);
  }
  protected void onDestroy(){ui.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
  int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
  GradientDrawable box(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
  TextView tv(String s,int sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);return t;}

  void showLogin(){
    ui.removeCallbacks(poll);
    ScrollView outer=new ScrollView(this); outer.setBackgroundColor(Color.WHITE);
    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(22),dp(54),dp(22),dp(30));outer.addView(root);
    TextView icon=tv("▣  ▣",28,Color.rgb(36,107,219));root.addView(icon);
    TextView title=tv("双机传",34,Color.rgb(23,32,42));title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setPadding(0,dp(12),0,0);root.addView(title);
    TextView sub=tv("两台手机只登录一次。以后打开就是同一个聊天框，直接发消息和文件。",14,Color.rgb(108,119,130));sub.setPadding(0,dp(10),0,dp(24));root.addView(sub);
    EditText email=new EditText(this);email.setHint("邮箱");email.setSingleLine();email.setInputType(33);email.setPadding(dp(14),0,dp(14),0);email.setBackground(box(Color.rgb(244,246,248),12));root.addView(email,new LinearLayout.LayoutParams(-1,dp(54)));
    EditText pass=new EditText(this);pass.setHint("密码");pass.setSingleLine();pass.setInputType(129);pass.setPadding(dp(14),0,dp(14),0);pass.setBackground(box(Color.rgb(244,246,248),12));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(54));pp.topMargin=dp(12);root.addView(pass,pp);
    TextView err=tv("",13,Color.rgb(198,67,67));err.setPadding(0,dp(8),0,0);root.addView(err);
    Button login=new Button(this);login.setText("登录并进入");login.setTextColor(Color.WHITE);login.setAllCaps(false);login.setBackground(box(Color.rgb(36,107,219),12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.topMargin=dp(14);root.addView(login,lp);
    Button reg=new Button(this);reg.setText("第一次使用：创建账号");reg.setTextColor(Color.rgb(36,107,219));reg.setAllCaps(false);reg.setBackground(box(Color.rgb(233,239,248),12));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(48));rp.topMargin=dp(10);root.addView(reg,rp);
    TextView note=tv("两台手机用同一个邮箱和密码。登录状态会保留。",12,Color.rgb(108,119,130));note.setGravity(Gravity.CENTER);note.setPadding(0,dp(16),0,0);root.addView(note);
    login.setOnClickListener(v->auth(false,email,pass,err,login,reg));
    reg.setOnClickListener(v->auth(true,email,pass,err,login,reg));
    setContentView(outer);
  }

  void auth(boolean register,EditText email,EditText pass,TextView err,Button a,Button b){
    String e=email.getText().toString().trim(),p=pass.getText().toString();
    if(!e.contains("@")){err.setText("请输入正确邮箱");return;} if(p.length()<(register?8:1)){err.setText(register?"密码至少 8 位":"请输入密码");return;}
    a.setEnabled(false);b.setEnabled(false);err.setText("");
    io.execute(()->{try{
      JSONObject x=new JSONObject();x.put("email",e);x.put("password",p);if(register)x.put("displayName","我");
      Resp r=req("POST",register?"/_api/auth/register_with_password":"/_api/auth/login_with_password",wrap(x),null);
      if(r.code/100!=2)throw new Exception(error(r.body)); if(r.setCookie==null)throw new Exception("登录状态保存失败");
      cookie=r.setCookie.split(";")[0];prefs.edit().putString("cookie",cookie).apply();runOnUiThread(()->showChat(false));
    }catch(Exception ex){runOnUiThread(()->{err.setText(ex.getMessage());a.setEnabled(true);b.setEnabled(true);});}});
  }

  void showChat(boolean demoMode){
    ui.removeCallbacks(poll);
    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(244,246,248));
    LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL);head.setPadding(dp(18),dp(14),dp(18),dp(12));head.setBackgroundColor(Color.WHITE);
    TextView title=tv("我的两台手机",18,Color.rgb(23,32,42));title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);head.addView(title);
    sync=tv(demoMode?"● 自动同步":"● 已连接",12,Color.rgb(35,133,91));sync.setPadding(0,dp(4),0,0);head.addView(sync);root.addView(head);
    View line=new View(this);line.setBackgroundColor(Color.rgb(223,229,234));root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    scroll=new ScrollView(this);scroll.setFillViewport(true);messages=new LinearLayout(this);messages.setOrientation(LinearLayout.VERTICAL);messages.setPadding(dp(14),dp(14),dp(14),dp(18));scroll.addView(messages);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
    LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.BOTTOM);bar.setPadding(dp(8),dp(8),dp(8),dp(8));bar.setBackgroundColor(Color.WHITE);
    Button add=new Button(this);add.setText("+");add.setTextSize(24);add.setAllCaps(false);add.setBackgroundColor(Color.TRANSPARENT);bar.addView(add,new LinearLayout.LayoutParams(dp(48),dp(48)));
    input=new EditText(this);input.setHint("输入消息");input.setMaxLines(4);input.setMinLines(1);input.setTextSize(15);input.setPadding(dp(12),dp(9),dp(12),dp(9));input.setBackground(box(Color.rgb(238,241,244),14));input.setImeOptions(EditorInfo.IME_ACTION_SEND);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1f);ip.leftMargin=dp(4);ip.rightMargin=dp(7);bar.addView(input,ip);
    Button send=new Button(this);send.setText("发送");send.setTextColor(Color.WHITE);send.setAllCaps(false);send.setBackground(box(Color.rgb(36,107,219),12));bar.addView(send,new LinearLayout.LayoutParams(dp(68),dp(48)));root.addView(bar);
    add.setOnClickListener(v->pick());send.setOnClickListener(v->sendText(send));input.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_SEND){sendText(send);return true;}return false;});
    setContentView(root);
    if(demoMode) renderDemo(); else {loadMessages(true);ui.postDelayed(poll,1800);}
  }

  void renderDemo(){
    addText("另一台手机","这条是在另一台手机发来的消息",false);
    addFile("另一台手机","照片_今天.jpg","2.8 MB",false);
    addText("这台手机","收到，直接在这里就能传。",true);
    addFile("这台手机","资料.pdf","860 KB",true);
    scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
  }
  void addText(String who,String body,boolean mine){
    LinearLayout row=new LinearLayout(this);row.setGravity(mine?Gravity.RIGHT:Gravity.LEFT);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(8);
    TextView t=tv(body,15,mine?Color.WHITE:Color.rgb(23,32,42));t.setMaxWidth(dp(310));t.setPadding(dp(12),dp(9),dp(12),dp(9));t.setBackground(box(mine?Color.rgb(36,107,219):Color.WHITE,16));row.addView(t);messages.addView(row,rp);
  }
  void addFile(String who,String name,String size,boolean mine){
    LinearLayout row=new LinearLayout(this);row.setGravity(mine?Gravity.RIGHT:Gravity.LEFT);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(8);
    LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(12),dp(10),dp(12),dp(8));b.setBackground(box(Color.WHITE,16));
    TextView n=tv("📎  "+name,14,Color.rgb(23,32,42));n.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.addView(n);TextView m=tv(size+"   点击下载",12,Color.rgb(108,119,130));m.setPadding(0,dp(5),0,0);b.addView(m);row.addView(b);messages.addView(row,rp);
  }

  void sendText(Button send){
    String s=input.getText().toString().trim();if(s.isEmpty()||demo)return;input.setText("");send.setEnabled(false);
    io.execute(()->{try{JSONObject x=new JSONObject();x.put("action","sendText");x.put("text",s);x.put("deviceId",deviceId);Resp r=req("POST","/_api/messages_action",wrap(x),cookie);if(r.code/100!=2)throw new Exception(error(r.body));loadMessages(true);}catch(Exception e){runOnUiThread(()->{input.setText(s);Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}finally{runOnUiThread(()->send.setEnabled(true));}});
  }
  void loadMessages(boolean bottom){
    if(loading)return;loading=true;io.execute(()->{try{Resp r=req("GET","/_api/messages",null,cookie);if(r.code==401){cookie="";prefs.edit().remove("cookie").apply();runOnUiThread(this::showLogin);return;}if(r.code/100!=2)throw new Exception("同步失败");JSONArray a=unwrap(r.body).getJSONArray("messages");runOnUiThread(()->{messages.removeAllViews();if(a.length()==0){TextView e=tv("直接发就行\n从任意一台手机发文字、图片或文件，另一台会自动收到。",14,Color.rgb(108,119,130));e.setGravity(Gravity.CENTER);e.setPadding(dp(28),dp(80),dp(28),0);messages.addView(e);}for(int i=0;i<a.length();i++){try{JSONObject m=a.getJSONObject(i);boolean mine=deviceId.equals(m.optString("senderDeviceId"));if("text".equals(m.optString("kind")))addText("",m.optString("textContent"),mine);else addRealFile(m,mine);}catch(Exception ignored){}}if(bottom)scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));sync.setText("● 自动同步");sync.setTextColor(Color.rgb(35,133,91));});}catch(Exception e){runOnUiThread(()->{if(sync!=null){sync.setText("● 正在重连");sync.setTextColor(Color.rgb(165,107,24));}});}finally{loading=false;}});
  }
  void addRealFile(JSONObject m,boolean mine)throws Exception{
    String id=m.getString("id"),name=m.optString("fileName","文件");long bytes=m.optLong("fileSize",0);
    LinearLayout row=new LinearLayout(this);row.setGravity(mine?Gravity.RIGHT:Gravity.LEFT);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(8);
    LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(12),dp(10),dp(12),dp(8));b.setBackground(box(Color.WHITE,16));
    TextView n=tv("📎  "+name,14,Color.rgb(23,32,42));n.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.addView(n);TextView meta=tv(size(bytes)+"   点击下载",12,Color.rgb(108,119,130));meta.setPadding(0,dp(5),0,0);b.addView(meta);b.setOnClickListener(v->download(id,name));row.addView(b);messages.addView(row,rp);
  }

  void pick(){if(demo)return;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK);}
  protected void onActivityResult(int rc,int result,Intent data){super.onActivityResult(rc,result,data);if(rc!=PICK||result!=RESULT_OK||data==null)return;ArrayList<Uri> list=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)list.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)list.add(data.getData());io.execute(()->{for(Uri u:list){try{upload(u);}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());break;}}loadMessages(true);});}
  void upload(Uri u)throws Exception{
    String name="file",type=getContentResolver().getType(u);if(type==null)type="application/octet-stream";Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null);if(c!=null){if(c.moveToFirst()){int x=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(x>=0)name=c.getString(x);}c.close();}
    File f=new File(getCacheDir(),UUID.randomUUID().toString());try(InputStream in=getContentResolver().openInputStream(u);FileOutputStream out=new FileOutputStream(f)){byte[] buf=new byte[65536];int n;while((n=in.read(buf))>0)out.write(buf,0,n);}long len=f.length();if(len<=0||len>100L*1024*1024){f.delete();throw new Exception("单个文件需小于 100MB");}
    JSONObject p=new JSONObject();p.put("action","presignFile");p.put("fileName",name);p.put("fileType",type);p.put("fileSize",len);Resp pr=req("POST","/_api/messages_action",wrap(p),cookie);if(pr.code/100!=2){f.delete();throw new Exception(error(pr.body));}JSONObject po=unwrap(pr.body);HttpURLConnection put=(HttpURLConnection)new URL(po.getString("uploadUrl")).openConnection();put.setRequestMethod("PUT");put.setDoOutput(true);put.setFixedLengthStreamingMode(len);JSONObject hs=po.getJSONObject("headers");Iterator<String> it=hs.keys();while(it.hasNext()){String k=it.next();put.setRequestProperty(k,hs.getString(k));}try(FileInputStream in=new FileInputStream(f);OutputStream out=put.getOutputStream()){byte[] buf=new byte[65536];int n;while((n=in.read(buf))>0)out.write(buf,0,n);}int pc=put.getResponseCode();put.disconnect();f.delete();if(pc/100!=2)throw new Exception("文件上传失败");
    JSONObject q=new JSONObject();q.put("action","sendFile");q.put("deviceId",deviceId);q.put("fileKey",po.getString("fileKey"));q.put("fileName",name);q.put("fileType",type);q.put("fileSize",len);Resp fr=req("POST","/_api/messages_action",wrap(q),cookie);if(fr.code/100!=2)throw new Exception(error(fr.body));
  }
  void download(String id,String name){try{DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);DownloadManager.Request r=new DownloadManager.Request(Uri.parse(BASE+"/_api/file_download?messageId="+URLEncoder.encode(id,"UTF-8")));r.setTitle(name);r.addRequestHeader("Cookie",cookie);r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name.replace("/","_"));dm.enqueue(r);Toast.makeText(this,"开始下载到 Downloads",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"下载失败",Toast.LENGTH_LONG).show();}}
  String size(long b){if(b<1024)return b+" B";if(b<1024*1024)return String.format(Locale.US,"%.1f KB",b/1024.0);return String.format(Locale.US,"%.1f MB",b/1024.0/1024.0);}

  String wrap(JSONObject x)throws Exception{JSONObject r=new JSONObject();r.put("json",x);return r.toString();}
  JSONObject unwrap(String s)throws Exception{return new JSONObject(s).getJSONObject("json");}
  String error(String s){try{JSONObject j=unwrap(s);return j.optString("error",j.optString("message","操作失败"));}catch(Exception e){return "操作失败，请重试";}}
  Resp req(String method,String path,String body,String c)throws Exception{
    HttpURLConnection h=(HttpURLConnection)new URL(BASE+path).openConnection();h.setRequestMethod(method);h.setConnectTimeout(15000);h.setReadTimeout(30000);h.setRequestProperty("Accept","application/json");if(c!=null&&!c.isEmpty())h.setRequestProperty("Cookie",c);if(body!=null){byte[] b=body.getBytes(StandardCharsets.UTF_8);h.setDoOutput(true);h.setRequestProperty("Content-Type","application/json");h.setFixedLengthStreamingMode(b.length);try(OutputStream o=h.getOutputStream()){o.write(b);}}
    int code=h.getResponseCode();InputStream in=code>=400?h.getErrorStream():h.getInputStream();String out="";if(in!=null){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)b.write(buf,0,n);out=b.toString("UTF-8");}String sc=h.getHeaderField("Set-Cookie");h.disconnect();return new Resp(code,out,sc);
  }
  static class Resp{int code;String body,setCookie;Resp(int c,String b,String s){code=c;body=b;setCookie=s;}}
}