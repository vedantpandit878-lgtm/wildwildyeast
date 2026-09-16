package com.wildwildyeast.voiceagent

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Text-to-speech with a suspending [say] that returns when the sentence has been spoken. */
class Speaker(context: Context) {
    private val ready = CompletableDeferred<Boolean>()
    private val waiters = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { utteranceId?.let { waiters.remove(it)?.complete(Unit) } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { utteranceId?.let { waiters.remove(it)?.complete(Unit) } }
            override fun onError(utteranceId: String?, errorCode: Int) { utteranceId?.let { waiters.remove(it)?.complete(Unit) } }
        })
    }

    suspend fun say(text: String) {
        if (text.isBlank() || !ready.await()) return
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>()
        waiters[id] = done
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            waiters.remove(id)
            return
        }
        withTimeoutOrNull(45_000) { done.await() }
    }

    fun stop() {
        tts.stop()
        waiters.values.forEach { it.complete(Unit) }
        waiters.clear()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }
}
