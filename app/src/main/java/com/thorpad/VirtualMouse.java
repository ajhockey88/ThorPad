package com.thorpad;

/**
 * A real (virtual USB) mouse. Android sees it exactly like a plugged-in mouse: the system
 * cursor appears on the top screen and apps such as GameNative get genuine mouse moves,
 * buttons and wheel notches.
 */
final class VirtualMouse extends UinputDevice implements PadOutput {
    private static final int REL_X = 0, REL_Y = 1, REL_HWHEEL = 6, REL_WHEEL = 8;
    private static final int BTN_LEFT = 272, BTN_RIGHT = 273, BTN_MIDDLE = 274, BTN_SIDE = 275, BTN_EXTRA = 276;
    private static final int KEY_BACK = 158, KEY_HOMEPAGE = 172, KEY_APPSELECT = 580;
    /**
     * How long a click holds the button. Like a real mouse click (~80-120 ms), it must span
     * a few frames: games that check the button once per frame miss shorter clicks.
     */
    private static final long CLICK_HOLD_MS = 90;

    private final float density;
    private float remX, remY;
    private float wheelAcc;
    private boolean leftHeld;

    VirtualMouse(float density, StateListener listener) {
        super("ThorPad Mouse", 22137,
                bits(UI_SET_EVBIT, EV_KEY, EV_REL) + ","
                        + bits(UI_SET_KEYBIT, BTN_LEFT, BTN_RIGHT, BTN_MIDDLE, BTN_SIDE, BTN_EXTRA, KEY_BACK, KEY_HOMEPAGE, KEY_APPSELECT) + ","
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

    /** Gap between waking the pointer and pressing the button. */
    private static final long WAKE_LEAD_MS = 80;

    private final Runnable releaseLeft = new Runnable() {
        @Override public void run() {
            send(EV_KEY, BTN_LEFT, 0);
            wiggle(); // a move with the button up, for apps that read buttons off moves
        }
    };

    /**
     * Nudges the pointer one pixel and back. Touching the bottom screen hides Android's mouse
     * cursor, and an app may spend the first mouse event after that on housekeeping (showing
     * the cursor, re-grabbing the pointer). The nudge takes that hit instead of the click.
     */
    private void wiggle() {
        send(EV_REL, REL_X, 1);
        send(EV_REL, REL_X, -1);
    }

    /** Clicks a button: wake the pointer, press, hold like a real click (with a nudge so the
     *  held state is seen even by apps that only check buttons when the mouse moves), release. */
    private Runnable pendingPress;

    private void clickButton(final int button, final Runnable release) {
        io.removeCallbacks(release);
        if (pendingPress != null) io.removeCallbacks(pendingPress);
        wiggle();
        pendingPress = new Runnable() {
            @Override public void run() {
                send(EV_KEY, button, 1);
                wiggle();
                io.postDelayed(release, CLICK_HOLD_MS);
            }
        };
        io.postDelayed(pendingPress, WAKE_LEAD_MS);
    }

    @Override public void click() { clickButton(BTN_LEFT, releaseLeft); }

    private final Runnable releaseRight = new Runnable() {
        @Override public void run() {
            send(EV_KEY, BTN_RIGHT, 0);
            wiggle();
        }
    };

    /** Long press on the pad = right click, like a laptop trackpad's secondary click. */
    @Override public void longPress() { clickButton(BTN_RIGHT, releaseRight); }

    @Override public void twoFingerTap() { clickButton(BTN_RIGHT, releaseRight); }

    @Override public void dragBegin() {
        // A tap-then-drag can start while the tap's click is still on its way: finish it first.
        if (pendingPress != null && io.hasCallbacks(pendingPress)) {
            io.removeCallbacks(pendingPress);
            send(EV_KEY, BTN_LEFT, 1);
            send(EV_KEY, BTN_LEFT, 0);
        }
        if (io.hasCallbacks(releaseLeft)) {
            io.removeCallbacks(releaseLeft);
            send(EV_KEY, BTN_LEFT, 0);
        }
        leftHeld = true;
        wiggle();
        send(EV_KEY, BTN_LEFT, 1);
    }

    @Override public void dragEnd() {
        if (!leftHeld) return;
        leftHeld = false;
        send(EV_KEY, BTN_LEFT, 0);
    }

    @Override public void scrollBegin() {
        wheelAcc = 0;
        wiggle(); // wake the pointer so the first notch isn't spent on it
    }

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

    /** The mouse's back and forward side buttons, which browsers (and Windows) use to go back and forward. */
    @Override public void navigateBack() { tap(BTN_SIDE, CLICK_HOLD_MS); }

    @Override public void navigateForward() { tap(BTN_EXTRA, CLICK_HOLD_MS); }

    @Override public void back() { tap(KEY_BACK, CLICK_HOLD_MS); }

    @Override public void home() { tap(KEY_HOMEPAGE, CLICK_HOLD_MS); }

    @Override public void recents() { tap(KEY_APPSELECT, CLICK_HOLD_MS); }
}
