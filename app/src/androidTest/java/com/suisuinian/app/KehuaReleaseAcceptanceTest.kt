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

@RunWith(AndroidJUnit4::class)
class KehuaReleaseAcceptanceTest {
    @Test
    fun originalEvidenceLockedClientLoads() {
        ActivityScenario.launch(KehuaActivity::class.java).use { scenario ->
            val ready = CountDownLatch(1)
            var source = ""
            var pageUrl = ""
            scenario.onActivity { activity ->
                activity.webView.postDelayed({
                    pageUrl = activity.webView.url.orEmpty()
                    activity.webView.evaluateJavascript(
                        "(document.documentElement && document.documentElement.outerHTML || '').slice(0,120000)"
                    ) { value ->
                        source = value.orEmpty()
                        ready.countDown()
                    }
                }, 4500)
            }
            assertTrue("Bundled client was not readable", ready.await(20, TimeUnit.SECONDS))
            assertTrue("Unexpected base URL: " + pageUrl, pageUrl.startsWith(KehuaActivity.PROD_URL))
            assertTrue(source.contains("此刻，说你想说的话～"))
            assertTrue(source.contains("共鸣已到达。请签收～！"))
            assertTrue(source.contains("正在寻找共鸣，请稍等～"))
            assertTrue(source.contains("我的动态"))
            assertTrue(source.contains("点亮"))
            assertFalse("Rejected lavender nav pill returned", source.contains("class=\\\"pill"))
            assertFalse("Rejected home feed label returned", source.contains("我说过的话"))
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
