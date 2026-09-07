package com.qxdnzbl.lumichat

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
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
    private var hideScheduled = false

    private val hideRunnable = Runnable {
        hideScheduled = false
        if (!::overlay.isInitialized) return@Runnable

        val targetRoot = findTargetRoot()
        if (targetRoot == null) {
            overlay.hideAll()
        } else {
            scheduleRefresh()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = SkinOverlayController(this) { text -> sendMessageToChatGpt(text) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::overlay.isInitialized || event == null) return
        val eventPackage = event.packageName?.toString().orEmpty()

        // Events produced by our own accessibility overlay must never make the skin disappear.
        if (eventPackage == applicationContext.packageName) return

        // A real ChatGPT event means the target is definitely still active.
        if (eventPackage == TARGET_PACKAGE) {
            cancelPendingHide()
            scheduleRefresh()
            return
        }

        // Keyboard, System UI, permission dialogs and accessibility overlays can emit events
        // while ChatGPT is still the foreground app. If the ChatGPT window still exists,
        // keep the skin stable instead of hiding it for a single foreign-package event.
        if (findTargetRoot() != null) {
            cancelPendingHide()
            scheduleRefresh()
            return
        }

        // Only consider hiding after a real window transition, and debounce it. This avoids
        // the skin <-> official ChatGPT flashing loop caused by transient System UI events.
        if (
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            scheduleHideCheck()
        }
    }

    override fun onInterrupt() {
        cancelPendingHide()
        if (::overlay.isInitialized) overlay.hideAll()
    }

    override fun onDestroy() {
        cancelPendingHide()
        if (::overlay.isInitialized) overlay.destroy()
        super.onDestroy()
    }

    private fun scheduleRefresh() {
        if (refreshScheduled) return
        refreshScheduled = true
        mainHandler.postDelayed({
            refreshScheduled = false
            refreshFromRoot()
        }, 110L)
    }

    private fun scheduleHideCheck() {
        if (hideScheduled) return
        hideScheduled = true
        mainHandler.postDelayed(hideRunnable, 650L)
    }

    private fun cancelPendingHide() {
        if (!hideScheduled) return
        mainHandler.removeCallbacks(hideRunnable)
        hideScheduled = false
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
        val root = findTargetRoot()
        if (root == null) {
            scheduleHideCheck()
            return
        }

        cancelPendingHide()
        val snapshot = ChatUiSnapshot.from(root)

        // During composition, keyboard transitions or ChatGPT's own transient surfaces,
        // the editable node can disappear for a frame. Keep the existing overlay instead
        // of tearing it down and recreating it, which is visible as flashing.
        if (!snapshot.looksLikeChat) return

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

        val sentByButton = sendButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        val sentByIme = if (!sentByButton && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            editable.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        } else {
            false
        }
        val sent = sentByButton || sentByIme

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
