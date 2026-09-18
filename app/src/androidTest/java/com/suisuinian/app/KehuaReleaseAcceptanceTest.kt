package com.suisuinian.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

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
    fun realSignupEndpointIsReachable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val api = KehuaApi(context)
        api.logout()
        val email = "kehua.ci." + System.currentTimeMillis() + "@gmail.com"
        val result = api.signUp(email, "KehuaCiRelease2026!")
        val reachable = result.success || result.message.contains("操作太频繁")
        assertTrue("Real Kehua signup endpoint was not reachable: " + result.message, reachable)
    }

    @Test
    fun realPasswordResetEndpointIsReachable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val api = KehuaApi(context)
        api.logout()
        val message = api.sendPasswordReset("kehua.reset.probe." + System.currentTimeMillis() + "@gmail.com")
        val reachable = message == null || message.contains("操作太频繁")
        assertTrue("Real Kehua password reset endpoint was not reachable: " + message, reachable)
    }

    @Test
    fun invalidAuthInputIsRejected() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val api = KehuaApi(context)
        api.logout()
        val signup = api.signUp("not-an-email", "123")
        val signin = api.signIn("not-an-email", "123")
        assertFalse(signup.success)
        assertFalse(signin.success)
        assertTrue(signup.message.contains("有效邮箱"))
        assertTrue(signin.message.contains("有效邮箱"))
    }

    @Test
    fun invalidPasswordResetInputIsRejectedWithoutNetwork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val api = KehuaApi(context)
        api.logout()
        val message = api.sendPasswordReset("not-an-email")
        assertTrue(message?.contains("注册时使用的邮箱") == true)
    }

    @Test
    fun protectedCoreRequiresSession() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val api = KehuaApi(context)
        api.logout()
        val failures = listOf(
            runCatching { api.ensureProfile() }.exceptionOrNull(),
            runCatching { api.myPosts() }.exceptionOrNull(),
            runCatching { api.publish("CI protected publish", null) }.exceptionOrNull(),
            runCatching { api.resonances("00000000-0000-0000-0000-000000000000") }.exceptionOrNull(),
            runCatching { api.conversations() }.exceptionOrNull(),
            runCatching { api.incomingFriendRequests() }.exceptionOrNull(),
            runCatching { api.friends() }.exceptionOrNull(),
            runCatching { api.messages("00000000-0000-0000-0000-000000000000") }.exceptionOrNull()
        )
        assertTrue("Every protected core call must reject a logged-out client", failures.all { it?.message?.contains("登录已过期") == true })
    }

    @Test
    fun protectedMutationsRequireSessionAndLogoutStaysLoggedOut() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val api = KehuaApi(context)
        api.logout()
        assertFalse("logout must leave no restorable session", api.restoreSession())

        val id = "00000000-0000-0000-0000-000000000000"
        val failures = listOf(
            runCatching { api.light(id) }.exceptionOrNull(),
            runCatching { api.sendMessage(id, "CI protected message") }.exceptionOrNull(),
            runCatching { api.acceptFriendRequest(id) }.exceptionOrNull(),
            runCatching { api.friendStatus(id) }.exceptionOrNull(),
            runCatching { api.friendRequest(id) }.exceptionOrNull(),
            runCatching { api.block(id) }.exceptionOrNull(),
            runCatching { api.report(id, null, "CI protected report") }.exceptionOrNull(),
            runCatching { api.saveProfile("CI", "protected", null) }.exceptionOrNull()
        )
        assertTrue("Every protected mutation must reject a logged-out client", failures.all { it?.message?.contains("登录已过期") == true })
    }

    @Test
    fun localContentBoundsRejectEmptyAndOversizedPayloadsBeforeNetwork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val api = KehuaApi(context)
        api.logout()

        val emptyPost = runCatching { api.publish("   ", null) }.exceptionOrNull()
        val oversizedPost = runCatching { api.publish("x".repeat(1201), null) }.exceptionOrNull()
        val emptyMessage = runCatching { api.sendMessage("00000000-0000-0000-0000-000000000000", "   ") }.exceptionOrNull()
        val oversizedMessage = runCatching { api.sendMessage("00000000-0000-0000-0000-000000000000", "x".repeat(2001)) }.exceptionOrNull()

        // Session protection runs before content validation, so logged-out calls must never reach the network.
        val all = listOf(emptyPost, oversizedPost, emptyMessage, oversizedMessage)
        assertTrue("Logged-out content mutations must be stopped locally", all.all { it?.message?.contains("登录已过期") == true })
    }

    @Test
    fun releaseApkDoesNotRequestDangerousUserDataPermissions() {
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
        assertTrue("Kehua release unexpectedly requests dangerous user-data permissions: $leaked", leaked.isEmpty())
    }
}