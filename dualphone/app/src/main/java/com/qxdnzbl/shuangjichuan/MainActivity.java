package com.qxdnzbl.shuangjichuan;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.Toast;
import java.util.*;

public class MainActivity extends Activity {
  static final String APP_URL = "https://shuangji-chuan.floot.app";
  static final int FILE_CHOOSER = 9101;
  WebView web;
  ValueCallback<Uri[]> fileCallback;

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setStatusBarColor(Color.WHITE);
    getWindow().setNavigationBarColor(Color.WHITE);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

    web = new WebView(this);
    web.setBackgroundColor(Color.WHITE);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setAllowFileAccess(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    s.setCacheMode(WebSettings.LOAD_DEFAULT);

    CookieManager cm = CookieManager.getInstance();
    cm.setAcceptCookie(true);
    cm.setAcceptThirdPartyCookies(web, true);

    web.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
        Uri u=req.getUrl();
        if ("http".equals(u.getScheme()) || "https".equals(u.getScheme())) {
          view.loadUrl(u.toString());
          return true;
        }
        try { startActivity(new Intent(Intent.ACTION_VIEW,u)); } catch(Exception ignored) {}
        return true;
      }
      @Override public void onPageFinished(WebView view,String url) {
        super.onPageFinished(view,url);
        CookieManager.getInstance().flush();
      }
    });

    web.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> callback, FileChooserParams params) {
        if(fileCallback!=null) fileCallback.onReceiveValue(null);
        fileCallback=callback;
        Intent i=params.createIntent();
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        try { startActivityForResult(i,FILE_CHOOSER); }
        catch(Exception e) {
          fileCallback=null;
          Toast.makeText(MainActivity.this,"无法打开文件选择器",Toast.LENGTH_SHORT).show();
          return false;
        }
        return true;
      }
    });

    web.setDownloadListener((url,userAgent,contentDisposition,mimetype,contentLength)->{
      try {
        DownloadManager.Request r=new DownloadManager.Request(Uri.parse(url));
        String cookie=CookieManager.getInstance().getCookie(url);
        if(cookie!=null) r.addRequestHeader("Cookie",cookie);
        if(userAgent!=null) r.addRequestHeader("User-Agent",userAgent);
        r.setMimeType(mimetype);
        r.setTitle(URLUtil.guessFileName(url,contentDisposition,mimetype));
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,URLUtil.guessFileName(url,contentDisposition,mimetype));
        ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
        Toast.makeText(this,"开始下载",Toast.LENGTH_SHORT).show();
      } catch(Exception e) {
        try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
        catch(Exception ignored) {}
      }
    });

    setContentView(web);
    if(b==null) web.loadUrl(APP_URL);
  }

  @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
    super.onActivityResult(requestCode,resultCode,data);
    if(requestCode!=FILE_CHOOSER || fileCallback==null) return;
    Uri[] result=null;
    if(resultCode==RESULT_OK && data!=null) {
      if(data.getClipData()!=null) {
        int n=data.getClipData().getItemCount();
        result=new Uri[n];
        for(int i=0;i<n;i++) result[i]=data.getClipData().getItemAt(i).getUri();
      } else if(data.getData()!=null) result=new Uri[]{data.getData()};
    }
    fileCallback.onReceiveValue(result);
    fileCallback=null;
  }

  @Override public void onBackPressed() {
    if(web!=null && web.canGoBack()) web.goBack(); else super.onBackPressed();
  }

  @Override protected void onPause() {
    super.onPause();
    CookieManager.getInstance().flush();
  }
}
