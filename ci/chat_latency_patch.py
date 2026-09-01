from pathlib import Path

# Make outgoing messages appear locally immediately, then reconcile with Supabase.
p = Path('app/src/main/java/com/suisuinian/app/SupabaseApi.kt')
s = p.read_text()
old = '''    suspend fun sendText(conversationId: String, text: String): String? = withContext(Dispatchers.IO) {
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
'''
new = '''    suspend fun sendText(conversationId: String, text: String): String? = withContext(Dispatchers.IO) {
        val clean = text.trim()
        if (clean.isBlank()) return@withContext "消息不能为空"

        var optimisticId = ""
        try {
            requireSession()
            val clientMessageId = UUID.randomUUID().toString()
            optimisticId = "local:$clientMessageId"
            val cache = chatCaches.getOrPut(conversationId) { ChatCache() }
            val optimistic = LiveMessage(
                id = optimisticId,
                senderId = userId(),
                content = clean,
                createdAt = java.time.Instant.now().toString()
            )
            withContext(Dispatchers.Main) {
                if (cache.state.none { it.id == optimisticId }) cache.state.add(optimistic)
            }

            val body = JSONObject()
                .put("conversation_id", conversationId)
                .put("sender_id", userId())
                .put("client_message_id", clientMessageId)
                .put("message_type", "text")
                .put("content", clean)
            val responseText = restPost("/rest/v1/messages", body, prefer = "return=representation")
            val arr = if (responseText.isBlank()) JSONArray() else JSONArray(responseText)
            val item = arr.optJSONObject(0)
            val confirmed = item?.let {
                LiveMessage(it.optString("id"), it.optString("sender_id"), it.optString("content"), it.optString("created_at"))
            }
            withContext(Dispatchers.Main) {
                val tempIndex = cache.state.indexOfFirst { it.id == optimisticId }
                if (confirmed != null && confirmed.id.isNotBlank()) {
                    val serverIndex = cache.state.indexOfFirst { it.id == confirmed.id }
                    when {
                        serverIndex >= 0 && tempIndex >= 0 -> cache.state.removeAt(tempIndex)
                        tempIndex >= 0 -> cache.state[tempIndex] = confirmed
                        serverIndex < 0 -> cache.state.add(confirmed)
                    }
                } else if (tempIndex >= 0) cache.state.removeAt(tempIndex)
            }
            null
        } catch (e: Exception) {
            val cache = chatCaches[conversationId]
            withContext(Dispatchers.Main) {
                if (optimisticId.isNotBlank()) cache?.state?.removeAll { it.id == optimisticId }
            }
            cleanError(e)
        }
    }
'''
if old not in s:
    raise SystemExit('optimistic send patch target not found')
p.write_text(s.replace(old, new))

# Make Realtime the primary receive path; keep a slow network refresh only as fallback.
p = Path('app/src/main/java/com/suisuinian/app/SocialExperimentActivity.kt')
s = p.read_text()
old = '''        while (isActive) {
            runCatching {
                val latest = api.messages(cid)
                api.markConversationRead(cid)
                latest
            }.onSuccess {
                if (it != messages) messages = it
                status = ""
            }.onFailure {
                status = it.message ?: "消息加载失败"
            }
            delay(1000)
        }'''
new = '''        runCatching {
            val latest = api.messages(cid)
            api.markConversationRead(cid)
            latest
        }.onSuccess {
            messages = it
            status = ""
        }.onFailure {
            status = it.message ?: "消息加载失败"
        }
        while (isActive) {
            delay(8000)
            runCatching { api.messages(cid) }
                .onFailure { status = it.message ?: "消息同步失败" }
        }'''
if old not in s:
    raise SystemExit('chat polling patch target not found')
s = s.replace(old, new)
old = '''                        scope.launch {
                            val error = api.sendText(cid, text)
                            if (error == null) {
                                input = ""
                                status = ""
                                runCatching { api.messages(cid) }.onSuccess { messages = it }
                            } else status = error
                        }'''
new = '''                        input = ""
                        status = ""
                        scope.launch {
                            val error = api.sendText(cid, text)
                            if (error != null) {
                                if (input.isBlank()) input = text
                                status = error
                            }
                        }'''
if old not in s:
    raise SystemExit('send UI patch target not found')
p.write_text(s.replace(old, new))
