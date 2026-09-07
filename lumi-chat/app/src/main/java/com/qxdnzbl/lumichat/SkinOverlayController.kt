package com.qxdnzbl.lumichat

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SkinOverlayController(
    private val service: AccessibilityService,
    private val onSend: (String) -> Boolean
) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = service.resources.displayMetrics.density

    private var fullView: View? = null
    private var miniView: View? = null
    private var messageList: LinearLayout? = null
    private var input: EditText? = null
    private var lastSnapshot: ChatUiSnapshot? = null
    private var lastSignature: String = ""
    private var manualHidden = false

    fun show(snapshot: ChatUiSnapshot) {
        lastSnapshot = snapshot
        if (manualHidden) {
            showMini()
            return
        }
        if (fullView == null) addFullOverlay()
        render(snapshot)
    }

    fun hideAll() {
        removeView(fullView)
        removeView(miniView)
        fullView = null
        miniView = null
        messageList = null
        input = null
        lastSignature = ""
        manualHidden = false
    }

    fun destroy() = hideAll()

    private fun addFullOverlay() {
        removeView(miniView)
        miniView = null

        val root = FrameLayout(service).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(255, 246, 251),
                    Color.rgb(247, 243, 255),
                    Color.rgb(240, 249, 255),
                    Color.rgb(248, 255, 249)
                )
            )
        }

        val column = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(28), dp(12), dp(12))
        }
        root.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val header = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(4), dp(8))
        }
        val titleBox = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(TextView(service).apply {
            text = "微光"
            textSize = 19f
            setTextColor(Color.rgb(52, 49, 58))
            setTypeface(typeface, Typeface.BOLD)
        })
        titleBox.addView(TextView(service).apply {
            text = "官方 ChatGPT · 只是换了界面"
            textSize = 11f
            setTextColor(Color.rgb(126, 119, 135))
        })
        header.addView(titleBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(actionChip("原版") { hideToMini() })
        column.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))

        val scroll = ScrollView(service).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }
        val list = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(10))
        }
        messageList = list
        scroll.addView(list, ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val composer = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(6), dp(4))
            background = roundedSolid(Color.argb(244, 255, 255, 255), 28f, Color.argb(80, 255, 255, 255))
            elevation = dp(4).toFloat()
        }
        val edit = EditText(service).apply {
            hint = "回复 ChatGPT"
            textSize = 16f
            setTextColor(Color.rgb(52, 49, 58))
            setHintTextColor(Color.rgb(155, 147, 159))
            background = null
            maxLines = 5
            setPadding(dp(10), dp(6), dp(8), dp(6))
            isSingleLine = false
        }
        input = edit
        composer.addView(edit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val send = TextView(service).apply {
            text = "↑"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(236, 175, 215), Color.rgb(190, 165, 240))
            ).apply {
                shape = GradientDrawable.OVAL
            }
            setOnClickListener { sendCurrentText() }
        }
        composer.addView(send, LinearLayout.LayoutParams(dp(48), dp(48)))
        column.addView(composer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fullView = root
        wm.addView(root, WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_STABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        })
    }

    private fun render(snapshot: ChatUiSnapshot) {
        val signature = snapshot.messages.joinToString("|") { "${it.isUser}:${it.text}" }
        if (signature == lastSignature) return
        lastSignature = signature
        val list = messageList ?: return
        list.removeAllViews()

        if (snapshot.messages.isEmpty()) {
            val empty = TextView(service).apply {
                text = "想说什么就说。"
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(55, 49, 59))
                setPadding(dp(16), dp(90), dp(16), dp(24))
            }
            list.addView(empty, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }

        val maxBubbleWidth = (service.resources.displayMetrics.widthPixels * 0.86f).toInt()
        snapshot.messages.forEach { message ->
            val row = FrameLayout(service).apply { setPadding(0, dp(5), 0, dp(5)) }
            val bubble = TextView(service).apply {
                text = message.text
                textSize = 16f
                setTextColor(Color.rgb(52, 49, 58))
                setLineSpacing(0f, 1.14f)
                setPadding(dp(16), dp(12), dp(16), dp(12))
                maxWidth = maxBubbleWidth
                setTextIsSelectable(true)
                background = if (message.isUser) {
                    GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(
                            Color.rgb(249, 216, 234),
                            Color.rgb(231, 217, 248),
                            Color.rgb(220, 236, 251)
                        )
                    ).apply { cornerRadius = dp(24).toFloat() }
                } else {
                    roundedSolid(Color.argb(235, 255, 255, 255), 24f, Color.argb(95, 255, 255, 255))
                }
            }
            val bubbleParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = if (message.isUser) Gravity.END else Gravity.START
                leftMargin = if (message.isUser) dp(42) else 0
                rightMargin = if (message.isUser) 0 else dp(28)
            }
            row.addView(bubble, bubbleParams)
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        list.post {
            (list.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun sendCurrentText() {
        val edit = input ?: return
        val text = edit.text?.toString().orEmpty().trim()
        if (text.isEmpty()) return
        if (onSend(text)) {
            edit.setText("")
            val imm = service.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(edit.windowToken, 0)
            edit.clearFocus()
        }
    }

    private fun hideToMini() {
        manualHidden = true
        removeView(fullView)
        fullView = null
        messageList = null
        input = null
        lastSignature = ""
        showMini()
    }

    private fun showMini() {
        if (miniView != null) return
        val mini = TextView(service).apply {
            text = "微光"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(64, 55, 69))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedSolid(Color.argb(244, 250, 232, 247), 22f, Color.argb(110, 255, 255, 255))
            elevation = dp(6).toFloat()
            setOnClickListener {
                manualHidden = false
                removeView(miniView)
                miniView = null
                addFullOverlay()
                lastSnapshot?.let(::render)
            }
        }
        miniView = mini
        wm.addView(mini, WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(10)
        })
    }

    private fun actionChip(label: String, onClick: () -> Unit): TextView = TextView(service).apply {
        text = label
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(88, 78, 95))
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = roundedSolid(Color.argb(230, 255, 255, 255), 18f, Color.argb(90, 255, 255, 255))
        setOnClickListener { onClick() }
    }

    private fun roundedSolid(fill: Int, radiusDp: Float, stroke: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun removeView(view: View?) {
        if (view == null) return
        runCatching { wm.removeViewImmediate(view) }
    }

    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()
}
