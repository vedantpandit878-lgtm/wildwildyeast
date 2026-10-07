package com.wildwildyeast.voiceagent.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BuiltInTest {
    private fun p(s: String) = BuiltInCommands.parse(s)

    @Test
    fun `people commands`() {
        assertEquals(BuiltIn.Call("Mum"), p("Call Mum"))
        assertEquals(BuiltIn.Call("Ravi Kumar"), p("hey, phone Ravi Kumar on mobile"))
        assertNull(p("call an uber to the airport"))
        assertNull(p("call me a cab"))
        assertEquals(BuiltIn.Sms("Mum", "I'm leaving now"), p("text Mum I'm leaving now"))
        assertEquals(BuiltIn.Sms("Dad", "running late"), p("send a text to Dad saying running late"))
        assertEquals(BuiltIn.WhatsApp("Priya", "see you at 6"), p("WhatsApp Priya see you at 6"))
        assertEquals(BuiltIn.WhatsApp("Priya", "see you at 6"), p("message Priya on WhatsApp saying see you at 6"))
        assertEquals(BuiltIn.Message("Mum", "I'll be late"), p("message Mum I'll be late"))
        assertEquals(BuiltIn.Message("Mum", "I'll be late"), p("tell Mum that I'll be late"))
        assertEquals(BuiltIn.Email("Ravi", "the invoice", null), p("email Ravi about the invoice"))
        assertEquals(BuiltIn.Email("ravi@example.com", null, "please send the file"), p("email ravi@example.com saying please send the file"))
    }

    @Test
    fun `time and duration commands`() {
        assertEquals(BuiltIn.SetAlarm(6, 30, null), p("set an alarm for 6:30 am"))
        assertEquals(BuiltIn.SetAlarm(19, 0, null), p("set alarm for 7 pm"))
        assertEquals(BuiltIn.SetAlarm(7, 0, "Wake up"), p("wake me up at 7 in the morning"))
        assertEquals(BuiltIn.SetAlarm(6, 45, "gym"), p("set an alarm for quarter to seven called gym"))
        assertEquals(BuiltIn.SetTimer(600, null), p("set a timer for 10 minutes"))
        assertEquals(BuiltIn.SetTimer(5400, null), p("start a timer for an hour and a half"))
        assertEquals(BuiltIn.SetTimer(90, "eggs"), p("set a timer for ninety seconds called eggs"))
        assertEquals(BuiltIn.Reminder("call the dentist", 17, 0), p("remind me to call the dentist at 5 pm"))
        assertEquals(BuiltIn.CalendarEvent("Ravi", 15, 0, true), p("add a meeting with Ravi tomorrow at 3 pm"))
        assertEquals(15 to 30, BuiltInCommands.time("half past three in the afternoon"))
        assertEquals(0 to 0, BuiltInCommands.time("midnight"))
        assertEquals(25, BuiltInCommands.number("twenty five"))
    }

    @Test
    fun `navigation media web and toggles`() {
        assertEquals(BuiltIn.Navigate("the airport", null), p("navigate to the airport"))
        assertEquals(BuiltIn.Navigate("Phoenix Mall", "walking"), p("take me to Phoenix Mall by walking"))
        assertEquals(BuiltIn.ShowMap("the nearest pharmacy"), p("where is the nearest pharmacy"))
        assertEquals(BuiltIn.PlayMusic("Coldplay"), p("play some Coldplay on Spotify"))
        assertNull(p("play chess"))
        assertEquals(BuiltIn.WebSearch("best biryani near me"), p("search for best biryani near me"))
        assertEquals(BuiltIn.WebSearch("how tall is Everest"), p("google how tall is Everest"))
        assertEquals(BuiltIn.OpenUrl("https://bbc.com/news"), p("open bbc.com/news"))
        assertEquals(BuiltIn.Flashlight(true), p("turn on the flashlight"))
        assertEquals(BuiltIn.Flashlight(false), p("torch off"))
        assertEquals(BuiltIn.Wifi(false), p("turn off wifi"))
        assertEquals(BuiltIn.Bluetooth(true), p("switch bluetooth on"))
        assertEquals(BuiltIn.Volume(null, 1), p("volume up"))
        assertEquals(BuiltIn.Volume(null, 0), p("mute"))
        assertEquals(BuiltIn.Volume(50, 0), p("set the volume to fifty percent"))
    }

    @Test
    fun `system and questions, and things that must fall through to the agent`() {
        assertEquals(BuiltIn.OpenApp("Gmail"), p("open Gmail"))
        assertEquals(BuiltIn.OpenApp("WhatsApp"), p("launch the WhatsApp app"))
        assertNull(p("open Gmail and read me the newest subject"))
        assertNull(p("book an Uber from home to the airport"))
        assertNull(p("search gmail for invoices"))
        assertEquals(BuiltIn.System(BuiltIn.SystemKey.LOCK), p("lock the screen"))
        assertEquals(BuiltIn.System(BuiltIn.SystemKey.SCREENSHOT), p("take a screenshot"))
        assertEquals(BuiltIn.System(BuiltIn.SystemKey.HOME), p("go home"))
        assertEquals(BuiltIn.TellTime, p("what time is it"))
        assertEquals(BuiltIn.TellBattery, p("how much battery do I have"))
        assertEquals(BuiltIn.ReadScreen, p("read the screen"))
        assertEquals(BuiltIn.TakePhoto, p("take a selfie"))
        assertEquals(BuiltIn.Help, p("what can you do"))
    }
}
