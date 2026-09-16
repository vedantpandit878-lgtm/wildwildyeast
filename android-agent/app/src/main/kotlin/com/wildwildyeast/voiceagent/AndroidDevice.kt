package com.wildwildyeast.voiceagent

import android.os.SystemClock
import com.wildwildyeast.voiceagent.core.ActionResult
import com.wildwildyeast.voiceagent.core.AgentAction
import com.wildwildyeast.voiceagent.core.DeviceController
import com.wildwildyeast.voiceagent.core.ScreenState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Adapts the accessibility service to the core module's DeviceController. */
class AndroidDevice(private val service: AgentAccessibilityService) : DeviceController {

    override suspend fun capture(withScreenshot: Boolean): ScreenState = withContext(Dispatchers.Main) {
        val state = service.snapshot()
        if (withScreenshot) state.copy(screenshotPng = service.screenshotPng()) else state
    }

    override suspend fun perform(action: AgentAction): ActionResult = withContext(Dispatchers.Main) {
        val result: Result<String> = when (action) {
            is AgentAction.Tap -> service.tap(action.nodeId)
            is AgentAction.LongPress -> service.longPress(action.nodeId)
            is AgentAction.TapAt -> service.tapAt(action.x, action.y)
            is AgentAction.TypeText -> service.typeText(action.nodeId, action.text, action.submit)
            is AgentAction.Scroll -> service.scroll(action.direction.name, action.nodeId)
            is AgentAction.Press -> service.press(action.key.name)
            is AgentAction.OpenApp -> service.openApp(action.appName)
            is AgentAction.Wait -> { delay((action.seconds * 1000).toLong()); Result.success("waited") }
            AgentAction.Screenshot, is AgentAction.AskUser, is AgentAction.Finish ->
                Result.failure(IllegalStateException("handled by the loop"))
        }
        settle(action)
        result.fold({ ActionResult.ok(it) }, { ActionResult.fail(it.message ?: "failed") })
    }

    /** Give the UI time to react, then wait until accessibility events stop arriving. */
    private suspend fun settle(action: AgentAction) {
        val initial = when (action) {
            is AgentAction.OpenApp -> 1500L
            is AgentAction.Wait -> 0L
            is AgentAction.Scroll -> 500L
            else -> 600L
        }
        delay(initial)
        val deadline = SystemClock.uptimeMillis() + 2500
        while (SystemClock.uptimeMillis() < deadline) {
            if (SystemClock.uptimeMillis() - service.lastEventUptime > 450) return
            delay(150)
        }
    }

    override suspend fun askUser(question: String): String = service.overlay.ask(question)

    override suspend fun confirm(question: String): Boolean = service.overlay.confirm(question)

    override fun installedAppNames(): List<String> = service.launchableApps().keys.toList()
}
