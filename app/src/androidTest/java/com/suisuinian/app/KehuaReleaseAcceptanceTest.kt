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

@RunWith(AndroidJUnit4::class)
class KehuaReleaseAcceptanceTest {
    @Test
    fun nativeLauncherStartsWithoutCrash() {
        ActivityScenario.launch(KehuaNativeActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
        }
    }

    @Test
    fun nativeApkDoesNotRequestUnexpectedSensitivePermissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = packageInfo.requestedPermissions?.toSet().orEmpty()
        val forbidden = setOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG
        )
        assertTrue("Unexpected sensitive permissions: ${requested.intersect(forbidden)}", requested.intersect(forbidden).isEmpty())
    }

    @Test
    fun onlyNativeLauncherActivityIsExported() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        )
        val prefix = "com.suisuinian.app."
        val exportedActivities = packageInfo.activities.orEmpty()
            .filter { it.exported && it.name.startsWith(prefix) }
            .map { it.name }
            .toSet()
        assertTrue(
            "Only KehuaNativeActivity may be exported, found: $exportedActivities",
            exportedActivities == setOf(KehuaNativeActivity::class.java.name)
        )
        assertTrue(packageInfo.services.orEmpty().none { it.exported && it.name.startsWith(prefix) })
        assertTrue(packageInfo.receivers.orEmpty().none { it.exported && it.name.startsWith(prefix) })
        assertTrue(packageInfo.providers.orEmpty().none { it.exported && it.name.startsWith(prefix) })
    }
}
