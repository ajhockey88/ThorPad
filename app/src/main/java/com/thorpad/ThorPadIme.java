package com.thorpad;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/**
 * ThorPad as an Android keyboard. Picked as the keyboard in Settings, it shows nothing on the
 * top screen: when a text box there asks for a keyboard, it shows ThorPad's keyboard on the
 * bottom screen instead (in the trackpad, or on its own when the trackpad is closed), and that
 * keyboard types straight into the text box.
 */
public class ThorPadIme extends InputMethodService implements KeyOutput, TextContext {
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
            KeyboardLog.add("done, trackpad " + (l != null ? "open" : "closed"));
            hideOverlay();
            if (l != null) l.onKeyboardDone();
        }
    };

    private final Runnable wanted = new Runnable() {
        @Override public void run() {
            if (inputStarted && !isOwnScreen(getCurrentInputEditorInfo())) askForKeyboard();
        }
    };

    private void askForKeyboard() {
        Listener l = listener;
        KeyboardLog.add("ask for keyboard, trackpad " + (l != null ? "open" : "closed"));
        if (l != null) {
            hideOverlay();
            l.onKeyboardWanted();
        } else {
            showOverlay();
        }
    }

    // ---- Keyboard on the bottom screen while the trackpad is closed ------------------------

    private KeyboardOverlay overlay;
    private TouchWatcher overlayTaps;

    /**
     * ThorPad isn't open: show just the keyboard on the bottom screen, in a window that never
     * takes focus, so the top screen's text box keeps listening. Opening the trackpad instead
     * pulled focus to the bottom screen and the typed text went nowhere.
     */
    private void showOverlay() {
        if (overlay == null) {
            overlay = new KeyboardOverlay(this, new KeyboardOverlay.Listener() {
                @Override public void onOverlayClosed() {
                    stopOverlayTaps();
                    if (isInputViewShown()) requestHideSelf(0);
                }
            });
        }
        if (!ShizukuClient.hasPermission()) {
            // No way to check which screen the text box is on: show it unless that's not allowed.
            if (!overlay.isShowing() && !overlay.show(overlayKeys)) openTrackpadInstead();
            else if (overlay.isShowing()) watchOverlayTaps();
            return;
        }
        final String field = lastField;
        final EditorInfo info = getCurrentInputEditorInfo();
        final String pkg = info != null ? info.packageName : null;
        new Thread(new Runnable() {
            @Override public void run() {
                // Only a text box on the top screen gets the keyboard. One in an app on the bottom
                // screen would be covered by it, and every tap there turned into a key press.
                android.view.Display top = Displays.pointerDisplay(ThorPadIme.this);
                String[] app = top != null ? TopApp.on(top.getDisplayId()) : null;
                final boolean onTop = app == null || pkg == null || app[0].equals(pkg);
                if (onTop && !KeyboardOverlay.allowed(ThorPadIme.this)) {
                    // Drawing over other apps isn't allowed yet: Shizuku can allow it, once.
                    ShizukuClient.runShell("appops set " + getPackageName() + " SYSTEM_ALERT_WINDOW allow");
                }
                handler.post(new Runnable() {
                    @Override public void run() {
                        if (!inputStarted || listener != null || field == null || !field.equals(lastField)) return;
                        if (!onTop) {
                            KeyboardLog.add("overlay: " + pkg + " isn't on the top screen");
                            hideOverlay();
                            return;
                        }
                        if (overlay.isShowing() || overlay.show(overlayKeys)) watchOverlayTaps();
                        else openTrackpadInstead();
                    }
                });
            }
        }, "ThorPad overlay check").start();
    }

    /** Keys from the bottom-screen keyboard: dropped (and the keyboard put away) without a text box. */
    private final KeyOutput overlayKeys = new OverlayKeys();

    private final class OverlayKeys implements KeyOutput, TextContext {
        @Override public boolean capitalizeNext() { return active() != null && ThorPadIme.this.capitalizeNext(); }
        @Override public boolean allowsCorrections() { return ThorPadIme.this.allowsCorrections(); }
        @Override public CharSequence textBefore(int n) { return active() != null ? ThorPadIme.this.textBefore(n) : null; }
        @Override public boolean isPassword() { return ThorPadIme.this.isPassword(); }

        @Override public void typeChar(char c) {
            if (active() == null) { strayKey(); return; }
            ThorPadIme.this.typeChar(c);
        }

        @Override public void special(int key) {
            if (active() == null) { strayKey(); return; }
            ThorPadIme.this.special(key);
        }
    }

    private void strayKey() {
        KeyboardLog.add("overlay: key with no text box, closing");
        hideOverlay();
    }

    private void openTrackpadInstead() {
        KeyboardLog.add("overlay not possible, opening trackpad");
        LauncherActivity.openTrackpad(this, true);
    }

    private void hideOverlay() {
        stopOverlayTaps();
        if (overlay != null) overlay.hide();
    }

    /** A tap on the top screen outside the text box closes the keyboard, as in the trackpad. */
    private void watchOverlayTaps() {
        if (overlayTaps == null) {
            overlayTaps = new TouchWatcher(new TouchWatcher.Listener() {
                @Override public void onTap(String device, final long downAt) {
                    // Taps on the keyboard itself are the bottom screen's.
                    if (overlay == null || Math.abs(downAt - overlay.lastTouchAt) < 250) return;
                    handler.postDelayed(new Runnable() {
                        @Override public void run() {
                            if (overlay == null || !overlay.isShowing()) return;
                            if (Math.abs(downAt - overlay.lastTouchAt) < 250) return;
                            if (lastTextBoxActivity < downAt) {
                                KeyboardLog.add("overlay: tap outside the text box");
                                hideOverlay();
                                if (isInputViewShown()) requestHideSelf(0);
                            }
                        }
                    }, 350);
                }
            });
        }
        overlayTaps.start();
    }

    private void stopOverlayTaps() {
        if (overlayTaps != null) overlayTaps.stop();
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
        long now = android.os.SystemClock.uptimeMillis();
        if (newSelStart == 0 && newSelEnd == 0 && (oldSelStart > 0 || oldSelEnd > 0)
                && now - lastOwnEdit > OWN_EDIT_MS && isEmpty()) {
            // The text box emptied without ThorPad's keys doing it: the app sent the message
            // (a Send button on the top screen, say). The keyboard's job is done.
            KeyboardLog.add("text sent");
            finished();
            return;
        }
        lastTextBoxActivity = now;
    }

    private static final long OWN_EDIT_MS = 400;
    /** When ThorPad's keys last changed the text box. */
    private long lastOwnEdit;

    private boolean isEmpty() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return false;
        CharSequence before = ic.getTextBeforeCursor(1, 0), after = ic.getTextAfterCursor(1, 0);
        return before != null && after != null && before.length() == 0 && after.length() == 0;
    }

    /** The text was sent: close the bottom keyboard now. */
    private void finished() {
        handler.removeCallbacks(done);
        hideOverlay();
        Listener l = listener;
        if (l != null) l.onKeyboardDone();
    }

    private boolean closesOnEnter() {
        EditorInfo info = getCurrentInputEditorInfo();
        if (info == null) return false;
        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        return action == EditorInfo.IME_ACTION_SEND || action == EditorInfo.IME_ACTION_SEARCH
                || action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE;
    }

    static void bottomKeyboardClosed() {
        ThorPadIme ime = instance;
        if (ime != null && ime.isInputViewShown()) ime.requestHideSelf(0);
    }

    static void setListener(Listener l) {
        listener = l;
        // The trackpad opened: it has its own keyboard.
        final ThorPadIme ime = instance;
        if (l != null && ime != null) {
            ime.handler.post(new Runnable() {
                @Override public void run() { ime.hideOverlay(); }
            });
        }
    }

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
        hideOverlay();
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
        KeyboardLog.add("startInput " + attribute.packageName + " field=" + attribute.fieldId
                + " type=" + attribute.inputType + " restarting=" + restarting);
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
        KeyboardLog.add("finishInput");
        if (inputStarted) doneSoon();
        inputStarted = false;
        super.onFinishInput();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        KeyboardLog.add("finishInputView");
        doneSoon(); // the app hid the keyboard, e.g. after Search or Send
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        KeyboardLog.add("startInputView " + info.packageName + " restarting=" + restarting);
        handler.removeCallbacks(done);
        makeInvisible();
        if (isOwnScreen(info)) {
            // A text box on the trackpad's own screen (e.g. ThorPad's settings): stay out of the way.
            requestHideSelf(0);
            return;
        }
        handler.removeCallbacks(wanted); // asking once is enough
        askForKeyboard();
    }

    /**
     * Whether the text box is ThorPad's own (its settings, on the trackpad's screen). This
     * keyboard's window isn't a guide to which screen a text box is on: Android moves it
     * between screens and it can still be on the last one, which stopped every text box after
     * the first from opening the keyboard.
     */
    private boolean isOwnScreen(EditorInfo info) {
        return info != null && getPackageName().equals(info.packageName);
    }

    // ---- Typing ---------------------------------------------------------------------------

    @Override public boolean capitalizeNext() {
        InputConnection ic = getCurrentInputConnection();
        EditorInfo info = getCurrentInputEditorInfo();
        if (ic == null || info == null || isPassword()) return false;
        int cls = info.inputType & android.text.InputType.TYPE_MASK_CLASS;
        int v = info.inputType & android.text.InputType.TYPE_MASK_VARIATION;
        if (cls != android.text.InputType.TYPE_CLASS_TEXT
                || v == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || v == android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                || v == android.text.InputType.TYPE_TEXT_VARIATION_URI) return false;
        // Sentence starts always, plus whatever more the box asks for (every word, all caps).
        // Many apps' boxes don't ask for sentence capitals at all, which left them lowercase.
        int modes = android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | (info.inputType
                & (android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS));
        return ic.getCursorCapsMode(modes) != 0;
    }

    /**
     * Only passwords and email addresses are typed exactly as pressed. Search and address bars
     * (often marked as web addresses) and boxes that aren't marked as plain text still get
     * corrections, as they did before 1.8.
     */
    @Override public boolean allowsCorrections() {
        EditorInfo info = getCurrentInputEditorInfo();
        if (info == null) return true;
        if (isPassword()) return false;
        int cls = info.inputType & android.text.InputType.TYPE_MASK_CLASS;
        int v = info.inputType & android.text.InputType.TYPE_MASK_VARIATION;
        return !(cls == android.text.InputType.TYPE_CLASS_TEXT
                && (v == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || v == android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS));
    }

    @Override public boolean isPassword() {
    EditorInfo info = getCurrentInputEditorInfo();
    if (info == null) return false;
    int v = info.inputType & android.text.InputType.TYPE_MASK_VARIATION;
    int cls = info.inputType & android.text.InputType.TYPE_MASK_CLASS;
    return cls == android.text.InputType.TYPE_CLASS_TEXT
            && (v == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            || v == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            || v == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
            || cls == android.text.InputType.TYPE_CLASS_NUMBER
            && v == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }

    @Override public CharSequence textBefore(int n) {
        InputConnection ic = getCurrentInputConnection();
        return ic == null ? null : ic.getTextBeforeCursor(n, 0);
    }

    @Override public void typeChar(char c) {
        lastOwnEdit = android.os.SystemClock.uptimeMillis();
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
        // Enter doesn't count: a box that empties right after Enter has sent its text.
        if (key != ENTER) lastOwnEdit = android.os.SystemClock.uptimeMillis();
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        switch (key) {
            case BACKSPACE: backspace(ic); break;
            case ENTER:
                // Search, Go, Send, Next... or a new line in a multi-line box.
                if (!sendDefaultEditorAction(true)) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
                } else if (closesOnEnter()) {
                    KeyboardLog.add("enter: editor action");
                    finished(); // Send, Search, Go or Done: nothing more to type here
                }
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
