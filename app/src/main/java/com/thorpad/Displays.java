package com.thorpad;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;

/**
 * The Thor runs its top screen as Android's default display and the bottom screen as a
 * second built-in display. "Swap screens" in settings flips that if a firmware differs.
 */
final class Displays {
    private Displays() {}

    static Display secondary(Context context) {
        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        for (Display d : dm.getDisplays()) {
            if (d.getDisplayId() != Display.DEFAULT_DISPLAY && d.getState() != Display.STATE_OFF) {
                return d;
            }
        }
        for (Display d : dm.getDisplays()) {
            if (d.getDisplayId() != Display.DEFAULT_DISPLAY) return d;
        }
        return null;
    }

    static Display primary(Context context) {
        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        return dm.getDisplay(Display.DEFAULT_DISPLAY);
    }

    /** The screen the mouse pointer lives on. */
    static Display pointerDisplay(Context context) {
        if (new Prefs(context).swapScreens()) {
            Display s = secondary(context);
            if (s != null) return s;
        }
        return primary(context);
    }

    /** The screen the trackpad should fill, or null if there is only one screen. */
    static Display trackpadDisplay(Context context) {
        if (new Prefs(context).swapScreens()) return primary(context);
        return secondary(context);
    }
}
