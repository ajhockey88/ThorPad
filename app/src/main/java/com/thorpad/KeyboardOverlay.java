package com.thorpad;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * ThorPad's keyboard on the bottom screen without opening the trackpad. Opening an activity
 * there moves Android's focus away from the top screen, and the text box stops listening to
 * the keyboard. This window never takes focus, so the text box keeps it and every key goes
 * straight in through ThorPad's Android keyboard. Whatever was on the bottom screen stays put.
 */
final class KeyboardOverlay implements KeyboardView.Listener {
    interface Listener {
        /** The keyboard's Close key was pressed. */
        void onOverlayClosed();
    }

    private final Context context;
    private final Listener listener;
    private WindowManager windows;
    private KeyboardView keyboard;
    /** When a key on this keyboard was last touched (uptime ms). */
    volatile long lastTouchAt;

    KeyboardOverlay(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    static boolean allowed(Context context) {
        return Settings.canDrawOverlays(context);
    }

    boolean isShowing() { return keyboard != null; }

    /** Docked keyboards take this share of the bottom screen's height. */
    static final float DOCKED_HEIGHT = 0.52f;
    private boolean docked;

    boolean isDocked() { return keyboard != null && docked; }

    /** Shows the keyboard filling the trackpad's screen, typing into output. False if it can't. */
    boolean show(KeyOutput output) { return show(output, false); }

    /**
     * Shows the keyboard on the trackpad's screen, typing into output: filling it for a text box
     * on the top screen, or docked along its bottom half for a text box in an app on that screen.
     * False if it can't.
     */
    boolean show(KeyOutput output, boolean dock) {
        if (keyboard != null && docked != dock) hide();
        if (keyboard != null) return true;
        Display display = Displays.trackpadDisplay(context);
        if (display == null || !allowed(context)) return false;
        try {
            Context screen = context.createDisplayContext(display)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
            WindowManager wm = (WindowManager) screen.getSystemService(Context.WINDOW_SERVICE);
            KeyboardView view = new KeyboardView(screen);
            view.setOnTouchListener(new View.OnTouchListener() {
                @Override public boolean onTouch(View v, MotionEvent ev) {
                    if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) lastTouchAt = SystemClock.uptimeMillis();
                    return false; // the keyboard still handles the touch
                }
            });
            Prefs prefs = new Prefs(context);
            view.reset();
            view.setTypeOnRelease(prefs.typeOnRelease());
            view.setSentenceHelp(prefs.doubleSpacePeriod(), prefs.autoCaps());
            view.setAutocorrect(prefs.autocorrect() ? Autocorrect.get(context) : null);
            view.setListener(this);
            view.setOutput(output);
            view.setDocked(dock);
            android.graphics.Point size = new android.graphics.Point();
            display.getRealSize(size);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    dock ? Math.round(size.y * DOCKED_HEIGHT) : WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.OPAQUE);
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            lp.gravity = android.view.Gravity.BOTTOM;
            lp.setTitle("ThorPad keyboard");
            wm.addView(view, lp);
            windows = wm;
            keyboard = view;
            docked = dock;
            KeyboardLog.add("overlay: shown on display " + display.getDisplayId() + (dock ? ", docked" : ""));
            return true;
        } catch (RuntimeException e) {
            KeyboardLog.add("overlay: failed " + e);
            return false;
        }
    }

    void hide() {
        KeyboardView view = keyboard;
        if (view == null) return;
        keyboard = null;
        view.setOutput(null);
        view.setAutocorrect(null);
        try {
            windows.removeView(view);
        } catch (RuntimeException ignored) {
        }
        KeyboardLog.add("overlay: hidden");
    }

    @Override public void onCloseKeyboard() {
        hide();
        listener.onOverlayClosed();
    }
}
