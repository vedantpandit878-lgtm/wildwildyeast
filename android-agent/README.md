# Voice Agent for Android

A personal "do what I say" assistant for your own Android phone. You tap a
floating microphone bubble, say something like *"open Gmail and reply to the
last email from Priya saying I'll be there at 6"* or *"book an Uber from home
to the airport"*, and the app reads the screen, decides the next tap, performs
it, reads the screen again, and keeps going until the job is done. Before it
sends, pays, books, posts or deletes anything it asks you to confirm.

It is built for testing on your own device. It is not a Play Store app.

## How it works

```
 you speak ──► SpeechRecognizer ──► goal text
                                        │
        ┌───────────────────────────────▼──────────────────────────────┐
        │  AgentLoop (core module, plain Kotlin)                        │
        │  1. read screen (accessibility tree, screenshot on demand)     │
        │  2. send goal + history + screen to Claude with tools          │
        │  3. Claude returns one tool call: tap / type / scroll / ...    │
        │  4. SafetyGate: risky tap? ask you first                       │
        │  5. AccessibilityService performs it, waits for UI to settle   │
        │  6. loop until Claude calls finish                              │
        └───────────────────────────────────────────────────────────────┘
                                        │
                              TextToSpeech reads the result
```

* **Learn once, replay for free.** The first time you say a command the AI
  works it out and every successful step is recorded with a description of the
  element it touched (label, id, position, app). Words of the command that were
  typed or tapped become slots: "message Mum I'm leaving" is stored as the
  pattern "message {name} {text}", so "message Dad I'll be late" replays the
  same taps with the new name and text (`Slots.kt`). The next time you say a
  matching command, `RoutineReplayer` re-finds each element on the live
  screen and performs the steps with no AI at all. If an element cannot be
  found (the app changed, a pop-up appeared), the AI takes over from that
  point and the corrected routine is saved. Risky taps still go through the
  confirmation gate during replay. Routines are stored in the app's private
  `routines.json`; the setup screen shows how many there are and can clear them.
* `core/` is a plain JVM Kotlin module: screen model, tool definitions, the
  safety gate and the Claude loop. It has no Android dependency, so it compiles
  and runs its unit tests on any machine with a JDK (`./gradlew :core:test`).
* `app/` is the Android app: the accessibility service (read tree, tap, type,
  swipe, screenshot, launch apps), the floating bubble and question panel,
  speech in/out, and a small setup screen.

Two interchangeable brains:

* **On-device Gemini Nano** (default). Runs on the phone through Android's
  AICore service via the ML Kit GenAI Prompt API. Free, offline, nothing
  leaves the device. Needs a recent Pixel or Samsung phone; the setup screen
  shows whether it is supported and downloads the model once. It is a small
  model, so it handles short tasks well and long multi-step tasks less
  reliably; it does not look at screenshots, only the element list.
* **Claude Opus 5** via the Anthropic API, with an automatic fallback to Claude
  Opus 4.8 if a request is refused. Much more capable; costs per use. Only the
  accessibility tree is sent each step; screenshots are sent only when the
  model asks for one or when the tree is nearly empty.

Whichever brain runs, successful tasks become routines that replay for free.

## Get the app without installing anything on a computer

Every change pushed to GitHub builds the app automatically (see
`.github/workflows/android-agent.yml`). To get the latest APK:

1. Open the repository on GitHub, click the **Actions** tab, and open the most
   recent **Build Voice Agent APK** run with a green tick.
2. Scroll down to **Artifacts** and download **VoiceAgent-apk**. It is a zip
   file containing `VoiceAgent.apk`. Doing this on the phone itself is easiest.
3. On the phone, open the zip, tap `VoiceAgent.apk`, and allow installs from
   this source when Android asks.

## Build it yourself (optional)

1. Open the `android-agent` folder in Android Studio (Ladybug or newer). It
   will create `local.properties` with your SDK path, which is what enables the
   `app` module (see `settings.gradle.kts`).
2. Enable **Developer options** and **USB debugging** on the phone, plug it in,
   and press **Run**. Or build an APK with `./gradlew :app:assembleDebug` and
   install `app/build/outputs/apk/debug/app-debug.apk`.

## First-time setup on the phone

1. Open **Voice Agent** and go through the setup screen:
   1. Enable the accessibility service (Settings > Accessibility > Installed
      apps > Voice Agent). Android will warn you that the app can read the
      screen and perform actions. That is the point.
   2. Grant microphone and notification permissions.
   3. Choose the brain. On-device is the default; if the status line says
      "not downloaded yet", tap **Download on-device AI** and wait (it is a
      one-time download over Wi-Fi). If you prefer Claude, pick it and paste
      your Anthropic API key; it is stored in the app's private storage only.
2. A microphone bubble now floats over every app. Drag it anywhere. Tap it,
   speak, and watch. Tap it again to stop at any time.

For the first tests, turn on **Ask before every tap** in the setup screen, and
use the **Run typed command** box so you can watch the bubble's status line
without speaking.

## Built-in commands (no AI involved)

These are recognised by `BuiltInCommands` and executed directly through
Android by `BuiltInExecutor`, so they are instant and work offline:

| Say | What happens |
|---|---|
| call Mum / phone Ravi | looks up the contact and calls (asks which one if several match) |
| text Mum I'm leaving / sms Dad running late | sends an SMS after you confirm |
| WhatsApp Priya see you at 6 | opens the chat with the text and taps send after you confirm |
| message Mum I'll be late | WhatsApp if installed, otherwise SMS (switchable in settings) |
| email Ravi about the invoice | opens a prefilled email |
| open Gmail | launches the app |
| set an alarm for 6:30 am / wake me up at 7 | sets the alarm silently |
| set a timer for 10 minutes | starts a timer |
| remind me to call the dentist at 5 pm | alarm with that label |
| navigate to the airport / take me to X by walking | starts Google Maps directions |
| where is the nearest pharmacy | shows it on the map |
| play Coldplay | plays in your default music app |
| search for best biryani near me / google X | web search |
| open bbc.com | opens the site |
| flashlight on/off, volume up/down/mute, volume to 50 percent | device controls |
| wifi on/off, bluetooth on/off | opens the system panel and taps the switch |
| add a meeting with Ravi tomorrow at 3 pm | opens a prefilled calendar event |
| take a photo | opens the camera |
| what time is it / what's the date / battery level | spoken answer |
| read the screen | reads the visible text aloud |
| go home, go back, lock the screen, take a screenshot, open notifications, open settings | system actions |
| what can you do | reads this list |

Anything that is not one of these goes to the learned routines, then to the
brain.

## Good first commands

* "Open the calculator and work out 48 times 12"
* "Open Gmail and tell me the subject of the newest email"
* "Open WhatsApp, find the chat with Mum, and type hello but don't send it"
* "Open Uber and check the price from here to the airport" (it will ask before confirming a ride)

## What to expect

* **Speed.** Each step is one round trip to Claude, typically 3 to 8 seconds.
  A ride booking with a dozen screens takes about a minute.
* **Cost.** Nothing with the on-device brain. With Claude, roughly 10 to 60
  cents the first time a task runs, depending on length. Repeats of a learned
  command are free either way.
* **Replay limits.** Slots cover text you dictate and names you pick. Commands
  whose steps depend on live content (choosing the cheapest ride) may still
  need the brain on some runs.
* **Confirmation.** Two layers. The system prompt tells Claude to ask before
  irreversible actions. Independently, `SafetyGate` in the app intercepts taps
  on elements labelled Send, Pay, Confirm, Book, Order, Delete, Post and so on
  and shows a Yes/No panel that also accepts a spoken yes or no. Edit the word
  list in `core/.../SafetyGate.kt` to suit you.
* **Passwords and OTPs.** The agent is told never to type them. When a login
  screen appears it will ask you to complete it and then continue.
* **Failure modes.** Custom-drawn UIs (games, some web views) give an empty
  tree; the agent falls back to screenshots and coordinate taps, which is less
  reliable. Pop-ups, captchas and app redesigns can confuse it. It gives up
  after 40 steps and tells you where it left the phone.

## If voice does not start from the bubble

Android restricts microphone access for apps in the background. The service
tries to run as a microphone foreground service (you will see a persistent
"Voice Agent is ready" notification). If Android refused that, open the Voice
Agent app once; it retries from the foreground. On some phones you also need
to turn off battery optimisation for the app.

## Layout

```
android-agent/
├── core/src/main/kotlin/com/wildwildyeast/voiceagent/core/
│   ├── ScreenState.kt        screen + element model, prompt rendering
│   ├── AgentAction.kt        the actions the model can request
│   ├── DeviceController.kt   interface the Android app implements
│   ├── AgentTools.kt         tool schemas for Claude + parser
│   ├── SafetyGate.kt         client-side confirmation rules
│   └── AgentLoop.kt          the plan/act/observe loop, system prompt
├── core/src/test/...         unit tests (run anywhere)
└── app/src/main/kotlin/com/wildwildyeast/voiceagent/
    ├── AgentAccessibilityService.kt  read tree, tap, type, swipe, screenshot, open app
    ├── NanoModel.kt                  Gemini Nano through the ML Kit Prompt API
    ├── BuiltInExecutor.kt            runs built-in commands through Android intents and services
    ├── AndroidDevice.kt              DeviceController on top of the service
    ├── OverlayController.kt          floating bubble + question panel
    ├── VoiceInput.kt / Speaker.kt    speech recognition / text to speech
    ├── AgentSession.kt               runs one task end to end
    ├── Settings.kt                   API key and toggles
    └── MainActivity.kt               setup screen
```

## Status

The `core` module is compiled and unit tested against the Anthropic Java SDK
(2.63.0). The `app` module compiles on GitHub Actions. It has not yet been
exercised on a physical phone, so expect some rough edges on first use; the
bubble's status line and `adb logcat -s VoiceAgent VoiceAgentService` show
what the agent is doing.
