package com.qxdnzbl.lumichat

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ChatGptSkinService : AccessibilityService() {
    companion object {
        const val TARGET_PACKAGE = "com.openai.chatgpt"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var overlay: SkinOverlayController
    private var refreshScheduled = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = SkinOverlayController(this) { text -> sendMessageToChatGpt(text) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::overlay.isInitialized || event == null) return
        val eventPackage = event.packageName?.toString().orEmpty()

        if (eventPackage == applicationContext.packageName) return
        if (eventPackage != TARGET_PACKAGE) {
            overlay.hideAll()
            return
        }

        scheduleRefresh()
    }

    override fun onInterrupt() {
        if (::overlay.isInitialized) overlay.hideAll()
    }

    override fun onDestroy() {
        if (::overlay.isInitialized) overlay.destroy()
        super.onDestroy()
    }

    private fun scheduleRefresh() {
        if (refreshScheduled) return
        refreshScheduled = true
        mainHandler.postDelayed({
            refreshScheduled = false
            refreshFromRoot()
        }, 90L)
    }

    private fun findTargetRoot(): AccessibilityNodeInfo? {
        windows.forEach { window ->
            val root = window.root ?: return@forEach
            if (root.packageName?.toString() == TARGET_PACKAGE) return root
        }
        val active = rootInActiveWindow
        return active?.takeIf { it.packageName?.toString() == TARGET_PACKAGE }
    }

    private fun refreshFromRoot() {
        val root = findTargetRoot() ?: run {
            overlay.hideAll()
            return
        }
        val snapshot = ChatUiSnapshot.from(root)
        if (!snapshot.looksLikeChat) {
            overlay.hideAll()
            return
        }
        overlay.show(snapshot)
    }

    private fun sendMessageToChatGpt(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        val root = findTargetRoot() ?: return false

        val editable = findFirst(root) { it.isEditable && it.isVisibleToUser } ?: return false
        val setTextArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, clean)
        }
        if (!editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setTextArgs)) return false
        editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        val inputRect = Rect().also { editable.getBoundsInScreen(it) }
        val sendButton = findFirst(root) { node ->
            if (!node.isClickable || !node.isVisibleToUser) return@findFirst false
            val label = buildString {
                append(node.contentDescription?.toString().orEmpty())
                append(' ')
                append(node.text?.toString().orEmpty())
                append(' ')
                append(node.viewIdResourceName.orEmpty())
            }.lowercase()
            val sendNamed = listOf("send", "发送", "提交", "send message").any { label.contains(it) }
            if (sendNamed) return@findFirst true

            val r = Rect().also { node.getBoundsInScreen(it) }
            r.top >= inputRect.top - 40 && r.left > inputRect.centerX() && r.width() in 24..180 && r.height() in 24..180
        }

        val sent = sendButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true ||
            editable.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)

        if (sent) mainHandler.postDelayed({ scheduleRefresh() }, 250L)
        return sent
    }

    private fun findFirst(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (predicate(node)) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::addLast)
            }
        }
        return null
    }
}

data class MirroredMessage(
    val text: String,
    val isUser: Boolean,
    val top: Int
)

data class ChatUiSnapshot(
    val messages: List<MirroredMessage>,
    val looksLikeChat: Boolean
) {
    companion object {
        private val ignoredExact = setOf(
            "chatgpt", "new chat", "新对话", "share", "分享", "copy", "复制",
            "regenerate", "重新生成", "read aloud", "朗读", "good response", "bad response",
            "attach", "附件", "voice", "语音", "menu", "菜单", "send", "发送"
        )

        fun from(root: AccessibilityNodeInfo): ChatUiSnapshot {
            val editable = mutableListOf<Pair<AccessibilityNodeInfo, Rect>>()
            val textNodes = mutableListOf<Pair<String, Rect>>()
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)

            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (!node.isVisibleToUser) continue
                val rect = Rect().also { node.getBoundsInScreen(it) }
                if (node.isEditable) editable += node to rect

                val text = node.text?.toString()?.trim().orEmpty()
                if (text.isNotEmpty() && node.childCount == 0) {
                    textNodes += text to rect
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let(queue::addLast)
                }
            }

            val input = editable.maxByOrNull { it.second.top }
                ?: return ChatUiSnapshot(emptyList(), false)
            val inputRect = input.second
            val rootRect = Rect().also { root.getBoundsInScreen(it) }
            val screenWidth = rootRect.width().coerceAtLeast(1)
            val topCutoff = (rootRect.height() * 0.07f).toInt()

            val cleaned = textNodes
                .asSequence()
                .filter { (_, r) -> r.bottom < inputRect.top - 8 && r.top > topCutoff && r.height() > 0 }
                .mapNotNull { (text, r) ->
                    val lower = text.lowercase()
                    if (text.length <= 1) return@mapNotNull null
                    if (ignoredExact.contains(lower)) return@mapNotNull null
                    if (lower.startsWith("chatgpt ") && text.length < 24) return@mapNotNull null
                    if (text == input.first.text?.toString()) return@mapNotNull null

                    val isUser = r.right > screenWidth * 0.83f && r.left > screenWidth * 0.16f
                    MirroredMessage(text = text, isUser = isUser, top = r.top)
                }
                .distinctBy { "${it.top}:${it.text}" }
                .sortedBy { it.top }
                .toList()

            return ChatUiSnapshot(cleaned.takeLast(40), true)
        }
    }
}
