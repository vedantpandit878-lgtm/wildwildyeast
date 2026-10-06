package com.wildwildyeast.voiceagent.core

/**
 * Turns a recorded routine into a pattern. If the AI typed "I'm leaving" and
 * the command was "message Mum I'm leaving", the routine becomes
 * "message Mum {s1}" and "message Mum I'll be late" replays it with new text.
 * The same works for tapped labels such as contact names.
 */
object Slots {
    private val MARKER = Regex("^\\{s(\\d+)}$")

    /** Generic UI labels that must never become slots. */
    private val STOP = setOf(
        "ok", "okay", "yes", "no", "send", "search", "next", "done", "back", "cancel", "open", "close",
        "menu", "more", "home", "new", "add", "call", "chat", "chats", "message", "messages", "compose",
        "continue", "allow", "deny", "skip", "save", "edit", "delete", "share", "settings", "the", "and",
    )

    /** Only letters, digits, apostrophes and single spaces; case preserved. */
    fun clean(s: String): String = s.replace(Regex("[^\\p{L}\\p{N}'\\s]"), " ").trim().replace(Regex("\\s+"), " ")

    /** Returns the template (null if nothing was slotted) and the steps with slot numbers filled in. */
    fun parameterize(command: String, steps: List<RoutineStep>): Pair<String?, List<RoutineStep>> {
        val tokens = clean(command).split(' ').filter { it.isNotEmpty() }.toMutableList()
        if (tokens.size < 2) return null to steps
        val out = steps.toMutableList()
        val slotOf = LinkedHashMap<String, Int>() // lowercase value -> slot number

        fun candidates(): List<Pair<Int, String>> = steps.mapIndexedNotNull { i, step ->
            when (step.kind) {
                StepKind.TYPE_TEXT -> step.text?.takeIf { clean(it).length >= 2 }?.let { i to it }
                StepKind.TAP, StepKind.LONG_PRESS, StepKind.TAP_AT -> step.target?.label()
                    ?.takeIf { l -> clean(l).length >= 3 && l.any { it.isLetter() } && clean(l).lowercase() !in STOP }
                    ?.let { i to it }
                else -> null
            }
        }.sortedByDescending { clean(it.second).length }

        for ((index, value) in candidates()) {
            val key = clean(value).lowercase()
            val existing = slotOf[key]
            if (existing != null) {
                out[index] = out[index].copy(slot = existing)
                continue
            }
            val valueTokens = key.split(' ')
            val at = findSpan(tokens, valueTokens) ?: continue
            if (valueTokens.size >= tokens.size) continue // the whole command cannot be a slot
            val n = slotOf.size + 1
            slotOf[key] = n
            tokens[at] = "{s$n}"
            repeat(valueTokens.size - 1) { tokens.removeAt(at + 1) }
            out[index] = out[index].copy(slot = n)
        }
        if (slotOf.isEmpty()) return null to steps
        if (tokens.none { MARKER.matchEntire(it) == null }) return null to steps // template must keep a literal word
        return tokens.joinToString(" ") to out
    }

    /** Match a spoken command against a template; returns slot values, or null if it does not fit. */
    fun match(template: String, command: String): Map<Int, String>? {
        val parts = template.split(' ').filter { it.isNotEmpty() }
        val slotNumbers = mutableListOf<Int>()
        val pattern = parts.joinToString("\\s+") { part ->
            val m = MARKER.matchEntire(part)
            if (m != null) { slotNumbers += m.groupValues[1].toInt(); "(.+?)" } else Regex.escape(part)
        }
        val regex = Regex("^$pattern$", RegexOption.IGNORE_CASE)
        val m = regex.matchEntire(clean(command)) ?: return null
        return slotNumbers.mapIndexed { i, n -> n to m.groupValues[i + 1].trim() }.toMap()
    }

    /** Apply slot values to a step so the replayer looks for the new text or label. */
    fun apply(step: RoutineStep, slots: Map<Int, String>): RoutineStep {
        val value = step.slot?.let { slots[it] } ?: return step
        return when (step.kind) {
            StepKind.TYPE_TEXT -> step.copy(text = value)
            StepKind.TAP, StepKind.LONG_PRESS, StepKind.TAP_AT ->
                step.copy(target = step.target?.copy(text = value, contentDescription = null, resourceId = null))
            else -> step
        }
    }

    private fun findSpan(tokens: List<String>, value: List<String>): Int? {
        if (value.isEmpty() || value.size > tokens.size) return null
        outer@ for (start in 0..tokens.size - value.size) {
            for (j in value.indices) {
                if (!tokens[start + j].equals(value[j], ignoreCase = true)) continue@outer
            }
            return start
        }
        return null
    }
}
