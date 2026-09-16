package com.wildwildyeast.voiceagent.core

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineTest {
    private fun node(
        id: Int, text: String?, resId: String? = null, cls: String = "android.widget.Button",
        top: Int = id * 100, editable: Boolean = false, focused: Boolean = false,
    ) = ScreenNode(
        id = id, className = cls, text = text, contentDescription = null, resourceId = resId,
        left = 0, top = top, right = 500, bottom = top + 90, depth = 1,
        clickable = true, longClickable = false, editable = editable, scrollable = false,
        checkable = false, checked = false, selected = false, focused = focused, enabled = true, password = false,
    )

    private fun screen(pkg: String, vararg nodes: ScreenNode) =
        ScreenState(pkg, pkg, 1080, 2400, nodes.toList())

    @Test
    fun `commands normalize and match loosely`() {
        assertEquals("book uber from home airport", Routine.normalize("Please book an Uber from home to the airport!"))
        val a = Routine.normalize("book an uber to the airport")
        val b = Routine.normalize("book uber to airport")
        assertEquals(1.0, Routine.similarity(a, b))
        assertTrue(Routine.similarity(Routine.normalize("open gmail"), Routine.normalize("open whatsapp")) < 0.8)
    }

    @Test
    fun `store finds exact and similar commands and survives a round trip`() {
        val file = File.createTempFile("routines", ".json").apply { delete() }
        val store = JsonFileRoutineStore(file)
        val routine = Routine("Open Gmail", Routine.normalize("Open Gmail"), listOf(RoutineStep(StepKind.OPEN_APP, appName = "Gmail")), "Opened Gmail.", 1L)
        store.save(routine)
        assertNotNull(store.find("open gmail please"))
        assertNull(store.find("open whatsapp"))
        val reloaded = JsonFileRoutineStore(file)
        assertEquals(routine, reloaded.all().single())
        reloaded.clear()
        assertTrue(JsonFileRoutineStore(file).all().isEmpty())
    }

    @Test
    fun `elements are re-found by id or label even when they move, and rejected otherwise`() {
        val recordedOn = screen("com.ubercab", node(1, "Where to?", resId = "com.ubercab:id/dest", editable = true))
        val target = TargetRef.of(recordedOn.nodes[0], recordedOn)

        val moved = screen("com.ubercab", node(7, "Where to?", resId = "com.ubercab:id/dest", top = 900, editable = true))
        assertEquals(7, RoutineReplayer.findNode(target, moved)?.id)

        val relabelled = screen("com.ubercab", node(3, "Enter destination", resId = "com.ubercab:id/dest", editable = true))
        assertEquals(3, RoutineReplayer.findNode(target, relabelled)?.id)

        val different = screen("com.ubercab", node(4, "Ride history", resId = "com.ubercab:id/history"))
        assertNull(RoutineReplayer.findNode(target, different))
    }

    @Test
    fun `replay performs recorded steps and stops at the first stuck step`() = runTest {
        val device = FakeDevice(
            screens = listOf(
                screen("launcher", node(1, "Uber")),
                screen("com.ubercab", node(1, "Where to?", editable = true)),
                screen("com.ubercab", node(1, "Airport, Terminal 1")),
                screen("com.ubercab", node(1, "Something unexpected")),
            ),
        )
        val recorder = RoutineRecorder()
        recorder.record(AgentAction.OpenApp("Uber"), device.screens[0])
        recorder.record(AgentAction.TypeText(1, "Airport", false), device.screens[1])
        recorder.record(AgentAction.Tap(1), device.screens[2])
        recorder.record(AgentAction.Tap(1), device.screens[2].copy(nodes = listOf(node(1, "Confirm UberGo"))))
        assertEquals(4, recorder.steps.size)

        val routine = Routine("book uber to airport", "book uber airport", recorder.steps, "Booked.", 0L)
        val result = RoutineReplayer(device, SafetyGate(), findTimeoutMs = 10).replay(routine)
        assertEquals(3, result.completedSteps)
        assertFalse(result.finished)
        assertEquals(StepKind.TAP, result.failedStep?.kind)
        assertEquals(listOf("open app \"Uber\"", "type \"Airport\" into element 1", "tap element 1"), device.performed.map { it.describe() })
        assertTrue(device.confirmations.isEmpty(), "no risky tap reached the gate")
    }

    /** A device whose screen advances every time an action is performed. */
    private class FakeDevice(val screens: List<ScreenState>) : DeviceController {
        val performed = mutableListOf<AgentAction>()
        val confirmations = mutableListOf<String>()
        private var index = 0
        override suspend fun capture(withScreenshot: Boolean) = screens[index.coerceAtMost(screens.size - 1)]
        override suspend fun perform(action: AgentAction): ActionResult { performed += action; index++; return ActionResult.ok() }
        override suspend fun askUser(question: String) = "yes"
        override suspend fun confirm(question: String): Boolean { confirmations += question; return true }
        override fun installedAppNames() = listOf("Uber")
    }
}
