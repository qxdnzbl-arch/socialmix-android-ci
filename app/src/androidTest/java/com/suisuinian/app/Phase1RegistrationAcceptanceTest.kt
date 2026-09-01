package com.suisuinian.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase1RegistrationAcceptanceTest {
    private class IsolatedContext(base: Context, private val suffix: String) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("${name}_$suffix", mode)
    }

    @Test
    fun phase1EndToEndAcceptance() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val ctxA = IsolatedContext(base, "skill_a")
        val ctxB = IsolatedContext(base, "skill_b")
        val ctxC = IsolatedContext(base, "skill_c")
        val a = SupabaseApi(ctxA)
        val b = SupabaseApi(ctxB)
        val c = SupabaseApi(ctxC)
        a.logout(); b.logout(); c.logout()

        val loginA = a.signIn("skillci_a_fe71b659@gmail.com", "gFxNcUHRWI91e5dIn_WJhZ4S")
        val loginB = b.signIn("skillci_b_3b1f86a9@gmail.com", "PcX9ZdzKPVEwtWmZOaiyY88Y")
        val loginC = c.signIn("skillci_c_2d944368@gmail.com", "492m3xV1eezD-52fQfBMoHj2")
        assertTrue("A login failed: ${loginA.message}", loginA.success)
        assertTrue("B login failed: ${loginB.message}", loginB.success)
        assertTrue("C login failed: ${loginC.message}", loginC.success)

        val profileA = requireNotNull(a.myProfile())
        val profileB = requireNotNull(b.myProfile())
        assertTrue(profileA.username == "skillci_a_7e8d6e")
        assertTrue(profileB.username == "skillci_b_3d9cbd")

        val foundB = a.searchExactUsername("skillci_b_3d9cbd")
        assertNotNull("A cannot search B", foundB)

        if (a.friends().none { it.id == profileB.id }) {
            assertNull("friend request failed", a.sendFriendRequest(profileB.id))
            val request = b.incomingRequests().firstOrNull { it.sender.id == profileA.id }
            assertNotNull("B did not receive friend request", request)
            assertNull("accept friend request failed", b.respondFriendRequest(requireNotNull(request).id, true))
        }
        assertTrue("A does not see B as friend", a.friends().any { it.id == profileB.id })
        assertTrue("B does not see A as friend", b.friends().any { it.id == profileA.id })

        val cid = requireNotNull(a.conversationId(profileB.id))
        val bRealtime = b.chatFeed(cid)
        b.syncMessages(cid)
        a.startRealtime()
        b.startRealtime()
        delay(1200)

        val marker = "skill-phase1-${System.currentTimeMillis()}"
        assertNull("A send failed", a.sendText(cid, marker))
        var realtimeReceived = false
        repeat(30) {
            if (bRealtime.any { it.content == marker }) {
                realtimeReceived = true
                return@repeat
            }
            delay(100)
        }
        assertTrue("B did not receive A message through realtime", realtimeReceived)

        val bAfterRestart = SupabaseApi(IsolatedContext(base, "skill_b"))
        assertTrue("B session did not survive API recreation", bAfterRestart.isLoggedIn)
        var history = bAfterRestart.cachedMessages(cid)
        if (history.none { it.content == marker }) history = bAfterRestart.syncMessages(cid)
        assertTrue("message history did not persist", history.any { it.content == marker })

        val cView = c.chatFeed(cid)
        c.startRealtime()
        runCatching { c.syncMessages(cid) }
        assertFalse("non-member C can read existing conversation", cView.any { it.content == marker })
        val privateMarker = "skill-private-${System.currentTimeMillis()}"
        assertNull("A private send failed", a.sendText(cid, privateMarker))
        delay(1800)
        assertFalse("non-member C received conversation realtime data", cView.any { it.content == privateMarker })

        val sessionPrefs = base.getSharedPreferences("socialmix_live_session_skill_a", Context.MODE_PRIVATE)
        val expiredJwt = "eyJhbGciOiJub25lIn0.eyJleHAiOjF9.invalid"
        sessionPrefs.edit().putString("access_token", expiredJwt).commit()
        val refreshedProfile = a.myProfile()
        assertNotNull("expired JWT was not refreshed using refresh_token", refreshedProfile)

        a.logout()
        assertFalse("logout left A logged in", a.isLoggedIn)
        assertTrue("logged-out user can still read friends", runCatching { a.friends() }.isFailure)
        assertTrue("logged-out user can still sync chat", runCatching { a.syncMessages(cid) }.isFailure)
    }
}
