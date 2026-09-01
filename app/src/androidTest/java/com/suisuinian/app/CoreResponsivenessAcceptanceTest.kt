package com.suisuinian.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoreResponsivenessAcceptanceTest {
    private class IsolatedContext(base: Context, private val suffix: String) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("${name}_$suffix", mode)
    }

    @Test
    fun cachedStartupOptimisticSendRealtimeReceiveAndNoDuplicate() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val ctxA = IsolatedContext(base, "production_core_a")
        val ctxB = IsolatedContext(base, "production_core_b")
        val a = SupabaseApi(ctxA)
        val b = SupabaseApi(ctxB)
        a.logout(); b.logout()

        val loginA = a.signIn("skillci_a_fe71b659@gmail.com", "gFxNcUHRWI91e5dIn_WJhZ4S")
        val loginB = b.signIn("skillci_b_3b1f86a9@gmail.com", "PcX9ZdzKPVEwtWmZOaiyY88Y")
        assertTrue("A login failed: ${loginA.message}", loginA.success)
        assertTrue("B login failed: ${loginB.message}", loginB.success)

        val profileA = requireNotNull(a.myProfile())
        val profileB = requireNotNull(b.myProfile())

        val recreatedA = SupabaseApi(IsolatedContext(base, "production_core_a"))
        val cachedProfile = recreatedA.cachedProfile()
        assertNotNull("profile cache missing after successful sync", cachedProfile)
        assertEquals(profileA.id, requireNotNull(cachedProfile).id)

        if (a.friends().none { it.id == profileB.id }) {
            assertNull("friend request failed", a.sendFriendRequest(profileB.id))
            val request = b.incomingRequests().firstOrNull { it.sender.id == profileA.id }
            assertNotNull("B did not receive friend request", request)
            assertNull("accept friend request failed", b.respondFriendRequest(requireNotNull(request).id, true))
        }

        val summaries = a.directConversations()
        assertTrue("conversation list is empty after friendship", summaries.any { it.friend.id == profileB.id })
        assertTrue("conversation cache missing", a.cachedConversations().any { it.friend.id == profileB.id })

        val conversationId = requireNotNull(a.conversationId(profileB.id))
        val aView = a.chatFeed(conversationId)
        val bView = b.chatFeed(conversationId)
        a.syncMessages(conversationId)
        b.syncMessages(conversationId)
        a.startRealtime()
        b.startRealtime()

        // Let both authenticated realtime sockets become warm before measuring receive latency.
        delay(1200)

        val marker = "production-fast-${System.currentTimeMillis()}"
        val sendJob = launch { assertNull("A send failed", a.sendText(conversationId, marker)) }

        var localAppearedFast = false
        repeat(10) {
            if (aView.any { it.content == marker }) {
                localAppearedFast = true
                return@repeat
            }
            delay(30)
        }
        assertTrue("outgoing message did not appear locally within 300ms", localAppearedFast)
        sendJob.join()

        var realtimeReceived = false
        repeat(20) {
            if (bView.any { it.content == marker }) {
                realtimeReceived = true
                return@repeat
            }
            delay(100)
        }
        assertTrue("B did not receive warm realtime message within 2s", realtimeReceived)

        // Reconcile against the server and verify optimistic + realtime paths did not duplicate the row.
        a.syncMessages(conversationId)
        b.syncMessages(conversationId)
        assertEquals("A has duplicate message bubble", 1, aView.count { it.content == marker })
        assertEquals("B has duplicate message bubble", 1, bView.count { it.content == marker })

        val cachedMessages = SupabaseApi(IsolatedContext(base, "production_core_a")).cachedMessages(conversationId)
        assertTrue("message cache did not persist confirmed message", cachedMessages.any { it.content == marker && it.delivery == MessageDelivery.SENT })
    }
}
