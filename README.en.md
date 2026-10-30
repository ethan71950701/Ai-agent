# PhoneAI Overlay

English | [繁體中文](README.md)

An AI phone assistant built as a single app. Tell it what to do by voice or text and it can open apps, toggle settings, create reminders, look at the screen and tap, swipe and type on its own, and search the web when it needs fresh information. **Root gives the most complete experience, but it also works without root** (Shizuku or normal mode).

The whole project is one app: **no PC, no Termux, no Python scripts**.

> This is a personal experiment. Many features have only been verified on the author's own phone (Samsung Galaxy S25+, Android 16), so it may not work on every device. **If you want to continue development, read [`CLAUDE.md`](CLAUDE.md) and [`HANDOFF.md`](HANDOFF.md) first** (both are in Chinese).

## What it can do

| Feature | Description |
|---|---|
| Assistant panel | Type or speak. Open it from the app's home screen, a quick-settings tile, a long press of the power button (set it as the default digital assistant), a floating button on the screen edge, a home-screen widget or "Share to assistant" |
| Fixed commands (instant) | Turn Wi-Fi / Bluetooth on or off, go back, set the volume (you can say a percentage), launch any app by name |
| Chat and tools | Ask questions directly. The chat assistant can use memory (to-dos, notes), alarms, the calendar, **Samsung Reminder**, maps, dialing, file transfer and read-only system commands (battery, storage, which apps are installed…) |
| Screen automation | The AI looks at the screen (accessibility element list, or a screenshot when needed) and taps, swipes, drags and types step by step to finish what you asked (up to 12 steps; a cheap model goes first and a stronger one takes over when stuck) |
| Remembered routes | A clean run is saved as a **macro** and replayed next time; after a wrong turn the AI writes itself a **lesson** that is added to later prompts |
| Background mode | Operates on a virtual screen in the background so you can keep using the phone, with preview and takeover (needs root) |
| When only you can do it | For an unlock pattern, password or code the AI pauses and shows a draggable card asking you to take over; press Continue when done |
| Switch AI service to save money | Claude by default; you can also choose Gemini (free tier), DeepSeek, OpenAI or any OpenAI-compatible service, with separate cheap and vision slots, an A/B comparison page and per-service cost estimates |
| Web search | When fresh information is needed (Claude only, limited per task) |
| File transfer | Pair your own phones (and the Windows app in `windows-transfer/`) and send files; off by default |
| Memory and schedules | To-dos and notes, and scheduled simple queries such as "tell me the battery every morning at 8" |
| Voice recognition | Offline SenseVoice model (downloaded inside the app); the text is sent after a short countdown so you can edit it |
| While it runs | A ripple at every tap, Stop and Pause buttons in the notification, and a Now Bar capsule on Samsung phones (optional) |
| Logs and diagnostics | The task history page lists every step's thinking, action, result and cost estimate, and exports to a text file (no API keys); plus self-check and diagnostics pages |
| Interface | A liquid-glass bottom bar; theme colour and Traditional Chinese / English interface can be changed in Settings |

## Three execution modes

| Mode | Needs | Difference |
|---|---|---|
| root | A rooted phone | The most complete and fastest; background mode and all fixed commands work |
| Shizuku | Shizuku installed and started (no root) | `input`, `settings put`, `screencap` and similar run with adb-level permission; features that need real root (background screen) are not supported |
| Normal | Only the accessibility service | Taps and screenshots use accessibility; Wi-Fi, Bluetooth and similar can only open the settings page for you to switch |

Pick a mode in the first-run guide; change it later under "Settings → While running tasks → Execution mode". A full comparison is in [`docs/NO_ROOT.md`](docs/NO_ROOT.md) (Chinese).

## Requirements

- Android 13 or later (the author tests on Android 16)
- An API key for one AI service: [Anthropic](https://console.anthropic.com/) (default) or another service such as Gemini (the app has a step-by-step guide for getting a key). The key is stored only in the app's private folder on the phone, never in the repo
- Accessibility service and overlay permissions (the in-app "Setup guide" walks you through them)

## Install

1. Download the latest `PhoneAI.apk` from this repo's **Releases** page and install it. The APK is signed with a fixed key, so later versions install over the old one; "Features → Maintenance → Check for updates" in the app can also update it and shows what changed.
2. Open the app and follow the guide: choose an execution mode, then overlay, accessibility service and notification permissions (plus root access if you use root).
3. Choose a service and enter your key under "Features → AI service and API key" (a service you already entered a key for is not asked again).
4. Try typing "turn on Bluetooth" or "open the Wi-Fi page in Settings" in the home screen input box.

## Safety and privacy

- It taps the screen with root, Shizuku or accessibility permission. File transfer is off by default; when turned on it listens on the local network, and only paired devices can connect, content is encrypted, only `/sdcard` is exposed and every file taken triggers a notification.
- **Sensitive actions always show a confirmation dialog** (payment, transfer, deletion, installation, sending messages, login, passwords); no response counts as "no".
- During screen automation, **a screenshot or the on-screen text is sent to the AI service you chose at every step**. Do not use it on banking, private messaging or other personal screens (background mode refuses to operate protected apps such as banking and payment).
- Never paste your API key into a chat or commit it to git. Screenshots, logs and model files are excluded by `.gitignore`.

## Build it yourself

```bash
cd android-overlay
gradle testDebugUnitTest assembleDebug   # needs Android SDK 36 and Gradle 8.14 or newer
```

Release APKs are built by GitHub Actions (`.github/workflows/android.yml`) and published to Releases.

## Project layout

```
CLAUDE.md          Notes for Claude Code (read this first, Chinese)
HANDOFF.md         Full handoff: goals, status table, decisions, next steps (Chinese)
android-overlay/   App source (Java, Gradle)
.github/workflows/ Cloud APK build
windows-transfer/  Windows app for file transfer (Python)
docs/              Notes (AppFunctions, the three modes, file transfer design…); docs/archive/ holds Termux-era history and finished roadmaps
```

## Credits

- Offline speech recognition: [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) and SenseVoice.
- The glass effect's design was informed by the KernelSU manager's FloatingBottomBar / Lens and Kyant's AndroidLiquidGlass (design only; none of their code was copied).

## License

Apache License 2.0, see [`LICENSE`](LICENSE).
