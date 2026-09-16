package com.wildwildyeast.voiceagent.core

import com.anthropic.core.JsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTest {
    private fun node(id: Int, text: String?, clickable: Boolean = true, editable: Boolean = false) = ScreenNode(
        id = id, className = "android.widget.Button", text = text, contentDescription = null, resourceId = null,
        left = 0, top = id * 100, right = 500, bottom = id * 100 + 90, depth = 1,
        clickable = clickable, longClickable = false, editable = editable, scrollable = false,
        checkable = false, checked = false, selected = false, focused = false, enabled = true, password = false,
    )

    private val screen = ScreenState(
        packageName = "com.ubercab", appLabel = "Uber", width = 1080, height = 2400,
        nodes = listOf(node(1, "Where to?", editable = true), node(2, "Confirm UberGo"), node(3, "Settings")),
    )

    @Test
    fun `tool definitions cover every action`() {
        val names = AgentTools.definitions().map { it.name() }.toSet()
        assertEquals(
            setOf("tap", "long_press", "tap_at", "type_text", "scroll", "press", "open_app", "wait", "screenshot", "ask_user", "finish"),
            names,
        )
    }

    @Test
    fun `parses tool inputs including string-typed numbers and booleans`() {
        assertEquals(AgentAction.Tap(2), AgentTools.parse("tap", JsonValue.from(mapOf("node_id" to 2))).getOrThrow())
        assertEquals(AgentAction.Tap(7), AgentTools.parse("tap", JsonValue.from(mapOf("node_id" to "7"))).getOrThrow())
        assertEquals(
            AgentAction.TypeText(1, "Airport", true),
            AgentTools.parse("type_text", JsonValue.from(mapOf("node_id" to 1, "text" to "Airport", "submit" to true))).getOrThrow(),
        )
        assertEquals(
            AgentAction.Scroll(AgentAction.Direction.DOWN, null),
            AgentTools.parse("scroll", JsonValue.from(mapOf("direction" to "down"))).getOrThrow(),
        )
        assertEquals(AgentAction.Press(AgentAction.Key.BACK), AgentTools.parse("press", JsonValue.from(mapOf("key" to "back"))).getOrThrow())
        assertEquals(AgentAction.Wait(2.5), AgentTools.parse("wait", JsonValue.from(mapOf("seconds" to 2.5))).getOrThrow())
        assertEquals(AgentAction.Screenshot, AgentTools.parse("screenshot", JsonValue.from(emptyMap<String, Any>())).getOrThrow())
        assertEquals(
            AgentAction.Finish("Booked.", true),
            AgentTools.parse("finish", JsonValue.from(mapOf("summary" to "Booked.", "success" to true))).getOrThrow(),
        )
        assertTrue(AgentTools.parse("tap", JsonValue.from(emptyMap<String, Any>())).isFailure)
        assertTrue(AgentTools.parse("bogus", JsonValue.from(emptyMap<String, Any>())).isFailure)
    }

    @Test
    fun `safety gate confirms risky taps and lets safe ones through`() {
        val gate = SafetyGate()
        assertNotNull(gate.confirmationFor(AgentAction.Tap(2), screen))
        assertNull(gate.confirmationFor(AgentAction.Tap(3), screen))
        assertNull(gate.confirmationFor(AgentAction.Scroll(AgentAction.Direction.DOWN, null), screen))
        // tap_at resolves to the element under the point
        assertNotNull(gate.confirmationFor(AgentAction.TapAt(250, 245), screen))
        assertNull(gate.confirmationFor(AgentAction.TapAt(250, 345), screen))
        assertTrue(gate.isRiskyLabel("Place order"))
        assertTrue(gate.isRiskyLabel("SEND"))
        assertTrue(!gate.isRiskyLabel("Sender name"))
        assertNotNull(SafetyGate(confirmEverything = true).confirmationFor(AgentAction.Tap(3), screen))
    }

    @Test
    fun `screen renders compactly for the prompt`() {
        val text = screen.toPromptText()
        assertTrue(text.contains("app: Uber (com.ubercab)"))
        assertTrue(text.contains("[1] Button \"Where to?\" (0,100,500,190) clickable,editable"))
        assertTrue(text.contains("[2] Button \"Confirm UberGo\""))
    }
}
