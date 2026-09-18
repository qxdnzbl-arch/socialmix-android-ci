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

/**
 * Engineering-only regression gates for the current revival candidate.
 *
 * IMPORTANT: Product behavior is intentionally NOT accepted here. Visuals, auth/recovery,
 * and social interaction rules remain user-confirmation gates under KEHUA-REVIVAL-AUTHORITY.md.
 * These tests may only prove product-definition-neutral Android infrastructure properties.
 */
@RunWith(AndroidJUnit4::class)
class KehuaReleaseAcceptanceTest {
    @Test
    fun launcherStartsWithoutCrash() {
        ActivityScenario.launch(KehuaActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
        }
    }

    @Test
    fun engineeringApkDoesNotRequestDangerousUserDataPermissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
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
        val leaked = requested.intersect(forbidden)
        assertTrue("Kehua engineering candidate unexpectedly requests dangerous user-data permissions: $leaked", leaked.isEmpty())
    }

    @Test
    fun appOwnedComponentsDoNotExposeUnexpectedEntryPoints() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        )
        val appPrefix = "com.suisuinian.app."
        val exportedActivities = packageInfo.activities.orEmpty().filter { it.exported && it.name.startsWith(appPrefix) }.map { it.name }.toSet()
        val exportedServices = packageInfo.services.orEmpty().filter { it.exported && it.name.startsWith(appPrefix) }.map { it.name }
        val exportedReceivers = packageInfo.receivers.orEmpty().filter { it.exported && it.name.startsWith(appPrefix) }.map { it.name }
        val exportedProviders = packageInfo.providers.orEmpty().filter { it.exported && it.name.startsWith(appPrefix) }.map { it.name }

        assertTrue("Only KehuaActivity may be exported by Kehua code, found: $exportedActivities", exportedActivities == setOf(KehuaActivity::class.java.name))
        assertTrue("Kehua code must not export services: $exportedServices", exportedServices.isEmpty())
        assertTrue("Kehua code must not export receivers: $exportedReceivers", exportedReceivers.isEmpty())
        assertTrue("Kehua code must not export providers: $exportedProviders", exportedProviders.isEmpty())
    }
}
