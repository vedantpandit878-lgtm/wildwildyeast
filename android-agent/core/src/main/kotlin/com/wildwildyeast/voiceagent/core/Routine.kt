package com.wildwildyeast.voiceagent.core

/** Where an action was aimed, described so it can be found again on a live screen. */
data class TargetRef(
    val packageName: String,
    val className: String,
    val text: String?,
    val contentDescription: String?,
    val resourceId: String?,
    val centerX: Int,
    val centerY: Int,
    val screenWidth: Int,
    val screenHeight: Int,
) {
    fun label(): String = listOfNotNull(text, contentDescription).firstOrNull { it.isNotBlank() } ?: ""

    companion object {
        fun of(node: ScreenNode, screen: ScreenState) = TargetRef(
            packageName = screen.packageName,
            className = node.className,
            text = node.text,
            contentDescription = node.contentDescription,
            resourceId = node.resourceId,
            centerX = node.centerX,
            centerY = node.centerY,
            screenWidth = screen.width,
            screenHeight = screen.height,
        )
    }
}

enum class StepKind { TAP, LONG_PRESS, TAP_AT, TYPE_TEXT, SCROLL, PRESS, OPEN_APP, WAIT }

/** One recorded action. Only the fields relevant to [kind] are set. */
data class RoutineStep(
    val kind: StepKind,
    val packageName: String = "",
    val target: TargetRef? = null,
    val x: Int? = null,
    val y: Int? = null,
    val text: String? = null,
    val submit: Boolean? = null,
    val direction: String? = null,
    val key: String? = null,
    val appName: String? = null,
    val seconds: Double? = null,
) {
    fun describe(): String = when (kind) {
        StepKind.TAP -> "tap \"${target?.label()}\""
        StepKind.LONG_PRESS -> "long-press \"${target?.label()}\""
        StepKind.TAP_AT -> "tap at ($x, $y)"
        StepKind.TYPE_TEXT -> "type \"$text\""
        StepKind.SCROLL -> "scroll ${direction?.lowercase()}"
        StepKind.PRESS -> "press ${key?.lowercase()}"
        StepKind.OPEN_APP -> "open $appName"
        StepKind.WAIT -> "wait ${seconds}s"
    }
}

/** A spoken command and the steps that completed it last time. */
data class Routine(
    val command: String,
    val normalized: String,
    val steps: List<RoutineStep>,
    val summary: String,
    val createdAt: Long,
    val runs: Int = 0,
) {
    companion object {
        /** Lower-case, no punctuation, no filler words, single spaces. */
        fun normalize(command: String): String {
            val filler = setOf("please", "hey", "can", "you", "could", "would", "just", "the", "a", "an", "to", "for", "me", "my", "and", "then", "now")
            return command.lowercase()
                .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() && it !in filler }
                .joinToString(" ")
        }

        /** Token overlap between two normalized commands, 0..1. */
        fun similarity(a: String, b: String): Double {
            val ta = a.split(' ').filter { it.isNotEmpty() }.toSet()
            val tb = b.split(' ').filter { it.isNotEmpty() }.toSet()
            if (ta.isEmpty() || tb.isEmpty()) return 0.0
            return ta.intersect(tb).size.toDouble() / ta.union(tb).size
        }
    }
}
