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
        val accounts = listOf(
            arrayOf("Skill CI A", "skillci_a_7e8d6e", "skillci_a_fe71b659@gmail.com", "gFxNcUHRWI91e5dIn_WJhZ4S"),
            arrayOf("Skill CI B", "skillci_b_3d9cbd", "skillci_b_3b1f86a9@gmail.com", "PcX9ZdzKPVEwtWmZOaiyY88Y"),
            arrayOf("Skill CI C", "skillci_c_95c25a", "skillci_c_2d944368@gmail.com", "492m3xV1eezD-52fQfBMoHj2")
        )

        for (account in accounts) {
            val api = SupabaseApi(context)
            api.logout()
            val result = api.signUp(account[0], account[1], account[2], account[3])
            assertTrue("registration failed for ${account[1]}: ${result.message}", result.success)
            api.logout()
        }
    }
}
