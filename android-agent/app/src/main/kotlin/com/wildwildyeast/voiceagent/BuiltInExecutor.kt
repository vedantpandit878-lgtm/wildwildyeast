package com.wildwildyeast.voiceagent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.app.SearchManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.wildwildyeast.voiceagent.core.AgentAction
import com.wildwildyeast.voiceagent.core.AgentOutcome
import com.wildwildyeast.voiceagent.core.BuiltIn
import com.wildwildyeast.voiceagent.core.BuiltInCommands
import com.wildwildyeast.voiceagent.core.BuiltInHandler
import com.wildwildyeast.voiceagent.core.ScreenNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Runs [BuiltIn] commands straight through Android: intents, system services and
 * a few accessibility taps. No AI, no network, instant.
 */
class BuiltInExecutor(private val service: AgentAccessibilityService) : BuiltInHandler {

    private val device = AndroidDevice(service)
    private val settings get() = Settings(service)

    override suspend fun handle(action: BuiltIn): AgentOutcome? = try {
        when (action) {
            is BuiltIn.Call -> call(action.who)
            is BuiltIn.Sms -> sms(action.who, action.text)
            is BuiltIn.WhatsApp -> whatsapp(action.who, action.text)
            is BuiltIn.Message -> if (settings.messageBySms || !hasWhatsApp()) sms(action.who, action.text) else whatsapp(action.who, action.text)
            is BuiltIn.Email -> email(action)
            is BuiltIn.OpenApp -> service.openApp(action.app).fold({ ok(it) }, { null }) // unknown app: let the agent try
            is BuiltIn.SetAlarm -> {
                launch(
                    Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                        .putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        .apply { action.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) } },
                )
                ok("Alarm set for ${clock(action.hour, action.minute)}" + (action.label?.let { ", $it" } ?: "") + ".")
            }
            is BuiltIn.SetTimer -> {
                launch(
                    Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        .apply { action.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) } },
                )
                ok("Timer set for ${spoken(action.seconds)}.")
            }
            is BuiltIn.Reminder -> {
                launch(
                    Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                        .putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, action.what)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
                )
                ok("I'll remind you to ${action.what} at ${clock(action.hour, action.minute)}.")
            }
            is BuiltIn.Navigate -> {
                val mode = when (action.mode) { "walking", "foot" -> "&mode=w"; "bike", "bicycle" -> "&mode=b"; "transit", "train", "bus", "public transport" -> "&mode=r"; else -> "" }
                val maps = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${enc(action.destination)}$mode")).setPackage("com.google.android.apps.maps")
                if (!launch(maps)) launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${enc(action.destination)}")))
                ok("Starting directions to ${action.destination}.")
            }
            is BuiltIn.ShowMap -> { launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${enc(action.place)}"))); ok("Showing ${action.place} on the map.") }
            is BuiltIn.PlayMusic -> {
                launch(
                    Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                        .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                        .putExtra(SearchManager.QUERY, action.query),
                )
                ok("Playing ${action.query}.")
            }
            is BuiltIn.WebSearch -> { launch(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, action.query)); ok("Searching for ${action.query}.") }
            is BuiltIn.OpenUrl -> { launch(Intent(Intent.ACTION_VIEW, Uri.parse(action.url))); ok("Opening ${action.url.removePrefix("https://")}.") }
            is BuiltIn.Flashlight -> flashlight(action.on)
            is BuiltIn.Volume -> volume(action)
            is BuiltIn.Wifi -> toggleFromPanel(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY), listOf("wi-fi", "wifi", "wlan"), action.on, "Wi-Fi")
            is BuiltIn.Bluetooth -> toggleFromPanel(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), listOf("bluetooth", "use bluetooth"), action.on, "Bluetooth")
            is BuiltIn.CalendarEvent -> calendar(action)
            BuiltIn.TakePhoto -> { launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); ok("Camera is open.") }
            BuiltIn.TellTime -> ok("It's " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + ".")
            BuiltIn.TellDate -> ok("Today is " + SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()) + ".")
            BuiltIn.TellBattery -> {
                val bm = service.getSystemService(BatteryManager::class.java)
                val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ok("Battery is at $pct percent" + (if (bm.isCharging) " and charging." else "."))
            }
            BuiltIn.ReadScreen -> readScreen()
            BuiltIn.Help -> ok("I can " + BuiltInCommands.HELP.joinToString(", ") + ". Anything else, I'll work out on screen.")
            is BuiltIn.System -> system(action.key)
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "permission missing", e)
        fail("I need a permission for that. Open the Voice Agent app and tap grant permissions.")
    } catch (e: Exception) {
        Log.e(TAG, "built-in failed", e)
        null
    }

    // ------------------------------------------------------------ people

    private data class Contact(val name: String, val number: String, val type: Int)

    private suspend fun call(who: String): AgentOutcome {
        val digits = who.filter { it.isDigit() || it == '+' }
        val number = if (digits.length >= 5 && digits.length >= who.length - 4) digits else {
            val c = pickContact(who) ?: return fail("I couldn't find $who in your contacts.")
            c.number
        }
        val canCall = ContextCompat.checkSelfPermission(service, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        launch(Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
        return ok(if (canCall) "Calling $who." else "Dialer is open for $who; tap call.")
    }

    private suspend fun sms(who: String, text: String): AgentOutcome {
        val c = pickContact(who) ?: return fail("I couldn't find $who in your contacts.")
        if (!service.overlay.confirm("Text ${c.name}: \"$text\"?")) return fail("Okay, not sending.")
        if (ContextCompat.checkSelfPermission(service, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(c.number))).putExtra("sms_body", text))
            return ok("Messages is open with your text to ${c.name}; tap send. Grant the SMS permission in the app to send automatically.")
        }
        withContext(Dispatchers.IO) {
            val sm = service.getSystemService(SmsManager::class.java)
            val parts = sm.divideMessage(text)
            if (parts.size == 1) sm.sendTextMessage(c.number, null, text, null, null)
            else sm.sendMultipartTextMessage(c.number, null, parts, null, null)
        }
        return ok("Sent to ${c.name}.")
    }

    private suspend fun whatsapp(who: String, text: String): AgentOutcome {
        if (!hasWhatsApp()) return sms(who, text)
        val c = pickContact(who) ?: return fail("I couldn't find $who in your contacts.")
        val e164 = e164(c.number) ?: return fail("I couldn't work out an international number for ${c.name}.")
        val pkg = if (installed("com.whatsapp")) "com.whatsapp" else "com.whatsapp.w4b"
        launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${e164.removePrefix("+")}?text=${enc(text)}")).setPackage(pkg))
        // Wait for the chat to open with the draft, then press WhatsApp's send button.
        val send = waitForNode(7000) { n, screenPkg ->
            screenPkg == pkg && n.clickable && (n.label().equals("send", true) || n.resourceId?.endsWith(":id/send") == true)
        } ?: return ok("WhatsApp is open with your message to ${c.name}; tap send.")
        if (!service.overlay.confirm("Send \"$text\" to ${c.name} on WhatsApp?")) return fail("Okay, it's left as a draft.")
        val result = device.perform(AgentAction.Tap(send.id))
        return if (result.ok) ok("Sent to ${c.name} on WhatsApp.") else ok("WhatsApp is open with your message to ${c.name}; tap send.")
    }

    private suspend fun email(action: BuiltIn.Email): AgentOutcome {
        val to = action.who?.let { who ->
            if (who.contains('@')) who else lookupEmail(who) ?: return fail("I couldn't find an email address for $who.")
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + (to?.let { Uri.encode(it) } ?: "")))
        action.subject?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
        action.body?.let { intent.putExtra(Intent.EXTRA_TEXT, it) }
        launch(intent)
        return ok("Email to ${action.who ?: "..."} is ready; check it and tap send.")
    }

    /** Find a contact by name; asks the person to choose when several match. */
    private suspend fun pickContact(name: String): Contact? {
        if (ContextCompat.checkSelfPermission(service, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("READ_CONTACTS")
        }
        val all = withContext(Dispatchers.IO) { queryContacts(name) }
        if (all.isEmpty()) return null
        val byName = all.groupBy { it.name }
        val exact = byName.keys.filter { it.equals(name, ignoreCase = true) }
        val names = if (exact.isNotEmpty()) exact else byName.keys.toList()
        val chosenName = if (names.size == 1) names[0] else {
            val answer = service.overlay.ask("Which one: ${names.take(5).joinToString(", ")}?")
            names.firstOrNull { it.equals(answer.trim(), true) }
                ?: names.firstOrNull { it.lowercase().contains(answer.trim().lowercase()) }
                ?: answer.trim().toIntOrNull()?.let { names.getOrNull(it - 1) }
                ?: return null
        }
        val numbers = byName.getValue(chosenName)
        return numbers.firstOrNull { it.type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE } ?: numbers.first()
    }

    private fun queryContacts(name: String): List<Contact> {
        val out = LinkedHashMap<String, Contact>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val proj = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
        )
        service.contentResolver.query(uri, proj, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null)?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0) ?: continue
                val num = c.getString(1) ?: continue
                val key = n + "|" + num.filter { it.isDigit() }
                out.putIfAbsent(key, Contact(n, num, c.getInt(2)))
            }
        }
        return out.values.toList()
    }

    private suspend fun lookupEmail(name: String): String? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(service, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return@withContext null
        val uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI
        service.contentResolver.query(
            uri, arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    private fun e164(number: String): String? {
        val iso = runCatching { service.getSystemService(TelephonyManager::class.java).networkCountryIso }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: Locale.getDefault().country
        return PhoneNumberUtils.formatNumberToE164(number, iso.uppercase())
            ?: number.filter { it.isDigit() || it == '+' }.takeIf { it.startsWith("+") }
    }

    // ------------------------------------------------------------ device

    private fun flashlight(on: Boolean): AgentOutcome {
        val cm = service.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull { id ->
            val ch = cm.getCameraCharacteristics(id)
            ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true && ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return fail("This phone has no flashlight I can control.")
        cm.setTorchMode(id, on)
        return ok(if (on) "Flashlight on." else "Flashlight off.")
    }

    private fun volume(action: BuiltIn.Volume): AgentOutcome {
        val am = service.getSystemService(AudioManager::class.java)
        val stream = AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(stream)
        val level = action.level
        when {
            level != null -> am.setStreamVolume(stream, (max * level / 100.0).toInt().coerceIn(0, max), AudioManager.FLAG_SHOW_UI)
            action.delta > 0 -> am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            action.delta < 0 -> am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            else -> {
                am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                runCatching { am.ringerMode = AudioManager.RINGER_MODE_VIBRATE }
            }
        }
        return ok(
            when {
                action.level != null -> "Volume set to ${action.level} percent."
                action.delta > 0 -> "Volume up."
                action.delta < 0 -> "Volume down."
                else -> "Muted."
            },
        )
    }

    /** Android no longer lets apps flip Wi-Fi or Bluetooth directly: open the system panel and tap the switch. */
    private suspend fun toggleFromPanel(panel: Intent, labels: List<String>, on: Boolean, what: String): AgentOutcome {
        if (!launch(panel)) return fail("I couldn't open the $what settings.")
        val toggle = waitForNode(5000) { n, _ -> n.checkable && labels.any { l -> n.label().lowercase().contains(l) || (n.resourceId ?: "").lowercase().contains(l.replace("-", "")) } }
            ?: return ok("The $what panel is open; tap the switch.")
        if (toggle.checked == on) return ok("$what is already ${if (on) "on" else "off"}.")
        device.perform(AgentAction.Tap(toggle.id))
        delay(800)
        service.press("BACK")
        return ok("$what ${if (on) "on" else "off"}.")
    }

    private fun calendar(action: BuiltIn.CalendarEvent): AgentOutcome {
        val cal = Calendar.getInstance()
        if (action.tomorrow) cal.add(Calendar.DAY_OF_YEAR, 1)
        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, action.title)
        val hour = action.hour
        if (hour != null) {
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, action.minute ?: 0)
            cal.set(Calendar.SECOND, 0)
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, cal.timeInMillis + 60 * 60 * 1000)
        } else {
            intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
        }
        launch(intent)
        return ok("Calendar is open with \"${action.title}\"; check it and tap save.")
    }

    private suspend fun readScreen(): AgentOutcome {
        val screen = device.capture(false)
        val text = screen.nodes.asSequence()
            .map { it.label().trim() }
            .filter { it.length > 1 }
            .distinct()
            .joinToString(". ")
            .take(700)
        return if (text.isBlank()) fail("I can't read anything on this screen.") else ok(text)
    }

    private fun system(key: BuiltIn.SystemKey): AgentOutcome {
        val ok = when (key) {
            BuiltIn.SystemKey.HOME -> service.press("HOME").isSuccess
            BuiltIn.SystemKey.BACK -> service.press("BACK").isSuccess
            BuiltIn.SystemKey.RECENTS -> service.press("RECENTS").isSuccess
            BuiltIn.SystemKey.NOTIFICATIONS -> service.press("NOTIFICATIONS").isSuccess
            BuiltIn.SystemKey.LOCK -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            BuiltIn.SystemKey.SCREENSHOT -> service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            BuiltIn.SystemKey.SETTINGS -> launch(Intent(Settings.ACTION_SETTINGS))
        }
        return if (ok) AgentOutcome(true, if (key == BuiltIn.SystemKey.LOCK) "" else "Done.", 1) else fail("That didn't work.")
    }

    // ------------------------------------------------------------ helpers

    private suspend fun waitForNode(timeoutMs: Long, predicate: (ScreenNode, String) -> Boolean): ScreenNode? {
        val deadline = System.currentTimeMillis() + timeoutMs
        delay(900)
        while (System.currentTimeMillis() < deadline) {
            val screen = device.capture(false)
            screen.nodes.firstOrNull { predicate(it, screen.packageName) }?.let { return it }
            delay(400)
        }
        return null
    }

    private fun launch(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(service.packageManager) == null && intent.`package` == null && intent.action != Intent.ACTION_CALL) {
            Log.w(TAG, "no activity for $intent")
        }
        service.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "launch failed: $e")
        false
    }

    private fun installed(pkg: String): Boolean = service.packageManager.getLaunchIntentForPackage(pkg) != null
    private fun hasWhatsApp(): Boolean = installed("com.whatsapp") || installed("com.whatsapp.w4b")
    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun ok(msg: String) = AgentOutcome(true, msg, 1)
    private fun fail(msg: String) = AgentOutcome(false, msg, 1)
    private fun clock(h: Int, m: Int): String {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m) }
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
    }

    private fun spoken(seconds: Int): String {
        val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
        return listOfNotNull(
            h.takeIf { it > 0 }?.let { "$it hour${if (it > 1) "s" else ""}" },
            m.takeIf { it > 0 }?.let { "$it minute${if (it > 1) "s" else ""}" },
            s.takeIf { it > 0 }?.let { "$it second${if (it > 1) "s" else ""}" },
        ).joinToString(" ")
    }

    companion object {
        private const val TAG = "VoiceAgentBuiltIn"
    }
}
