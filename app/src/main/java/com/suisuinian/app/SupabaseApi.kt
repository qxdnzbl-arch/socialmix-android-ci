package com.suisuinian.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val SUPABASE_URL = "https://nvwdtfnhsyfdopaxdylx.supabase.co"
private const val SUPABASE_KEY = "sb_publishable_S4IE-ziO7WQ_JAK9tuQGgQ_cszwKBWB"
private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

data class LiveProfile(val id: String, val username: String, val displayName: String)
data class IncomingRequest(val id: String, val sender: LiveProfile)
data class LiveMessage(val id: String, val senderId: String, val content: String, val createdAt: String)
data class ConversationSummary(
    val conversationId: String,
    val friend: LiveProfile,
    val lastMessage: String,
    val lastMessageType: String,
    val lastMessageAt: String,
    val unreadCount: Long
)
data class AuthResult(val success: Boolean, val needsEmailConfirmation: Boolean = false, val message: String = "")

private class IdentityMessageList(private val source: SnapshotStateList<LiveMessage>) : AbstractList<LiveMessage>() {
    override val size: Int get() = source.size
    override fun get(index: Int): LiveMessage = source[index]
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

private class ChatCache {
    val state = mutableStateListOf<LiveMessage>()
    val view: List<LiveMessage> = IdentityMessageList(state)
}

class SupabaseApi(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("socialmix_live_session", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder().build()
    private val realtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val chatCaches = ConcurrentHashMap<String, ChatCache>()
    private val chatJobs = ConcurrentHashMap<String, Job>()
    private val sessionLock = Any()

    val isLoggedIn: Boolean
        get() = userId().isNotBlank() && (accessToken().isNotBlank() || refreshToken().isNotBlank())

    fun userId(): String = prefs.getString("user_id", "") ?: ""
    private fun accessToken(): String = prefs.getString("access_token", "") ?: ""
    private fun refreshToken(): String = prefs.getString("refresh_token", "") ?: ""

    suspend fun signUp(displayName: String, username: String, email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .put(
                    "data",
                    JSONObject()
                        .put("display_name", displayName.trim())
                        .put("username", username.trim().lowercase())
                )
            val json = callAuth("/auth/v1/signup", payload)
            val user = json.optJSONObject("user")
                ?: return@withContext AuthResult(false, message = errorText(json, "注册失败"))
            val token = json.optString("access_token")
            if (token.isBlank()) {
                return@withContext AuthResult(
                    success = true,
                    needsEmailConfirmation = true,
                    message = "注册成功，请先完成邮箱确认后登录"
                )
            }
            saveSession(json)
            AuthResult(true)
        } catch (e: Exception) {
            AuthResult(false, message = cleanError(e))
        }
    }

    suspend fun signIn(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("email", email.trim()).put("password", password)
            val json = callAuth("/auth/v1/token?grant_type=password", payload)
            if (json.optString("access_token").isBlank()) {
                return@withContext AuthResult(false, message = errorText(json, "登录失败"))
            }
            saveSession(json)
            AuthResult(true)
        } catch (e: Exception) {
            AuthResult(false, message = cleanError(e))
        }
    }

    fun logout() {
        chatJobs.values.forEach { it.cancel() }
        chatJobs.clear()
        chatCaches.clear()
        prefs.edit().clear().apply()
    }

    suspend fun myProfile(): LiveProfile? = withContext(Dispatchers.IO) {
        requireSession()
        val arr = restGet("/rest/v1/profiles?id=eq.${enc(userId())}&select=id,username,display_name&limit=1")
        profileFrom(arr.optJSONObject(0))
    }

    suspend fun searchExactUsername(username: String): LiveProfile? = withContext(Dispatchers.IO) {
        requireSession()
        val value = username.trim().lowercase()
        if (value.isBlank()) return@withContext null
        val arr = restGet("/rest/v1/profiles?username=eq.${enc(value)}&select=id,username,display_name&limit=1")
        val profile = profileFrom(arr.optJSONObject(0)) ?: return@withContext null
        if (profile.id == userId()) null else profile
    }

    suspend fun sendFriendRequest(targetId: String): String? = withContext(Dispatchers.IO) {
        try {
            requireSession()
            rpc("send_friend_request", JSONObject().put("target_user", targetId))
            null
        } catch (e: Exception) {
            cleanError(e)
        }
    }

    suspend fun incomingRequests(): List<IncomingRequest> = withContext(Dispatchers.IO) {
        requireSession()
        val arr = restGet(
            "/rest/v1/friend_requests?receiver_id=eq.${enc(userId())}&status=eq.pending&select=id,sender_id&order=created_at.desc"
        )
        buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val sender = profileById(item.optString("sender_id")) ?: continue
                add(IncomingRequest(item.optString("id"), sender))
            }
        }
    }

    suspend fun respondFriendRequest(requestId: String, accept: Boolean): String? = withContext(Dispatchers.IO) {
        try {
            requireSession()
            rpc(
                "respond_friend_request",
                JSONObject().put("request_id", requestId).put("accept_request", accept)
            )
            null
        } catch (e: Exception) {
            cleanError(e)
        }
    }

    suspend fun friends(): List<LiveProfile> = withContext(Dispatchers.IO) {
        requireSession()
        val me = userId()
        val arr = restGet(
            "/rest/v1/friendships?or=(user_a.eq.${enc(me)},user_b.eq.${enc(me)})&select=user_a,user_b&order=created_at.asc"
        )
        val ids = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val a = item.optString("user_a")
            val b = item.optString("user_b")
            ids += if (a == me) b else a
        }
        ids.distinct().mapNotNull { profileById(it) }
    }

    suspend fun directConversations(): List<ConversationSummary> = withContext(Dispatchers.IO) {
        requireSession()
        val text = rpc("list_my_direct_conversations", JSONObject())
        val arr = if (text.isBlank()) JSONArray() else JSONArray(text)
        buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val conversationId = item.optString("conversation_id")
                val friendId = item.optString("friend_id")
                if (conversationId.isBlank() || friendId.isBlank()) continue
                val friend = LiveProfile(
                    id = friendId,
                    username = item.optString("friend_username"),
                    displayName = item.optString("friend_display_name").ifBlank { item.optString("friend_username") }
                )
                add(
                    ConversationSummary(
                        conversationId = conversationId,
                        friend = friend,
                        lastMessage = if (item.isNull("last_message")) "" else item.optString("last_message"),
                        lastMessageType = if (item.isNull("last_message_type")) "" else item.optString("last_message_type"),
                        lastMessageAt = if (item.isNull("last_message_at")) "" else item.optString("last_message_at"),
                        unreadCount = item.optLong("unread_count", 0L)
                    )
                )
            }
        }
    }

    suspend fun conversationId(friendId: String): String? = withContext(Dispatchers.IO) {
        requireSession()
        val ids = listOf(userId(), friendId).sorted()
        val key = "${ids[0]}:${ids[1]}"
        val arr = restGet("/rest/v1/conversations?direct_key=eq.${enc(key)}&select=id&limit=1")
        arr.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
    }

    suspend fun markConversationRead(conversationId: String) = withContext(Dispatchers.IO) {
        requireSession()
        rpc("mark_conversation_read", JSONObject().put("target_conversation", conversationId))
        Unit
    }

    suspend fun messages(conversationId: String): List<LiveMessage> = withContext(Dispatchers.IO) {
        requireSession()
        val fetched = fetchMessages(conversationId)
        val cache = chatCaches.getOrPut(conversationId) { ChatCache() }
        withContext(Dispatchers.Main) {
            if (cache.state.toList() != fetched) {
                cache.state.clear()
                cache.state.addAll(fetched)
            }
        }
        ensureRealtimeListener(conversationId, cache)
        cache.view
    }

    suspend fun sendText(conversationId: String, text: String): String? = withContext(Dispatchers.IO) {
        val clean = text.trim()
        if (clean.isBlank()) return@withContext "消息不能为空"
        try {
            requireSession()
            val body = JSONObject()
                .put("conversation_id", conversationId)
                .put("sender_id", userId())
                .put("client_message_id", UUID.randomUUID().toString())
                .put("message_type", "text")
                .put("content", clean)
            restPost("/rest/v1/messages", body, prefer = "return=minimal")
            null
        } catch (e: Exception) {
            cleanError(e)
        }
    }

    private fun fetchMessages(conversationId: String): List<LiveMessage> {
        val arr = restGet(
            "/rest/v1/messages?conversation_id=eq.${enc(conversationId)}&select=id,sender_id,content,created_at&message_type=eq.text&order=created_at.asc&limit=300"
        )
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                add(
                    LiveMessage(
                        id = item.optString("id"),
                        senderId = item.optString("sender_id"),
                        content = item.optString("content"),
                        createdAt = item.optString("created_at")
                    )
                )
            }
        }
    }

    private fun ensureRealtimeListener(conversationId: String, cache: ChatCache) {
        if (chatJobs[conversationId]?.isActive == true) return
        chatJobs[conversationId] = realtimeScope.launch {
            runCatching {
                watchTextMessages(conversationId) { incoming ->
                    mainHandler.post {
                        val index = cache.state.indexOfFirst { it.id == incoming.id }
                        if (index < 0) {
                            cache.state.add(incoming)
                        } else if (cache.state[index] != incoming) {
                            cache.state[index] = incoming
                        }
                    }
                }
            }
        }
    }

    private suspend fun watchTextMessages(conversationId: String, onMessage: (LiveMessage) -> Unit) {
        requireSession()
        if (accessToken().isBlank()) return

        val realtimeClient = createSupabaseClient(SUPABASE_URL, SUPABASE_KEY) {
            accessToken = { accessToken() }
            install(Realtime)
        }
        val channel = realtimeClient.channel("chat-$conversationId-${UUID.randomUUID()}")
        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter = "conversation_id=eq.$conversationId"
        }

        coroutineScope {
            val collectJob = launch {
                changes.collect { action ->
                    val record = action.record
                    if (record["message_type"]?.jsonPrimitive?.contentOrNull != "text") return@collect
                    val id = record["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val senderId = record["sender_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val content = record["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val createdAt = record["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (id.isNotBlank() && senderId.isNotBlank()) {
                        onMessage(LiveMessage(id, senderId, content, createdAt))
                    }
                }
            }
            try {
                channel.subscribe(blockUntilSubscribed = true)
                awaitCancellation()
            } finally {
                collectJob.cancel()
                runCatching { channel.unsubscribe() }
            }
        }
    }

    private suspend fun profileById(id: String): LiveProfile? {
        if (id.isBlank()) return null
        val arr = restGet("/rest/v1/profiles?id=eq.${enc(id)}&select=id,username,display_name&limit=1")
        return profileFrom(arr.optJSONObject(0))
    }

    private fun profileFrom(item: JSONObject?): LiveProfile? {
        if (item == null) return null
        val id = item.optString("id")
        if (id.isBlank()) return null
        return LiveProfile(
            id = id,
            username = item.optString("username"),
            displayName = item.optString("display_name").ifBlank { item.optString("username") }
        )
    }

    private fun callAuth(path: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(SUPABASE_URL + path)
            .header("apikey", SUPABASE_KEY)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (!response.isSuccessful) {
                throw IllegalStateException(errorText(json, "请求失败 ${response.code}"))
            }
            return json
        }
    }

    private fun saveSession(json: JSONObject) {
        val editor = prefs.edit()
        json.optString("access_token").takeIf { it.isNotBlank() }?.let { editor.putString("access_token", it) }
        json.optString("refresh_token").takeIf { it.isNotBlank() }?.let { editor.putString("refresh_token", it) }
        json.optJSONObject("user")?.optString("id")?.takeIf { it.isNotBlank() }?.let { editor.putString("user_id", it) }
        editor.apply()
    }

    private suspend fun requireSession() {
        if (!ensureSession()) throw IllegalStateException("登录已过期，请重新登录")
    }

    private suspend fun ensureSession(): Boolean = withContext(Dispatchers.IO) {
        if (userId().isBlank()) return@withContext false
        val token = accessToken()
        if (token.isBlank()) return@withContext refreshSessionBlocking(force = true)
        if (jwtExpiresSoon(token)) refreshSessionBlocking(force = false) else true
    }

    private fun jwtExpiresSoon(token: String): Boolean {
        return try {
            val parts = token.split('.')
            if (parts.size < 2) return true
            val payload = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                Charsets.UTF_8
            )
            val exp = JSONObject(payload).optLong("exp", 0L)
            exp <= (System.currentTimeMillis() / 1000L) + 90L
        } catch (_: Exception) {
            true
        }
    }

    private fun refreshSessionBlocking(force: Boolean): Boolean = synchronized(sessionLock) {
        val current = accessToken()
        if (!force && current.isNotBlank() && !jwtExpiresSoon(current)) return@synchronized true

        val refresh = refreshToken()
        if (refresh.isBlank()) return@synchronized false

        try {
            val json = callAuth(
                "/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", refresh)
            )
            if (json.optString("access_token").isBlank()) return@synchronized false
            saveSession(json)
            true
        } catch (e: Exception) {
            val message = cleanError(e)
            if (
                message.contains("refresh token", ignoreCase = true) ||
                message.contains("invalid token", ignoreCase = true) ||
                message.contains("token has been revoked", ignoreCase = true)
            ) {
                prefs.edit().clear().apply()
                false
            } else {
                throw e
            }
        }
    }

    private fun authRequest(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("apikey", SUPABASE_KEY)
            .header("Authorization", "Bearer ${accessToken()}")
            .header("Content-Type", "application/json")
    }

    private fun restGet(path: String): JSONArray {
        var attempt = 0
        while (true) {
            val request = authRequest(SUPABASE_URL + path).get().build()
            val response = http.newCall(request).execute()
            val code = response.code
            val ok = response.isSuccessful
            val text = response.use { it.body?.string().orEmpty() }
            if (ok) return if (text.isBlank()) JSONArray() else JSONArray(text)

            if (attempt == 0 && isAuthFailure(code, text) && refreshSessionBlocking(force = true)) {
                attempt++
                continue
            }
            throw IllegalStateException(
                if (isAuthFailure(code, text)) "登录已过期，请重新登录" else parseError(text, code)
            )
        }
    }

    private fun restPost(path: String, body: JSONObject, prefer: String? = null): String {
        var attempt = 0
        while (true) {
            val builder = authRequest(SUPABASE_URL + path)
            if (prefer != null) builder.header("Prefer", prefer)
            val request = builder.post(body.toString().toRequestBody(JSON_MEDIA)).build()
            val response = http.newCall(request).execute()
            val code = response.code
            val ok = response.isSuccessful
            val text = response.use { it.body?.string().orEmpty() }
            if (ok) return text

            if (attempt == 0 && isAuthFailure(code, text) && refreshSessionBlocking(force = true)) {
                attempt++
                continue
            }
            throw IllegalStateException(
                if (isAuthFailure(code, text)) "登录已过期，请重新登录" else parseError(text, code)
            )
        }
    }

    private fun isAuthFailure(code: Int, text: String): Boolean =
        code == 401 || text.contains("JWT expired", ignoreCase = true)

    private fun rpc(name: String, body: JSONObject): String = restPost("/rest/v1/rpc/$name", body)

    private fun parseError(text: String, code: Int): String {
        return try {
            val json = JSONObject(text)
            json.optString("message")
                .ifBlank { json.optString("msg") }
                .ifBlank { json.optString("error_description") }
                .ifBlank { "请求失败 $code" }
        } catch (_: Exception) {
            "请求失败 $code"
        }
    }

    private fun errorText(json: JSONObject, fallback: String): String =
        json.optString("msg")
            .ifBlank { json.optString("message") }
            .ifBlank { json.optString("error_description") }
            .ifBlank { fallback }

    private fun cleanError(error: Exception): String =
        error.message?.replace("java.lang.IllegalStateException: ", "")?.take(160) ?: "操作失败"

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
