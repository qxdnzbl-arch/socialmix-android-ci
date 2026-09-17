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
    fun realAuthEndpointIsReachable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val api = KehuaApi(context)
        api.logout()
        val email = "kehua-ci-nonexistent-" + System.currentTimeMillis() + "@gmail.com"
        val result = api.signIn(email, "KehuaCiRelease2026!")
        assertFalse("Nonexistent account unexpectedly signed in", result.success)
        assertTrue(
            "Real Kehua auth endpoint did not return the expected credential response: " + result.message,
            result.message.contains("邮箱或密码")
        )
    }
}