package com.wildwildyeast.voiceagent.core

/** Everything the planner can ask the phone to do. */
sealed class AgentAction {
    abstract fun describe(): String

    data class Tap(val nodeId: Int) : AgentAction() {
        override fun describe() = "tap element $nodeId"
    }

    data class LongPress(val nodeId: Int) : AgentAction() {
        override fun describe() = "long-press element $nodeId"
    }

    data class TapAt(val x: Int, val y: Int) : AgentAction() {
        override fun describe() = "tap at ($x, $y)"
    }

    data class TypeText(val nodeId: Int?, val text: String, val submit: Boolean) : AgentAction() {
        override fun describe() = "type \"$text\"" + (nodeId?.let { " into element $it" } ?: "") + (if (submit) " and press enter" else "")
    }

    data class Scroll(val direction: Direction, val nodeId: Int?) : AgentAction() {
        override fun describe() = "scroll ${direction.name.lowercase()}" + (nodeId?.let { " in element $it" } ?: "")
    }

    data class Press(val key: Key) : AgentAction() {
        override fun describe() = "press ${key.name.lowercase()}"
    }

    data class OpenApp(val appName: String) : AgentAction() {
        override fun describe() = "open app \"$appName\""
    }

    data class Wait(val seconds: Double) : AgentAction() {
        override fun describe() = "wait ${seconds}s"
    }

    object Screenshot : AgentAction() {
        override fun describe() = "take screenshot"
    }

    data class AskUser(val question: String) : AgentAction() {
        override fun describe() = "ask user: $question"
    }

    data class Finish(val summary: String, val success: Boolean) : AgentAction() {
        override fun describe() = (if (success) "done: " else "gave up: ") + summary
    }

    enum class Direction { UP, DOWN, LEFT, RIGHT }
    enum class Key { BACK, HOME, RECENTS, ENTER, NOTIFICATIONS }
}

data class ActionResult(val ok: Boolean, val message: String) {
    companion object {
        fun ok(message: String = "ok") = ActionResult(true, message)
        fun fail(message: String) = ActionResult(false, message)
    }
}
