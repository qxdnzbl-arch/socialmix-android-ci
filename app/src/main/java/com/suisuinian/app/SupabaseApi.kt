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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
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
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val SUPABASE_URL = "https://nvwdtfnhsyfdopaxdylx.supabase.co"
private const val SUPABASE_KEY = "sb_publishable_S4IE-ziO7WQ_JAK9tuQGgQ_cszwKBWB"
private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

data class LiveProfile(val id: String, val username: String, val displayName: String)
data class IncomingRequest(val id: String, val sender: LiveProfile)

enum class MessageDelivery { SENDING, SENT, FAILED }

data class LiveMessage(
    val id: String,
    val senderId: String,
    val content: String,
    val createdAt: String,
    val clientMessageId: String = "",
    val delivery: MessageDelivery = MessageDelivery.SENT,
    val errorMessage: String = ""
)

data class ConversationSummary(
    val conversationId: String,
    val friend: LiveProfile,
    val lastMessage: String,
    val lastMessageType: String,
    val lastMessageAt: String,
    val unreadCount: Long
)

data class AuthResult(
    val success: Boolean,
    val needsEmailConfirmation: Boolean = false,
    val message: String = ""
)

class SupabaseApi(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("socialmix_live_session", Context.MODE_PRIVATE)
    private val store = LocalChatStore(appContext)
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .writeTimeout(7, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionLock = Any()

    private val conversationState = mutableStateListOf<ConversationSummary>()
    private val friendState = mutableStateListOf<LiveProfile>()
    private val chatStates = ConcurrentHashMap<String, SnapshotStateList<LiveMessage>>()

    @Volatile
    private var activeConversationId: String? = null
    @Volatile
    private var inboxJob: Job? = null

    private val realtimeClient = createSupabaseClient(SUPABASE_URL, SUPABASE_KEY) {
        accessToken = { accessToken() }
        install(Realtime)
    }

    init {
        store.markInterruptedSendsFailed()
        conversationState.addAll(store.conversations())
        friendState.addAll(store.friends())
    }

    val isLoggedIn: Boolean
        get() = userId().isNotBlank() && (accessToken().isNotBlank() || refreshToken().isNotBlank())

    fun userId(): String = prefs.getString("user_id", "") ?: ""
    private fun accessToken(): String = prefs.getString("access_token", "") ?: ""
    private fun refreshToken(): String = prefs.getString("refresh_token", "") ?: ""

    fun cachedProfile(): LiveProfile? = store.profile()
    fun conversationFeed(): SnapshotStateList<ConversationSummary> = conversationState
    fun friendFeed(): SnapshotStateList<LiveProfile> = friendState
    fun cachedConversations(): List<ConversationSummary> = store.conversations()
    fun cachedFriends(): List<LiveProfile> = store.friends()
    fun cachedConversationId(friendId: String): String? = store.conversationIdForFriend(friendId)
    fun cachedMessages(conversationId: String): List<LiveMessage> = store.messages(conversationId)

    fun chatFeed(conversationId: String): SnapshotStateList<LiveMessage> =
        chatStates.getOrPut(conversationId) {
            mutableStateListOf<LiveMessage>().apply { addAll(store.messages(conversationId)) }
        }

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
            cacheAuthMetadata(user)
            startRealtime()
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
            json.optJSONObject("user")?.let { cacheAuthMetadata(it) }
            startRealtime()
            AuthResult(true)
        } catch (e: Exception) {
            AuthResult(false, message = cleanError(e))
        }
    }

    fun logout() {
        inboxJob?.cancel()
        inboxJob = null
        activeConversationId = null
        chatStates.clear()
        mainHandler.post {
            conversationState.clear()
            friendState.clear()
        }
        store.clearAll()
        prefs.edit().clear().apply()
    }

    fun startRealtime() {
        if (!isLoggedIn || inboxJob?.isActive == true) return
        inboxJob = scope.launch {
            var backoff = 700L
            while (isActive && isLoggedIn) {
                try {
                    watchInbox()
                    backoff = 700L
                } catch (_: CancellationException) {
                    throw CancellationException()
                } catch (_: Exception) {
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(10_000L)
                }
            }
        }
    }

    private suspend fun watchInbox() {
        requireSession()
        if (accessToken().isBlank()) return
        val channel = realtimeClient.channel("messages-${userId()}-${UUID.randomUUID()}")
        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
        }

        coroutineScope {
            val collectJob = launch {
                changes.collect { action ->
                    val record = action.record
                    if (record["message_type"]?.jsonPrimitive?.contentOrNull != "text") return@collect
                    val conversationId = record["conversation_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val id = record["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val senderId = record["sender_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val clientMessageId = record["client_message_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val content = record["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val createdAt = record["created_at"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (conversationId.isBlank() || id.isBlank() || senderId.isBlank()) return@collect
                    val message = LiveMessage(
                        id = id,
                        senderId = senderId,
                        content = content,
                        createdAt = createdAt,
                        clientMessageId = clientMessageId,
                        delivery = MessageDelivery.SENT
                    )
                    onServerMessage(conversationId, message)
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

    private fun onServerMessage(conversationId: String, message: LiveMessage) {
        store.upsertServerMessage(conversationId, message)
        mainHandler.post {
            chatStates[conversationId]?.let { mergeMessage(it, message) }
            updateConversationFromMessage(conversationId, message)
        }
        if (message.senderId != userId() && activeConversationId == conversationId) {
            scope.launch { runCatching { markConversationRead(conversationId) } }
        }
    }

    suspend fun myProfile(): LiveProfile? = withContext(Dispatchers.IO) {
        requireSession()
        val arr = restGet("/rest/v1/profiles?id=eq.${enc(userId())}&select=id,username,display_name&limit=1")
        val fresh = profileFrom(arr.optJSONObject(0))
        if (fresh != null) store.saveProfile(fresh)
        fresh ?: store.profile()
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
        val text = rpc("list_my_incoming_friend_requests", JSONObject())
        val arr = if (text.isBlank()) JSONArray() else JSONArray(text)
        buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val senderId = item.optString("sender_id")
                if (senderId.isBlank()) continue
                add(
                    IncomingRequest(
                        id = item.optString("request_id"),
                        sender = LiveProfile(
                            id = senderId,
                            username = item.optString("sender_username"),
                            displayName = item.optString("sender_display_name").ifBlank { item.optString("sender_username") }
                        )
                    )
                )
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
            if (accept) {
                runCatching { refreshFriends() }
                runCatching { refreshConversations() }
            }
            null
        } catch (e: Exception) {
            cleanError(e)
        }
    }

    suspend fun refreshFriends(): List<LiveProfile> = withContext(Dispatchers.IO) {
        requireSession()
        val text = rpc("list_my_friends", JSONObject())
        val arr = if (text.isBlank()) JSONArray() else JSONArray(text)
        val result = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val id = item.optString("id")
                if (id.isBlank()) continue
                add(
                    LiveProfile(
                        id = id,
                        username = item.optString("username"),
                        displayName = item.optString("display_name").ifBlank { item.optString("username") }
                    )
                )
            }
        }
        store.replaceFriends(result)
        withContext(Dispatchers.Main) {
            friendState.clear()
            friendState.addAll(result)
        }
        result
    }

    suspend fun friends(): List<LiveProfile> = refreshFriends()

    suspend fun refreshConversations(): List<ConversationSummary> = withContext(Dispatchers.IO) {
        requireSession()
        val text = rpc("list_my_direct_conversations", JSONObject())
        val arr = if (text.isBlank()) JSONArray() else JSONArray(text)
        val result = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val conversationId = item.optString("conversation_id")
                val friendId = item.optString("friend_id")
                if (conversationId.isBlank() || friendId.isBlank()) continue
                add(
                    ConversationSummary(
                        conversationId = conversationId,
                        friend = LiveProfile(
                            id = friendId,
                            username = item.optString("friend_username"),
                            displayName = item.optString("friend_display_name").ifBlank { item.optString("friend_username") }
                        ),
                        lastMessage = if (item.isNull("last_message")) "" else item.optString("last_message"),
                        lastMessageType = if (item.isNull("last_message_type")) "" else item.optString("last_message_type"),
                        lastMessageAt = if (item.isNull("last_message_at")) "" else item.optString("last_message_at"),
                        unreadCount = item.optLong("unread_count", 0L)
                    )
                )
            }
        }
        store.replaceConversations(result)
        withContext(Dispatchers.Main) {
            conversationState.clear()
            conversationState.addAll(result)
        }
        result
    }

    suspend fun directConversations(): List<ConversationSummary> = refreshConversations()

    suspend fun conversationId(friendId: String): String? = withContext(Dispatchers.IO) {
        store.conversationIdForFriend(friendId)?.let { return@withContext it }
        requireSession()
        val ids = listOf(userId(), friendId).sorted()
        val key = "${ids[0]}:${ids[1]}"
        val arr = restGet("/rest/v1/conversations?direct_key=eq.${enc(key)}&select=id&limit=1")
        arr.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
    }

    fun enterChat(conversationId: String) {
        activeConversationId = conversationId
        startRealtime()
        mainHandler.post { setConversationUnread(conversationId, 0L) }
        scope.launch { runCatching { markConversationRead(conversationId) } }
    }

    fun leaveChat(conversationId: String) {
        if (activeConversationId == conversationId) activeConversationId = null
    }

    suspend fun markConversationRead(conversationId: String) = withContext(Dispatchers.IO) {
        requireSession()
        rpc("mark_conversation_read", JSONObject().put("target_conversation", conversationId))
        withContext(Dispatchers.Main) { setConversationUnread(conversationId, 0L) }
        Unit
    }

    suspend fun syncMessages(conversationId: String): List<LiveMessage> = withContext(Dispatchers.IO) {
        requireSession()
        val fetched = fetchRecentMessages(conversationId)
        fetched.forEach { store.upsertServerMessage(conversationId, it) }
        val merged = store.messages(conversationId)
        withContext(Dispatchers.Main) {
            val state = chatFeed(conversationId)
            state.clear()
            state.addAll(merged)
        }
        merged
    }

    suspend fun messages(conversationId: String): List<LiveMessage> {
        startRealtime()
        return syncMessages(conversationId)
    }

    suspend fun sendText(conversationId: String, text: String): String? {
        val clean = text.trim()
        if (clean.isBlank()) return "消息不能为空"
        val sender = userId()
        if (sender.isBlank()) return "登录已过期，请重新登录"

        val clientId = UUID.randomUUID().toString()
        val optimistic = LiveMessage(
            id = "local:$clientId",
            senderId = sender,
            content = clean,
            createdAt = Instant.now().toString(),
            clientMessageId = clientId,
            delivery = MessageDelivery.SENDING
        )

        withContext(Dispatchers.Main) {
            mergeMessage(chatFeed(conversationId), optimistic)
            updateConversationFromMessage(conversationId, optimistic)
        }
        withContext(Dispatchers.IO) { store.saveOptimistic(conversationId, optimistic) }
        return sendExisting(conversationId, optimistic)
    }

    suspend fun retryText(conversationId: String, message: LiveMessage): String? {
        if (message.clientMessageId.isBlank()) return "这条消息不能重试"
        val sending = message.copy(delivery = MessageDelivery.SENDING, errorMessage = "")
        withContext(Dispatchers.Main) { mergeMessage(chatFeed(conversationId), sending) }
        withContext(Dispatchers.IO) {
            store.markSending(conversationId, message.senderId, message.clientMessageId)
        }
        return sendExisting(conversationId, sending)
    }

    private suspend fun sendExisting(conversationId: String, message: LiveMessage): String? = withContext(Dispatchers.IO) {
        try {
            requireSession()
            val confirmed = sendTextIdempotent(
                conversationId = conversationId,
                clientMessageId = message.clientMessageId,
                content = message.content
            )
            store.upsertServerMessage(conversationId, confirmed)
            withContext(Dispatchers.Main) {
                mergeMessage(chatFeed(conversationId), confirmed)
                updateConversationFromMessage(conversationId, confirmed)
            }
            null
        } catch (e: Exception) {
            val error = cleanError(e)
            store.markFailed(conversationId, message.senderId, message.clientMessageId, error)
            val failed = message.copy(delivery = MessageDelivery.FAILED, errorMessage = error)
            withContext(Dispatchers.Main) { mergeMessage(chatFeed(conversationId), failed) }
            error
        }
    }

    private suspend fun sendTextIdempotent(
        conversationId: String,
        clientMessageId: String,
        content: String
    ): LiveMessage {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                val text = rpc(
                    "send_text_message",
                    JSONObject()
                        .put("target_conversation", conversationId)
                        .put("target_client_message", clientMessageId)
                        .put("target_content", content)
                )
                val arr = if (text.isBlank()) JSONArray() else JSONArray(text)
                val item = arr.optJSONObject(0) ?: throw IllegalStateException("服务器没有返回消息")
                return LiveMessage(
                    id = item.optString("id"),
                    senderId = item.optString("sender_id"),
                    content = item.optString("content"),
                    createdAt = item.optString("created_at"),
                    clientMessageId = item.optString("client_message_id"),
                    delivery = MessageDelivery.SENT
                )
            } catch (e: Exception) {
                lastError = e
                if (attempt >= 2 || !isTransient(e)) throw e
                delay(350L * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("发送失败")
    }

    private fun fetchRecentMessages(conversationId: String): List<LiveMessage> {
        val arr = restGet(
            "/rest/v1/messages?conversation_id=eq.${enc(conversationId)}" +
                "&select=id,sender_id,client_message_id,content,created_at" +
                "&message_type=eq.text&order=created_at.desc&limit=250"
        )
        val newestFirst = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                add(
                    LiveMessage(
                        id = item.optString("id"),
                        senderId = item.optString("sender_id"),
                        content = item.optString("content"),
                        createdAt = item.optString("created_at"),
                        clientMessageId = item.optString("client_message_id"),
                        delivery = MessageDelivery.SENT
                    )
                )
            }
        }
        return newestFirst.asReversed()
    }

    private fun mergeMessage(state: SnapshotStateList<LiveMessage>, incoming: LiveMessage) {
        val index = state.indexOfFirst { existing ->
            existing.id == incoming.id ||
                (incoming.clientMessageId.isNotBlank() &&
                    existing.senderId == incoming.senderId &&
                    existing.clientMessageId == incoming.clientMessageId)
        }
        if (index >= 0) {
            state[index] = incoming
        } else {
            state.add(incoming)
        }
    }

    private fun updateConversationFromMessage(conversationId: String, message: LiveMessage) {
        val index = conversationState.indexOfFirst { it.conversationId == conversationId }
        if (index < 0) {
            scope.launch { runCatching { refreshConversations() } }
            return
        }
        val old = conversationState[index]
        val unread = when {
            message.senderId == userId() -> old.unreadCount
            activeConversationId == conversationId -> 0L
            else -> old.unreadCount + 1L
        }
        val updated = old.copy(
            lastMessage = message.content,
            lastMessageType = "text",
            lastMessageAt = message.createdAt,
            unreadCount = unread
        )
        conversationState.removeAt(index)
        conversationState.add(0, updated)
        store.saveConversation(updated)
    }

    private fun setConversationUnread(conversationId: String, count: Long) {
        val index = conversationState.indexOfFirst { it.conversationId == conversationId }
        if (index < 0) return
        val updated = conversationState[index].copy(unreadCount = count)
        conversationState[index] = updated
        store.saveConversation(updated)
    }

    private fun cacheAuthMetadata(user: JSONObject) {
        val id = user.optString("id")
        val metadata = user.optJSONObject("user_metadata")
        val username = metadata?.optString("username").orEmpty()
        val displayName = metadata?.optString("display_name").orEmpty()
        if (id.isNotBlank() && username.isNotBlank()) {
            store.saveProfile(LiveProfile(id, username, displayName.ifBlank { username }))
        }
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
        val oldUser = userId()
        val newUser = json.optJSONObject("user")?.optString("id").orEmpty()
        if (oldUser.isNotBlank() && newUser.isNotBlank() && oldUser != newUser) {
            store.clearAll()
            mainHandler.post {
                conversationState.clear()
                friendState.clear()
                chatStates.clear()
            }
        }

        val editor = prefs.edit()
        json.optString("access_token").takeIf { it.isNotBlank() }?.let { editor.putString("access_token", it) }
        json.optString("refresh_token").takeIf { it.isNotBlank() }?.let { editor.putString("refresh_token", it) }
        newUser.takeIf { it.isNotBlank() }?.let { editor.putString("user_id", it) }
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

    private fun isTransient(error: Exception): Boolean {
        val text = error.message.orEmpty().lowercase()
        return text.contains("timeout") ||
            text.contains("timed out") ||
            text.contains("failed to connect") ||
            text.contains("unable to resolve") ||
            text.contains("connection reset") ||
            text.contains("请求失败 429") ||
            text.contains("请求失败 502") ||
            text.contains("请求失败 503") ||
            text.contains("请求失败 504")
    }

    private fun cleanError(error: Exception): String {
        val raw = error.message?.replace("java.lang.IllegalStateException: ", "")?.take(160).orEmpty()
        return when {
            raw.contains("timeout", true) || raw.contains("timed out", true) -> "网络超时，点失败消息可重试"
            raw.contains("failed to connect", true) || raw.contains("unable to resolve", true) -> "网络不可用，点失败消息可重试"
            raw.isBlank() -> "操作失败"
            else -> raw
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
