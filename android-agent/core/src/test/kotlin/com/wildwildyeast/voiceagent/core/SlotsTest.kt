package com.wildwildyeast.voiceagent.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SlotsTest {
    private fun target(label: String, pkg: String = "com.whatsapp") =
        TargetRef(pkg, "android.widget.TextView", label, null, "com.whatsapp:id/name", 100, 200, 1080, 2400)

    private val steps = listOf(
        RoutineStep(StepKind.OPEN_APP, "launcher", appName = "WhatsApp"),
        RoutineStep(StepKind.TAP, "com.whatsapp", target = target("Mum")),
        RoutineStep(StepKind.TYPE_TEXT, "com.whatsapp", target = target("Message", "com.whatsapp"), text = "I'm leaving now"),
        RoutineStep(StepKind.TAP, "com.whatsapp", target = target("Send")),
    )

    @Test
    fun `typed text and tapped names become slots`() {
        val (template, slotted) = Slots.parameterize("Message Mum I'm leaving now", steps)
        assertEquals("Message {s2} {s1}", template)
        assertEquals(1, slotted[2].slot)
        assertEquals(2, slotted[1].slot)
        assertNull(slotted[3].slot, "generic 'Send' label must not become a slot")

        val values = Slots.match(template!!, "message Dad I'll be late, sorry")
        assertEquals(mapOf(2 to "Dad", 1 to "I'll be late sorry"), values)
        assertNull(Slots.match(template, "call Mum"))

        val applied = Slots.apply(slotted[2], values!!)
        assertEquals("I'll be late sorry", applied.text)
        val tapped = Slots.apply(slotted[1], values)
        assertEquals("Dad", tapped.target?.text)
        assertNull(tapped.target?.resourceId, "a slotted tap must match by label, not by shared list ids")
    }

    @Test
    fun `commands with nothing variable get no template`() {
        val (template, same) = Slots.parameterize("open gmail", listOf(RoutineStep(StepKind.OPEN_APP, appName = "Gmail")))
        assertNull(template)
        assertEquals(1, same.size)
    }

    @Test
    fun `store prefers template matches and fills slots`() {
        val store = InMemoryRoutineStore()
        val (template, slotted) = Slots.parameterize("message Mum I'm leaving now", steps)
        store.save(Routine("message Mum I'm leaving now", Routine.normalize("message Mum I'm leaving now"), slotted, "Sent.", 0L, template = template))
        val match = store.find("Message Mum see you at 6")
        assertEquals(mapOf(2 to "Mum", 1 to "see you at 6"), match?.slots)
        assertNull(store.find("open gmail"))
    }
}
