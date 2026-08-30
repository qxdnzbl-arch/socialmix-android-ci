package com.suisuinian.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase1RegistrationAcceptanceTest {
    @Test
    fun createsRealSupabaseAccountsAndProfiles() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val api = SupabaseApi(context)
        api.logout()
        val result = api.signUp(
            "Skill CI B",
            "skillci_b_3d9cbd",
            "skillci_b_3b1f86a9@gmail.com",
            "PcX9ZdzKPVEwtWmZOaiyY88Y"
        )
        assertTrue("registration failed for skillci_b_3d9cbd: ${result.message}", result.success)
        assertTrue("expected email confirmation flow", result.needsEmailConfirmation || api.isLoggedIn)
        api.logout()
    }
}
