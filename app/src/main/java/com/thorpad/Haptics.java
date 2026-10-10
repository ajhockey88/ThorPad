package com.thorpad;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Plays the buzz for each action, with the feel and strength picked for it in Settings.
 */
final class Haptics {
    // Actions.
    static final int CLICK = 0, RIGHT_CLICK = 1, DRAG_START = 2, DRAG_END = 3, HOLD = 4,
            SCROLL = 5, KEY = 6, CLEAR_ALL = 7, BAR = 8, NAVIGATE = 9;
    static final String[] ACTION_NAMES = {"Click", "Right click", "Drag start", "Drag end",
            "Press and hold", "Scroll wheel notch", "Keyboard key", "Clear all", "Top bar buttons", "Back / forward swipe"};
    private static final String[] ACTION_KEYS = {"click", "right_click", "drag_start", "drag_end",
            "hold", "scroll", "key", "clear_all", "bar", "navigate"};

    // Feels.
    static final int OFF = 0, TICK = 1, TAP = 2, HEAVY = 3, DOUBLE = 4, BUZZ = 5;
    static final String[] FEEL_NAMES = {"Off", "Tick", "Click", "Heavy", "Double", "Buzz"};

    private static final int[] DEFAULT_FEEL = {TAP, DOUBLE, HEAVY, TICK, HEAVY, TICK, TAP, HEAVY, TAP, TAP};
    private static final int[] DEFAULT_STRENGTH = {60, 60, 80, 50, 80, 35, 45, 100, 50, 70};

    private Haptics() {}

    static String feelKey(int action) { return "haptic_feel_" + ACTION_KEYS[action]; }

    static String strengthKey(int action) { return "haptic_strength_" + ACTION_KEYS[action]; }

    static int feel(SharedPreferences sp, int action) {
        return sp.getInt(feelKey(action), DEFAULT_FEEL[action]);
    }

    /** 0 to 100. */
    static int strength(SharedPreferences sp, int action) {
        return sp.getInt(strengthKey(action), DEFAULT_STRENGTH[action]);
    }

    /** Buzzes for an action. */
    static void play(Context context, int action) {
        SharedPreferences sp = new Prefs(context).raw();
        play(context, feel(sp, action), strength(sp, action));
    }

    /** Buzzes with a given feel and strength, e.g. to preview a setting. */
    static void play(Context context, int feel, int strength) {
        if (feel == OFF || strength <= 0) return;
        Vibrator v = vibrator(context);
        if (v == null || !v.hasVibrator()) return;
        VibrationEffect effect = effect(v, feel, Math.min(strength, 100) / 100f);
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH));
        } else {
            v.vibrate(effect, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build());
        }
    }

    private static Vibrator vibrator;

    private static Vibrator vibrator(Context context) {
        if (vibrator == null) {
            VibratorManager vm = (VibratorManager) context.getApplicationContext()
                    .getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vm != null) vibrator = vm.getDefaultVibrator();
        }
        return vibrator;
    }

    /** Whether the vibration motor can buzz softer or harder, not just on or off. */
    static boolean hasStrengthControl(Context context) {
        Vibrator v = vibrator(context);
        return v != null && v.hasAmplitudeControl();
    }

    // Base length of each feel, in ms.
    private static final int[] FEEL_MS = {0, 10, 20, 40, 20, 80};

    /**
     * Strength sets the vibration's amplitude where the motor supports it, and its length
     * too, so it still makes a difference on motors that only switch on and off.
     */
    private static VibrationEffect effect(Vibrator v, int feel, float scale) {
        boolean amplitude = v.hasAmplitudeControl();
        int amp = amplitude ? Math.round(40 + scale * 215) : VibrationEffect.DEFAULT_AMPLITUDE;
        float lengthScale = amplitude ? 0.6f + 0.8f * scale : 0.3f + 1.4f * scale;
        long ms = Math.max(3, Math.round(FEEL_MS[feel] * lengthScale));
        if (feel == DOUBLE) {
            return VibrationEffect.createWaveform(new long[] {0, ms, 70, ms}, new int[] {0, amp, 0, amp}, -1);
        }
        return VibrationEffect.createOneShot(ms, amp);
    }
}
