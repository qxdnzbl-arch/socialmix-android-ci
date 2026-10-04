package com.qxdnzbl.shuangjichuan;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.*;
import android.webkit.*;
import android.widget.Toast;
import org.json.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
  private static final int PICK=7070, PERMS=7071;
  private static final String UI_FILE="ui-current.html";
  private static final int BUNDLED_UI_VERSION=5;
  private static final String SECRET="6686986c94d4a4d34fd705665b962491078a94688d3f730b568d36a2c526c470";

  private final ExecutorService io=Executors.newCachedThreadPool();
  private SharedPreferences prefs;
  private TransferDb db;
  private WebView web;

  private final BroadcastReceiver receiver=new BroadcastReceiver(){
    @Override public void onReceive(Context c,Intent i){
      if(TransferService.ACTION_CHANGED.equals(i.getAction())) notifyWeb();
    }
  };

  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setStatusBarColor(Color.WHITE);
    getWindow().setNavigationBarColor(Color.WHITE);
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
    }

    if(isDebuggable() && getIntent().getBooleanExtra("ciSend",false)){
      db.addText("ci-local-message",true,"hello",System.currentTimeMillis(),"pending");
    }
    if(isDebuggable() && getIntent().getBooleanExtra("ciSendFile",false)){
      try{
        File dir=new File(getFilesDir(),"outgoing");dir.mkdirs();
        File test=new File(dir,"ci-offline-file.txt");
        try(FileOutputStream out=new FileOutputStream(test)){out.write("offline-file".getBytes(StandardCharsets.UTF_8));}
        db.addFile("ci-local-file",true,"ci-offline-file.txt",test.getAbsolutePath(),test.length(),System.currentTimeMillis()+1,"pending");
      }catch(Exception e){throw new RuntimeException(e);}
    }

    web=new WebView(this);
    WebSettings s=web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);
    s.setAllowContentAccess(true);
    s.setCacheMode(WebSettings.LOAD_NO_CACHE);
    web.setBackgroundColor(Color.WHITE);
    web.addJavascriptInterface(new Bridge(),"Android");
    web.setWebChromeClient(new WebChromeClient());
    setContentView(web);

    ensureLocalUi();
    loadLocalUi();
    TransferService.start(this);
    requestNearbyPermissions();
    checkForUiUpdate();
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

  @Override protected void onDestroy(){
    io.shutdownNow();
    if(web!=null) web.destroy();
    super.onDestroy();
  }

  private boolean isDebuggable(){
    return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;
  }

  private void requestNearbyPermissions(){
    ArrayList<String> need=new ArrayList<>();
    if(Build.VERSION.SDK_INT>=32){
      addIfMissing(need,Manifest.permission.BLUETOOTH_ADVERTISE);
      addIfMissing(need,Manifest.permission.BLUETOOTH_CONNECT);
      addIfMissing(need,Manifest.permission.BLUETOOTH_SCAN);
      addIfMissing(need,Manifest.permission.NEARBY_WIFI_DEVICES);
    }else if(Build.VERSION.SDK_INT>=31){
      addIfMissing(need,Manifest.permission.BLUETOOTH_ADVERTISE);
      addIfMissing(need,Manifest.permission.BLUETOOTH_CONNECT);
      addIfMissing(need,Manifest.permission.BLUETOOTH_SCAN);
    }else if(Build.VERSION.SDK_INT>=29){
      addIfMissing(need,Manifest.permission.ACCESS_FINE_LOCATION);
    }else{
      addIfMissing(need,Manifest.permission.ACCESS_COARSE_LOCATION);
    }
    if(!need.isEmpty()) requestPermissions(need.toArray(new String[0]),PERMS);
  }

  private void addIfMissing(List<String> out,String p){
    if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED) out.add(p);
  }

  @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
    super.onRequestPermissionsResult(requestCode,permissions,grantResults);
    if(requestCode==PERMS) TransferService.wake(this);
  }

  private void ensureLocalUi(){
    File dst=new File(getFilesDir(),UI_FILE);
    if(dst.isFile()) return;
    try(InputStream in=getAssets().open("ui.html");OutputStream out=new FileOutputStream(dst)){
      copy(in,out);
      prefs.edit().putInt("ui_version",BUNDLED_UI_VERSION).apply();
    }catch(Exception e){
      throw new RuntimeException(e);
    }
  }

  private void loadLocalUi(){
    web.loadUrl(Uri.fromFile(new File(getFilesDir(),UI_FILE)).toString());
  }

  private void notifyWeb(){
    runOnUiThread(()->web.evaluateJavascript("window.nativeRefresh&&window.nativeRefresh()",null));
  }

  private void checkForUiUpdate(){
    io.execute(()->{
      String[] manifests={
        "https://oppo-iphone-transfer-qr.onrender.com/dual/ota/manifest.json",
        "https://oppo-iphone-transfer.onrender.com/dual/ota/manifest.json"
      };
      try{
        JSONObject mf=null;
        for(String u:manifests){
          try{mf=new JSONObject(fetchText(u));break;}catch(Exception ignored){}
        }
        if(mf==null) return;
        int remote=mf.optInt("version",0);
        int current=prefs.getInt("ui_version",BUNDLED_UI_VERSION);
        if(remote<=current) return;

        JSONArray urls=mf.optJSONArray("urls");
        if(urls==null) return;
        byte[] html=null;
        for(int i=0;i<urls.length();i++){
          try{
            html=fetchBytes(urls.getString(i));
            if(!new String(html,StandardCharsets.UTF_8).contains("Android.")){
              html=null;
              continue;
            }
            break;
          }catch(Exception ignored){}
        }
        if(html==null) return;

        File dst=new File(getFilesDir(),UI_FILE);
        File tmp=new File(getFilesDir(),UI_FILE+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){
          out.write(html);
          out.getFD().sync();
        }
        if(dst.exists()&&!dst.delete()) return;
        if(!tmp.renameTo(dst)) return;
        prefs.edit().putInt("ui_version",remote).apply();
        runOnUiThread(this::loadLocalUi);
      }catch(Exception ignored){}
    });
  }

  private static byte[] fetchBytes(String u)throws Exception{
    URLConnection c=new URL(u).openConnection();
    c.setConnectTimeout(5000);
    c.setReadTimeout(8000);
    c.setUseCaches(false);
    try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
      copy(in,out);
      return out.toByteArray();
    }
  }

  private static String fetchText(String u)throws Exception{
    return new String(fetchBytes(u),StandardCharsets.UTF_8);
  }

  private static void copy(InputStream in,OutputStream out)throws IOException{
    byte[] b=new byte[65536];
    int n;
    while((n=in.read(b))>0) out.write(b,0,n);
  }

  private class Bridge{
    @JavascriptInterface public String getState(){
      JSONObject root=new JSONObject();
      try{
        root.put("setupNeeded",false);
        root.put("uiVersion",prefs.getInt("ui_version",BUNDLED_UI_VERSION));
        JSONArray a=new JSONArray();
        for(TransferDb.Msg m:db.all()){
          JSONObject x=new JSONObject();
          x.put("id",m.id);
          x.put("mine",m.mine);
          x.put("kind",m.kind);
          x.put("text",m.text==null?"":m.text);
          x.put("fileName",m.fileName==null?"":m.fileName);
          x.put("fileSize",m.fileSize);
          x.put("status",m.status==null?"":m.status);
          x.put("createdAt",m.createdAt);
          a.put(x);
        }
        root.put("messages",a);
      }catch(Exception ignored){}
      return root.toString();
    }

    @JavascriptInterface public void sendText(String t){
      String s=t==null?"":t.trim();
      if(s.isEmpty()) return;
      db.addText(UUID.randomUUID().toString(),true,s,System.currentTimeMillis(),"pending");
      notifyWeb();
      TransferService.wake(MainActivity.this);
    }

    @JavascriptInterface public void pickFiles(){
      runOnUiThread(()->{
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK);
      });
    }

    @JavascriptInterface public void saveFile(String id){
      io.execute(()->{
        for(TransferDb.Msg m:db.all()){
          if(m.id.equals(id)&&!m.mine&&m.filePath!=null){
            saveToDownloads(m);
            break;
          }
        }
      });
    }

    @JavascriptInterface public void reportUi(String m){
      Log.i("DualPhoneUi",m==null?"":m);
    }

    @JavascriptInterface public void reportLayout(double v,double b){
      Log.i("DualPhoneLayout","visible="+Math.round(v)+" bottom="+Math.round(b));
    }
  }

  @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
    super.onActivityResult(requestCode,resultCode,data);
    if(requestCode!=PICK||resultCode!=RESULT_OK||data==null) return;

    ArrayList<Uri> uris=new ArrayList<>();
    if(data.getClipData()!=null){
      for(int i=0;i<data.getClipData().getItemCount();i++) uris.add(data.getClipData().getItemAt(i).getUri());
    }else if(data.getData()!=null){
      uris.add(data.getData());
    }

    io.execute(()->{
      for(Uri u:uris){
        try{queueFile(u);}
        catch(Exception e){
          runOnUiThread(()->Toast.makeText(this,"文件读取失败",Toast.LENGTH_SHORT).show());
        }
      }
      notifyWeb();
      TransferService.wake(this);
    });
  }

  private void queueFile(Uri u)throws Exception{
    String name="文件";
    try(Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
      if(c!=null&&c.moveToFirst()){
        int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if(i>=0&&c.getString(i)!=null) name=c.getString(i);
      }
    }

    String id=UUID.randomUUID().toString();
    File dir=new File(getFilesDir(),"outgoing");
    dir.mkdirs();
    File dst=new File(dir,id+"_"+name.replace("/","_").replace("\\","_"));

    long size=0;
    try(InputStream in=getContentResolver().openInputStream(u);OutputStream out=new FileOutputStream(dst)){
      byte[] b=new byte[65536];
      int n;
      while((n=in.read(b))>0){
        size+=n;
        if(size>500L*1024*1024) throw new IOException("too large");
        out.write(b,0,n);
      }
    }
    db.addFile(id,true,name,dst.getAbsolutePath(),size,System.currentTimeMillis(),"pending");
  }

  private void saveToDownloads(TransferDb.Msg m){
    try{
      File src=new File(m.filePath);
      if(!src.isFile()) throw new IOException();
      String name=m.fileName==null?"文件":m.fileName;
      if(Build.VERSION.SDK_INT>=29){
        ContentValues v=new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME,name);
        v.put(MediaStore.Downloads.IS_PENDING,1);
        Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
        if(u==null) throw new IOException();
        try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(u)){
          copy(in,out);
        }
        ContentValues done=new ContentValues();
        done.put(MediaStore.Downloads.IS_PENDING,0);
        getContentResolver().update(u,done,null,null);
      }
      runOnUiThread(()->Toast.makeText(this,"已保存到下载",Toast.LENGTH_SHORT).show());
    }catch(Exception e){
      runOnUiThread(()->Toast.makeText(this,"保存失败",Toast.LENGTH_SHORT).show());
    }
  }
}
