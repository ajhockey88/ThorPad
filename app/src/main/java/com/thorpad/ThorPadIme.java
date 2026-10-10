package com.thorpad;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/**
 * ThorPad as an Android keyboard. Picked as the keyboard in Settings, it shows nothing on the
 * top screen: when a text box there asks for a keyboard, it opens ThorPad's keyboard on the
 * bottom screen instead, and that keyboard types straight into the text box.
 */
public class ThorPadIme extends InputMethodService implements KeyOutput {
    /** Told when a text box asks for the keyboard; set while the trackpad is open. */
    interface Listener {
        void onKeyboardWanted();
        /** The top screen left its text box (or the app hid the keyboard). */
        void onKeyboardDone();
    }

    private static ThorPadIme instance;
    private static Listener listener;
    private boolean inputStarted;
    private String lastField;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Leaving one text box for another finishes one input and starts the next, so "done" waits
    // a moment and is called off if a text box starts again.
    private static final long DONE_DELAY_MS = 300;
    private final Runnable done = new Runnable() {
        @Override public void run() {
            Listener l = listener;
            if (l != null) l.onKeyboardDone();
        }
    };

    private final Runnable wanted = new Runnable() {
        @Override public void run() {
            if (inputStarted && onPointerScreen()) askForKeyboard();
        }
    };

    private void askForKeyboard() {
        Listener l = listener;
        if (l != null) {
            l.onKeyboardWanted();
        } else {
            // ThorPad isn't open: bring up the trackpad with its keyboard showing.
            LauncherActivity.openTrackpad(this, true);
        }
    }

    private void doneSoon() {
        handler.removeCallbacks(done);
        handler.postDelayed(done, DONE_DELAY_MS);
    }

    /**
     * The bottom-screen keyboard was closed: hide this (invisible) keyboard window too, so the
     * next tap on a text box asks for a keyboard again and reopens it.
     */
    /** When the text box last changed its cursor or a text box last started (uptime ms). */
    static volatile long lastTextBoxActivity;

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        lastTextBoxActivity = android.os.SystemClock.uptimeMillis();
    }

    static void bottomKeyboardClosed() {
        ThorPadIme ime = instance;
        if (ime != null && ime.isInputViewShown()) ime.requestHideSelf(0);
    }

    static void setListener(Listener l) { listener = l; }

    /** The ThorPad keyboard, while it is the keyboard for a selected text box; else null. */
    static ThorPadIme active() {
        ThorPadIme ime = instance;
        return ime != null && ime.inputStarted && ime.getCurrentInputConnection() != null ? ime : null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    public View onCreateInputView() {
        // Nothing to show on the top screen: the keys are on the bottom screen.
        return new View(this);
    }

    /**
     * The keyboard window takes no room and no touches: apps are never pushed up or covered,
     * on either screen.
     */
    @Override
    public void onComputeInsets(Insets outInsets) {
        super.onComputeInsets(outInsets);
        int h = 0;
        if (getWindow() != null && getWindow().getWindow() != null) {
            h = getWindow().getWindow().getDecorView().getHeight();
        }
        outInsets.contentTopInsets = h;
        outInsets.visibleTopInsets = h;
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION;
        outInsets.touchableRegion.setEmpty();
    }

    /** No background or navigation-bar strip: the keyboard window is fully see-through. */
    private void makeInvisible() {
        if (getWindow() == null || getWindow().getWindow() == null) return;
        android.view.Window w = getWindow().getWindow();
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        w.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        w.getDecorView().setBackgroundColor(android.graphics.Color.TRANSPARENT);
    }

    @Override
    public boolean onEvaluateFullscreenMode() { return false; }

    @Override
    public void onWindowShown() {
        super.onWindowShown();
        makeInvisible();
    }

    /** Keep answering text boxes while ThorPad's virtual USB keyboard is plugged in. */
    @Override
    public boolean onEvaluateInputViewShown() { return true; }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        boolean wasStarted = inputStarted;
        inputStarted = attribute.inputType != android.text.InputType.TYPE_NULL;
        if (inputStarted) {
            lastTextBoxActivity = android.os.SystemClock.uptimeMillis();
            handler.removeCallbacks(done);
            // A text box got focus: open the bottom keyboard, without waiting for the app to ask
            // for a keyboard window. Android passes restarting = true for every text box after
            // the first in the same app, so only a restart of the very same text box is skipped.
            String field = attribute.packageName + "/" + attribute.fieldId;
            boolean sameField = restarting && field.equals(lastField);
            lastField = field;
            if (!sameField) handler.post(wanted);
        } else {
            lastField = null; // coming back to the same text box later counts as new
            if (wasStarted) doneSoon(); // focus moved to something that isn't a text box
        }
    }

    @Override
    public void onFinishInput() {
        if (inputStarted) doneSoon();
        inputStarted = false;
        super.onFinishInput();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        doneSoon(); // the app hid the keyboard, e.g. after Search or Send
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        handler.removeCallbacks(done);
        makeInvisible();
        if (!onPointerScreen()) {
            // A text box on the trackpad's own screen (e.g. ThorPad's settings): stay out of the way.
            requestHideSelf(0);
            return;
        }
        handler.removeCallbacks(wanted); // asking once is enough
        askForKeyboard();
    }

    /** Whether the text box is on the top screen, not an app sharing the trackpad's screen. */
    private boolean onPointerScreen() {
        Display target = Displays.pointerDisplay(this);
        Display here = null;
        try {
            if (getWindow() != null && getWindow().getWindow() != null) {
                here = getWindow().getWindow().getDecorView().getDisplay();
            }
        } catch (RuntimeException ignored) {
        }
        return here == null || target == null || here.getDisplayId() == target.getDisplayId();
    }

    // ---- Typing ---------------------------------------------------------------------------

    @Override public void typeChar(char c) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(String.valueOf(c), 1);
    }

    /**
     * Deletes through the text box's connection, like commitText, so a correction's deletes and
     * its new word arrive in order. A DEL key event is queued separately and can land after the
     * new word, which left the typo in place. Boxes that show no text (terminals, games) still
     * get the key.
     */
    private void backspace(InputConnection ic) {
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            ic.commitText("", 1);
            return;
        }
        CharSequence before = ic.getTextBeforeCursor(2, 0);
        if (before != null && before.length() > 0 && ic.deleteSurroundingTextInCodePoints(1, 0)) return;
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
    }

    @Override public void special(int key) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        switch (key) {
            case BACKSPACE: backspace(ic); break;
            case ENTER:
                // Search, Go, Send, Next... or a new line in a multi-line box.
                if (!sendDefaultEditorAction(true)) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
                break;
            case TAB: sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB); break;
            case ESCAPE: sendDownUpKeyEvents(KeyEvent.KEYCODE_ESCAPE); break;
            case LEFT: sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT); break;
            case RIGHT: sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT); break;
            case UP: sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP); break;
            case DOWN: sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN); break;
            case CLEAR_ALL:
                ic.performContextMenuAction(android.R.id.selectAll);
                ic.commitText("", 1);
                break;
        }
    }
}
