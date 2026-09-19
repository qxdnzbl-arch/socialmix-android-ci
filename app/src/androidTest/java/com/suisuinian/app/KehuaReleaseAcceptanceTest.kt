package com.suisuinian.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.File
import java.io.FileOutputStream
import android.graphics.Bitmap

@RunWith(AndroidJUnit4::class)
class KehuaReleaseAcceptanceTest {
    @Test
    fun originalEvidenceLockedClientLoads() {
        ActivityScenario.launch(KehuaActivity::class.java).use { scenario ->
            val sourceReady = CountDownLatch(1)
            val visibleReady = CountDownLatch(1)
            var source = ""
            var pageUrl = ""
            var visibleText = ""
            scenario.onActivity { activity ->
                activity.webView.postDelayed({
                    pageUrl = activity.webView.url.orEmpty()
                    activity.webView.evaluateJavascript(
                        "(document.documentElement&&document.documentElement.outerHTML||'').slice(0,120000)"
                    ) { value ->
                        source = value.orEmpty()
                        sourceReady.countDown()
                    }
                    activity.webView.evaluateJavascript(
                        "(document.getElementById('app')&&document.getElementById('app').innerText||'').slice(0,3000)"
                    ) { value ->
                        visibleText = value.orEmpty()
                        visibleReady.countDown()
                    }
                }, 4500)
            }
            assertTrue("Bundled client source was not readable", sourceReady.await(20, TimeUnit.SECONDS))
            assertTrue("Rendered client UI was not readable", visibleReady.await(20, TimeUnit.SECONDS))
            assertTrue("Unexpected base URL: " + pageUrl, pageUrl.startsWith(KehuaActivity.PROD_URL))
            assertTrue(source.contains("此刻，说你想说的话～"))
            assertTrue(source.contains("共鸣已到达。请签收～！"))
            assertTrue(source.contains("正在寻找共鸣，请稍等～"))
            assertTrue(source.contains("我的动态"))
            assertTrue(source.contains("点亮"))
            assertTrue("JavaScript did not render login UI: " + visibleText, visibleText.contains("可话") && visibleText.contains("登录"))
            assertFalse("Rejected lavender nav pill returned", source.contains("class=\\\"pill"))
            assertFalse("Rejected home feed label returned", source.contains("我说过的话"))
        }
    }

    @Test
    fun renderOriginalHomeVisualFixtureForArtifactReview() {
        ActivityScenario.launch(KehuaActivity::class.java).use { scenario ->
            val ready = CountDownLatch(1)
            var visibleText = ""
            scenario.onActivity { activity ->
                activity.webView.postDelayed({
                    activity.webView.evaluateJavascript(
                        "rpc=async function(name,args){if(name==='kehua_prod_home')return {new_count:1,my_posts:[],resonances:[]};return {}};renderShell();switchTab(0).then(function(){document.body.dataset.visualReady='1'});'visual-started';"
                    ) {
                        activity.webView.postDelayed({
                            activity.webView.evaluateJavascript(
                                "(document.getElementById('app')&&document.getElementById('app').innerText||'').slice(0,2000)"
                            ) { text ->
                                visibleText = text.orEmpty()
                                val bitmap: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                                val out = File(activity.filesDir, "acceptance-original-home.png")
                                FileOutputStream(out).use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
                                ready.countDown()
                            }
                        }, 1200)
                    }
                }, 2500)
            }
            assertTrue("Visual fixture was not captured", ready.await(15, TimeUnit.SECONDS))
            assertTrue("Original heading missing from visual fixture: " + visibleText, visibleText.contains("此刻，说你想说的话～"))
            assertTrue("Original resonance status missing from visual fixture: " + visibleText, visibleText.contains("共鸣已到达。请签收～！"))
            assertFalse("Rejected home feed returned in visual fixture", visibleText.contains("我说过的话"))
        }
    }

    @Test
    fun appRequestsNoUnexpectedSensitivePermissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = packageInfo.requestedPermissions?.toSet().orEmpty()
        val forbidden = setOf(
            Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS, Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG
        )
        assertTrue(
            "Unexpected sensitive permissions: " + requested.intersect(forbidden),
            requested.intersect(forbidden).isEmpty()
        )
    }

    @Test
    fun onlyLauncherActivityIsExported() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        )
        val prefix = "com.suisuinian.app."
        val exportedActivities = packageInfo.activities.orEmpty()
            .filter { it.exported && it.name.startsWith(prefix) }
            .map { it.name }
            .toSet()
        assertTrue(exportedActivities == setOf(KehuaActivity::class.java.name))
        assertTrue(packageInfo.services.orEmpty().none { it.exported && it.name.startsWith(prefix) })
        assertTrue(packageInfo.receivers.orEmpty().none { it.exported && it.name.startsWith(prefix) })
        assertTrue(packageInfo.providers.orEmpty().none { it.exported && it.name.startsWith(prefix) })
    }
}
