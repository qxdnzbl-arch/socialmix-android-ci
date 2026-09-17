package com.suisuinian.app

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
        assertTrue("Real Kehua signup failed: " + result.message, result.success)
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
}
