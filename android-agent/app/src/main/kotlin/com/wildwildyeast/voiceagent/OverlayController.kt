package com.wildwildyeast.voiceagent

import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * The floating microphone bubble and the question panel. Both are accessibility
 * overlays, so no "draw over other apps" permission is needed.
 */
class OverlayController(private val service: AgentAccessibilityService) {

    private val ctx = ContextThemeWrapper(service, android.R.style.Theme_DeviceDefault_Light)
    private val wm = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density

    private var bubble: View? = null
    private var status: TextView? = null
    private var panel: View? = null
    private var pending: CompletableDeferred<String>? = null
    private var listenJob: Job? = null

    fun show() {
        if (bubble != null) return
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mic = TextView(ctx).apply {
            text = "🎤" // microphone
            textSize = 24f
            gravity = Gravity.CENTER
            val size = dp(56)
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#1F1F1F")) }
        }
        val label = TextView(ctx).apply {
            text = "Tap to speak"
            setTextColor(Color.WHITE)
            textSize = 13f
            maxWidth = dp(200)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#CC1F1F1F")) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(6)
            }
        }
        root.addView(mic)
        root.addView(label)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(160)
        }
        makeDraggable(root, params) { service.session.onBubbleTapped() }
        wm.addView(root, params)
        bubble = root
        status = label
    }

    fun hide() {
        dismissPanel()
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        status = null
    }

    fun setStatus(text: String) {
        service.scope.launch(Dispatchers.Main) { status?.text = text.take(90) }
    }

    suspend fun confirm(question: String): Boolean {
        val answer = ask(question, yesNo = true)
        return isYes(answer)
    }

    suspend fun ask(question: String): String = ask(question, yesNo = false)

    /** Show the question, read it out, listen for a spoken answer, and also accept a typed one. */
    private suspend fun ask(question: String, yesNo: Boolean): String = withContext(Dispatchers.Main) {
        dismissPanel()
        val deferred = CompletableDeferred<String>()
        pending = deferred
        setStatus("Waiting for you")

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.WHITE) }
        }
        root.addView(TextView(ctx).apply { text = question; textSize = 17f; setTextColor(Color.BLACK) })
        val input = if (yesNo) null else EditText(ctx).apply { hint = "Type your answer or just speak" }
        input?.let { root.addView(it) }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        fun button(label: String, onClick: () -> Unit) = Button(ctx).apply { text = label; setOnClickListener { onClick() } }
        row.addView(button("Stop task") { deferred.complete("cancel"); service.session.stop() })
        if (yesNo) {
            row.addView(button("No") { deferred.complete("no") })
            row.addView(button("Yes") { deferred.complete("yes") })
        } else {
            row.addView(button("Send") { deferred.complete(input?.text?.toString()?.trim().orEmpty()) })
        }
        row.addView(button("🎤") { startListening(deferred, yesNo) })
        root.addView(row)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            y = dp(24)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        wm.addView(root, params)
        panel = root

        // Read the question aloud, then listen once; typing or tapping still works meanwhile.
        listenJob = service.scope.launch {
            service.speaker.say(question)
            if (!deferred.isCompleted) startListening(deferred, yesNo)
        }
        try {
            deferred.await()
        } finally {
            listenJob?.cancel()
            listenJob = null
            dismissPanel()
        }
    }

    private fun startListening(deferred: CompletableDeferred<String>, yesNo: Boolean) {
        listenJob?.cancel()
        listenJob = service.scope.launch {
            setStatus("Listening")
            val heard = VoiceInput(service).listen()
            if (deferred.isCompleted) return@launch
            when {
                heard.isNullOrBlank() -> setStatus("Didn't catch that. Tap the mic or a button.")
                yesNo && !isYes(heard) && !isNo(heard) -> setStatus("Please say yes or no")
                else -> deferred.complete(heard)
            }
        }
    }

    fun dismissPanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        pending?.let { if (!it.isCompleted) it.complete("cancel") }
        pending = null
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams, onClick: () -> Unit) {
        val slop = ViewConfiguration.get(ctx).scaledTouchSlop
        var startX = 0; var startY = 0
        var downX = 0f; var downY = 0f
        var moved = false
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y; downX = e.rawX; downY = e.rawY; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        params.x = startX + dx.toInt(); params.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(v, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) { v.performClick(); onClick() }; true }
                else -> false
            }
        }
    }

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    companion object {
        private val YES = listOf("yes", "yeah", "yep", "yup", "ok", "okay", "sure", "confirm", "go ahead", "do it", "proceed", "correct", "haan", "ha")
        private val NO = listOf("no", "nope", "don't", "dont", "stop", "cancel", "never mind", "nahi", "na")
        fun isYes(s: String): Boolean {
            val t = s.trim().lowercase()
            return YES.any { t == it || t.startsWith("$it ") || t.startsWith("$it,") }
        }
        fun isNo(s: String): Boolean {
            val t = s.trim().lowercase()
            return NO.any { t == it || t.startsWith("$it ") || t.startsWith("$it,") }
        }
    }
}
