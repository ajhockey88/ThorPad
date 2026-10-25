package com.thorpad;

import android.content.Context;
import android.content.SharedPreferences;

/** User-tunable settings, shared by the trackpad, the service and the settings screen. */
final class Prefs {
    static final String KEY_SENSITIVITY = "sensitivity";
    static final String KEY_ACCELERATION = "acceleration";
    static final String KEY_SCROLL_SPEED = "scroll_speed";
    static final String KEY_INVERT_SCROLL = "invert_scroll";
    static final String KEY_SWAP_SCREENS = "swap_screens";
    static final String KEY_POINTER_SIZE = "pointer_size";
    static final String KEY_AUTOCORRECT = "autocorrect";
    static final String KEY_TYPE_ON_RELEASE = "type_on_release";
    static final String KEY_DIM_WHEN_IDLE = "dim_when_idle";
    static final String KEY_KEYBOARD_BUTTON = "keyboard_button";
    static final String KEY_SMOOTH_SCROLL = "smooth_scroll";
    static final String KEY_STRETCH_KEYBOARD = "stretch_keyboard";
    static final String KEY_KEEP_KEYBOARD = "keep_keyboard";
    /** The user's last pick of Android keyboard was ThorPad's (or ThorPad's was seen in use). */
    private static final String KEY_WANTS_THORPAD_KEYBOARD = "wants_thorpad_keyboard";
    /** ThorPad Keyboard has a layout, so Android stops asking to configure it. */
    private static final String KEY_KEYBOARD_LAYOUT_SET = "keyboard_layout_set";
    static final String KEY_DOUBLE_SPACE = "double_space_period";
    static final String KEY_AUTO_CAPS = "auto_caps";
    static final String KEY_SHOW_HINTS = "show_hints";
    static final String KEY_LANGUAGE = "keyboard_language";
    static final String KEY_PC_MODE = "pc_mode";
    static final String KEY_PC_APPS = "pc_apps";
    /** PC controls: switch automatically for streaming apps, never, or always. */
    static final int PC_AUTO = 0, PC_NEVER = 1, PC_ALWAYS = 2;

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
    /** Use the top screen as the trackpad and put the pointer on the bottom screen. */
    boolean swapScreens() { return sp.getBoolean(KEY_SWAP_SCREENS, false); }
    float pointerSize() { return sp.getFloat(KEY_POINTER_SIZE, 1.0f); }
    /** Keyboard: fix misspelled words when a word is finished. */
    boolean autocorrect() { return sp.getBoolean(KEY_AUTOCORRECT, true); }
    /** Keyboard: two quick spaces type ". ". */
    boolean doubleSpacePeriod() { return sp.getBoolean(KEY_DOUBLE_SPACE, true); }
    /** Keyboard: capital letter at the start of each sentence. */
    boolean autoCaps() { return sp.getBoolean(KEY_AUTO_CAPS, true); }
    /** Keyboard: type when the finger lifts instead of when it lands. */
    boolean typeOnRelease() { return sp.getBoolean(KEY_TYPE_ON_RELEASE, false); }
    /** Turn the trackpad screen's brightness right down after a while without a touch. */
    boolean dimWhenIdle() { return sp.getBoolean(KEY_DIM_WHEN_IDLE, true); }
    /** A small keyboard button in the trackpad's bottom-right corner. */
    boolean keyboardButton() { return sp.getBoolean(KEY_KEYBOARD_BUTTON, false); }
    boolean smoothScroll() { return sp.getBoolean(KEY_SMOOTH_SCROLL, false); }
    boolean stretchKeyboard() { return sp.getBoolean(KEY_STRETCH_KEYBOARD, true); }
    boolean keepKeyboard() { return sp.getBoolean(KEY_KEEP_KEYBOARD, true); }
    boolean wantsThorPadKeyboard() { return sp.getBoolean(KEY_WANTS_THORPAD_KEYBOARD, false); }
    void setWantsThorPadKeyboard(boolean v) {
        if (wantsThorPadKeyboard() != v) sp.edit().putBoolean(KEY_WANTS_THORPAD_KEYBOARD, v).apply();
    }
    boolean keyboardLayoutSet() { return sp.getBoolean(KEY_KEYBOARD_LAYOUT_SET, false); }
    void setKeyboardLayoutSet() { sp.edit().putBoolean(KEY_KEYBOARD_LAYOUT_SET, true).apply(); }
    /** Gesture hints drawn along the bottom of the trackpad. */
    boolean showHints() { return sp.getBoolean(KEY_SHOW_HINTS, false); }
    /** Keyboard and app language: en, es, fr, de, pt or it. */
    String language() { return sp.getString(KEY_LANGUAGE, "en"); }
    void setLanguage(String code) {
        sp.edit().putString(KEY_LANGUAGE, code).apply();
        Lang.set(code);
    }
    int pcMode() { return sp.getInt(KEY_PC_MODE, PC_AUTO); }
    void setPcMode(int mode) { sp.edit().putInt(KEY_PC_MODE, mode).apply(); }
    /** Apps the user marked as PC streaming apps, on top of the ones ThorPad knows. */
    java.util.Set<String> pcApps() {
        return new java.util.HashSet<>(sp.getStringSet(KEY_PC_APPS, java.util.Collections.<String>emptySet()));
    }
    void setPcApp(String pkg, boolean pc) {
        java.util.Set<String> apps = pcApps();
        if (pc) apps.add(pkg); else apps.remove(pkg);
        sp.edit().putStringSet(KEY_PC_APPS, apps).apply();
    }
}
