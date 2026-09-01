package com.suisuinian.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    fun cachedStartupOptimisticSendAndRealtimeReceive() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val ctxA = IsolatedContext(base, "core_quality_a")
        val ctxB = IsolatedContext(base, "core_quality_b")
        val a = SupabaseApi(ctxA)
        val b = SupabaseApi(ctxB)
        a.logout(); b.logout()

        val loginA = a.signIn("skillci_a_fe71b659@gmail.com", "gFxNcUHRWI91e5dIn_WJhZ4S")
        val loginB = b.signIn("skillci_b_3b1f86a9@gmail.com", "PcX9ZdzKPVEwtWmZOaiyY88Y")
        assertTrue("A login failed: ${loginA.message}", loginA.success)
        assertTrue("B login failed: ${loginB.message}", loginB.success)

        val pa = a.myProfile()
        val pb = b.myProfile()
        assertNotNull(pa)
        assertNotNull(pb)
        val profileA = requireNotNull(pa)
        val profileB = requireNotNull(pb)

        val recreatedA = SupabaseApi(IsolatedContext(base, "core_quality_a"))
        val cachedProfile = recreatedA.cachedProfile()
        assertNotNull("profile cache missing after successful sync", cachedProfile)
        assertTrue(requireNotNull(cachedProfile).id == profileA.id)

        if (a.friends().none { it.id == profileB.id }) {
            assertNull("friend request failed", a.sendFriendRequest(profileB.id))
            val request = b.incomingRequests().firstOrNull { it.sender.id == profileA.id }
            assertNotNull("B did not receive friend request", request)
            assertNull("accept friend request failed", b.respondFriendRequest(requireNotNull(request).id, true))
        }

        val summaries = a.directConversations()
        assertTrue("conversation list is empty after friendship", summaries.any { it.friend.id == profileB.id })
        assertTrue(
            "conversation cache was not persisted",
            a.cachedConversations().any { it.friend.id == profileB.id }
        )

        val cid = a.conversationId(profileB.id)
        assertNotNull("direct conversation missing", cid)
        val conversationId = requireNotNull(cid)

        val aView = a.messages(conversationId)
        val bView = b.messages(conversationId)
        val marker = "core-fast-${System.currentTimeMillis()}"

        val sendJob = launch { assertNull("A send failed", a.sendText(conversationId, marker)) }

        var localAppearedFast = false
        repeat(10) {
            if (aView.any { it.content == marker }) {
                localAppearedFast = true
                return@repeat
            }
            delay(50)
        }
        assertTrue("outgoing message did not appear locally within 500ms", localAppearedFast)
        sendJob.join()

        var realtimeReceived = false
        repeat(20) {
            if (bView.any { it.content == marker }) {
                realtimeReceived = true
                return@repeat
            }
            delay(150)
        }
        assertTrue("B did not receive message through realtime within 3s", realtimeReceived)

        val cachedMessages = SupabaseApi(IsolatedContext(base, "core_quality_a")).cachedMessages(conversationId)
        assertTrue("message cache did not persist confirmed message", cachedMessages.any { it.content == marker })
    }
}
