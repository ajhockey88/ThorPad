package com.thorpad;

import android.inputmethodservice.InputMethodService;
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
    interface Listener { void onKeyboardWanted(); }

    private static ThorPadIme instance;
    private static Listener listener;
    private boolean inputStarted;

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
        View v = new View(this);
        v.setMinimumHeight(1);
        return v;
    }

    @Override
    public boolean onEvaluateFullscreenMode() { return false; }

    /** Keep answering text boxes while ThorPad's virtual USB keyboard is plugged in. */
    @Override
    public boolean onEvaluateInputViewShown() { return true; }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        inputStarted = attribute.inputType != android.text.InputType.TYPE_NULL;
    }

    @Override
    public void onFinishInput() {
        inputStarted = false;
        super.onFinishInput();
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (restarting) return;
        Listener l = listener;
        if (l != null) {
            l.onKeyboardWanted();
        } else if (onPointerScreen()) {
            // ThorPad isn't open: bring up the trackpad with its keyboard showing.
            LauncherActivity.openTrackpad(this, true);
        }
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

    @Override public void special(int key) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        switch (key) {
            case BACKSPACE: sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); break;
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
