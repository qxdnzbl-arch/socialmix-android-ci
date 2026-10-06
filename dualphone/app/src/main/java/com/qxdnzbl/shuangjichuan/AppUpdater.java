package com.qxdnzbl.shuangjichuan;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class AppUpdater {
  private static final String REPO="qxdnzbl-arch/socialmix-android-ci";
  private static final String CHANNEL="https://api.github.com/repos/"+REPO+"/releases/tags/dualphone-latest";
  private final Activity activity;
  private final ExecutorService io=Executors.newSingleThreadExecutor();
  private final Handler main=new Handler(Looper.getMainLooper());
  private final AtomicBoolean busy=new AtomicBoolean();
  private final SharedPreferences prefs;
  private volatile boolean closed;
  private boolean awaitingPermission;
  private AlertDialog activeDialog;
  private volatile boolean cancelled;
  private volatile HttpURLConnection activeConnection;
  private File pending;
  private JSONObject pendingMeta;
  private AlertDialog progress;

  AppUpdater(Activity activity){this.activity=activity;prefs=activity.getSharedPreferences("app_updates",Context.MODE_PRIVATE);}
  void close(){closed=true;io.shutdownNow();main.removeCallbacksAndMessages(null);if(progress!=null)progress.dismiss();if(activeDialog!=null)activeDialog.dismiss();}
  void onResume(){
    if(awaitingPermission){
      awaitingPermission=false;
      if(activity.getPackageManager().canRequestPackageInstalls())install();
      else toast("未允许安装，稍后可在检查更新中重试");
      return;
    }
    long now=System.currentTimeMillis();
    if(now-prefs.getLong("last_check",0L)>6*60*60*1000L)check(false);
  }
  void check(boolean manual){
    if(closed||!busy.compareAndSet(false,true)){if(manual)toast("正在检查或下载更新");return;}
    cancelled=false;
    if(manual)showProgress("检查更新","正在检查");
    io.execute(()->{
      JSONObject meta=null;Exception failure=null;
      try{meta=loadMeta();}catch(Exception e){failure=e;}
      final JSONObject result=meta;final Exception error=failure;
      main.post(()->{
        busy.set(false);dismissProgress();if(!alive()||cancelled)return;
        if(error!=null){Log.i("DualPhoneUpdate","check_unavailable");if(manual)toast("暂时无法检查更新，请稍后重试");return;}
        prefs.edit().putLong("last_check",System.currentTimeMillis()).apply();
        int version=result.optInt("versionCode",0);
        if(version<=BuildConfig.VERSION_CODE){if(manual)toast("已是最新版本");Log.i("DualPhoneUpdate","up_to_date="+BuildConfig.VERSION_CODE);return;}
        if(!manual&&version==prefs.getInt("offered_version",0)&&System.currentTimeMillis()-prefs.getLong("offered_at",0L)<24*60*60*1000L)return;
        prefs.edit().putInt("offered_version",version).putLong("offered_at",System.currentTimeMillis()).apply();
        String notes=result.optString("notes","");
        AlertDialog d=new AlertDialog.Builder(activity).setTitle("有新版本 "+result.optString("versionName"))
          .setMessage(notes.isEmpty()?"可在这里下载并更新，聊天和文件会保留。":notes)
          .setNegativeButton("稍后",null).setPositiveButton("更新",(x,w)->download(result)).create();show(d);
      });
    });
  }
  private JSONObject loadMeta()throws Exception{
    JSONObject meta;
    try{
      JSONObject release=new JSONObject(readText(CHANNEL));
      if(release.optBoolean("draft")||release.optBoolean("prerelease"))throw new IOException("unpublished");
      meta=new JSONObject(release.getString("body"));
    }catch(Exception first){
      meta=new JSONObject(readText("https://github.com/"+REPO+"/releases/download/dualphone-latest/update.json"));
    }
    if(!activity.getPackageName().equals(meta.getString("packageName"))||meta.getInt("versionCode")<1
      ||!meta.getString("sha256").matches("[0-9a-fA-F]{64}"))throw new IOException("invalid metadata");
    URL apk=new URL(meta.getString("apkUrl"));
    if(!"https".equals(apk.getProtocol())||!"github.com".equals(apk.getHost())||!apk.getPath().startsWith("/"+REPO+"/releases/download/"))throw new IOException("invalid download");
    return meta;
  }
  private String readText(String address)throws Exception{
    HttpURLConnection connection=open(address,5000);activeConnection=connection;try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
      byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>256*1024)throw new IOException("metadata too large");}
      return out.toString("UTF-8");
    }finally{connection.disconnect();activeConnection=null;}
  }
  private HttpURLConnection open(String address,int timeout)throws Exception{
    URL url=new URL(address);
    for(int i=0;i<6;i++){
      String host=url.getHost();
      if(!"https".equals(url.getProtocol())||!(host.equals("github.com")||host.equals("api.github.com")||host.endsWith(".githubusercontent.com")))throw new IOException("untrusted redirect");
      HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(timeout);c.setReadTimeout(timeout);
      c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","ShuangJiChuan/"+BuildConfig.VERSION_NAME);c.setRequestProperty("Accept","application/vnd.github+json");
      int code=c.getResponseCode();
      if(code>=300&&code<400){String next=c.getHeaderField("Location");c.disconnect();if(next==null)throw new IOException("redirect missing");url=new URL(url,next);continue;}
      if(code!=200){c.disconnect();throw new IOException("http "+code);}return c;
    }
    throw new IOException("too many redirects");
  }
  private void download(JSONObject meta){
    if(!busy.compareAndSet(false,true))return;
    showProgress("应用更新","正在下载 0%");
    io.execute(()->{
      File dir=new File(activity.getCacheDir(),"updates");dir.mkdirs();
      File part=new File(dir,"update.part"),apk=new File(dir,"update-"+meta.optInt("versionCode")+".apk");
      Exception failure=null;
      try{
        if(apk.isFile())verify(apk,meta);
        else{
          HttpURLConnection connection=open(meta.getString("apkUrl"),15000);activeConnection=connection;
          try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(part)){
            long expected=connection.getContentLengthLong(),received=0,last=0;byte[] buffer=new byte[65536];int n;
            while((n=in.read(buffer))!=-1){
              if(closed||cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException();
              out.write(buffer,0,n);received+=n;if(received>64L*1024*1024)throw new IOException("update too large");
              if(SystemClock.elapsedRealtime()-last>250){last=SystemClock.elapsedRealtime();final int percent=expected>0?(int)Math.min(100,received*100/expected):-1;main.post(()->{if(progress!=null)progress.setMessage(percent<0?"正在下载":"正在下载 "+percent+"%");});}
            }
            out.getFD().sync();
          }finally{connection.disconnect();activeConnection=null;}
          verify(part,meta);if(!part.renameTo(apk))throw new IOException("rename failed");
        }
        Log.i("DualPhoneUpdate","verified_version="+meta.getInt("versionCode"));
      }catch(Exception e){failure=e;part.delete();apk.delete();}
      final Exception error=failure;
      main.post(()->{
        busy.set(false);dismissProgress();if(!alive()||cancelled)return;
        if(error!=null){Log.i("DualPhoneUpdate","download_rejected "+error.getClass().getSimpleName());toast("更新未完成，请稍后重试");return;}
        pending=apk;pendingMeta=meta;requestInstall();
      });
    });
  }
  private void verify(File apk,JSONObject meta)throws Exception{
    MessageDigest digest=MessageDigest.getInstance("SHA-256");
    try(InputStream in=new FileInputStream(apk)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
    StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
    if(!hash.toString().equalsIgnoreCase(meta.getString("sha256")))throw new SecurityException("checksum");
    PackageManager pm=activity.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
    PackageInfo candidate=pm.getPackageArchiveInfo(apk.getAbsolutePath(),flags),current=pm.getPackageInfo(activity.getPackageName(),flags);
    if(candidate==null||!current.packageName.equals(candidate.packageName))throw new SecurityException("package");
    long version=Build.VERSION.SDK_INT>=28?candidate.getLongVersionCode():candidate.versionCode;
    if(version!=meta.getInt("versionCode")||version<=BuildConfig.VERSION_CODE)throw new SecurityException("version");
    android.content.pm.Signature[] expected=signatures(current),actual=signatures(candidate);
    if(expected.length==0||actual.length!=expected.length)throw new SecurityException("signature count");
    Set<String> a=new HashSet<>(),b=new HashSet<>();for(android.content.pm.Signature sig:expected)a.add(sig.toCharsString());for(android.content.pm.Signature sig:actual)b.add(sig.toCharsString());
    if(!a.equals(b))throw new SecurityException("signature mismatch");
  }
  private android.content.pm.Signature[] signatures(PackageInfo p){
    if(Build.VERSION.SDK_INT>=28)return p.signingInfo==null?new android.content.pm.Signature[0]:p.signingInfo.getApkContentsSigners();
    return p.signatures==null?new android.content.pm.Signature[0]:p.signatures;
  }
  private void requestInstall(){
    if(!activity.getPackageManager().canRequestPackageInstalls()){
      AlertDialog d=new AlertDialog.Builder(activity).setTitle("允许应用更新").setMessage("首次更新需要允许双机传安装应用。打开开关后返回，会继续更新。")
        .setNegativeButton("稍后",null).setPositiveButton("去允许",(x,w)->{
          awaitingPermission=true;
          try{activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));}catch(Exception e){awaitingPermission=false;toast("无法打开安装设置");}
        }).create();show(d);return;
    }
    install();
  }
  private void install(){
    if(pending==null||!pending.isFile()||pendingMeta==null)return;
    try{
      Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".updates",pending);
      Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive");
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("更新",uri));
      activity.startActivity(intent);Log.i("DualPhoneUpdate","installer_opened");
    }catch(Exception e){toast("无法打开安装界面，请在检查更新中重试");}
  }
  private void showProgress(String title,String message){
    cancelled=false;progress=new AlertDialog.Builder(activity).setTitle(title).setMessage(message).setNegativeButton("取消",(d,w)->cancelRequest()).create();
    progress.setOnCancelListener(d->cancelRequest());show(progress);
  }
  private void cancelRequest(){cancelled=true;HttpURLConnection connection=activeConnection;if(connection!=null)connection.disconnect();}
  private void dismissProgress(){if(progress!=null){progress.dismiss();progress=null;}}
  private void show(AlertDialog dialog){
    if(!alive())return;activeDialog=dialog;dialog.show();if(dialog.getWindow()!=null){GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(244,245,247));bg.setCornerRadius(18*activity.getResources().getDisplayMetrics().density);dialog.getWindow().setBackgroundDrawable(bg);}
  }
  private boolean alive(){return !closed&&!activity.isFinishing()&&!activity.isDestroyed();}
  private void toast(String text){if(alive())Toast.makeText(activity,text,Toast.LENGTH_SHORT).show();}
}
