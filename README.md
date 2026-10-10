# <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="48" align="center" alt="ThorPad icon"> ThorPad

🎮 A simple trackpad with bonus features designed for the AYN Thor.

🖱️ Turns the AYN Thor's bottom screen into a laptop-style trackpad for a mouse pointer on the top screen, plus a full-screen keyboard ⌨️ for typing.

## 👆 Gestures

| On the bottom screen | On the top screen |
|---|---|
| ☝️ Slide one finger | Moves the pointer (faster flicks travel further) |
| 👆 Tap | Click |
| ✊ Tap, then touch again and slide | Drag (press, move, release) |
| ✌️ Two fingers up or down | Mouse wheel (real wheel notches in mouse mode) |
| 🤞 Two-finger tap, or press and hold | Right click in mouse mode; Back / long press in touch mode |

The bar along the top has ⌨️ Keyboard, 🏠 Home, 🗂️ Apps (recents), ⚙️ Settings and ✖️ Close.

## ⌨️ Keyboard

**Keyboard** turns the whole bottom screen into a keyboard. The keys touch edge to edge, with
only a hairline between them, so the whole screen can be tapped. **✕ Close** returns to the trackpad.

- ⚡ Keys type the moment your finger lands (Settings can switch this to "type when the finger
  lifts", which lets you slide onto the right key). Each finger is tracked separately, so
  pressing the next key before lifting the last still counts. Every press flashes and buzzes.
- 🔣 **?123** and **=\<** switch to numbers, symbols, Esc, Tab and arrow keys. Tap ⇧ once for one
  capital letter, or twice quickly for caps lock. Hold ⌫ to keep deleting.
- 🔌 In mouse mode, ThorPad plugs in a virtual USB keyboard while the keyboard is open, so keys
  reach games and apps like a real keyboard. In touch mode, text goes into the selected text box.
- ✨ **Autocorrect** (on by default, switch in Settings) fixes a misspelled word when you finish it
  with a space or punctuation. While you type, the strip shows "→ correction"; tap the strip to keep
  your spelling. Pressing backspace right after a correction undoes it. Either way, ThorPad
  remembers that word and won't correct it again.

## ⚙️ Settings

Settings has pointer speed, acceleration, scroll speed, pointer size, tap to click,
classic (mouse-wheel) scroll direction, and a swap-screens option.

## 🔀 Two modes

🖱️ **Mouse mode (needed for GameNative / Winlator games such as Bloons TD6).** ThorPad plugs in a
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
Back/Home/App-switch keys) and inject evdev events. ThorPad talks to Shizuku through a small
built-in client (`ShizukuClient`, `ShizukuBinderProvider`) using Shizuku's AIDL interface.

Touch mode:

- the pointer is an accessibility overlay window on the top display;
- clicks, long presses, drags and scrolls are touch gestures injected on the top display
  at the pointer (`dispatchGesture` with `setDisplayId`); scrolling holds a virtual finger
  under the pointer and moves it with your two fingers, so lists and pages scroll with
  momentum like a real swipe;
- the trackpad window never takes input focus, so the top-screen app keeps the gamepad
  and keyboard, and Back/Home/Apps act on the top screen.

🔒 ThorPad has no internet permission. In touch mode it reads only the selected text box, to type into it, and nothing else on screen.

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
