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

* `core/` is a plain JVM Kotlin module: screen model, tool definitions, the
  safety gate and the Claude loop. It has no Android dependency, so it compiles
  and runs its unit tests on any machine with a JDK (`./gradlew :core:test`).
* `app/` is the Android app: the accessibility service (read tree, tap, type,
  swipe, screenshot, launch apps), the floating bubble and question panel,
  speech in/out, and a small setup screen.

The model is Claude Opus 5 by default, with an automatic server-side fallback
to Claude Opus 4.8 if a request is refused. Adaptive thinking is on. Only the
accessibility tree is sent each step; screenshots are sent only when the model
asks for one or when the tree is nearly empty (web views, maps, games).

## Build and install

1. Open the `android-agent` folder in Android Studio (Ladybug or newer). It
   will create `local.properties` with your SDK path, which is what enables the
   `app` module (see `settings.gradle.kts`).
2. Enable **Developer options** and **USB debugging** on the phone, plug it in,
   and press **Run**. Or build an APK with `./gradlew :app:assembleDebug` and
   install `app/build/outputs/apk/debug/app-debug.apk`.
3. Open **Voice Agent** on the phone and go through the setup screen:
   1. Enable the accessibility service (Settings > Accessibility > Installed
      apps > Voice Agent). Android will warn you that the app can read the
      screen and perform actions. That is the point.
   2. Grant microphone and notification permissions.
   3. Paste your Anthropic API key and tap **Save key**. It is stored in the
      app's private storage on the phone only.
4. A microphone bubble now floats over every app. Drag it anywhere. Tap it,
   speak, and watch. Tap it again to stop at any time.

For the first tests, turn on **Ask before every tap** in the setup screen, and
use the **Run typed command** box so you can watch the bubble's status line
without speaking.

## Good first commands

* "Open the calculator and work out 48 times 12"
* "Open Gmail and tell me the subject of the newest email"
* "Open WhatsApp, find the chat with Mum, and type hello but don't send it"
* "Open Uber and check the price from here to the airport" (it will ask before confirming a ride)

## What to expect

* **Speed.** Each step is one round trip to Claude, typically 3 to 8 seconds.
  A ride booking with a dozen screens takes about a minute.
* **Cost.** Roughly 10 to 60 cents per task at Opus 5 pricing, depending on
  length. The system prompt is cached; screenshots are the expensive part.
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
    ├── AndroidDevice.kt              DeviceController on top of the service
    ├── OverlayController.kt          floating bubble + question panel
    ├── VoiceInput.kt / Speaker.kt    speech recognition / text to speech
    ├── AgentSession.kt               runs one task end to end
    ├── Settings.kt                   API key and toggles
    └── MainActivity.kt               setup screen
```

## Status

The `core` module is compiled and unit tested against the real Anthropic Java
SDK (2.63.0). The `app` module was written against the Android 11+ APIs but
has not yet been compiled or run on a device from this environment, because
the Android SDK could not be downloaded here. Expect to fix small compile
issues on the first build in Android Studio.
