from pathlib import Path

p = Path('app/src/main/java/com/suisuinian/app/SupabaseApi.kt')
s = p.read_text()
s = s.replace(
    'import kotlinx.coroutines.CoroutineScope\n',
    'import kotlinx.coroutines.CompletableDeferred\nimport kotlinx.coroutines.CoroutineScope\n'
)
s = s.replace(
    'import kotlinx.coroutines.withContext\n',
    'import kotlinx.coroutines.withContext\nimport kotlinx.coroutines.withTimeoutOrNull\n'
)
s = s.replace(
    '    private val chatJobs = ConcurrentHashMap<String, Job>()\n    private val sessionLock = Any()',
    '    private val chatJobs = ConcurrentHashMap<String, Job>()\n    private val chatReady = ConcurrentHashMap<String, CompletableDeferred<Unit>>()\n    private val sessionLock = Any()'
)
s = s.replace(
    '        chatJobs.clear()\n        chatCaches.clear()',
    '        chatJobs.clear()\n        chatReady.values.forEach { it.cancel() }\n        chatReady.clear()\n        chatCaches.clear()'
)

old = '''    private fun ensureRealtimeListener(conversationId: String, cache: ChatCache) {
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
'''
new = '''    private suspend fun ensureRealtimeListener(conversationId: String, cache: ChatCache) {
        val existingReady = chatReady[conversationId]
        if (chatJobs[conversationId]?.isActive == true && existingReady != null) {
            withTimeoutOrNull(8_000) { existingReady.await() }
                ?: throw IllegalStateException("实时聊天连接超时")
            return
        }

        val ready = CompletableDeferred<Unit>()
        chatReady[conversationId] = ready
        chatJobs[conversationId] = realtimeScope.launch {
            try {
                watchTextMessages(conversationId, ready) { incoming ->
                    mainHandler.post {
                        val index = cache.state.indexOfFirst { it.id == incoming.id }
                        if (index < 0) {
                            cache.state.add(incoming)
                        } else if (cache.state[index] != incoming) {
                            cache.state[index] = incoming
                        }
                    }
                }
            } catch (e: Exception) {
                if (!ready.isCompleted) ready.completeExceptionally(e)
            } finally {
                chatReady.remove(conversationId, ready)
            }
        }
        withTimeoutOrNull(8_000) { ready.await() }
            ?: throw IllegalStateException("实时聊天连接超时")
    }

    private suspend fun watchTextMessages(
        conversationId: String,
        ready: CompletableDeferred<Unit>,
        onMessage: (LiveMessage) -> Unit
    ) {
        requireSession()
        if (accessToken().isBlank()) {
            ready.completeExceptionally(IllegalStateException("登录已过期，请重新登录"))
            return
        }
'''
if old not in s:
    raise SystemExit('realtime listener patch target not found')
s = s.replace(old, new)

old2 = '''            try {
                channel.subscribe(blockUntilSubscribed = true)
                awaitCancellation()
            } finally {'''
new2 = '''            try {
                channel.subscribe(blockUntilSubscribed = true)
                if (!ready.isCompleted) ready.complete(Unit)
                awaitCancellation()
            } finally {'''
if old2 not in s:
    raise SystemExit('realtime subscribe patch target not found')
s = s.replace(old2, new2)

p.write_text(s)
