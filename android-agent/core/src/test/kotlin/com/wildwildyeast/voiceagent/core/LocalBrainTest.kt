package com.wildwildyeast.voiceagent.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalBrainTest {
    private fun node(id: Int, text: String, editable: Boolean = false) = ScreenNode(
        id, "android.widget.Button", text, null, null, 0, id * 100, 500, id * 100 + 90, 1,
        clickable = true, longClickable = false, editable = editable, scrollable = false,
        checkable = false, checked = false, selected = false, focused = false, enabled = true, password = false,
    )

    @Test
    fun `parses a JSON action even when wrapped in prose`() {
        val a = LocalBrain.parse("Sure! Here you go:\n```json\n{\"action\":\"tap\",\"node_id\":4}\n```").getOrThrow()
        assertEquals(AgentAction.Tap(4), a)
        val b = LocalBrain.parse("{\"action\": \"type_text\", \"node_id\": 2, \"text\": \"hi {there}\", \"submit\": true}").getOrThrow()
        assertEquals(AgentAction.TypeText(2, "hi {there}", true), b)
        assertTrue(LocalBrain.parse("I don't know").isFailure)
    }

    @Test
    fun `a local brain drives the loop end to end and the routine is learned`() = runTest {
        val screens = listOf(
            ScreenState("launcher", "Home", 1080, 2400, listOf(node(1, "Calculator"))),
            ScreenState("com.calc", "Calculator", 1080, 2400, listOf(node(1, "7"), node(2, "8"), node(3, "="))),
            ScreenState("com.calc", "Calculator", 1080, 2400, listOf(node(1, "56"))),
        )
        var index = 0
        val device = object : DeviceController {
            val performed = mutableListOf<AgentAction>()
            override suspend fun capture(withScreenshot: Boolean) = screens[index.coerceAtMost(screens.size - 1)]
            override suspend fun perform(action: AgentAction): ActionResult { performed += action; index++; return ActionResult.ok() }
            override suspend fun askUser(question: String) = "yes"
            override suspend fun confirm(question: String) = true
            override fun installedAppNames() = listOf("Calculator", "Gmail")
        }
        val replies = ArrayDeque(
            listOf(
                "{\"action\":\"open_app\",\"app_name\":\"Calculator\"}",
                "{\"action\":\"tap\",\"node_id\":3}",
                "{\"action\":\"finish\",\"success\":true,\"summary\":\"The answer is 56.\"}",
            ),
        )
        val prompts = mutableListOf<String>()
        val model = object : LocalTextModel {
            override val name = "fake"
            override suspend fun generate(system: String, prompt: String): String { prompts += prompt; return replies.removeFirst() }
        }
        val store = InMemoryRoutineStore()
        val result = Assistant({ LocalBrain(model) }, device, store).run("open calculator and press equals")
        assertTrue(result.outcome.success)
        assertEquals("The answer is 56.", result.outcome.summary)
        assertTrue(result.saved)
        assertEquals(listOf(StepKind.OPEN_APP, StepKind.TAP), store.all().single().steps.map { it.kind })
        assertTrue(prompts.last().contains("## Current screen"))
        assertTrue(prompts.last().contains("1. open app \"Calculator\" -> Action done: ok"))
        assertTrue(prompts.last().length < 9000)
    }
}
