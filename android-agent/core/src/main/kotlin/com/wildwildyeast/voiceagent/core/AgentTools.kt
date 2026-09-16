package com.wildwildyeast.voiceagent.core

import com.anthropic.core.JsonValue
import com.anthropic.models.beta.messages.BetaTool

/** Tool definitions the model sees, and the parser that turns its calls into [AgentAction]s. */
object AgentTools {
    const val TAP = "tap"
    const val LONG_PRESS = "long_press"
    const val TAP_AT = "tap_at"
    const val TYPE_TEXT = "type_text"
    const val SCROLL = "scroll"
    const val PRESS = "press"
    const val OPEN_APP = "open_app"
    const val WAIT = "wait"
    const val SCREENSHOT = "screenshot"
    const val ASK_USER = "ask_user"
    const val FINISH = "finish"

    fun definitions(): List<BetaTool> = listOf(
        tool(
            TAP, "Tap an element by the id shown in square brackets in the screen listing. Prefer this over tap_at.",
            mapOf("node_id" to prop("integer", "Element id from the screen listing")), listOf("node_id"),
        ),
        tool(
            LONG_PRESS, "Long-press an element by id (context menus, selection).",
            mapOf("node_id" to prop("integer", "Element id from the screen listing")), listOf("node_id"),
        ),
        tool(
            TAP_AT, "Tap at absolute screen pixel coordinates. Only use when the target is not in the element listing, e.g. after looking at a screenshot.",
            mapOf("x" to prop("integer", "X pixel"), "y" to prop("integer", "Y pixel")), listOf("x", "y"),
        ),
        tool(
            TYPE_TEXT, "Type text into an editable element. Replaces the field's current content. Set submit=true to press the keyboard's enter/search/send action afterwards.",
            mapOf(
                "node_id" to prop("integer", "Editable element id. Omit to type into the currently focused field."),
                "text" to prop("string", "Exact text to enter"),
                "submit" to prop("boolean", "Press enter/search after typing (default false)"),
            ), listOf("text"),
        ),
        tool(
            SCROLL, "Scroll the screen or a scrollable element. Direction is where the content should move: 'down' reveals content further down the page.",
            mapOf(
                "direction" to enumProp(listOf("up", "down", "left", "right"), "Scroll direction"),
                "node_id" to prop("integer", "Optional scrollable element id; defaults to the whole screen"),
            ), listOf("direction"),
        ),
        tool(
            PRESS, "Press a system key.",
            mapOf("key" to enumProp(listOf("back", "home", "recents", "enter", "notifications"), "Key to press")), listOf("key"),
        ),
        tool(
            OPEN_APP, "Launch an installed app by its name as it appears in the launcher.",
            mapOf("app_name" to prop("string", "App label, e.g. 'Uber', 'Gmail', 'WhatsApp'")), listOf("app_name"),
        ),
        tool(
            WAIT, "Wait for the screen to load or change.",
            mapOf("seconds" to prop("number", "Seconds to wait, 0.5 to 10")), listOf("seconds"),
        ),
        tool(
            SCREENSHOT, "Take a screenshot of the current screen. Use when the element listing is empty or unclear (web views, maps, games, images).",
            emptyMap(), emptyList(),
        ),
        tool(
            ASK_USER, "Ask the person a question and wait for their spoken answer. Use for missing details (which contact? which address?), for choices, and to get explicit approval before anything irreversible.",
            mapOf("question" to prop("string", "Short, specific question")), listOf("question"),
        ),
        tool(
            FINISH, "End the task. Call this when the goal is achieved, or when it cannot be achieved.",
            mapOf(
                "summary" to prop("string", "One or two sentences telling the person what happened, to be read aloud"),
                "success" to prop("boolean", "true if the goal was achieved"),
            ), listOf("summary", "success"),
        ),
    )

    /** Parse a tool call into an action. Returns a failure message instead of throwing so it can be sent back to the model. */
    fun parse(name: String, input: JsonValue): Result<AgentAction> = runCatching {
        val args: Map<String, JsonValue> = input.asObject().orElse(emptyMap())
        fun str(key: String): String? = args[key]?.asString()?.orElse(null)
        fun int(key: String): Int? = args[key]?.asNumber()?.orElse(null)?.toInt()
            ?: str(key)?.trim()?.toIntOrNull()
        fun dbl(key: String): Double? = args[key]?.asNumber()?.orElse(null)?.toDouble()
            ?: str(key)?.trim()?.toDoubleOrNull()
        fun bool(key: String): Boolean? = args[key]?.asBoolean()?.orElse(null)
            ?: str(key)?.trim()?.lowercase()?.let { if (it == "true") true else if (it == "false") false else null }
        fun require(key: String): String = str(key) ?: error("missing '$key'")

        when (name) {
            TAP -> AgentAction.Tap(int("node_id") ?: error("missing 'node_id'"))
            LONG_PRESS -> AgentAction.LongPress(int("node_id") ?: error("missing 'node_id'"))
            TAP_AT -> AgentAction.TapAt(int("x") ?: error("missing 'x'"), int("y") ?: error("missing 'y'"))
            TYPE_TEXT -> AgentAction.TypeText(int("node_id"), require("text"), bool("submit") ?: false)
            SCROLL -> AgentAction.Scroll(
                AgentAction.Direction.valueOf(require("direction").trim().uppercase()),
                int("node_id"),
            )
            PRESS -> AgentAction.Press(AgentAction.Key.valueOf(require("key").trim().uppercase()))
            OPEN_APP -> AgentAction.OpenApp(require("app_name"))
            WAIT -> AgentAction.Wait((dbl("seconds") ?: 1.0).coerceIn(0.2, 10.0))
            SCREENSHOT -> AgentAction.Screenshot
            ASK_USER -> AgentAction.AskUser(require("question"))
            FINISH -> AgentAction.Finish(require("summary"), bool("success") ?: true)
            else -> error("unknown tool '$name'")
        }
    }

    private fun prop(type: String, description: String): JsonValue =
        JsonValue.from(mapOf("type" to type, "description" to description))

    private fun enumProp(values: List<String>, description: String): JsonValue =
        JsonValue.from(mapOf("type" to "string", "enum" to values, "description" to description))

    private fun tool(name: String, description: String, props: Map<String, JsonValue>, required: List<String>): BetaTool {
        val properties = BetaTool.InputSchema.Properties.builder()
        props.forEach { (k, v) -> properties.putAdditionalProperty(k, v) }
        return BetaTool.builder()
            .name(name)
            .description(description)
            .inputSchema(
                BetaTool.InputSchema.builder()
                    .properties(properties.build())
                    .required(required)
                    .build(),
            )
            .build()
    }
}
