package com.thorpad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
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
    private static final long DRAG_WINDOW_MS = 260;
    private static final long LONG_PRESS_MS = 550;

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
                buzz(HapticFeedbackConstants.LONG_PRESS);
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
        canvas.drawText("Tap: click  ·  Two fingers: scroll  ·  Tap, then slide: drag",
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
                    twoTravel = 0;
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
                    float dy = avgY - twoLastY;
                    twoLastY = avgY;
                    twoTravel += Math.abs(dy);
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
                            buzz(HapticFeedbackConstants.CLOCK_TICK);
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
                    if (dragArmed && s != null) {
                        s.dragBegin();
                        buzz(HapticFeedbackConstants.LONG_PRESS);
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
                    buzz(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE);
                } else if (state == TWO_FINGER && !scrolling && maxPointers == 2 && held < 350) {
                    if (s != null) s.twoFingerTap();
                    buzz(HapticFeedbackConstants.CONTEXT_CLICK);
                } else if ((state == IDLE || (state == MOVING && maxStray < tapSlop))
                        && maxPointers == 1 && !longPressed && held < TAP_MAX_MS) {
                    // A tap, even if the finger rolled a little and nudged the pointer.
                    if (s != null && prefs.tapToClick()) {
                        s.click();
                        buzz(HapticFeedbackConstants.VIRTUAL_KEY);
                    }
                    lastTapUpAt = now;
                    state = DONE;
                    return true;
                } else if (state == TWO_FINGER) {
                    endScroll(s);
                }
                state = DONE;
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                handler.removeCallbacks(longPress);
                touching = false;
                invalidate();
                if (state == DRAGGING && s != null) s.dragEnd();
                endScroll(s);
                state = DONE;
                return true;
            }
        }
        return true;
    }

    /** Haptic feedback for every action, even if the system's touch vibration is off for views. */
    private void buzz(int type) {
        performHapticFeedback(type, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
    }

    private void endScroll(PadOutput s) {
        if (scrolling) {
            scrolling = false;
            if (s != null) s.scrollEnd();
        }
    }
}
