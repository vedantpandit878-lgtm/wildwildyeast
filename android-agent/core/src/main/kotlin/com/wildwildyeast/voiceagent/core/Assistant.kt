package com.wildwildyeast.voiceagent.core

import com.anthropic.client.AnthropicClient

/**
 * Front door for a spoken command. Replays a learned routine when one matches,
 * otherwise (or when the replay gets stuck) runs the AI loop, and saves what
 * worked so the next time is free.
 */
class Assistant(
    private val client: AnthropicClient,
    private val device: DeviceController,
    private val store: RoutineStore,
    private val config: AgentConfig = AgentConfig(),
    private val listener: AgentListener = object : AgentListener {},
) {
    data class Result(val outcome: AgentOutcome, val replayed: Boolean, val usedAi: Boolean, val saved: Boolean)

    suspend fun run(command: String): Result {
        val routine = store.find(command)
        val recorder = RoutineRecorder()
        val recording = object : AgentListener by listener {
            override fun onStepPerformed(action: AgentAction, before: ScreenState, result: ActionResult) {
                recorder.record(action, before)
                listener.onStepPerformed(action, before, result)
            }
        }

        // The replayer reports each replayed step through the same listener, so the
        // recorder ends up holding the replayed prefix followed by the AI's new steps.
        var context: String? = null
        if (routine != null) {
            listener.onStatus("Replaying what worked last time")
            val replay = RoutineReplayer(device, config.safetyGate, recording).replay(routine)
            if (replay.finished) {
                store.save(routine.copy(runs = routine.runs + 1))
                return Result(AgentOutcome(true, routine.summary, replay.completedSteps), replayed = true, usedAi = false, saved = false)
            }
            if (replay.declined) {
                return Result(AgentOutcome(false, "Stopped: ${replay.reason}.", replay.completedSteps), replayed = true, usedAi = false, saved = false)
            }
            val done = routine.steps.take(replay.completedSteps).joinToString("; ") { it.describe() }
            context = buildString {
                append("A saved routine for this command was replayed first. ")
                if (done.isNotEmpty()) append("Steps already done: ").append(done).append(". ")
                append("It stopped because ").append(replay.reason ?: "a step failed")
                append(". Continue from the current screen; do not repeat the completed steps unless the screen shows they did not take effect.")
            }
            listener.onStatus("Routine stuck, asking the AI")
        }

        val outcome = AgentLoop(client, device, config, recording).run(command, context)
        var saved = false
        if (outcome.success && recorder.steps.isNotEmpty()) {
            store.save(
                Routine(
                    command = command,
                    normalized = Routine.normalize(command),
                    steps = recorder.steps,
                    summary = outcome.summary,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            saved = true
        }
        return Result(outcome, replayed = routine != null, usedAi = true, saved = saved)
    }
}
