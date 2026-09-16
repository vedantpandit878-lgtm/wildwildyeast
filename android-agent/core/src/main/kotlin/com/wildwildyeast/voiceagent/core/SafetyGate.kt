package com.wildwildyeast.voiceagent.core

/**
 * Decides which actions must be confirmed by the person before they run, no
 * matter what the model says. This is the last line of defence against a
 * misread screen: anything that spends money, sends a message, or destroys
 * data goes through here.
 */
class SafetyGate(
    private val extraRiskyWords: List<String> = emptyList(),
    /** When true, every tap is confirmed. Useful while testing. */
    private val confirmEverything: Boolean = false,
) {
    private val riskyWords: List<String> = listOf(
        "send", "pay", "pay now", "confirm", "book", "order", "place order", "buy", "purchase",
        "checkout", "check out", "subscribe", "delete", "remove", "post", "publish", "submit",
        "transfer", "request", "reserve", "accept", "agree", "call", "share", "unsubscribe",
        "reply", "tweet", "upload", "sign", "apply", "donate", "tip", "cancel ride", "cancel order",
        "block", "report", "uninstall", "factory reset", "erase", "format",
    ) + extraRiskyWords

    /** Returns a question to ask the person, or null if the action is safe to run silently. */
    fun confirmationFor(action: AgentAction, screen: ScreenState?): String? {
        val target: ScreenNode? = when (action) {
            is AgentAction.Tap -> screen?.node(action.nodeId)
            is AgentAction.LongPress -> screen?.node(action.nodeId)
            is AgentAction.TapAt -> screen?.nodeAt(action.x, action.y)
            is AgentAction.TypeText -> if (action.submit) screen?.let { s -> action.nodeId?.let { s.node(it) } } else null
            else -> null
        }
        if (target == null && !(action is AgentAction.TypeText && action.submit)) {
            return if (confirmEverything && action.isTapLike()) "Tap ${action.describe()}?" else null
        }
        val label = target?.label().orEmpty()
        val risky = isRiskyLabel(label) || (action is AgentAction.TypeText && action.submit && target?.let { isRiskyLabel(it.label()) } == true)
        if (!risky && !confirmEverything) return null
        val appName = screen?.appLabel ?: "the current app"
        return when (action) {
            is AgentAction.TypeText -> "Send \"${action.text}\" in $appName?"
            else -> if (label.isBlank()) "Tap the unlabeled element at (${target?.centerX}, ${target?.centerY}) in $appName?"
            else "Tap \"$label\" in $appName?"
        }
    }

    fun isRiskyLabel(label: String): Boolean {
        val l = label.trim().lowercase()
        if (l.isEmpty()) return false
        val words = l.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        return riskyWords.any { risky ->
            if (risky.contains(' ')) l.contains(risky) else words.contains(risky)
        }
    }

    private fun AgentAction.isTapLike() = this is AgentAction.Tap || this is AgentAction.TapAt || this is AgentAction.LongPress
}
