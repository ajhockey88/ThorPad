package com.thorpad;

import android.content.Context;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

/**
 * Keeps ThorPad as Android's keyboard once the user has picked it. Android quietly falls back
 * to another keyboard (usually Gboard) when ThorPad is updated or reinstalled, or its keyboard
 * stops; with Shizuku, ThorPad switches it back when it opens and whenever the setting changes
 * while it's open. Settings > Keyboard > Keep ThorPad as the keyboard turns this off.
 */
final class KeyboardKeeper {
    private static volatile boolean running;

    private KeyboardKeeper() {}

    static String thorPadId(Context c) {
        InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        for (InputMethodInfo info : imm.getInputMethodList()) {
            if (info.getPackageName().equals(c.getPackageName())) return info.getId();
        }
        return null;
    }

    static String current(Context c) {
        return Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
    }

    /** The user picked a keyboard in ThorPad: keep ThorPad's only if it was ThorPad's. */
    static void chosen(Context c, boolean thorPad) {
        new Prefs(c).setWantsThorPadKeyboard(thorPad);
    }

    /** Switches back to ThorPad's keyboard if Android dropped it. Runs in the background. */
    static void check(final Context context) {
        final Context c = context.getApplicationContext();
        if (running) return;
        running = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    restore(c);
                } finally {
                    running = false;
                }
            }
        }, "ThorPad keep keyboard").start();
    }

    private static void restore(Context c) {
        Prefs prefs = new Prefs(c);
        String id = thorPadId(c);
        if (id == null) return;
        if (id.equals(current(c))) {
            prefs.setWantsThorPadKeyboard(true); // in use, however it got picked
            return;
        }
        if (!prefs.keepKeyboard() || !prefs.wantsThorPadKeyboard() || !ShizukuClient.hasPermission()) return;
        KeyboardLog.add("keeper: keyboard was " + current(c) + ", switching back to ThorPad");
        ShizukuClient.runShell("ime enable " + id + " && ime set " + id);
        if (!id.equals(current(c))) {
            // Some firmware ignores "ime set": write Android's setting directly.
            ShizukuClient.runShell("settings put secure default_input_method " + id);
        }
    }
}
