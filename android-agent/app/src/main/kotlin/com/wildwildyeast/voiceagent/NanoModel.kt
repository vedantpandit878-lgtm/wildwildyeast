package com.wildwildyeast.voiceagent

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.prompt.DownloadStatus
import com.google.mlkit.genai.prompt.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.wildwildyeast.voiceagent.core.LocalTextModel

/**
 * Gemini Nano running on the phone through Android's AICore service. Free,
 * offline, nothing leaves the device. Available on recent Pixel and Samsung
 * phones; the system downloads the model once.
 */
class NanoModel(@Suppress("unused") private val context: Context) : LocalTextModel {

    override val name: String = "Gemini Nano (on-device)"

    private val model by lazy { Generation.getClient() }

    @Volatile
    private var systemInstructionsWork = true

    /** One of [FeatureStatus] AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE. */
    suspend fun status(): Int = try {
        model.checkStatus()
    } catch (e: Exception) {
        Log.w(TAG, "checkStatus failed: $e")
        FeatureStatus.UNAVAILABLE
    }

    suspend fun isReady(): Boolean = status() == FeatureStatus.AVAILABLE

    fun describe(status: Int): String = when (status) {
        FeatureStatus.AVAILABLE -> "ready"
        FeatureStatus.DOWNLOADABLE -> "not downloaded yet"
        FeatureStatus.DOWNLOADING -> "downloading"
        else -> "not supported on this phone"
    }

    /** Download the model through AICore. Reports progress text; returns true when complete. */
    suspend fun download(onProgress: (String) -> Unit): Boolean {
        var ok = false
        try {
            model.download().collect { status ->
                when (status) {
                    is DownloadStatus.DownloadStarted -> onProgress("Download started")
                    is DownloadStatus.DownloadProgress -> onProgress("Downloaded ${status.totalBytesDownloaded / (1024 * 1024)} MB")
                    DownloadStatus.DownloadCompleted -> { ok = true; onProgress("Download complete") }
                    is DownloadStatus.DownloadFailed -> onProgress("Download failed: ${status.e.message}")
                }
            }
        } catch (e: Exception) {
            onProgress("Download failed: ${e.message}")
        }
        return ok
    }

    override suspend fun generate(system: String, prompt: String): String {
        if (systemInstructionsWork) {
            try {
                val response = model.generateContent(
                    generateContentRequest(SystemInstruction(system), TextPart(prompt)) {
                        temperature = 0.1f
                        maxOutputTokens = 200
                    },
                )
                return response.candidates.firstOrNull()?.text ?: ""
            } catch (e: Exception) {
                // Older Nano versions do not support system instructions; fold them into the prompt instead.
                Log.w(TAG, "system instruction request failed, retrying inline: $e")
                systemInstructionsWork = false
            }
        }
        val response = model.generateContent(
            generateContentRequest(TextPart("## Instructions\n$system\n\n$prompt")) {
                temperature = 0.1f
                maxOutputTokens = 200
            },
        )
        return response.candidates.firstOrNull()?.text ?: ""
    }

    fun close() {
        runCatching { model.close() }
    }

    companion object {
        private const val TAG = "VoiceAgentNano"
    }
}
