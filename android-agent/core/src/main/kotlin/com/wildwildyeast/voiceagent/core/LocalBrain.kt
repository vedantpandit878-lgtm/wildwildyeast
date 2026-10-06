package com.wildwildyeast.voiceagent.core

import com.anthropic.core.JsonValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/** A small text model that runs on the phone itself. */
interface LocalTextModel {
    val name: String

    /** Generate a short completion. [system] is a short behavioural instruction, [prompt] the full request. */
    suspend fun generate(system: String, prompt: String): String
}

/**
 * Planner for small on-device models. Keeps the prompt short (one screen, a few
 * lines of history) and asks for exactly one JSON action per turn. Images are
 * not used.
 */
class LocalBrain(
    private val model: LocalTextModel,
    private val maxPromptChars: Int = 9000,
    private val maxScreenNodes: Int = 60,
) : Brain {
    override val name: String get() = model.name
    override val acceptsImages: Boolean = false

    private var goal = ""
    private var context: String? = null
    private var apps: List<String> = emptyList()
    private var screen: ScreenState? = null
    private val history = ArrayDeque<String>()
    private var lastAction: AgentAction? = null
    private var repeats = 0

    override suspend fun begin(goal: String, context: String?, apps: List<String>, screen: ScreenState) {
        this.goal = goal
        this.context = context
        this.apps = apps
        this.screen = screen
        history.clear()
        lastAction = null
        repeats = 0
    }

    override suspend fun next(): BrainTurn {
        val raw = model.generate(SYSTEM, buildPrompt())
        val parsed = parse(raw)
        parsed.getOrNull()?.let { action ->
            if (action == lastAction) repeats++ else repeats = 0
            lastAction = action
        }
        return BrainTurn(listOf(parsed), text = null)
    }

    override suspend fun report(observations: List<Observation>) {
        observations.forEach { obs ->
            obs.screen?.let { screen = it }
            val line = obs.text.lineSequence().firstOrNull()?.take(140) ?: ""
            history.addLast("${history.size + 1}. ${lastAction?.describe() ?: "note"} -> $line")
            while (history.size > 6) history.removeFirst()
        }
    }

    fun buildPrompt(): String {
        val s = screen
        val sb = StringBuilder()
        sb.append("## Task\n\"").append(goal).append("\"\n")
        context?.takeIf { it.isNotBlank() }?.let { sb.append(it.trim()).append('\n') }
        sb.append("\n## Apps you can open\n").append(relevantApps().joinToString(", ")).append('\n')
        sb.append("\n## Done so far\n")
        if (history.isEmpty()) sb.append("nothing yet\n") else history.forEach { sb.append(it).append('\n') }
        if (repeats >= 1) sb.append("The last action did not change the screen. Choose a different action.\n")
        sb.append("\n## Current screen\n")
        if (s == null) sb.append("unknown\n") else sb.append(compactScreen(s, maxScreenNodes))
        sb.append("\n## Reply with exactly one JSON object from this list, nothing else\n")
        sb.append(ACTION_MENU)
        var prompt = sb.toString()
        if (prompt.length > maxPromptChars && s != null) {
            val fewer = maxOf(12, maxScreenNodes - (prompt.length - maxPromptChars) / 45)
            prompt = prompt.replace(compactScreen(s, maxScreenNodes), compactScreen(s, fewer))
        }
        return prompt
    }

    private fun relevantApps(): List<String> {
        val words = goal.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()
        val mentioned = apps.filter { app -> words.any { w -> app.lowercase().contains(w) || w.contains(app.lowercase()) } }
        return (mentioned + apps.take(30)).distinct().take(40)
    }

    companion object {
        val SYSTEM = """
You control an Android phone for its owner. Each turn you see the task, what was done so far, and the current screen as numbered elements. Pick the single best next action and reply with one JSON object only, no other text.
Rules: open the needed app first. Use element ids from the screen. After typing into a search or address box, tap the matching suggestion. Never repeat an action that did nothing; scroll or go back instead. Before sending, paying, booking, ordering or deleting, use ask_user and wait for a yes. Never type passwords or codes. When the task is done, or impossible, use finish with a one-sentence summary.
""".trimIndent()

        val ACTION_MENU = """
{"action":"open_app","app_name":"Gmail"}
{"action":"tap","node_id":3}
{"action":"long_press","node_id":3}
{"action":"type_text","node_id":3,"text":"hello","submit":false}
{"action":"scroll","direction":"down"}
{"action":"press","key":"back"}
{"action":"wait","seconds":2}
{"action":"ask_user","question":"Which contact?"}
{"action":"finish","success":true,"summary":"Sent the message."}
""".trimIndent() + "\n"

        private val mapper = jacksonObjectMapper()

        fun compactScreen(s: ScreenState, maxNodes: Int): String = buildString {
            append("app: ").append(s.appLabel)
            if (s.keyboardVisible) append(" (keyboard open)")
            append('\n')
            if (s.nodes.isEmpty()) append("no elements\n")
            s.nodes.take(maxNodes).forEach { n ->
                append('[').append(n.id).append("] ").append(shortClass(n.className))
                val label = n.label()
                if (label.isNotBlank()) append(" \"").append(label.take(60)).append('"')
                val flags = buildList {
                    if (n.editable) add("editable")
                    if (n.scrollable) add("scrollable")
                    if (n.checkable) add(if (n.checked) "checked" else "unchecked")
                    if (n.selected) add("selected")
                    if (!n.enabled) add("disabled")
                }
                if (flags.isNotEmpty()) append(' ').append(flags.joinToString(","))
                append('\n')
            }
            if (s.nodes.size > maxNodes) append("... ").append(s.nodes.size - maxNodes).append(" more below, scroll to see\n")
        }

        private fun shortClass(className: String): String = when (val c = className.substringAfterLast('.')) {
            "Button", "ImageButton" -> "Btn"
            "TextView" -> "Text"
            "EditText", "AutoCompleteTextView" -> "Edit"
            "ImageView" -> "Img"
            "CheckBox", "Switch" -> "Check"
            "RecyclerView", "ListView", "ScrollView" -> "List"
            else -> c.take(12)
        }

        /** Extract the first JSON object from the model's text and turn it into an action. */
        fun parse(raw: String): Result<AgentAction> {
            val json = firstJsonObject(raw) ?: return Result.failure(IllegalArgumentException("reply contained no JSON object"))
            return runCatching {
                val map: Map<String, Any?> = mapper.readValue(json)
                val name = map["action"]?.toString() ?: map["tool"]?.toString() ?: error("missing \"action\"")
                val args = map.filterKeys { it != "action" && it != "tool" }
                AgentTools.parse(name, JsonValue.from(args)).getOrThrow()
            }
        }

        private fun firstJsonObject(text: String): String? {
            val start = text.indexOf('{')
            if (start < 0) return null
            var depth = 0
            var inString = false
            var escaped = false
            for (i in start until text.length) {
                val c = text[i]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        c == '\\' -> escaped = true
                        c == '"' -> inString = false
                    }
                } else when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
                }
            }
            return null
        }
    }
}
