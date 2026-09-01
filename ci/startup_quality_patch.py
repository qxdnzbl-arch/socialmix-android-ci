from pathlib import Path

# This patch is intentionally applied after realtime_patch.py and chat_latency_patch.py.
# It keeps the current server schema untouched and improves perceived + actual responsiveness
# by making local cache the first render source and the network a background reconciliation source.

api_path = Path('app/src/main/java/com/suisuinian/app/SupabaseApi.kt')
s = api_path.read_text()

s = s.replace(
    'import java.util.concurrent.ConcurrentHashMap\n',
    'import java.util.concurrent.ConcurrentHashMap\nimport java.util.concurrent.TimeUnit\n'
)

old = '    private val http = OkHttpClient.Builder().build()\n'
new = '''    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
'''
if old not in s:
    raise SystemExit('http client patch target not found')
s = s.replace(old, new)

old = '''    fun userId(): String = prefs.getString("user_id", "") ?: ""
    private fun accessToken(): String = prefs.getString("access_token", "") ?: ""
    private fun refreshToken(): String = prefs.getString("refresh_token", "") ?: ""
'''
new = '''    fun userId(): String = prefs.getString("user_id", "") ?: ""
    private fun accessToken(): String = prefs.getString("access_token", "") ?: ""
    private fun refreshToken(): String = prefs.getString("refresh_token", "") ?: ""

    fun cachedProfile(): LiveProfile? {
        val id = prefs.getString("profile_id", "") ?: ""
        val username = prefs.getString("profile_username", "") ?: ""
        val displayName = prefs.getString("profile_display_name", "") ?: ""
        if (id.isBlank() || username.isBlank()) return null
        return LiveProfile(id, username, displayName.ifBlank { username })
    }

    fun cachedConversations(): List<ConversationSummary> {
        val raw = prefs.getString("conversation_cache", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val friendId = item.optString("friend_id")
                    val conversationId = item.optString("conversation_id")
                    if (friendId.isBlank() || conversationId.isBlank()) continue
                    val friend = LiveProfile(
                        id = friendId,
                        username = item.optString("friend_username"),
                        displayName = item.optString("friend_display_name").ifBlank { item.optString("friend_username") }
                    )
                    add(
                        ConversationSummary(
                            conversationId = conversationId,
                            friend = friend,
                            lastMessage = item.optString("last_message"),
                            lastMessageType = item.optString("last_message_type"),
                            lastMessageAt = item.optString("last_message_at"),
                            unreadCount = item.optLong("unread_count", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun cachedConversationId(friendId: String): String? =
        cachedConversations().firstOrNull { it.friend.id == friendId }?.conversationId

    fun cachedMessages(conversationId: String): List<LiveMessage> {
        if (conversationId.isBlank()) return emptyList()
        val raw = prefs.getString("message_cache_$conversationId", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val id = item.optString("id")
                    val senderId = item.optString("sender_id")
                    if (id.isBlank() || senderId.isBlank()) continue
                    add(
                        LiveMessage(
                            id = id,
                            senderId = senderId,
                            content = item.optString("content"),
                            createdAt = item.optString("created_at")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun cacheProfile(profile: LiveProfile) {
        prefs.edit()
            .putString("profile_id", profile.id)
            .putString("profile_username", profile.username)
            .putString("profile_display_name", profile.displayName)
            .apply()
    }

    private fun cacheConversations(items: List<ConversationSummary>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("conversation_id", item.conversationId)
                    .put("friend_id", item.friend.id)
                    .put("friend_username", item.friend.username)
                    .put("friend_display_name", item.friend.displayName)
                    .put("last_message", item.lastMessage)
                    .put("last_message_type", item.lastMessageType)
                    .put("last_message_at", item.lastMessageAt)
                    .put("unread_count", item.unreadCount)
            )
        }
        prefs.edit().putString("conversation_cache", arr.toString()).apply()
    }

    private fun cacheMessages(conversationId: String, items: List<LiveMessage>) {
        if (conversationId.isBlank()) return
        val arr = JSONArray()
        items.takeLast(120).forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("sender_id", item.senderId)
                    .put("content", item.content)
                    .put("created_at", item.createdAt)
            )
        }
        prefs.edit().putString("message_cache_$conversationId", arr.toString()).apply()
    }
'''
if old not in s:
    raise SystemExit('cache helper insertion target not found')
s = s.replace(old, new)

old = '''    suspend fun myProfile(): LiveProfile? = withContext(Dispatchers.IO) {
        requireSession()
        val arr = restGet("/rest/v1/profiles?id=eq.${enc(userId())}&select=id,username,display_name&limit=1")
        profileFrom(arr.optJSONObject(0))
    }
'''
new = '''    suspend fun myProfile(): LiveProfile? = withContext(Dispatchers.IO) {
        requireSession()
        val arr = restGet("/rest/v1/profiles?id=eq.${enc(userId())}&select=id,username,display_name&limit=1")
        val fresh = profileFrom(arr.optJSONObject(0))
        if (fresh != null) cacheProfile(fresh)
        fresh ?: cachedProfile()
    }
'''
if old not in s:
    raise SystemExit('profile cache patch target not found')
s = s.replace(old, new)

old = '''    suspend fun directConversations(): List<ConversationSummary> = withContext(Dispatchers.IO) {
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
'''
new = '''    suspend fun directConversations(): List<ConversationSummary> = withContext(Dispatchers.IO) {
        requireSession()
        val text = rpc("list_my_direct_conversations", JSONObject())
        val arr = if (text.isBlank()) JSONArray() else JSONArray(text)
        val result = buildList {
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
        cacheConversations(result)
        result
    }
'''
if old not in s:
    raise SystemExit('conversation cache patch target not found')
s = s.replace(old, new)

old = '''    suspend fun messages(conversationId: String): List<LiveMessage> = withContext(Dispatchers.IO) {
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
'''
new = '''    suspend fun messages(conversationId: String): List<LiveMessage> = withContext(Dispatchers.IO) {
        requireSession()
        val fetched = fetchMessages(conversationId)
        cacheMessages(conversationId, fetched)
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
'''
if old not in s:
    raise SystemExit('message cache patch target not found')
s = s.replace(old, new)

old = '''                    mainHandler.post {
                        val index = cache.state.indexOfFirst { it.id == incoming.id }
                        if (index < 0) {
                            cache.state.add(incoming)
                        } else if (cache.state[index] != incoming) {
                            cache.state[index] = incoming
                        }
                    }
'''
new = '''                    mainHandler.post {
                        val index = cache.state.indexOfFirst { it.id == incoming.id }
                        if (index < 0) {
                            cache.state.add(incoming)
                        } else if (cache.state[index] != incoming) {
                            cache.state[index] = incoming
                        }
                        cacheMessages(conversationId, cache.state.toList())
                    }
'''
if old not in s:
    raise SystemExit('realtime cache patch target not found')
s = s.replace(old, new)

# The next blocks target the optimistic-send implementation injected by chat_latency_patch.py.
old = '''            withContext(Dispatchers.Main) {
                if (cache.state.none { it.id == optimisticId }) cache.state.add(optimistic)
            }

            val body = JSONObject()
'''
new = '''            withContext(Dispatchers.Main) {
                if (cache.state.none { it.id == optimisticId }) cache.state.add(optimistic)
            }
            cacheMessages(conversationId, cache.state.toList())

            val body = JSONObject()
'''
if old not in s:
    raise SystemExit('optimistic local-cache patch target not found')
s = s.replace(old, new)

old = '''                } else if (tempIndex >= 0) cache.state.removeAt(tempIndex)
            }
            null
        } catch (e: Exception) {
'''
new = '''                } else if (tempIndex >= 0) cache.state.removeAt(tempIndex)
            }
            cacheMessages(conversationId, cache.state.toList())
            null
        } catch (e: Exception) {
'''
if old not in s:
    raise SystemExit('optimistic confirm-cache patch target not found')
s = s.replace(old, new)

old = '''            withContext(Dispatchers.Main) {
                if (optimisticId.isNotBlank()) cache?.state?.removeAll { it.id == optimisticId }
            }
            cleanError(e)
'''
new = '''            withContext(Dispatchers.Main) {
                if (optimisticId.isNotBlank()) cache?.state?.removeAll { it.id == optimisticId }
            }
            if (cache != null) cacheMessages(conversationId, cache.state.toList())
            cleanError(e)
'''
if old not in s:
    raise SystemExit('optimistic failure-cache patch target not found')
s = s.replace(old, new)

old = '''    private fun saveSession(json: JSONObject) {
        val editor = prefs.edit()
        json.optString("access_token").takeIf { it.isNotBlank() }?.let { editor.putString("access_token", it) }
        json.optString("refresh_token").takeIf { it.isNotBlank() }?.let { editor.putString("refresh_token", it) }
        json.optJSONObject("user")?.optString("id")?.takeIf { it.isNotBlank() }?.let { editor.putString("user_id", it) }
        editor.apply()
    }
'''
new = '''    private fun saveSession(json: JSONObject) {
        val editor = prefs.edit()
        json.optString("access_token").takeIf { it.isNotBlank() }?.let { editor.putString("access_token", it) }
        json.optString("refresh_token").takeIf { it.isNotBlank() }?.let { editor.putString("refresh_token", it) }
        val user = json.optJSONObject("user")
        val id = user?.optString("id").orEmpty()
        id.takeIf { it.isNotBlank() }?.let {
            editor.putString("user_id", it)
            editor.putString("profile_id", it)
        }
        val metadata = user?.optJSONObject("user_metadata")
        metadata?.optString("username")?.takeIf { it.isNotBlank() }?.let { editor.putString("profile_username", it) }
        metadata?.optString("display_name")?.takeIf { it.isNotBlank() }?.let { editor.putString("profile_display_name", it) }
        editor.apply()
    }
'''
if old not in s:
    raise SystemExit('session metadata cache patch target not found')
s = s.replace(old, new)

api_path.write_text(s)

ui_path = Path('app/src/main/java/com/suisuinian/app/SocialExperimentActivity.kt')
s = ui_path.read_text()

old = '''private fun LiveMessagesPage(api: SupabaseApi, onChat: (LiveProfile) -> Unit) {
    var conversations by remember { mutableStateOf<List<ConversationSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (isActive) {
            runCatching { api.directConversations() }
                .onSuccess {
                    conversations = it
                    error = ""
                }
                .onFailure { error = it.message ?: "加载失败" }
            loading = false
            delay(1500)
        }
    }
'''
new = '''private fun LiveMessagesPage(api: SupabaseApi, onChat: (LiveProfile) -> Unit) {
    var conversations by remember { mutableStateOf(api.cachedConversations()) }
    var loading by remember { mutableStateOf(conversations.isEmpty()) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (isActive) {
            runCatching { api.directConversations() }
                .onSuccess {
                    conversations = it
                    error = ""
                }
                .onFailure {
                    if (conversations.isEmpty()) error = it.message ?: "加载失败"
                }
            loading = false
            delay(8000)
        }
    }
'''
if old not in s:
    raise SystemExit('messages page cache patch target not found')
s = s.replace(old, new)

old = '            loading -> CenterText("正在加载…")\n'
new = '            loading && conversations.isEmpty() -> CenterText("正在加载…")\n'
if old not in s:
    raise SystemExit('messages loading state patch target not found')
s = s.replace(old, new, 1)

old = '''private fun MePage(api: SupabaseApi, onContacts: () -> Unit, onLogout: () -> Unit) {
    var me by remember { mutableStateOf<LiveProfile?>(null) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        runCatching { api.myProfile() }
            .onSuccess { me = it }
            .onFailure { error = it.message ?: "加载失败" }
    }
'''
new = '''private fun MePage(api: SupabaseApi, onContacts: () -> Unit, onLogout: () -> Unit) {
    var me by remember { mutableStateOf(api.cachedProfile()) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        runCatching { api.myProfile() }
            .onSuccess {
                if (it != null) me = it
                error = ""
            }
            .onFailure {
                if (me == null) error = it.message ?: "账号信息暂不可用"
            }
    }
'''
if old not in s:
    raise SystemExit('me page cache patch target not found')
s = s.replace(old, new)

old = '''                        when {
                            me != null -> "账号：${me!!.username}"
                            error.isNotBlank() -> error
                            else -> "加载中…"
                        },
'''
new = '''                        when {
                            me != null -> "账号：${me!!.username}"
                            error.isNotBlank() -> error
                            else -> "账号信息同步中"
                        },
'''
if old not in s:
    raise SystemExit('me page loading copy patch target not found')
s = s.replace(old, new)

old = '''private fun LiveChatPage(api: SupabaseApi, friend: LiveProfile, onBack: () -> Unit) {
    var conversationId by remember(friend.id) { mutableStateOf<String?>(null) }
    var messages by remember(friend.id) { mutableStateOf<List<LiveMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
'''
new = '''private fun LiveChatPage(api: SupabaseApi, friend: LiveProfile, onBack: () -> Unit) {
    val cachedCid = remember(friend.id) { api.cachedConversationId(friend.id) }
    var conversationId by remember(friend.id) { mutableStateOf<String?>(cachedCid) }
    var messages by remember(friend.id) { mutableStateOf(cachedCid?.let { api.cachedMessages(it) } ?: emptyList()) }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(cachedCid == null) }
'''
if old not in s:
    raise SystemExit('chat initial cache patch target not found')
s = s.replace(old, new)

old = '''    LaunchedEffect(friend.id) {
        runCatching { api.conversationId(friend.id) }
            .onSuccess { conversationId = it }
            .onFailure { status = it.message ?: "会话加载失败" }
        loading = false
        val cid = conversationId ?: return@LaunchedEffect
'''
new = '''    LaunchedEffect(friend.id) {
        if (conversationId == null) {
            runCatching { api.conversationId(friend.id) }
                .onSuccess { conversationId = it }
                .onFailure { status = it.message ?: "会话加载失败" }
        }
        loading = false
        val cid = conversationId ?: return@LaunchedEffect
        if (messages.isEmpty()) {
            val cached = api.cachedMessages(cid)
            if (cached.isNotEmpty()) messages = cached
        }
'''
if old not in s:
    raise SystemExit('chat conversation cache patch target not found')
s = s.replace(old, new)

old = '            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("正在打开会话…", color = BSub) }\n'
new = '            loading && conversationId == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("正在打开会话…", color = BSub) }\n'
if old not in s:
    raise SystemExit('chat loading state patch target not found')
s = s.replace(old, new)

ui_path.write_text(s)

print('startup quality patch applied')
