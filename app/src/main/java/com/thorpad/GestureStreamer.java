package com.thorpad;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.GestureDescription.StrokeDescription;
import android.graphics.Path;
import android.os.SystemClock;

/**
 * Feeds one long, live touch stroke to a screen, piece by piece, as the finger on the
 * trackpad moves. Used for dragging and for two-finger scrolling.
 *
 * Android only accepts the next piece of a continued stroke after the previous one has
 * been played, and each piece must start exactly where the last one ended, so movement
 * that arrives in between is coalesced into the next piece.
 */
final class GestureStreamer {
    private static final long MIN_SEGMENT_MS = 8;
    private static final long MAX_SEGMENT_MS = 60;

    private final AccessibilityService service;
    private int displayId;

    private int generation;
    private boolean active;
    private boolean inFlight;
    private boolean ending;
    private StrokeDescription last;
    private float lastX, lastY, targetX, targetY;
    private long lastDispatchAt;

    GestureStreamer(AccessibilityService service) {
        this.service = service;
    }

    boolean isActive() { return active; }

    float currentX() { return targetX; }
    float currentY() { return targetY; }

    /** Puts a finger down at (x, y) and keeps it down. */
    void begin(int displayId, float x, float y) {
        generation++;
        this.displayId = displayId;
        active = true;
        ending = false;
        lastX = targetX = x;
        lastY = targetY = y;
        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x, y);
        last = new StrokeDescription(path, 0, MIN_SEGMENT_MS, true);
        send(last);
    }

    /** Moves the held finger to (x, y). */
    void moveTo(float x, float y) {
        if (!active || ending) return;
        targetX = x;
        targetY = y;
        pump();
    }

    /** Lifts the finger at its latest position. */
    void end() {
        if (!active) return;
        ending = true;
        pump();
    }

    /** Forgets the stroke; the next gesture sent to the screen will replace it. */
    void abandon() {
        generation++;
        active = false;
        inFlight = false;
        last = null;
    }

    private void pump() {
        if (!active || inFlight) return;
        boolean moved = targetX != lastX || targetY != lastY;
        if (!ending && !moved) return;

        long now = SystemClock.uptimeMillis();
        long duration = Math.max(MIN_SEGMENT_MS, Math.min(MAX_SEGMENT_MS, now - lastDispatchAt));
        Path path = new Path();
        path.moveTo(lastX, lastY);
        path.lineTo(targetX, targetY);
        last = last.continueStroke(path, 0, duration, !ending);
        lastX = targetX;
        lastY = targetY;
        send(last);
    }

    private void send(StrokeDescription stroke) {
        GestureDescription.Builder builder = new GestureDescription.Builder().addStroke(stroke);
        builder.setDisplayId(displayId);
        final int gen = generation;
        final boolean finishing = !stroke.willContinue();
        inFlight = true;
        lastDispatchAt = SystemClock.uptimeMillis();
        boolean accepted = service.dispatchGesture(builder.build(),
                new AccessibilityService.GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        if (gen != generation) return;
                        inFlight = false;
                        if (finishing) {
                            active = false;
                            last = null;
                        } else {
                            pump();
                        }
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        if (gen != generation) return;
                        abandon();
                    }
                }, null);
        if (!accepted) abandon();
    }
}
