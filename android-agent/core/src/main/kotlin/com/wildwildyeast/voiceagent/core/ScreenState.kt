package com.wildwildyeast.voiceagent.core

/** One interactive or labelled element on the current screen. */
data class ScreenNode(
    val id: Int,
    val className: String,
    val text: String?,
    val contentDescription: String?,
    val resourceId: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val depth: Int,
    val clickable: Boolean,
    val longClickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val checkable: Boolean,
    val checked: Boolean,
    val selected: Boolean,
    val focused: Boolean,
    val enabled: Boolean,
    val password: Boolean,
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom

    /** Text a human would use to refer to this element. */
    fun label(): String = listOfNotNull(text, contentDescription).firstOrNull { it.isNotBlank() } ?: ""

    fun toPromptLine(): String {
        val sb = StringBuilder()
        sb.append("  ".repeat(depth.coerceAtMost(8)))
        sb.append('[').append(id).append("] ")
        sb.append(className.substringAfterLast('.'))
        text?.takeIf { it.isNotBlank() }?.let { sb.append(" \"").append(it.take(120)).append('"') }
        contentDescription?.takeIf { it.isNotBlank() && it != text }
            ?.let { sb.append(" desc=\"").append(it.take(80)).append('"') }
        resourceId?.takeIf { it.isNotBlank() }?.let { sb.append(" id=").append(it.substringAfter(":id/")) }
        sb.append(" (").append(left).append(',').append(top).append(',').append(right).append(',').append(bottom).append(')')
        val flags = buildList {
            if (clickable) add("clickable")
            if (longClickable) add("long-clickable")
            if (editable) add("editable")
            if (password) add("password")
            if (scrollable) add("scrollable")
            if (checkable) add(if (checked) "checked" else "unchecked")
            if (selected) add("selected")
            if (focused) add("focused")
            if (!enabled) add("disabled")
        }
        if (flags.isNotEmpty()) sb.append(' ').append(flags.joinToString(","))
        return sb.toString()
    }
}

/** A snapshot of what is on the phone screen right now. */
data class ScreenState(
    val packageName: String,
    val appLabel: String,
    val width: Int,
    val height: Int,
    val nodes: List<ScreenNode>,
    /** PNG bytes of a (downscaled) screenshot, if one was taken. */
    val screenshotPng: ByteArray? = null,
    val keyboardVisible: Boolean = false,
    val note: String? = null,
) {
    fun node(id: Int): ScreenNode? = nodes.firstOrNull { it.id == id }

    /** Deepest node whose bounds contain the point, preferring ones with a label. */
    fun nodeAt(x: Int, y: Int): ScreenNode? {
        val hits = nodes.filter { it.contains(x, y) }
        return hits.filter { it.label().isNotBlank() }.maxByOrNull { it.depth } ?: hits.maxByOrNull { it.depth }
    }

    fun toPromptText(maxNodes: Int = 160): String {
        val sb = StringBuilder()
        sb.append("app: ").append(appLabel).append(" (").append(packageName).append(")\n")
        sb.append("screen size: ").append(width).append("x").append(height)
        if (keyboardVisible) sb.append(", keyboard visible")
        sb.append('\n')
        note?.let { sb.append("note: ").append(it).append('\n') }
        if (nodes.isEmpty()) {
            sb.append("elements: none reported by the accessibility tree (use screenshot + tap_at)\n")
        } else {
            sb.append("elements (id, type, text, bounds l,t,r,b):\n")
            nodes.take(maxNodes).forEach { sb.append(it.toPromptLine()).append('\n') }
            if (nodes.size > maxNodes) sb.append("... ").append(nodes.size - maxNodes).append(" more elements not shown; scroll to reveal\n")
        }
        return sb.toString()
    }

    /** Cheap fingerprint used to detect when the screen has stopped changing. */
    fun fingerprint(): Int = nodes.fold(packageName.hashCode()) { acc, n ->
        acc * 31 + n.className.hashCode() + (n.text?.hashCode() ?: 0) + n.top
    }
}
