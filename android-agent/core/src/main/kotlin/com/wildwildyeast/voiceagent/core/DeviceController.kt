package com.wildwildyeast.voiceagent.core

/**
 * The phone side of the agent. The Android app implements this on top of an
 * AccessibilityService; tests implement it with a fake.
 */
interface DeviceController {
    /** Read the current screen. Set [withScreenshot] to also capture pixels. */
    suspend fun capture(withScreenshot: Boolean): ScreenState

    /** Perform one action. Implementations should wait for the UI to settle before returning. */
    suspend fun perform(action: AgentAction): ActionResult

    /** Ask the person a question (spoken and shown) and wait for their reply. */
    suspend fun askUser(question: String): String

    /** Ask the person to confirm a risky action. */
    suspend fun confirm(question: String): Boolean

    /** Launchable app labels, used so the model can name apps exactly. */
    fun installedAppNames(): List<String>
}
