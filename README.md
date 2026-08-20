# AutoTyper

A minimalist, human-like auto-typer for Android (black & white theme).

AutoTyper registers as a real input method (keyboard), which is the only
root-free way to inject *genuine* keystrokes — with realistic per-key timing —
into other apps like Google Docs, Gmail, Messenger, etc. so it is not detected
as a paste.

## Features
- **Max-realism typing engine** — variable keystroke delay (bell-curve jitter),
  random typos + self-correction, rhythm pauses after punctuation, hesitation
  "thinking" pauses, and a slow warm-up start.
- **Speed (WPM) slider** (30–140) and **Humanity slider** (Robot → Messy human).
- **Movable floating panel** — drag anywhere, snaps to screen edges, collapses
  to a small pill, remembers its position. Start / Pause / Resume / Stop.
- **Live progress** — % complete + progress bar while typing.
- **Saved snippets** — save and reuse frequently-typed text.
- **Guided setup wizard** — enable keyboard, switch keyboard, overlay permission,
  battery-optimization exemption (important on Samsung / One UI).

## How it works
1. Paste your text in the app, set speed/humanity.
2. Hit **START**.
3. Switch to the **AutoTyper keyboard** (or it's already active) and tap the
   target text field.
4. AutoTyper types the text out, character by character, like a person.

> Note: Android only lets the *active* input method send keystrokes. So while
> auto-typing, AutoTyper must be the selected keyboard. Switch back to your
> normal keyboard when done (the app reminds you).

## Building
The APK is built automatically by GitHub Actions on every push to `main`.
Download the latest build from the **Actions** tab → latest run → **Artifacts**.

To build locally: `gradle assembleDebug` (Gradle 8.9, JDK 17).

## Permissions (why)
| Permission | Purpose |
|---|---|
| `BIND_INPUT_METHOD` | Lets AutoTyper act as a keyboard to inject keystrokes |
| `SYSTEM_ALERT_WINDOW` | The floating control panel |
| `FOREGROUND_SERVICE` | Keeps the panel alive (Samsung background-kill protection) |
| `POST_NOTIFICATIONS` | The ongoing panel notification |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Exemption from battery saving |
