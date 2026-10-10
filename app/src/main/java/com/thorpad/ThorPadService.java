package com.thorpad;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.GestureDescription.StrokeDescription;
import android.content.Context;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Draws the mouse pointer on the top screen and turns trackpad input into taps, drags and
 * scroll swipes there. Android only lets an accessibility service do both, which is why
 * ThorPad needs to be switched on under Settings > Accessibility.
 */
public class ThorPadService extends AccessibilityService
        implements DisplayManager.DisplayListener, PadOutput, KeyOutput {
    private static final long TAP_MS = 80;
    private static final long LONG_PRESS_MS = 650;
    /** Keep scroll strokes this far inside the screen edge. */
    private static final float EDGE = 4f;

    private static ThorPadService instance;

    static ThorPadService get() { return instance; }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private GestureStreamer streamer;
    private Prefs prefs;

    private Display display;
    private WindowManager windowManager;
    private PointerView pointer;
    private WindowManager.LayoutParams params;
    private int hotspot;
    private int width = 1920, height = 1080;
    private float x = -1, y = -1;
    private boolean pointerWanted;

    private float scrollY;
    private boolean scrolling;
    private boolean dragging;

    @Override
    protected void onServiceConnected() {
        instance = this;
        prefs = new Prefs(this);
        streamer = new GestureStreamer(this);
        ((DisplayManager) getSystemService(DISPLAY_SERVICE)).registerDisplayListener(this, handler);
        if (pointerWanted) showPointer();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        removePointer();
        ((DisplayManager) getSystemService(DISPLAY_SERVICE)).unregisterDisplayListener(this);
        if (instance == this) instance = null;
        super.onDestroy();
    }

    // ---- Pointer window -------------------------------------------------------------------

    /** Shows the pointer on the pointer screen. Called while the trackpad is open. */
    void showPointer() {
        pointerWanted = true;
        Display target = Displays.pointerDisplay(this);
        if (pointer != null && display != null && target != null
                && target.getDisplayId() == display.getDisplayId()) {
            pointer.setVisibility(android.view.View.VISIBLE);
            return;
        }
        removePointer();
        if (target == null) return;
        display = target;

        Context ctx = target.getDisplayId() == Display.DEFAULT_DISPLAY
                ? this
                : createWindowContext(target, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null);
        windowManager = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        refreshSize();

        pointer = new PointerView(ctx, prefs.pointerSize());
        hotspot = Math.round(1.5f * ctx.getResources().getDisplayMetrics().density * prefs.pointerSize());
        int size = pointer.sizePx();
        params = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        params.setTitle("ThorPad pointer");
        if (x < 0 || x >= width || y < 0 || y >= height) {
            x = width / 2f;
            y = height / 2f;
        }
        placePointer();
        try {
            windowManager.addView(pointer, params);
        } catch (RuntimeException e) {
            pointer = null;
        }
    }

    /** Hides the pointer when the trackpad is closed. */
    void hidePointer() {
        pointerWanted = false;
        cancelStreams();
        if (pointer != null) pointer.setVisibility(android.view.View.GONE);
    }

    /** Rebuilds the pointer, e.g. after its size or screen changed in settings. */
    void reloadPointer() {
        boolean wanted = pointerWanted;
        removePointer();
        if (wanted) showPointer();
    }

    private void removePointer() {
        if (pointer != null && windowManager != null) {
            try {
                windowManager.removeView(pointer);
            } catch (RuntimeException ignored) {
            }
        }
        pointer = null;
        display = null;
    }

    private void refreshSize() {
        if (windowManager == null) return;
        Rect bounds = windowManager.getMaximumWindowMetrics().getBounds();
        width = bounds.width();
        height = bounds.height();
        x = clamp(x, 0, width - 1);
        y = clamp(y, 0, height - 1);
    }

    private void placePointer() {
        if (params == null) return;
        params.x = Math.round(x) - hotspot;
        params.y = Math.round(y) - hotspot;
    }

    @Override
    public void onDisplayAdded(int displayId) {}

    @Override
    public void onDisplayRemoved(int displayId) {
        if (display != null && display.getDisplayId() == displayId) removePointer();
    }

    @Override
    public void onDisplayChanged(int displayId) {
        if (display != null && display.getDisplayId() == displayId && pointer != null) {
            refreshSize();
            placePointer();
            windowManager.updateViewLayout(pointer, params);
        }
    }

    // ---- Input from the trackpad ----------------------------------------------------------

    @Override public void moveBy(float dx, float dy) {
        if (pointer == null) return;
        x = clamp(x + dx, 0, width - 1);
        y = clamp(y + dy, 0, height - 1);
        placePointer();
        try {
            windowManager.updateViewLayout(pointer, params);
        } catch (RuntimeException ignored) {
        }
        if (dragging) streamer.moveTo(x, y);
    }

    @Override public void click() {
        tapAt(TAP_MS);
    }

    @Override public void longPress() {
        tapAt(LONG_PRESS_MS);
    }

    private void tapAt(long durationMs) {
        if (pointer == null) return;
        cancelStreams();
        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder()
                .addStroke(new StrokeDescription(path, 0, durationMs));
        builder.setDisplayId(display.getDisplayId());
        dispatchGesture(builder.build(), null, null);
    }

    /** Presses down where the pointer is and keeps holding while the pointer moves. */
    @Override public void dragBegin() {
        if (pointer == null) return;
        cancelStreams();
        dragging = true;
        pointer.setPressed2(true);
        streamer.begin(display.getDisplayId(), x, y);
    }

    @Override public void dragEnd() {
        if (!dragging) return;
        dragging = false;
        if (pointer != null) pointer.setPressed2(false);
        streamer.end();
    }

    /**
     * Starts a scroll: a finger goes down under the pointer and follows the two-finger
     * movement, so lists, pages and maps scroll just as if dragged on the top screen.
     */
    @Override public void scrollBegin() {
        if (pointer == null) return;
        cancelStreams();
        scrolling = true;
        scrollY = clamp(y, EDGE, height - 1 - EDGE);
        streamer.begin(display.getDisplayId(), clamp(x, EDGE, width - 1 - EDGE), scrollY);
    }

    @Override public void scrollBy(float dy) {
        if (!scrolling) return;
        if (!streamer.isActive()) {
            // The previous stroke ran out of room or was interrupted: start a fresh one.
            scrollY = clamp(y, EDGE, height - 1 - EDGE);
            streamer.begin(display.getDisplayId(), clamp(x, EDGE, width - 1 - EDGE), scrollY);
        }
        float next = scrollY + dy;
        if (next < EDGE || next > height - 1 - EDGE) {
            // Reached the screen edge. Lift here and pick up again from the pointer, the way
            // you would re-grip a page with your finger.
            streamer.moveTo(streamer.currentX(), clamp(next, EDGE, height - 1 - EDGE));
            streamer.end();
            return;
        }
        scrollY = next;
        streamer.moveTo(streamer.currentX(), scrollY);
    }

    @Override public void scrollEnd() {
        if (!scrolling) return;
        scrolling = false;
        streamer.end();
    }

    private void cancelStreams() {
        if (dragging || scrolling || streamer.isActive()) {
            dragging = false;
            scrolling = false;
            if (pointer != null) pointer.setPressed2(false);
            streamer.abandon();
        }
    }

    @Override public void twoFingerTap() { back(); }

    @Override public void navigateBack() { back(); }

    /** Touch mode can't send a forward key, so a swipe left does nothing there. */
    @Override public void navigateForward() {}

    @Override public void back() { performGlobalAction(GLOBAL_ACTION_BACK); }

    @Override public void home() { performGlobalAction(GLOBAL_ACTION_HOME); }

    @Override public void recents() { performGlobalAction(GLOBAL_ACTION_RECENTS); }

    // ---- Typing, in touch mode -------------------------------------------------------------
    // Without a virtual keyboard, text is written straight into the selected text box.

    @Override public void typeChar(char c) {
        editFocusedText(String.valueOf(c), false);
    }

    @Override public void special(int key) {
        if (key == BACKSPACE) {
            editFocusedText(null, true);
        } else if (key == CLEAR_ALL) {
            AccessibilityNodeInfo node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (node == null || !node.isEditable()) return;
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        } else if (key == ENTER) {
            AccessibilityNodeInfo node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (node == null) return;
            if (!node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId())) {
                if (node.isMultiLine()) editFocusedText("\n", false);
            }
        } else if (key == TAB) {
            editFocusedText("\t", false);
        }
    }

    private void editFocusedText(String insert, boolean deleteOne) {
        AccessibilityNodeInfo node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (node == null || !node.isEditable()) return;
        CharSequence current = node.isShowingHintText() ? "" : node.getText();
        String text = current == null ? "" : current.toString();
        int start = node.getTextSelectionStart();
        int end = node.getTextSelectionEnd();
        if (start < 0 || end < 0 || start > text.length() || end > text.length()) {
            start = end = text.length();
        }
        if (start > end) { int t = start; start = end; end = t; }
        if (deleteOne && start == end) {
            if (start == 0) return;
            start = text.offsetByCodePoints(start, -1);
        }
        String replacement = insert == null ? "" : insert;
        String next = text.substring(0, start) + replacement + text.substring(end);
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, next);
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return;
        int caret = start + replacement.length();
        Bundle sel = new Bundle();
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, caret);
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, caret);
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
