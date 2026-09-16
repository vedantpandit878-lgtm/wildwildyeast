package com.wildwildyeast.voiceagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.wildwildyeast.voiceagent.core.ScreenNode
import com.wildwildyeast.voiceagent.core.ScreenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * The phone-side primitives: read the UI tree, tap, type, scroll, screenshot.
 * Everything here is a thin wrapper over the AccessibilityService API; the
 * decision making lives in the core module's AgentLoop.
 */
class AgentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VoiceAgentService"
        private const val CHANNEL_ID = "voice_agent"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var instance: AgentAccessibilityService? = null
            private set
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var overlay: OverlayController
        private set
    lateinit var speaker: Speaker
        private set
    lateinit var session: AgentSession
        private set

    @Volatile
    var lastEventUptime: Long = 0L
        private set

    /** Nodes from the most recent snapshot, keyed by the ids the model sees. */
    private var lastNodes: Map<Int, AccessibilityNodeInfo> = emptyMap()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        speaker = Speaker(this)
        overlay = OverlayController(this)
        session = AgentSession(this)
        overlay.show()
        ensureForeground()
        Log.i(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        lastEventUptime = SystemClock.uptimeMillis()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        if (::session.isInitialized) session.stop(silent = true)
        if (::overlay.isInitialized) overlay.hide()
        if (::speaker.isInitialized) speaker.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Promote to a microphone foreground service so speech recognition keeps
     * working while another app is in front. Android may refuse this while we
     * are in the background; MainActivity calls it again from the foreground.
     */
    fun ensureForeground() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Voice Agent", NotificationManager.IMPORTANCE_LOW),
            )
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Voice Agent is ready")
                .setContentText("Tap the bubble and speak.")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(open)
                .setOngoing(true)
                .build()
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            Log.w(TAG, "startForeground refused (will retry from the activity): $e")
        }
    }

    // ---------------------------------------------------------------- reading

    fun snapshot(): ScreenState {
        val metrics = resources.displayMetrics
        val nodes = ArrayList<ScreenNode>()
        val map = HashMap<Int, AccessibilityNodeInfo>()
        var keyboard = false
        var packageName = ""

        val roots = ArrayList<AccessibilityNodeInfo>()
        val allWindows: List<AccessibilityWindowInfo> = try { getWindows() } catch (e: Exception) { emptyList() }
        // Active app window first, then dialogs and other app windows; skip our own overlay and the keyboard.
        allWindows.sortedByDescending { it.isActive }.forEach { w ->
            when (w.type) {
                AccessibilityWindowInfo.TYPE_INPUT_METHOD -> keyboard = true
                AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> {}
                AccessibilityWindowInfo.TYPE_APPLICATION, AccessibilityWindowInfo.TYPE_SYSTEM -> {
                    w.root?.let { r ->
                        if (r.packageName?.toString() != this.packageName) roots += r
                    }
                }
                else -> {}
            }
        }
        if (roots.isEmpty()) rootInActiveWindow?.let { roots += it }

        var nextId = 1
        val rect = Rect()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (nodes.size >= 300) return
            if (!node.isVisibleToUser) return
            node.getBoundsInScreen(rect)
            if (rect.width() <= 0 || rect.height() <= 0) return
            val text = node.text?.toString()
            val desc = node.contentDescription?.toString()
            val interesting = !text.isNullOrBlank() || !desc.isNullOrBlank() ||
                node.isClickable || node.isEditable || node.isScrollable || node.isCheckable || node.isLongClickable
            var childDepth = depth
            if (interesting) {
                val id = nextId++
                map[id] = node
                nodes += ScreenNode(
                    id = id,
                    className = node.className?.toString() ?: "View",
                    text = text,
                    contentDescription = desc,
                    resourceId = node.viewIdResourceName,
                    left = rect.left, top = rect.top, right = rect.right, bottom = rect.bottom,
                    depth = depth,
                    clickable = node.isClickable,
                    longClickable = node.isLongClickable,
                    editable = node.isEditable,
                    scrollable = node.isScrollable,
                    checkable = node.isCheckable,
                    checked = node.isChecked,
                    selected = node.isSelected,
                    focused = node.isFocused,
                    enabled = node.isEnabled,
                    password = node.isPassword,
                )
                childDepth = depth + 1
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { walk(it, childDepth) }
            }
        }
        roots.forEachIndexed { index, root ->
            if (index == 0) packageName = root.packageName?.toString() ?: ""
            walk(root, 0)
        }
        lastNodes = map
        return ScreenState(
            packageName = packageName,
            appLabel = appLabel(packageName),
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            nodes = nodes,
            keyboardVisible = keyboard,
        )
    }

    fun appLabel(packageName: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (e: Exception) {
        packageName
    }

    suspend fun screenshotPng(maxWidth: Int = 720): ByteArray? {
        repeat(3) { attempt ->
            val bitmap = takeScreenshotBitmap()
            if (bitmap != null) {
                val scale = if (bitmap.width > maxWidth) maxWidth.toFloat() / bitmap.width else 1f
                val scaled = if (scale < 1f) {
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                } else bitmap
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                return out.toByteArray()
            }
            delay(700L * (attempt + 1)) // the system rate-limits screenshots
        }
        return null
    }

    private suspend fun takeScreenshotBitmap(): Bitmap? = suspendCancellableCoroutine { cont ->
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY, mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val hw = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        val sw = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        screenshot.hardwareBuffer.close()
                        cont.resume(sw)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "screenshot failed: $errorCode")
                        cont.resume(null)
                    }
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "screenshot threw: $e")
            cont.resume(null)
        }
    }

    // ---------------------------------------------------------------- acting

    private fun node(id: Int): AccessibilityNodeInfo? {
        val n = lastNodes[id] ?: return null
        return if (n.refresh()) n else null
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = node
        var hops = 0
        while (cur != null && hops < 6) {
            if (predicate(cur)) return cur
            cur = cur.parent
            hops++
        }
        return null
    }

    private fun center(node: AccessibilityNodeInfo): Pair<Float, Float> {
        val r = Rect()
        node.getBoundsInScreen(r)
        return r.exactCenterX() to r.exactCenterY()
    }

    suspend fun tap(id: Int): Result<String> {
        val n = node(id) ?: return Result.failure(IllegalStateException("element $id is no longer on screen"))
        val target = clickableAncestor(n) { it.isClickable }
        if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return Result.success("tapped \"${n.text ?: n.contentDescription ?: ""}\"")
        }
        val (x, y) = center(n)
        return if (gestureTap(x, y, 60)) Result.success("tapped at (${x.toInt()}, ${y.toInt()})")
        else Result.failure(IllegalStateException("tap gesture was rejected"))
    }

    suspend fun longPress(id: Int): Result<String> {
        val n = node(id) ?: return Result.failure(IllegalStateException("element $id is no longer on screen"))
        val target = clickableAncestor(n) { it.isLongClickable }
        if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) return Result.success("long-pressed")
        val (x, y) = center(n)
        return if (gestureTap(x, y, 700)) Result.success("long-pressed at (${x.toInt()}, ${y.toInt()})")
        else Result.failure(IllegalStateException("long-press gesture was rejected"))
    }

    suspend fun tapAt(x: Int, y: Int): Result<String> =
        if (gestureTap(x.toFloat(), y.toFloat(), 60)) Result.success("tapped at ($x, $y)")
        else Result.failure(IllegalStateException("tap gesture was rejected"))

    suspend fun typeText(id: Int?, text: String, submit: Boolean): Result<String> {
        val n: AccessibilityNodeInfo? = if (id != null) node(id) else rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (n == null) return Result.failure(IllegalStateException(if (id != null) "element $id is no longer on screen" else "no text field is focused; tap one first"))
        val field = clickableAncestor(n) { it.isEditable } ?: n
        if (!field.isEditable) return Result.failure(IllegalStateException("element ${id ?: ""} is not editable"))
        if (!field.isFocused) {
            field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            field.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(250)
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (!field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            return Result.failure(IllegalStateException("the field rejected the text"))
        }
        if (submit) {
            delay(300)
            if (!field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) {
                return Result.success("typed the text, but the enter action was not available; tap a search/send button instead")
            }
        }
        return Result.success("typed \"$text\"" + if (submit) " and pressed enter" else "")
    }

    suspend fun scroll(direction: String, id: Int?): Result<String> {
        if (id != null) {
            val n = node(id)
            val target = n?.let { clickableAncestor(it) { c -> c.isScrollable } }
            if (target != null) {
                val forward = direction == "DOWN" || direction == "RIGHT"
                val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                if (target.performAction(action)) return Result.success("scrolled ${direction.lowercase()}")
            }
        }
        val m = resources.displayMetrics
        val w = m.widthPixels.toFloat()
        val h = m.heightPixels.toFloat()
        val (from, to) = when (direction) {
            "DOWN" -> (w / 2 to h * 0.72f) to (w / 2 to h * 0.28f)
            "UP" -> (w / 2 to h * 0.28f) to (w / 2 to h * 0.72f)
            "LEFT" -> (w * 0.8f to h / 2) to (w * 0.2f to h / 2)
            else -> (w * 0.2f to h / 2) to (w * 0.8f to h / 2)
        }
        val path = Path().apply { moveTo(from.first, from.second); lineTo(to.first, to.second) }
        return if (dispatch(path, 350)) Result.success("swiped ${direction.lowercase()}")
        else Result.failure(IllegalStateException("swipe gesture was rejected"))
    }

    fun press(key: String): Result<String> {
        val ok = when (key) {
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "RECENTS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "NOTIFICATIONS" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "ENTER" -> rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id) ?: false
            else -> false
        }
        return if (ok) Result.success("pressed ${key.lowercase()}") else Result.failure(IllegalStateException("could not press ${key.lowercase()}"))
    }

    /** Launcher apps as label -> package. */
    fun launchableApps(): Map<String, String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .associate { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    fun openApp(name: String): Result<String> {
        val apps = launchableApps()
        val wanted = name.trim().lowercase()
        val match = apps.keys.firstOrNull { it.lowercase() == wanted }
            ?: apps.keys.firstOrNull { it.lowercase().startsWith(wanted) }
            ?: apps.keys.firstOrNull { it.lowercase().contains(wanted) || wanted.contains(it.lowercase()) }
            ?: return Result.failure(IllegalStateException("no installed app named \"$name\". Installed: ${apps.keys.take(60).joinToString(", ")}"))
        val pkg = apps.getValue(match)
        val launch = packageManager.getLaunchIntentForPackage(pkg)
            ?: return Result.failure(IllegalStateException("\"$match\" has no launcher activity"))
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            startActivity(launch)
            Result.success("opened $match")
        } catch (e: Exception) {
            Result.failure(IllegalStateException("could not launch $match: ${e.message}"))
        }
    }

    private suspend fun gestureTap(x: Float, y: Float, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatch(path, durationMs)
    }

    private suspend fun dispatch(path: Path, durationMs: Long): Boolean = suspendCancellableCoroutine { cont ->
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        val accepted = dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(false) }
            },
            null,
        )
        if (!accepted && cont.isActive) cont.resume(false)
    }
}
