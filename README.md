# ThorPad

A simple trackpad with bonus features designed for the AYN Thor.
Turns the AYN Thor's bottom screen into a laptop-style trackpad for a mouse pointer on the top screen.

## Gestures

| On the bottom screen | On the top screen |
|---|---|
| Slide one finger | Moves the pointer (faster flicks travel further) |
| Tap | Click |
| Tap, then touch again and slide | Drag (press, move, release) |
| Two fingers up or down | Mouse wheel (real wheel notches in mouse mode) |
| Two-finger tap, or press and hold | Right click in mouse mode; Back / long press in touch mode |

The bar along the top has Back, Home, Apps (recents), Settings and Close.
Settings has pointer speed, acceleration, scroll speed, pointer size, tap to click,
classic (mouse-wheel) scroll direction, and a swap-screens option.

## Two modes

**Mouse mode (needed for GameNative / Winlator games such as Bloons TD6).** ThorPad plugs in a
virtual USB mouse using Android's built-in `uinput` tool, started through
[Shizuku](https://github.com/RikkaApps/Shizuku). Android and every app see a real mouse: the
system cursor shows on the top screen, games get real mouse movement and button presses, and
two-finger scrolling sends real scroll-wheel notches (GameNative turns these into Windows
mouse-wheel events).

**Touch mode (no Shizuku).** An accessibility service draws its own pointer and turns clicks and
scrolls into touch gestures. Fine for ordinary Android apps, but games in GameNative read those
as touches, not as a mouse.

ThorPad uses mouse mode whenever Shizuku is running and has allowed it, and falls back to touch mode otherwise.

## Install

1. Copy `ThorPad.apk` to the Thor and open it to install.
2. For mouse mode: install Shizuku, start it (Shizuku > Start via Wireless debugging), open
   ThorPad, tap **Allow ThorPad in Shizuku** and allow it. Shizuku has to be started again after each reboot.
3. For touch mode: tap **Accessibility settings** in ThorPad and turn on **ThorPad trackpad**.
   If Android says the setting is restricted: Settings > Apps > ThorPad > ⋮ > **Allow restricted settings**, then try again.

## How it works

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

ThorPad does not read screen contents and has no internet permission.

## Building

`./build.sh` builds `build/ThorPad.apk` with Ubuntu's packaged Android tools (see the
script header); it downloads the two jars it compiles against from Maven Central. The project
also has standard Gradle files, so it can be opened and built in Android Studio.

The signing key is not in this repository. `build.sh` reads it from `keystore/thorpad.jks`
(or `$KEYSTORE`) and creates a new one if missing; an APK signed with a different key has to
be uninstalled before it can replace an older install.
