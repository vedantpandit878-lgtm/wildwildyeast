package com.wildwildyeast.voiceagent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings as SystemSettings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Setup screen: permissions, API key, and a way to test commands by typing. */
class MainActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private lateinit var status: TextView
    private lateinit var nanoStatus: TextView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val nano by lazy { NanoModel(applicationContext) }
    private var downloadJob: Job? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = Settings(this)
        status = findViewById(R.id.status)
        nanoStatus = findViewById(R.id.nanoStatus)

        val brainLocal = findViewById<RadioButton>(R.id.brainLocal)
        val brainClaude = findViewById<RadioButton>(R.id.brainClaude)
        if (settings.brain == Settings.BRAIN_CLAUDE) brainClaude.isChecked = true else brainLocal.isChecked = true
        brainLocal.setOnCheckedChangeListener { _, checked -> if (checked) settings.brain = Settings.BRAIN_LOCAL }
        brainClaude.setOnCheckedChangeListener { _, checked -> if (checked) settings.brain = Settings.BRAIN_CLAUDE }

        findViewById<Button>(R.id.btnDownloadNano).setOnClickListener {
            if (downloadJob?.isActive == true) return@setOnClickListener
            downloadJob = scope.launch {
                nanoStatus.text = "On-device AI: starting download (this can take a while on Wi-Fi)"
                nano.download { progress -> nanoStatus.text = "On-device AI: $progress" }
                refreshNanoStatus()
            }
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(SystemSettings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Find \"Voice Agent\" under installed apps and turn it on", Toast.LENGTH_LONG).show()
        }
        findViewById<Button>(R.id.btnPermissions).setOnClickListener {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        }

        val apiKey = findViewById<EditText>(R.id.apiKey)
        if (settings.apiKey.isNotBlank()) apiKey.setText(settings.apiKey)
        findViewById<Button>(R.id.btnSaveKey).setOnClickListener {
            settings.apiKey = apiKey.text.toString()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            refreshStatus()
        }

        val confirmAll = findViewById<CheckBox>(R.id.confirmEverything)
        confirmAll.isChecked = settings.confirmEverything
        confirmAll.setOnCheckedChangeListener { _, checked -> settings.confirmEverything = checked }

        val command = findViewById<EditText>(R.id.command)
        findViewById<Button>(R.id.btnRun).setOnClickListener {
            val text = command.text.toString().trim()
            val service = AgentAccessibilityService.instance
            when {
                service == null -> Toast.makeText(this, "Enable the accessibility service first", Toast.LENGTH_LONG).show()
                text.isEmpty() -> Toast.makeText(this, "Type a command", Toast.LENGTH_SHORT).show()
                else -> {
                    service.session.runGoal(text)
                    moveTaskToBack(true)
                }
            }
        }
        findViewById<Button>(R.id.btnForget).setOnClickListener {
            AgentAccessibilityService.instance?.session?.routines?.clear()
            Toast.makeText(this, "Saved routines cleared", Toast.LENGTH_SHORT).show()
            refreshStatus()
        }
        findViewById<Button>(R.id.btnVoice).setOnClickListener {
            val service = AgentAccessibilityService.instance
            if (service == null) {
                Toast.makeText(this, "Enable the accessibility service first", Toast.LENGTH_LONG).show()
            } else {
                service.session.startVoiceCommand()
                moveTaskToBack(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // We are in the foreground now, so the service is allowed to become a microphone foreground service.
        AgentAccessibilityService.instance?.ensureForeground()
        refreshStatus()
        scope.launch { refreshNanoStatus() }
    }

    override fun onDestroy() {
        scope.cancel()
        nano.close()
        super.onDestroy()
    }

    private suspend fun refreshNanoStatus() {
        val s = nano.status()
        nanoStatus.text = "On-device AI: " + nano.describe(s) +
            if (s == com.google.mlkit.genai.common.FeatureStatus.DOWNLOADABLE) ". Tap the button below to download it." else ""
    }

    private fun refreshStatus() {
        val service = AgentAccessibilityService.instance != null
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val key = settings.apiKey.isNotBlank()
        status.text = buildString {
            append(if (service) "✅ Accessibility service is on\n" else "❌ Accessibility service is off\n")
            append(if (mic) "✅ Microphone permission granted\n" else "❌ Microphone permission missing\n")
            append(if (key) "✅ API key saved" else "❌ No API key")
        }
    }
}
