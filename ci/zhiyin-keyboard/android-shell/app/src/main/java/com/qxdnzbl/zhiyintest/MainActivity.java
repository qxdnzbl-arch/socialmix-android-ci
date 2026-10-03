package com.qxdnzbl.zhiyintest;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
  private WebView web;
  private FrameLayout root;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

    WebView.setWebContentsDebuggingEnabled(true);
    root = new FrameLayout(this);
    web = new WebView(this);
    web.setWebViewClient(new WebViewClient());
    web.setWebChromeClient(new WebChromeClient());

    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);
    s.setAllowContentAccess(true);

    root.addView(web, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));
    setContentView(root);

    // Android browser-like resize fallback for CI:
    // when the real system IME covers the window, shrink the WebView to the actually visible frame.
    root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      @Override public void onGlobalLayout() {
        Rect visible = new Rect();
        root.getWindowVisibleDisplayFrame(visible);
        int[] loc = new int[2];
        root.getLocationOnScreen(loc);
        int visibleHeight = Math.max(1, visible.bottom - Math.max(visible.top, loc[1]));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) web.getLayoutParams();
        int fullHeight = root.getHeight();
        boolean keyboard = fullHeight > 0 && (fullHeight - visibleHeight) > fullHeight * 0.18f;
        int target = keyboard ? visibleHeight : ViewGroup.LayoutParams.MATCH_PARENT;
        if (lp.height != target) {
          lp.height = target;
          web.setLayoutParams(lp);
        }
      }
    });

    web.loadUrl("file:///android_asset/index.html");
  }

  @Override public void onBackPressed() {
    if (web != null && web.canGoBack()) web.goBack();
    else super.onBackPressed();
  }
}
