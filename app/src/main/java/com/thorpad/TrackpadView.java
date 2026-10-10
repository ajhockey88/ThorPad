package com.thorpad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * The touch surface. One finger moves the pointer, a tap clicks, tap-then-slide drags,
 * a long press and a two-finger tap do the output's secondary actions, and two fingers
 * up or down scroll.
 */
final class TrackpadView extends View {
    private static final int IDLE = 0;      // finger down, not yet decided
    private static final int MOVING = 1;    // moving the pointer
    private static final int DRAGGING = 2;  // holding a press on the top screen
    private static final int TWO_FINGER = 3; // scrolling, or a two-finger tap
    private static final int DONE = 4;      // ignore the rest of this touch

    private static final long TAP_MAX_MS = 350;
    /** A drag must start this soon after a tap's finger lifts... */
    private static final long DRAG_WINDOW_MS = 180;
    /** ...and the finger must rest this long before sliding, or it just moves the pointer. */
    private static final long DRAG_SETTLE_MS = 100;
    private static final long LONG_PRESS_MS = 550;
    /** How far two fingers travel sideways to go back or forward. */
    private static final float SWIPE_DP = 70;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float slop;
    /** A touch that strays less than this and lifts quickly still counts as a tap. */
    private final float tapSlop;
    private float maxStray;
    private final float density;
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Prefs prefs;
    private PadOutput output;

    private int state = DONE;
    private long downAt;
    private long lastTapUpAt;
    private float downX, downY, lastX, lastY;
    private long lastMoveAt;
    private float fingerTravel;
    private boolean dragArmed;
    private boolean longPressed;
    private int maxPointers;
    private float twoLastY, twoTravel;
    /** Where the two fingers started, to tell a sideways swipe from a scroll. */
    private float twoStartX, twoStartY;
    private boolean swiping;
    private float tickAcc;
    private boolean scrolling;
    private boolean touching;

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            if (state == IDLE && !dragArmed) {
                PadOutput s = output;
                if (s != null) s.longPress();
                longPressed = true;
                state = DONE;
                buzz(Haptics.HOLD);
            }
        }
    };

    TrackpadView(Context context) {
        super(context);
        prefs = new Prefs(context);
        density = context.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(context).getScaledTouchSlop() * 0.6f;
        tapSlop = ViewConfiguration.get(context).getScaledTouchSlop() * 1.5f;
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1.5f * density);
        label.setColor(Color.argb(70, 255, 255, 255));
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(13 * density);
        setHapticFeedbackEnabled(true);
    }

    /** Where gestures go; null while ThorPad is not set up. */
    void setOutput(PadOutput output) {
        if (this.output != output) state = DONE;
        this.output = output;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Mostly black, with a faint outline, to be kind to the OLED.
        float inset = 6 * density;
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        border.setColor(touching ? Color.argb(90, 120, 200, 255) : Color.argb(40, 255, 255, 255));
        canvas.drawRoundRect(rect, 18 * density, 18 * density, border);
        float line = label.getTextSize() * 1.5f;
        canvas.drawText("Tap: click  ·  Two fingers up/down: scroll  ·  Tap, hold, slide: drag",
                getWidth() / 2f, getHeight() - 18 * density - line, label);
        canvas.drawText("Two fingers right: back  ·  Two fingers left: forward",
                getWidth() / 2f, getHeight() - 18 * density, label);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        PadOutput s = output;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                long now = SystemClock.uptimeMillis();
                state = IDLE;
                touching = true;
                invalidate();
                downAt = now;
                lastMoveAt = now;
                downX = lastX = ev.getX();
                downY = lastY = ev.getY();
                fingerTravel = 0;
                maxStray = 0;
                maxPointers = 1;
                longPressed = false;
                scrolling = false;
                dragArmed = now - lastTapUpAt < DRAG_WINDOW_MS;
                handler.postDelayed(longPress, LONG_PRESS_MS);
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                maxPointers = Math.max(maxPointers, ev.getPointerCount());
                handler.removeCallbacks(longPress);
                if (state == DRAGGING || state == DONE) return true;
                if (ev.getPointerCount() == 2) {
                    state = TWO_FINGER;
                    twoLastY = (ev.getY(0) + ev.getY(1)) / 2f;
                    twoStartX = (ev.getX(0) + ev.getX(1)) / 2f;
                    twoStartY = twoLastY;
                    twoTravel = 0;
                    swiping = false;
                } else {
                    // Three or more fingers: not a gesture ThorPad uses.
                    endScroll(s);
                    state = DONE;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (state == TWO_FINGER) {
                    if (ev.getPointerCount() < 2) return true;
                    float avgY = (ev.getY(0) + ev.getY(1)) / 2f;
                    float avgX = (ev.getX(0) + ev.getX(1)) / 2f;
                    float dy = avgY - twoLastY;
                    twoLastY = avgY;
                    twoTravel += Math.abs(dy);
                    float sideways = avgX - twoStartX;
                    if (!scrolling && !swiping && Math.abs(sideways) > slop
                            && Math.abs(sideways) > 1.5f * Math.abs(avgY - twoStartY)) {
                        swiping = true; // a sideways swipe: back or forward, not a scroll
                    }
                    if (swiping) {
                        if (Math.abs(sideways) > SWIPE_DP * density) {
                            if (s != null) {
                                if (sideways > 0) s.navigateBack();
                                else s.navigateForward();
                            }
                            buzz(Haptics.NAVIGATE);
                            state = DONE;
                        }
                        return true;
                    }
                    if (!scrolling && twoTravel > slop) {
                        scrolling = true;
                        tickAcc = 0;
                        if (s != null) s.scrollBegin();
                    }
                    if (scrolling && s != null) {
                        float dir = prefs.invertScroll() ? -1f : 1f;
                        float amount = dy * prefs.scrollSpeed() * dir;
                        s.scrollBy(amount);
                        // A tick for every wheel notch, like a notched scroll wheel.
                        tickAcc += amount;
                        float notch = 22f * density;
                        if (Math.abs(tickAcc) >= notch) {
                            tickAcc %= notch;
                            buzz(Haptics.SCROLL);
                        }
                    }
                    return true;
                }
                if (state == DONE) return true;

                float x = ev.getX(), y = ev.getY();
                float dx = x - lastX, dy = y - lastY;
                lastX = x;
                lastY = y;
                fingerTravel += Math.abs(dx) + Math.abs(dy);
                maxStray = Math.max(maxStray, (float) Math.hypot(x - downX, y - downY));
                long now = SystemClock.uptimeMillis();
                long dt = Math.max(1, now - lastMoveAt);
                lastMoveAt = now;

                if (state == IDLE) {
                    if (Math.hypot(x - downX, y - downY) < slop) return true;
                    handler.removeCallbacks(longPress);
                    // Tap-then-drag: only when the second touch came quickly and settled first.
                    // A quick tap followed by an immediate swipe is just moving the pointer.
                    if (dragArmed && s != null && now - downAt >= DRAG_SETTLE_MS) {
                        s.dragBegin();
                        buzz(Haptics.DRAG_START);
                        state = DRAGGING;
                    } else {
                        state = MOVING;
                    }
                }
                if (s != null) {
                    // Pointer acceleration: slow movements are precise, quick flicks travel far.
                    float speed = (float) Math.hypot(dx, dy) / dt / density; // dp per ms
                    float gain = prefs.sensitivity() * (1f + prefs.acceleration() * Math.min(speed, 4f));
                    s.moveBy(dx * gain, dy * gain);
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                if (state == TWO_FINGER && ev.getPointerCount() == 2) {
                    if (scrolling) {
                        endScroll(s);
                        state = DONE;
                    }
                    // Otherwise wait for the last finger: it may be a two-finger tap.
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                handler.removeCallbacks(longPress);
                long now = SystemClock.uptimeMillis();
                long held = now - downAt;
                touching = false;
                invalidate();
                if (state == DRAGGING) {
                    if (s != null) s.dragEnd();
                    buzz(Haptics.DRAG_END);
                } else if (state == TWO_FINGER && !scrolling && !swiping && maxPointers == 2 && held < 350) {
                    if (s != null) s.twoFingerTap();
                    buzz(Haptics.RIGHT_CLICK);
                } else if (maybeTap(s, held, now)) {
                    return true;
                } else if (state == TWO_FINGER) {
                    endScroll(s);
                }
                state = DONE;
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                // Android (or the Thor's touch handling) took the touch away. If it looked like a
                // tap, still click: a short tap is the touch most likely to be cancelled.
                handler.removeCallbacks(longPress);
                long now = SystemClock.uptimeMillis();
                touching = false;
                invalidate();
                if (state == DRAGGING && s != null) s.dragEnd();
                endScroll(s);
                maybeTap(s, now - downAt, now);
                state = DONE;
                return true;
            }
        }
        return true;
    }

    /** Clicks if the touch that just ended was a tap. */
    private boolean maybeTap(PadOutput s, long held, long now) {
        if (state == DRAGGING || state == TWO_FINGER || state == DONE) return false;
        if (maxPointers != 1 || longPressed) return false;
        if (held >= TAP_MAX_MS || maxStray >= tapSlop) return false;
        // A tap, even if the finger rolled a little and nudged the pointer.
        buzz(Haptics.CLICK);
        if (s != null) s.click();
        lastTapUpAt = now;
        state = DONE;
        return true;
    }

    /** Haptic feedback for every action, as set up in Settings > Haptics. */
    private void buzz(int action) {
        Haptics.play(getContext(), action);
    }

    private void endScroll(PadOutput s) {
        if (scrolling) {
            scrolling = false;
            if (s != null) s.scrollEnd();
        }
    }
}
