# PhoneAI Overlay

English | [繁體中文](README.md)

An AI phone assistant for **rooted Android phones**, built as a single app. Tell it what to do by voice or text and it can open apps, toggle settings, look at the screen and tap, swipe and type on its own, and search the web when it needs fresh information.

The whole project is one app: **no PC, no Termux, no Python scripts**.

> This is a personal experiment. Many features have only been verified on the author's own phone, so it may not work on every device. **If you want to continue development, read [`CLAUDE.md`](CLAUDE.md) and [`HANDOFF.md`](HANDOFF.md) first** (both are in Chinese).

## What it can do

| Feature | Description |
|---|---|
| Assistant panel | Type or speak. Open it from the app's home screen, a quick-settings tile, a long press of the power button (set it as the default digital assistant) or a floating button on the screen edge |
| Fixed commands (root, instant) | Turn Wi-Fi / Bluetooth on or off, go back, change volume, launch any app by name |
| Chat and quick answers | Ask questions directly; simple ones such as battery, storage and time are answered locally with no network |
| Screen automation | Claude looks at screenshots and taps, swipes, drags and types step by step to finish what you asked (up to 8 steps, escalating to a stronger model when stuck) |
| Web search | Claude can search when it needs up-to-date information (limited per task) |
| Memory and schedules | To-dos and notes, and scheduled tasks like "remind me every morning at 8" |
| Voice recognition | Offline SenseVoice model (downloaded inside the app); the text is sent after a short countdown so you can edit it |
| While it runs | A ripple at every tap, a "Stop" button in the notification, and a Now Bar capsule on Samsung phones (optional) |
| Bottom bar and glass effect | A liquid-glass bottom bar with a lens button; theme colour and interface language can be changed in Settings |

## Requirements

- A rooted phone running Android 13 or later
- An [Anthropic API key](https://console.anthropic.com/) (stored only in the app's private folder on the phone, never in the repo)
- Accessibility service and overlay permissions (the in-app "Setup guide" walks you through them)

## Install

1. Download the latest `app-debug.apk` from this repo's **Releases** page and install it. The APK is signed with a fixed key, so later versions install over the old one; "Features → Maintenance → Check for updates" in the app can also update it.
2. Open the app and follow the setup guide: root access, overlay, accessibility service, notifications.
3. Paste your key under "Features → Maintenance → Claude API Key".
4. Try typing "turn on Bluetooth" or "open the Wi-Fi page in Settings" in the home screen input box.

## Safety and privacy

- It taps the screen with root privileges. The app has no network-facing entry point; internally there is only a connection bound to 127.0.0.1 that also requires a token readable only by root.
- **Sensitive actions always show a confirmation dialog** (payment, transfer, deletion, installation, sending messages, login, passwords); no response counts as "no".
- During screen automation, **a screenshot is sent to Anthropic at every step**. Do not use it on banking, private messaging or other personal screens.
- Never paste your API key into a chat or commit it to git. Screenshots, logs and model files are excluded by `.gitignore`.

## Build it yourself

```bash
cd android-overlay
gradle assembleDebug          # needs Android SDK 36 and Gradle 8.14 or newer
```

Release APKs are built by GitHub Actions (`.github/workflows/android.yml`) and published to Releases.

## Project layout

```
CLAUDE.md          Notes for Claude Code (read this first, Chinese)
HANDOFF.md         Full handoff: goals, status table, decisions, next steps (Chinese)
android-overlay/   App source (Java, Gradle)
.github/workflows/ Cloud APK build
docs/              Research and decision notes, troubleshooting (some are history from the Termux era)
```

## Credits

- Offline speech recognition: [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) and SenseVoice.
- The glass effect's design was informed by the KernelSU manager's FloatingBottomBar / Lens and Kyant's AndroidLiquidGlass (design only; none of their code was copied).

## License

Apache License 2.0, see [`LICENSE`](LICENSE).
