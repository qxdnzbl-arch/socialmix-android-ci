package com.suisuinian.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity

class KehuaActivity : ComponentActivity() {
    companion object {
        const val PROD_URL = "https://nvwdtfnhsyfdopaxdylx.supabase.co/functions/v1/kehua-original-web"
    }

    lateinit var webView: WebView
        private set
    @Volatile var lastFinishedUrl: String? = null
        private set
    @Volatile var lastPageTitle: String? = null
        private set

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserCode = 7401

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(248, 248, 250)
        window.navigationBarColor = Color.WHITE

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(248, 248, 250))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = settings.userAgentString + " KehuaAndroid/1.0"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                settings.safeBrowsingEnabled = true
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    val sameProject = uri.scheme == "https" && uri.host == "nvwdtfnhsyfdopaxdylx.supabase.co"
                    if (sameProject) return false
                    return try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                        true
                    } catch (_: ActivityNotFoundException) {
                        true
                    }
                }
                override fun onPageFinished(view: WebView, url: String) {
                    lastFinishedUrl = url
                    lastPageTitle = view.title
                    super.onPageFinished(view, url)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = filePathCallback
                    return try {
                        startActivityForResult(
                            fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "image/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            },
                            fileChooserCode
                        )
                        true
                    } catch (_: ActivityNotFoundException) {
                        fileCallback = null
                        false
                    }
                }
            }
        }

        setContentView(webView)
        if (savedInstanceState == null) webView.loadUrl(PROD_URL) else webView.restoreState(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("WebChromeClient file chooser compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == fileChooserCode) {
            val result = if (resultCode == Activity.RESULT_OK) WebChromeClient.FileChooserParams.parseResult(resultCode, data) else null
            fileCallback?.onReceiveValue(result)
            fileCallback = null
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.destroy()
        super.onDestroy()
    }
}
