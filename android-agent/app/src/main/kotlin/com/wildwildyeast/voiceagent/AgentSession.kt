package com.wildwildyeast.voiceagent

import android.util.Log
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.UnauthorizedException
import com.wildwildyeast.voiceagent.core.AgentAction
import com.wildwildyeast.voiceagent.core.AgentConfig
import com.wildwildyeast.voiceagent.core.AgentListener
import com.wildwildyeast.voiceagent.core.Assistant
import com.wildwildyeast.voiceagent.core.JsonFileRoutineStore
import com.wildwildyeast.voiceagent.core.SafetyGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.time.Duration

/** Owns the currently running task: voice capture -> AgentLoop -> spoken result. */
class AgentSession(private val service: AgentAccessibilityService) {

    private var job: Job? = null
    val isRunning: Boolean get() = job?.isActive == true

    /** Learned routines live in the app's private files directory. */
    val routines = JsonFileRoutineStore(File(service.filesDir, "routines.json"))

    fun onBubbleTapped() {
        if (isRunning) stop() else startVoiceCommand()
    }

    fun startVoiceCommand() {
        job = service.scope.launch {
            service.overlay.setStatus("Listening")
            val voice = VoiceInput(service)
            if (!voice.available) {
                service.overlay.setStatus("No speech recogniser on this phone")
                return@launch
            }
            val heard = voice.listen()
            if (heard.isNullOrBlank()) {
                service.overlay.setStatus("Didn't catch that. Tap to try again")
                service.speaker.say("I didn't catch that.")
                return@launch
            }
            runGoalInternal(heard)
        }
    }

    fun runGoal(goal: String) {
        if (isRunning) stop(silent = true)
        job = service.scope.launch { runGoalInternal(goal) }
    }

    fun stop(silent: Boolean = false) {
        val running = job?.isActive == true
        job?.cancel()
        job = null
        service.speaker.stop()
        service.overlay.dismissPanel()
        service.overlay.setStatus("Tap to speak")
        if (running && !silent) service.scope.launch { service.speaker.say("Stopped.") }
    }

    private suspend fun runGoalInternal(goal: String) {
        val settings = Settings(service)
        val key = settings.apiKey
        if (key.isBlank()) {
            service.overlay.setStatus("Set your API key in the Voice Agent app")
            service.speaker.say("Please set your API key in the Voice Agent app first.")
            return
        }
        service.overlay.setStatus("Heard: $goal")
        val client = AnthropicOkHttpClient.builder()
            .apiKey(key)
            .timeout(Duration.ofMinutes(5))
            .build()
        val assistant = Assistant(
            client = client,
            device = AndroidDevice(service),
            store = routines,
            config = AgentConfig(
                model = settings.model,
                safetyGate = SafetyGate(confirmEverything = settings.confirmEverything),
            ),
            listener = object : AgentListener {
                override fun onStatus(text: String) = service.overlay.setStatus(text)
                override fun onAction(step: Int, action: AgentAction) {
                    Log.i("VoiceAgent", "step $step: ${action.describe()}")
                    service.overlay.setStatus("$step. ${action.describe()}")
                }
            },
        )
        try {
            val result = assistant.run(goal)
            val outcome = result.outcome
            service.overlay.setStatus(
                when {
                    !outcome.success -> "Could not finish"
                    result.replayed && !result.usedAi -> "Done (replayed, free)"
                    result.saved -> "Done and remembered"
                    else -> "Done"
                },
            )
            service.speaker.say(outcome.summary)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnauthorizedException) {
            service.overlay.setStatus("API key rejected")
            service.speaker.say("The API key was rejected. Please check it in the app.")
        } catch (e: AnthropicServiceException) {
            Log.e("VoiceAgent", "API error", e)
            service.overlay.setStatus("API error: ${e.message?.take(60)}")
            service.speaker.say("The Claude API returned an error. Please try again.")
        } catch (e: Exception) {
            Log.e("VoiceAgent", "agent failed", e)
            service.overlay.setStatus("Error: ${e.message?.take(60)}")
            service.speaker.say("Something went wrong: ${e.message ?: "unknown error"}")
        } finally {
            service.overlay.dismissPanel()
            service.scope.launch {
                kotlinx.coroutines.delay(6000)
                if (!isRunning) service.overlay.setStatus("Tap to speak")
            }
        }
    }
}
