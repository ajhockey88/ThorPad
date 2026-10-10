package com.thorpad;

import android.content.Context;
import android.content.SharedPreferences;

/** User-tunable settings, shared by the trackpad, the service and the settings screen. */
final class Prefs {
    static final String KEY_SENSITIVITY = "sensitivity";
    static final String KEY_ACCELERATION = "acceleration";
    static final String KEY_SCROLL_SPEED = "scroll_speed";
    static final String KEY_INVERT_SCROLL = "invert_scroll";
    static final String KEY_TAP_TO_CLICK = "tap_to_click";
    static final String KEY_SWAP_SCREENS = "swap_screens";
    static final String KEY_POINTER_SIZE = "pointer_size";
    static final String KEY_AUTOCORRECT = "autocorrect";
    static final String KEY_TYPE_ON_RELEASE = "type_on_release";

    private final SharedPreferences sp;

    Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences("thorpad", Context.MODE_PRIVATE);
    }

    SharedPreferences raw() { return sp; }

    /** Pointer pixels per trackpad pixel at slow finger speed. */
    float sensitivity() { return sp.getFloat(KEY_SENSITIVITY, 1.8f); }
    /** How much faster the pointer gets when the finger moves fast. 0 = linear. */
    float acceleration() { return sp.getFloat(KEY_ACCELERATION, 0.6f); }
    /** Top-screen scroll distance per trackpad pixel of two-finger movement. */
    float scrollSpeed() { return sp.getFloat(KEY_SCROLL_SPEED, 2.0f); }
    /** false = natural (content follows fingers), true = classic scroll wheel direction. */
    boolean invertScroll() { return sp.getBoolean(KEY_INVERT_SCROLL, false); }
    boolean tapToClick() { return sp.getBoolean(KEY_TAP_TO_CLICK, true); }
    /** Use the top screen as the trackpad and put the pointer on the bottom screen. */
    boolean swapScreens() { return sp.getBoolean(KEY_SWAP_SCREENS, false); }
    float pointerSize() { return sp.getFloat(KEY_POINTER_SIZE, 1.0f); }
    /** Keyboard: fix misspelled words when a word is finished. */
    boolean autocorrect() { return sp.getBoolean(KEY_AUTOCORRECT, true); }
    /** Keyboard: type when the finger lifts instead of when it lands. */
    boolean typeOnRelease() { return sp.getBoolean(KEY_TYPE_ON_RELEASE, false); }
}
