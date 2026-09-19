package com.suisuinian.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.webkit.WebViewAssetLoader

class KehuaOriginalActivity : ComponentActivity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this)
        setContentView(web)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.databaseEnabled = true
        web.settings.loadsImagesAutomatically = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(
                    "(function(){const t=document.body&&document.body.innerText||'';return t.includes('获取验证码')&&t.includes('手机号');})()"
                ) { ok ->
                    if (ok == "true") {
                        view.contentDescription = "KEHUA_RENDER_OK"
                        Log.i("KehuaVerify", "KEHUA_RENDER_OK")
                    } else {
                        view.contentDescription = "KEHUA_RENDER_PENDING"
                        Log.i("KehuaVerify", "KEHUA_RENDER_PENDING")
                    }
                }
            }
        }

        if (savedInstanceState == null) web.loadUrl(APP_URL) else web.restoreState(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        web.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val APP_URL = "https://appassets.androidplatform.net/assets/index.html"
    }
}
