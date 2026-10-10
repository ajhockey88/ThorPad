# <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="48" align="center" alt="ThorPad icon"> ThorPad

[![Downloads](https://img.shields.io/github/downloads/ajhockey88/ThorPad/total?style=for-the-badge&logo=github&label=Downloads&color=yellowgreen)](https://github.com/ajhockey88/ThorPad/releases)
[![Latest release](https://img.shields.io/github/v/release/ajhockey88/ThorPad?style=for-the-badge&logo=github&label=Latest%20release&color=blue)](https://github.com/ajhockey88/ThorPad/releases/latest)
[![Reddit](https://img.shields.io/badge/Reddit-View%20post-ff4500?style=for-the-badge&logo=reddit&logoColor=white)](https://www.reddit.com/r/AynThor/comments/1x230sz/thorpad_a_trackpad_and_keyboard_for_the_ayn_thors/)

🎮 A simple trackpad with bonus features designed for the AYN Thor.

🖱️ Turns the AYN Thor's bottom screen into a laptop-style trackpad for a mouse pointer on the top screen, plus a full-screen keyboard ⌨️ for typing.

## 📸 Screens

Everything happens on the Thor's bottom screen (1240 × 1080).

| 🖱️ Trackpad | ⌨️ Keyboard with autocorrect |
|---|---|
| <img src="docs/screenshots/trackpad.png" width="400" alt="Trackpad screen"> | <img src="docs/screenshots/keyboard.png" width="400" alt="Keyboard with an autocorrect suggestion"> |
| 🔣 Numbers and symbols | ⚙️ Settings |
| <img src="docs/screenshots/symbols.png" width="400" alt="Symbols keyboard"> | <img src="docs/screenshots/settings.png" width="400" alt="Settings screen"> |
| 🧹 Hold ⌫ for Clear all | 📳 Haptics settings |
| <img src="docs/screenshots/clear-all.png" width="400" alt="Keyboard with backspace held and the red Clear all key"> | <img src="docs/screenshots/haptics.png" width="400" alt="Haptics settings"> |
| ⌨️ Choose keyboard | 🚀 First-run setup |
| <img src="docs/screenshots/choose-keyboard.png" width="400" alt="Choosing ThorPad keyboard"> | <img src="docs/screenshots/setup.png" width="400" alt="Setup screen"> |

## 👆 Gestures

| On the bottom screen | On the top screen |
|---|---|
| ☝️ Slide one finger | Moves the pointer (faster flicks travel further) |
| 👆 Tap | Click (a tap still counts if your finger rolls a little) |
| ✊ Tap, then quickly touch again, pause a moment, and slide | Drag (press, move, release) |
| ✌️ Two fingers up or down | Mouse wheel (real wheel notches in mouse mode) |
| 🤞 Two-finger tap, or press and hold | Right click in mouse mode; Back / long press in touch mode |

The bar along the top has ⌨️ Keyboard, 🏠 Home, 🗂️ Apps (recents), ⚙️ Settings and ✖️ Close.

📳 **Haptics:** you feel a buzz for every action: taps, right clicks, the start and end of a drag,
press and hold, each scroll-wheel notch, every keyboard key, Clear all, and the top-bar buttons.
In Settings > Haptics, each action gets its own feel (Off, Tick, Click, Heavy, Double or Buzz) and
strength, and every change plays a preview.

## ⌨️ Keyboard

**Keyboard** turns the whole bottom screen into a keyboard. The keys touch edge to edge, with
only a hairline between them, so the whole screen can be tapped. **✕ Close** returns to the trackpad.

- ⚡ Keys type the moment your finger lands (Settings can switch this to "type when the finger
  lifts", which lets you slide onto the right key). Each finger is tracked separately, so
  pressing the next key before lifting the last still counts. Every press flashes and buzzes.
- 🔣 **?123** and **=\<** switch to numbers, symbols, Esc, Tab and arrow keys. Tap ⇧ once for one
  capital letter, or twice quickly for caps lock. Hold ⌫ to keep deleting.
- 🧹 **Clear all:** while you hold ⌫, the space bar turns red and says **Clear all**. Tap it to wipe the whole text box.
- 🪄 **Opens by itself:** in Settings > **Choose keyboard**, pick **ThorPad keyboard**. Then whenever a
  text box on the top screen asks for a keyboard, ThorPad's keyboard opens on the bottom screen (and
  the trackpad opens too if it was closed), with no other keyboard popping up on the top screen. Keys
  go straight into the text box. With Shizuku, ThorPad switches keyboards for you; without it,
  Android asks you to turn the keyboard on and pick it. Text boxes drawn inside a Windows game in
  GameNative aren't Android text boxes, so open the keyboard yourself there.
- 🔌 In mouse mode, ThorPad plugs in a virtual USB keyboard while the keyboard is open, so keys
  reach games and apps like a real keyboard. In touch mode, text goes into the selected text box.
- ✨ **Autocorrect** (on by default, switch in Settings) fixes a misspelled word when you finish it
  with a space or punctuation. While you type, the strip shows "→ correction"; tap the strip to keep
  your spelling. Pressing backspace right after a correction undoes it. Either way, ThorPad
  remembers that word and won't correct it again.

## ⚙️ Settings

- 🏎️ **Pointer speed** and **pointer acceleration**
- 📜 **Scroll speed**, and **classic scroll direction** (fingers down scrolls down, like a mouse wheel)
- 🔍 **Pointer size** (touch mode's pointer)
- 🔁 **Swap screens** (pointer on the bottom, trackpad on the top)
- ✨ **Autocorrect** on or off
- ⌨️ **Choose keyboard:** which keyboard opens for top-screen text boxes (pick ThorPad keyboard to have this one open by itself)
- ✋ **Type when the finger lifts** (off by default, so keys type the instant you touch them)
- 📳 **Haptics:** a feel and strength for each action, with a preview as you change them
- 🔄 **Check for updates** (at the bottom): finds a newer ThorPad release on GitHub, downloads it
  and installs it. With Shizuku it installs on its own; without it, Android shows its install prompt
  (the first time, allow ThorPad to install apps).

## 🔀 Two modes

🖱️ **Mouse mode (needed for GameNative / Winlator games).** ThorPad plugs in a
virtual USB mouse using Android's built-in `uinput` tool, started through
[Shizuku](https://github.com/RikkaApps/Shizuku). Android and every app see a real mouse: the
system cursor shows on the top screen, games get real mouse movement and button presses, and
two-finger scrolling sends real scroll-wheel notches (GameNative turns these into Windows
mouse-wheel events).

👆 **Touch mode (no Shizuku).** An accessibility service draws its own pointer and turns clicks and
scrolls into touch gestures. Fine for ordinary Android apps, but games in GameNative read those
as touches, not as a mouse.

ThorPad uses mouse mode whenever Shizuku is running and has allowed it, and falls back to touch mode otherwise.

## 📲 Install

1. Copy `ThorPad.apk` to the Thor and open it to install.
2. For mouse mode: install Shizuku, start it (Shizuku > Start via Wireless debugging), open
   ThorPad, tap **Allow ThorPad in Shizuku** and allow it. Shizuku has to be started again after each reboot.
3. For touch mode: tap **Accessibility settings** in ThorPad and turn on **ThorPad trackpad**.
   If Android says the setting is restricted: Settings > Apps > ThorPad > ⋮ > **Allow restricted settings**, then try again.

## 🛠️ How it works

The Thor's own mouse mode is driven by AYN's input engine and the controller sticks; other
apps can only switch it on or off, not move its cursor. So ThorPad brings its own mouse.

Mouse mode: Shizuku runs `uinput -` as the shell user, and ThorPad streams it JSON commands
that register a USB mouse (BTN_LEFT/RIGHT/MIDDLE, REL_X/Y, REL_WHEEL/HWHEEL, plus
Back/Home/App-switch keys) and inject evdev events. While the keyboard is open, a second
`uinput` device acts as a USB keyboard (US layout); it is unplugged when the keyboard closes so
the top screen's own touch keyboard keeps working. Clicks hold the button about 90 ms, like a real
mouse, so games that check the button once per frame see them, and each click first nudges the
pointer a pixel and back to wake Android's cursor, which hides whenever the bottom screen is touched. ThorPad talks to Shizuku through a small
built-in client (`ShizukuClient`, `ShizukuBinderProvider`) using Shizuku's AIDL interface.

Touch mode:

- the pointer is an accessibility overlay window on the top display;
- clicks, long presses, drags and scrolls are touch gestures injected on the top display
  at the pointer (`dispatchGesture` with `setDisplayId`); scrolling holds a virtual finger
  under the pointer and moves it with your two fingers, so lists and pages scroll with
  momentum like a real swipe;
- the trackpad window never takes input focus, so the top-screen app keeps the gamepad
  and keyboard, and Back/Home/Apps act on the top screen.

🔒 ThorPad only goes online when you tap **Check for updates**, to read its GitHub releases and
download the new APK. Nothing else is sent anywhere. In touch mode it reads only the selected text box, to type into it, and nothing else on screen.

## 🙏 Credits

The autocorrect dictionary (`app/src/main/assets/words.txt`, built by `tools/make_dictionary.py`)
takes its spellings from SCOWL's American English word list and its word frequencies from
[wordfreq](https://github.com/rspeer/wordfreq) (data licensed CC BY-SA 4.0).

## 🏗️ Building

`./build.sh` builds `build/ThorPad.apk` with Ubuntu's packaged Android tools (see the
script header); it downloads the two jars it compiles against from Maven Central. The project
also has standard Gradle files, so it can be opened and built in Android Studio.

The signing key is not in this repository. `build.sh` reads it from `keystore/thorpad.jks`
(or `$KEYSTORE`) and creates a new one if missing; an APK signed with a different key has to
be uninstalled before it can replace an older install.
