package com.thorpad;

/**
 * A real (virtual USB) mouse. Android sees it exactly like a plugged-in mouse: the system
 * cursor appears on the top screen and apps such as GameNative get genuine mouse moves,
 * buttons and wheel notches.
 */
final class VirtualMouse extends UinputDevice implements PadOutput {
    private static final int REL_X = 0, REL_Y = 1, REL_HWHEEL = 6, REL_WHEEL = 8;
    private static final int BTN_LEFT = 272, BTN_RIGHT = 273, BTN_MIDDLE = 274;
    private static final int KEY_BACK = 158, KEY_HOMEPAGE = 172, KEY_APPSELECT = 580;
    private static final long CLICK_HOLD_MS = 25;

    private final float density;
    private float remX, remY;
    private float wheelAcc;
    private boolean leftHeld;

    VirtualMouse(float density, StateListener listener) {
        super("ThorPad Mouse", 22137,
                bits(UI_SET_EVBIT, EV_KEY, EV_REL) + ","
                        + bits(UI_SET_KEYBIT, BTN_LEFT, BTN_RIGHT, BTN_MIDDLE, KEY_BACK, KEY_HOMEPAGE, KEY_APPSELECT) + ","
                        + bits(UI_SET_RELBIT, REL_X, REL_Y, REL_WHEEL, REL_HWHEEL),
                listener);
        this.density = density;
    }

    @Override void stop() {
        leftHeld = false;
        super.stop();
    }

    @Override void send(int... events) {
        if (isReady()) super.send(events); // a mouse drops input until it is plugged in
    }

    @Override public void moveBy(float dx, float dy) {
        remX += dx;
        remY += dy;
        int ix = (int) remX, iy = (int) remY;
        if (ix == 0 && iy == 0) return;
        remX -= ix;
        remY -= iy;
        send(EV_REL, REL_X, ix, EV_REL, REL_Y, iy);
    }

    @Override public void click() { tap(BTN_LEFT, CLICK_HOLD_MS); }

    /** Long press on the pad = right click, like a laptop trackpad's secondary click. */
    @Override public void longPress() { tap(BTN_RIGHT, CLICK_HOLD_MS); }

    @Override public void twoFingerTap() { tap(BTN_RIGHT, CLICK_HOLD_MS); }

    @Override public void dragBegin() {
        leftHeld = true;
        send(EV_KEY, BTN_LEFT, 1);
    }

    @Override public void dragEnd() {
        if (!leftHeld) return;
        leftHeld = false;
        send(EV_KEY, BTN_LEFT, 0);
    }

    @Override public void scrollBegin() { wheelAcc = 0; }

    /**
     * One wheel notch per stretch of finger travel. Fingers moving up (dy < 0) turn the wheel
     * toward you, which scrolls content down ("natural" trackpad scrolling).
     */
    @Override public void scrollBy(float dy) {
        float notch = 22f * density;
        wheelAcc += dy;
        int notches = (int) (wheelAcc / notch);
        if (notches == 0) return;
        wheelAcc -= notches * notch;
        int step = notches > 0 ? 1 : -1;
        // Send separate notches so apps that read one wheel "click" per event see every one.
        for (int i = 0; i != notches; i += step) send(EV_REL, REL_WHEEL, step);
    }

    @Override public void scrollEnd() { wheelAcc = 0; }

    @Override public void back() { tap(KEY_BACK, CLICK_HOLD_MS); }

    @Override public void home() { tap(KEY_HOMEPAGE, CLICK_HOLD_MS); }

    @Override public void recents() { tap(KEY_APPSELECT, CLICK_HOLD_MS); }
}
