package com.thorpad;

/**
 * A real (virtual USB) mouse. Android sees it exactly like a plugged-in mouse: the system
 * cursor appears on the top screen and apps such as GameNative get genuine mouse moves,
 * buttons and wheel notches.
 */
final class VirtualMouse extends UinputDevice implements PadOutput {
    private static final int REL_X = 0, REL_Y = 1, REL_HWHEEL = 6, REL_WHEEL = 8, REL_WHEEL_HI_RES = 11;
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
    /** Hi-res wheel units (120 per notch) not sent yet, in smooth mode. */
    private float hiResAcc;
    /** Recent scroll speed in pixels per millisecond, for the glide after the finger lifts. */
    private float velocity;
    private long lastScrollAt;
    private final android.os.Handler glide = new android.os.Handler(android.os.Looper.getMainLooper());
    /** Smooth scrolling: small hi-res wheel steps, and a short glide after the finger lifts. */
    volatile boolean smooth;
    private boolean leftHeld;

    VirtualMouse(float density, StateListener listener) {
        super("ThorPad Mouse", 22137,
                bits(UI_SET_EVBIT, EV_KEY, EV_REL) + ","
                        + bits(UI_SET_KEYBIT, BTN_LEFT, BTN_RIGHT, BTN_MIDDLE, BTN_SIDE, BTN_EXTRA, KEY_BACK, KEY_HOMEPAGE, KEY_APPSELECT) + ","
                        + bits(UI_SET_RELBIT, REL_X, REL_Y, REL_WHEEL, REL_HWHEEL, REL_WHEEL_HI_RES),
                listener);
        this.density = density;
    }

    @Override void stop() {
        glide.removeCallbacks(glideStep);
        leftHeld = false;
        super.stop();
    }

    @Override void send(int... events) {
        if (!isReady()) return; // a mouse drops input until it is plugged in
        synchronized (moveLock) {
            lastMove = null; // moves after this go after it, not into an earlier move
        }
        super.send(events);
    }

    // When the touch screen reports faster than moves can be written, the moves waiting in
    // the queue add up into one instead of costing one write each.
    private final Object moveLock = new Object();
    private Move lastMove;

    private final class Move implements Runnable {
        int x, y;
        boolean sent;

        @Override public void run() {
            int mx, my;
            synchronized (moveLock) {
                sent = true;
                mx = x;
                my = y;
            }
            inject(EV_REL, REL_X, mx, EV_REL, REL_Y, my);
        }
    }

    @Override public void moveBy(float dx, float dy) {
        remX += dx;
        remY += dy;
        int ix = (int) remX, iy = (int) remY;
        if (ix == 0 && iy == 0) return;
        remX -= ix;
        remY -= iy;
        if (!isReady()) return;
        Move m;
        synchronized (moveLock) {
            if (lastMove != null && !lastMove.sent) {
                lastMove.x += ix;
                lastMove.y += iy;
                return;
            }
            m = lastMove = new Move();
            m.x = ix;
            m.y = iy;
        }
        io.post(m);
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
        glide.removeCallbacks(glideStep);
        wheelAcc = 0;
        hiResAcc = 0;
        velocity = 0;
        lastScrollAt = 0;
        wiggle(); // wake the pointer so the first notch isn't spent on it
    }

    /**
     * One wheel notch per stretch of finger travel. Fingers moving up (dy < 0) turn the wheel
     * toward you, which scrolls content down ("natural" trackpad scrolling).
     */
    @Override public void scrollBy(float dy) {
        long now = android.os.SystemClock.uptimeMillis();
        if (lastScrollAt != 0) {
            long dt = Math.max(1, Math.min(100, now - lastScrollAt));
            velocity = velocity * 0.5f + (dy / dt) * 0.5f;
        }
        lastScrollAt = now;
        turnWheel(dy);
    }

    private void turnWheel(float dy) {
        float notch = 22f * density;
        if (smooth) {
            // Hi-res steps in proportion to the finger's travel. Android reads these instead of
            // whole notches where it supports them; whole notches still go out for the rest.
            hiResAcc += dy / notch * 120f;
            int hi = (int) hiResAcc;
            if (hi == 0) return;
            hiResAcc -= hi;
            wheelAcc += hi;
            int notches = (int) (wheelAcc / 120f);
            wheelAcc -= notches * 120f;
            if (notches != 0) send(EV_REL, REL_WHEEL_HI_RES, hi, EV_REL, REL_WHEEL, notches);
            else send(EV_REL, REL_WHEEL_HI_RES, hi);
            return;
        }
        wheelAcc += dy;
        int notches = (int) (wheelAcc / notch);
        if (notches == 0) return;
        wheelAcc -= notches * notch;
        int step = notches > 0 ? 1 : -1;
        // Send separate notches so apps that read one wheel "click" per event see every one.
        for (int i = 0; i != notches; i += step) send(EV_REL, REL_WHEEL_HI_RES, step * 120, EV_REL, REL_WHEEL, step);
    }

    @Override public void scrollEnd() {
        long sinceLast = android.os.SystemClock.uptimeMillis() - lastScrollAt;
        if (smooth && sinceLast < 60 && Math.abs(velocity) > 0.3f) {
            glide.postDelayed(glideStep, 16);
        } else {
            wheelAcc = 0;
            hiResAcc = 0;
        }
    }

    void stopGlide() {
        glide.removeCallbacks(glideStep);
        wheelAcc = 0;
        hiResAcc = 0;
    }

    /** Keeps the wheel turning after a flick, slowing down each frame like a trackpad on a laptop. */
    private final Runnable glideStep = new Runnable() {
        @Override public void run() {
            velocity *= 0.92f;
            if (Math.abs(velocity) < 0.05f || !isReady()) {
                wheelAcc = 0;
                hiResAcc = 0;
                return;
            }
            turnWheel(velocity * 16f);
            glide.postDelayed(this, 16);
        }
    };

    /** The mouse's back and forward side buttons, which browsers (and Windows) use to go back and forward. */
    @Override public void navigateBack() { tap(BTN_SIDE, CLICK_HOLD_MS); }

    @Override public void navigateForward() { tap(BTN_EXTRA, CLICK_HOLD_MS); }

    @Override public void back() { tap(KEY_BACK, CLICK_HOLD_MS); }

    @Override public void home() { tap(KEY_HOMEPAGE, CLICK_HOLD_MS); }

    @Override public void recents() { tap(KEY_APPSELECT, CLICK_HOLD_MS); }
}
