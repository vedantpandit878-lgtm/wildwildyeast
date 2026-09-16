package com.wildwildyeast.voiceagent.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.math.abs

data class ReplayResult(
    val completedSteps: Int,
    val total: Int,
    val failedStep: RoutineStep? = null,
    val reason: String? = null,
    val declined: Boolean = false,
) {
    val finished: Boolean get() = completedSteps == total && failedStep == null
}

/** Re-runs recorded steps on the live screen, re-finding each element by its description. */
class RoutineReplayer(
    private val device: DeviceController,
    private val safetyGate: SafetyGate = SafetyGate(),
    private val listener: AgentListener = object : AgentListener {},
    /** How long to wait for an expected element or app to appear. */
    private val findTimeoutMs: Long = 6000,
) {
    suspend fun replay(routine: Routine): ReplayResult {
        val total = routine.steps.size
        routine.steps.forEachIndexed { index, step ->
            currentCoroutineContext().ensureActive()
            listener.onStatus("Replaying ${index + 1}/$total: ${step.describe()}")
            val resolved = resolve(step)
                ?: return ReplayResult(index, total, step, "could not find ${step.describe()} on the current screen")
            val (action, screen) = resolved
            safetyGate.confirmationFor(action, screen)?.let { question ->
                if (!device.confirm(question)) return ReplayResult(index, total, step, "you declined ${step.describe()}", declined = true)
            }
            listener.onAction(index + 1, action)
            val result = device.perform(action)
            if (!result.ok) return ReplayResult(index, total, step, result.message)
            listener.onStepPerformed(action, screen, result)
        }
        return ReplayResult(total, total)
    }

    /** Turn a recorded step into a concrete action for the screen that is showing now. */
    private suspend fun resolve(step: RoutineStep): Pair<AgentAction, ScreenState>? {
        when (step.kind) {
            StepKind.OPEN_APP -> return AgentAction.OpenApp(step.appName ?: return null) to device.capture(false)
            StepKind.WAIT -> return AgentAction.Wait(step.seconds ?: 1.0) to device.capture(false)
            StepKind.PRESS -> return AgentAction.Press(AgentAction.Key.valueOf(step.key ?: return null)) to device.capture(false)
            else -> {}
        }
        val deadline = System.currentTimeMillis() + findTimeoutMs
        var screen = device.capture(false)
        while (true) {
            val action = resolveOn(step, screen)
            if (action != null) return action to screen
            if (System.currentTimeMillis() >= deadline) return null
            delay(500)
            screen = device.capture(false)
        }
    }

    private fun resolveOn(step: RoutineStep, screen: ScreenState): AgentAction? {
        if (step.packageName.isNotBlank() && screen.packageName != step.packageName) return null
        return when (step.kind) {
            StepKind.TAP -> step.target?.let { findNode(it, screen) }?.let { AgentAction.Tap(it.id) }
            StepKind.LONG_PRESS -> step.target?.let { findNode(it, screen) }?.let { AgentAction.LongPress(it.id) }
            StepKind.TAP_AT -> {
                val node = step.target?.let { findNode(it, screen) }
                if (node != null) AgentAction.Tap(node.id)
                else if (step.x != null && step.y != null) AgentAction.TapAt(scaleX(step, screen, step.x), scaleY(step, screen, step.y))
                else null
            }
            StepKind.TYPE_TEXT -> {
                val text = step.text ?: return null
                if (step.target == null) {
                    if (screen.nodes.any { it.focused && it.editable }) AgentAction.TypeText(null, text, step.submit ?: false) else null
                } else {
                    findNode(step.target, screen, editableOnly = true)?.let { AgentAction.TypeText(it.id, text, step.submit ?: false) }
                }
            }
            StepKind.SCROLL -> {
                val dir = AgentAction.Direction.valueOf(step.direction ?: return null)
                val node = step.target?.let { findNode(it, screen) }
                AgentAction.Scroll(dir, node?.id)
            }
            else -> null
        }
    }

    private fun scaleX(step: RoutineStep, screen: ScreenState, x: Int): Int {
        val w = step.target?.screenWidth ?: screen.width
        return if (w > 0) x * screen.width / w else x
    }

    private fun scaleY(step: RoutineStep, screen: ScreenState, y: Int): Int {
        val h = step.target?.screenHeight ?: screen.height
        return if (h > 0) y * screen.height / h else y
    }

    companion object {
        /**
         * Score every element against the recorded description and return the best
         * one, or null if nothing is convincing. A matching resource id or label is
         * convincing on its own; class + position only counts when the recorded
         * element had no label or id (dynamic content such as prices).
         */
        fun findNode(target: TargetRef, screen: ScreenState, editableOnly: Boolean = false): ScreenNode? {
            val wantLabel = target.label().trim().lowercase()
            val wantId = target.resourceId?.takeIf { it.isNotBlank() }
            val sx = if (target.screenWidth > 0) screen.width.toDouble() / target.screenWidth else 1.0
            val sy = if (target.screenHeight > 0) screen.height.toDouble() / target.screenHeight else 1.0
            val expectedX = target.centerX * sx
            val expectedY = target.centerY * sy
            val tolX = screen.width * 0.08
            val tolY = screen.height * 0.06

            var best: ScreenNode? = null
            var bestScore = 0
            var bestDist = Double.MAX_VALUE
            for (node in screen.nodes) {
                if (editableOnly && !node.editable) continue
                var score = 0
                if (wantId != null && node.resourceId == wantId) score += 5
                val label = node.label().trim().lowercase()
                if (wantLabel.isNotEmpty() && label == wantLabel) score += 4
                if (node.className == target.className) score += 2
                val dist = maxOf(abs(node.centerX - expectedX) / tolX, abs(node.centerY - expectedY) / tolY)
                if (dist <= 1.0) score += 1
                val hasIdentity = wantId != null || wantLabel.isNotEmpty()
                val threshold = if (hasIdentity) 4 else 3
                if (score < threshold) continue
                if (score > bestScore || (score == bestScore && dist < bestDist)) {
                    best = node; bestScore = score; bestDist = dist
                }
            }
            return best
        }
    }
}
