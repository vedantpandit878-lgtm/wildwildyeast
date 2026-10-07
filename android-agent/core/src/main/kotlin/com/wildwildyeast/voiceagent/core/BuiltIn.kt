package com.wildwildyeast.voiceagent.core

/**
 * Commands the phone can do directly through Android, with no AI and no
 * screen reading: calls, messages, alarms, navigation, music, toggles. The
 * parser is deliberately conservative so anything ambiguous falls through to
 * routines and the AI.
 */
sealed class BuiltIn {
    data class Call(val who: String) : BuiltIn()
    data class Sms(val who: String, val text: String) : BuiltIn()
    data class WhatsApp(val who: String, val text: String) : BuiltIn()
    /** Plain "message X ..." : the app decides between WhatsApp and SMS. */
    data class Message(val who: String, val text: String) : BuiltIn()
    data class Email(val who: String?, val subject: String?, val body: String?) : BuiltIn()
    data class OpenApp(val app: String) : BuiltIn()
    data class SetAlarm(val hour: Int, val minute: Int, val label: String?) : BuiltIn()
    data class SetTimer(val seconds: Int, val label: String?) : BuiltIn()
    data class Reminder(val what: String, val hour: Int, val minute: Int) : BuiltIn()
    data class Navigate(val destination: String, val mode: String?) : BuiltIn()
    data class ShowMap(val place: String) : BuiltIn()
    data class PlayMusic(val query: String) : BuiltIn()
    data class WebSearch(val query: String) : BuiltIn()
    data class OpenUrl(val url: String) : BuiltIn()
    data class Flashlight(val on: Boolean) : BuiltIn()
    /** [level] 0..100, or null when [delta] is used: +1 up, -1 down, 0 mute. */
    data class Volume(val level: Int?, val delta: Int) : BuiltIn()
    data class Wifi(val on: Boolean) : BuiltIn()
    data class Bluetooth(val on: Boolean) : BuiltIn()
    data class CalendarEvent(val title: String, val hour: Int?, val minute: Int?, val tomorrow: Boolean) : BuiltIn()
    object TakePhoto : BuiltIn()
    object TellTime : BuiltIn()
    object TellDate : BuiltIn()
    object TellBattery : BuiltIn()
    object ReadScreen : BuiltIn()
    object Help : BuiltIn()
    enum class SystemKey { HOME, BACK, RECENTS, NOTIFICATIONS, LOCK, SCREENSHOT, SETTINGS }
    data class System(val key: SystemKey) : BuiltIn()
}

object BuiltInCommands {
    /** Short description of everything the phone can do without AI; also spoken for "what can you do". */
    val HELP: List<String> = listOf(
        "call someone", "text someone a message", "whatsapp someone a message", "email someone",
        "open an app", "set an alarm for a time", "set a timer for ten minutes", "remind me to do something at a time",
        "navigate to a place", "show a place on the map", "play a song or artist", "search the web for something",
        "turn the flashlight on or off", "volume up, down, mute, or set to a percentage", "turn wifi or bluetooth on or off",
        "add a meeting to the calendar", "take a photo", "what time is it", "what's the date", "battery level",
        "read the screen", "go home, go back, lock the screen, take a screenshot, open notifications, open settings",
    )

    private val UNITS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val TENS = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)

    /** Things that look like people in "call X" but are not. */
    private val NOT_A_PERSON = Regex("^(a|an|the|me|for|uber|ola|cab|taxi|it|them|back|this|that|that number|customer care)\\b", RegexOption.IGNORE_CASE)

    fun parse(raw: String): BuiltIn? {
        val text = raw.trim().replace(Regex("\\s+"), " ")
            .replace(Regex("^(hey|ok|okay|please|jarvis|can you|could you|would you|i want you to|i want to)[ ,]+", RegexOption.IGNORE_CASE), "")
            .trimEnd('.', '!', '?', ' ')
        if (text.isBlank()) return null
        val lower = text.lowercase()

        // ---- questions and system actions
        if (lower.matches(Regex("(what|which) (time|hour) is it( now)?|what's the time|tell me the time|time please"))) return BuiltIn.TellTime
        if (lower.matches(Regex("what('s| is) (the date|today's date|today)( today)?|what day is it( today)?|tell me the date"))) return BuiltIn.TellDate
        if (lower.matches(Regex("(what('s| is) (the |my )?battery( level| percentage)?|how much battery( is left| do i have)?|battery( level| status)?)"))) return BuiltIn.TellBattery
        if (lower.matches(Regex("(read|read me|read out|read aloud) (the |this |what's on the |whats on the )?(screen|page|this)|what('s| is) on (the|my) screen"))) return BuiltIn.ReadScreen
        if (lower.matches(Regex("(help|what can you do|what can i say|list (your )?commands|show (me )?commands)"))) return BuiltIn.Help
        if (lower.matches(Regex("(go|take me) (to )?(the )?home( screen)?|home screen"))) return BuiltIn.System(BuiltIn.SystemKey.HOME)
        if (lower.matches(Regex("(go|press|hit) back|back"))) return BuiltIn.System(BuiltIn.SystemKey.BACK)
        if (lower.matches(Regex("(show|open) (the )?(recent apps|recents|app switcher)"))) return BuiltIn.System(BuiltIn.SystemKey.RECENTS)
        if (lower.matches(Regex("(show|open|pull down|check) (the |my )?notifications?( shade| panel)?"))) return BuiltIn.System(BuiltIn.SystemKey.NOTIFICATIONS)
        if (lower.matches(Regex("lock (the )?(screen|phone)|lock it"))) return BuiltIn.System(BuiltIn.SystemKey.LOCK)
        if (lower.matches(Regex("take a screenshot|screenshot( this)?|capture (the )?screen"))) return BuiltIn.System(BuiltIn.SystemKey.SCREENSHOT)
        if (lower.matches(Regex("open (the )?(phone )?settings|go to settings"))) return BuiltIn.System(BuiltIn.SystemKey.SETTINGS)
        if (lower.matches(Regex("(take|click|snap) a (photo|picture|selfie|pic)|open (the )?camera"))) return BuiltIn.TakePhoto

        // ---- toggles
        Regex("(turn|switch) (on|off) (the )?(flashlight|torch|flash light)|(turn|switch) (the )?(flashlight|torch|flash light) (on|off)|(flashlight|torch) (on|off)")
            .matchEntire(lower)?.let { return BuiltIn.Flashlight(lower.contains(" on") || lower.endsWith("on")) }
        Regex("(turn|switch) (on|off) (the )?(wifi|wi-fi|wi fi)|(turn|switch) (the )?(wifi|wi-fi|wi fi) (on|off)|(wifi|wi-fi) (on|off)")
            .matchEntire(lower)?.let { return BuiltIn.Wifi(Regex("\\bon\\b").containsMatchIn(lower)) }
        Regex("(turn|switch) (on|off) (the )?bluetooth|(turn|switch) (the )?bluetooth (on|off)|bluetooth (on|off)")
            .matchEntire(lower)?.let { return BuiltIn.Bluetooth(Regex("\\bon\\b").containsMatchIn(lower)) }
        if (lower.matches(Regex("(mute|silence|silent)( the (phone|volume|sound))?|(turn|switch) (the )?(sound|volume) off|volume off"))) return BuiltIn.Volume(null, 0)
        if (lower.matches(Regex("(turn )?(the )?volume (up|louder)|louder|(increase|raise) (the )?volume"))) return BuiltIn.Volume(null, 1)
        if (lower.matches(Regex("(turn )?(the )?volume (down|lower|quieter)|quieter|softer|(decrease|lower|reduce) (the )?volume"))) return BuiltIn.Volume(null, -1)
        Regex("(set |put )?(the )?volume (to |at )?(\\w+)( percent| %)?").matchEntire(lower)?.let { m ->
            number(m.groupValues[4])?.let { if (it in 0..100) return BuiltIn.Volume(it, 0) }
        }
        if (lower.matches(Regex("(full|max|maximum) volume|volume (full|max|maximum)"))) return BuiltIn.Volume(100, 0)

        // ---- time based
        Regex("(set|create|make|put)( an| a| the)? alarm( for| at)? (.+)").matchEntire(lower)?.let { m ->
            val (spec, label) = splitLabel(m.groupValues[4])
            time(spec)?.let { return BuiltIn.SetAlarm(it.first, it.second, label) }
        }
        Regex("wake me( up)?( at| by)? (.+)").matchEntire(lower)?.let { m -> time(m.groupValues[3])?.let { return BuiltIn.SetAlarm(it.first, it.second, "Wake up") } }
        Regex("(set|start|create)( a| the)? (timer|countdown)( for| of)? (.+)").matchEntire(lower)?.let { m ->
            val (spec, label) = splitLabel(m.groupValues[5])
            duration(spec)?.let { return BuiltIn.SetTimer(it, label) }
        }
        Regex("(timer|countdown) (for )?(.+)").matchEntire(lower)?.let { m ->
            val (spec, label) = splitLabel(m.groupValues[3])
            duration(spec)?.let { return BuiltIn.SetTimer(it, label) }
        }
        Regex("remind me (to |about |that )?(.+?) (at|by) (.+)", RegexOption.IGNORE_CASE).matchEntire(text)?.let { m ->
            time(m.groupValues[4].lowercase())?.let { return BuiltIn.Reminder(m.groupValues[2].trim(), it.first, it.second) }
        }
        Regex("(add|create|put|schedule|set up)( a| an)? (meeting|event|appointment)( with| called| named| about| for)? (.+)", RegexOption.IGNORE_CASE).matchEntire(text)?.let { m ->
            var rest = m.groupValues[5].trim()
            val tomorrow = Regex("\\btomorrow\\b", RegexOption.IGNORE_CASE).containsMatchIn(rest)
            rest = rest.replace(Regex("\\b(tomorrow|today)\\b", RegexOption.IGNORE_CASE), "").trim()
            val tm = Regex("^(.*?)\\s+(at|@)\\s+(.+)$", RegexOption.IGNORE_CASE).matchEntire(rest)
            val t = tm?.let { time(it.groupValues[3].lowercase()) }
            val title = (if (t != null) tm.groupValues[1] else rest).trim().trimEnd(',')
            if (title.isNotBlank()) return BuiltIn.CalendarEvent(title, t?.first, t?.second, tomorrow)
        }

        // ---- navigation, media, web
        Regex("(navigate|directions|direction|drive|take me|get me|route) (me )?(to|towards) (.+?)( by (car|bike|bicycle|walking|foot|transit|train|bus|public transport))?", RegexOption.IGNORE_CASE)
            .matchEntire(text)?.let { m -> return BuiltIn.Navigate(m.groupValues[4].trim(), m.groupValues[6].ifBlank { null }?.lowercase()) }
        Regex("(where is|show me|find|locate|show) (.+?) on (the )?map|(where is|where's) (.+)", RegexOption.IGNORE_CASE).matchEntire(text)?.let { m ->
            val place = (m.groupValues[2].ifBlank { m.groupValues[5] }).trim()
            if (place.isNotBlank() && !place.lowercase().startsWith("my ")) return BuiltIn.ShowMap(place)
        }
        Regex("(play|put on|start playing) (some )?(.+?)( on (spotify|youtube music|youtube|music))?", RegexOption.IGNORE_CASE).matchEntire(text)?.let { m ->
            val q = m.groupValues[3].trim()
            if (q.isNotBlank() && !q.lowercase().matches(Regex("(a )?(game|games|chess|video|videos)"))) return BuiltIn.PlayMusic(q)
        }
        Regex("(google|search (the web |online |the internet |google )?for|look up|search) (.+)", RegexOption.IGNORE_CASE).matchEntire(text)?.let { m ->
            if (!lower.startsWith("search ") || lower.startsWith("search for ") || lower.startsWith("search the web") || lower.startsWith("search google") || lower.startsWith("search online")) {
                return BuiltIn.WebSearch(m.groupValues[3].trim())
            }
        }
        Regex("(open|go to|visit) (the )?(website |site |url )?((https?://)?[a-z0-9-]+(\\.[a-z0-9-]+)+(/\\S*)?)", RegexOption.IGNORE_CASE).matchEntire(text)?.let { m ->
            val url = m.groupValues[4]
            return BuiltIn.OpenUrl(if (url.startsWith("http")) url else "https://$url")
        }

        // ---- people
        Regex("(call|phone|ring|dial) (.+)", RegexOption.IGNORE_CASE).matchEntire(text)?.let { m ->
            val who = m.groupValues[2].trim().removePrefix("up ").removeSuffix(" on mobile").removeSuffix(" on his mobile").removeSuffix(" on her mobile")
            if (!NOT_A_PERSON.containsMatchIn(who) && !who.contains(" and ") && who.split(' ').size <= 4) return BuiltIn.Call(who)
        }
        Regex("(text|sms|send (a |an )?(text|sms)( message)? to) (.+?) (saying|that says|that|and say|with|the message|message)? ?[:\\-]? ?(.+)", RegexOption.IGNORE_CASE)
            .matchEntire(text)?.let { m -> personAndText(m.groupValues[5], m.groupValues[7])?.let { (w, t) -> return BuiltIn.Sms(w, t) } }
        Regex("(whatsapp|send (a )?whatsapp( message)? to|message (.+?) on whatsapp) ?(.+?)? (saying|that says|that|and say|with|the message|message)? ?[:\\-]? ?(.+)", RegexOption.IGNORE_CASE)
            .matchEntire(text)?.let { m ->
                val who = m.groupValues[4].ifBlank { m.groupValues[5] }
                personAndText(who, m.groupValues[7])?.let { (w, t) -> return BuiltIn.WhatsApp(w, t) }
            }
        Regex("(message|send (a )?message to|tell|msg) (.+?) (saying|that says|that|and say|with|the message|message)? ?[:\\-]? ?(.+)", RegexOption.IGNORE_CASE)
            .matchEntire(text)?.let { m -> personAndText(m.groupValues[3], m.groupValues[5])?.let { (w, t) -> return BuiltIn.Message(w, t) } }
        Regex("(email|mail|send (an )?email to) (.+?)( about | subject | with subject | saying | that says |: )(.+)", RegexOption.IGNORE_CASE).matchEntire(text)?.let { m ->
            val who = m.groupValues[3].trim()
            val sep = m.groupValues[4].trim().lowercase()
            val rest = m.groupValues[5].trim()
            return if (sep.startsWith("about") || sep.startsWith("subject") || sep.startsWith("with subject")) BuiltIn.Email(who, rest, null)
            else BuiltIn.Email(who, null, rest)
        }
        Regex("(email|mail|send (an )?email to) ([a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}|[a-z' ]{2,30})", RegexOption.IGNORE_CASE).matchEntire(text)?.let { m ->
            return BuiltIn.Email(m.groupValues[3].trim(), null, null)
        }

        // ---- apps (only a bare "open X"; anything longer is a task for the agent)
        Regex("(open|launch|start|run) (the )?([a-z0-9 .'&+-]{2,30})( app)?", RegexOption.IGNORE_CASE).matchEntire(text)?.let { m ->
            val app = m.groupValues[3].trim().removeSuffix(" app").trim()
            if (!app.contains(" and ") && !app.lowercase().matches(Regex("(settings|camera|notifications?|recents?|website.*)"))) return BuiltIn.OpenApp(app)
        }
        return null
    }

    /** "6:30 called gym" -> ("6:30", "gym"); no label -> (whole, null). */
    private fun splitLabel(rest: String): Pair<String, String?> {
        val m = Regex("^(.+?) (called|named|labelled|labeled) (.+)$").matchEntire(rest.trim()) ?: return rest.trim() to null
        return m.groupValues[1] to m.groupValues[3].trim().ifBlank { null }
    }

    private fun personAndText(who: String, text: String): Pair<String, String>? {
        val w = who.trim().trimEnd(',', ':')
        val t = text.trim().trimEnd()
        if (w.isBlank() || t.isBlank() || w.split(' ').size > 4) return null
        return w to t
    }

    /** "6:30 am", "7 pm", "seven thirty", "noon", "6 30 in the evening" -> hour (0-23), minute. */
    fun time(s: String): Pair<Int, Int>? {
        var t = s.trim().lowercase().replace(Regex("\\b(o'clock|oclock|tomorrow|today|tonight|in the|at)\\b"), " ").replace(Regex("\\s+"), " ").trim()
        if (t.isBlank()) return null
        if (t == "noon" || t == "midday") return 12 to 0
        if (t == "midnight") return 0 to 0
        var pm: Boolean? = null
        if (Regex("\\b(pm|p m|p\\.m\\.|evening|afternoon|night)\\b").containsMatchIn(t)) pm = true
        if (Regex("\\b(am|a m|a\\.m\\.|morning)\\b").containsMatchIn(t)) pm = false
        t = t.replace(Regex("\\b(pm|p m|p\\.m\\.|am|a m|a\\.m\\.|evening|afternoon|night|morning)\\b"), " ").replace(Regex("\\s+"), " ").trim()
        var hour: Int? = null
        var minute = 0
        Regex("^(\\d{1,2})[:. ](\\d{2})$").matchEntire(t)?.let { hour = it.groupValues[1].toInt(); minute = it.groupValues[2].toInt() }
        if (hour == null) Regex("^(\\d{1,2})$").matchEntire(t)?.let { hour = it.groupValues[1].toInt() }
        if (hour == null) Regex("^half past (\\w+)$").matchEntire(t)?.let { hour = number(it.groupValues[1]); minute = 30 }
        if (hour == null) Regex("^quarter past (\\w+)$").matchEntire(t)?.let { hour = number(it.groupValues[1]); minute = 15 }
        if (hour == null) Regex("^quarter to (\\w+)$").matchEntire(t)?.let { hour = number(it.groupValues[1])?.minus(1); minute = 45 }
        if (hour == null) {
            val words = t.split(' ')
            if (words.size in 1..3) {
                val h = number(words[0])
                val m = if (words.size > 1) number(words.drop(1).joinToString(" ")) else 0
                if (h != null && m != null) { hour = h; minute = m }
            }
        }
        val h = hour ?: return null
        if (h !in 0..24 || minute !in 0..59) return null
        var hh = h % 24
        if (pm == true && hh < 12) hh += 12
        if (pm == false && hh == 12) hh = 0
        return hh to minute
    }

    /** "10 minutes", "an hour and a half", "ninety seconds", "1 hour 20 minutes" -> seconds. */
    fun duration(s: String): Int? {
        val t = s.trim().lowercase().replace(" and ", " ").replace(",", " ").replace(Regex("\\s+"), " ")
        var total = 0
        var found = false
        val re = Regex("(\\d+(?:\\.\\d+)?|half an|half a|an|a|[a-z]+(?: [a-z]+)?) ?\\b(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\\b")
        for (m in re.findAll(t)) {
            val q = m.groupValues[1]
            val n: Double = when {
                q == "a" || q == "an" -> 1.0
                q.startsWith("half") -> 0.5
                else -> q.toDoubleOrNull() ?: number(q)?.toDouble() ?: continue
            }
            val unit = m.groupValues[2]
            val mult = when {
                unit.startsWith("h") -> 3600
                unit.startsWith("m") -> 60
                else -> 1
            }
            total += (n * mult).toInt()
            found = true
        }
        if (Regex("\\b(and a half|and half)\\b").containsMatchIn(s.lowercase()) && found) {
            total += if (s.lowercase().contains("hour")) 1800 else 30
        }
        return if (found && total > 0) total else null
    }

    /** Small number words and digits: "ten" -> 10, "twenty five" -> 25, "7" -> 7. */
    fun number(s: String): Int? {
        val t = s.trim().lowercase().replace("-", " ")
        t.toIntOrNull()?.let { return it }
        val parts = t.split(' ').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        if (parts.size == 1) return UNITS[parts[0]] ?: TENS[parts[0]]
        if (parts.size == 2) {
            val tens = TENS[parts[0]] ?: return null
            val unit = UNITS[parts[1]] ?: return null
            return tens + unit
        }
        return null
    }
}
