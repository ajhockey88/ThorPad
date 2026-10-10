package com.thorpad;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import moe.shizuku.server.IRemoteProcess;

/**
 * A real (virtual USB) mouse, made with Android's `uinput` shell tool through Shizuku.
 * Android sees it exactly like a plugged-in mouse: the system cursor appears on the top
 * screen and apps such as GameNative get genuine mouse moves, buttons and wheel notches.
 */
final class VirtualMouse implements PadOutput {
    // Linux input event codes.
    private static final int EV_SYN = 0, EV_KEY = 1, EV_REL = 2;
    private static final int REL_X = 0, REL_Y = 1, REL_HWHEEL = 6, REL_WHEEL = 8;
    private static final int BTN_LEFT = 272, BTN_RIGHT = 273, BTN_MIDDLE = 274;
    private static final int KEY_BACK = 158, KEY_HOMEPAGE = 172, KEY_APPSELECT = 580;
    // uinput ioctl numbers as the `uinput` tool's "configuration" expects them.
    private static final int UI_SET_EVBIT = 100, UI_SET_KEYBIT = 101, UI_SET_RELBIT = 102;

    private static final long CLICK_HOLD_MS = 25;

    interface StateListener { void onMouseStateChanged(boolean ready, String error); }

    private final HandlerThread thread = new HandlerThread("ThorPadMouse");
    private final Handler io;
    private final Prefs prefs;
    private final float density;
    private final StateListener listener;

    private IRemoteProcess process;
    private OutputStream stdin;
    private volatile boolean ready;

    private float remX, remY;
    private float wheelAcc;
    private boolean leftHeld;

    VirtualMouse(Prefs prefs, float density, StateListener listener) {
        this.prefs = prefs;
        this.density = density;
        this.listener = listener;
        thread.start();
        io = new Handler(thread.getLooper());
    }

    boolean isReady() { return ready; }

    void start() {
        io.post(new Runnable() {
            @Override public void run() {
                if (process != null) return;
                try {
                    process = ShizukuClient.newProcess(new String[] {"uinput", "-"});
                    ParcelFileDescriptor pfd = process.getOutputStream();
                    stdin = new ParcelFileDescriptor.AutoCloseOutputStream(pfd);
                    write("{\"id\":1,\"command\":\"register\",\"name\":\"ThorPad Mouse\","
                            + "\"vid\":4660,\"pid\":22137,\"bus\":\"usb\",\"configuration\":["
                            + "{\"type\":" + UI_SET_EVBIT + ",\"data\":[" + EV_KEY + "," + EV_REL + "]},"
                            + "{\"type\":" + UI_SET_KEYBIT + ",\"data\":[" + BTN_LEFT + "," + BTN_RIGHT + ","
                            + BTN_MIDDLE + "," + KEY_BACK + "," + KEY_HOMEPAGE + "," + KEY_APPSELECT + "]},"
                            + "{\"type\":" + UI_SET_RELBIT + ",\"data\":[" + REL_X + "," + REL_Y + ","
                            + REL_WHEEL + "," + REL_HWHEEL + "]}]}");
                    // Give Android a moment to register the new device before sending input.
                    SystemClock.sleep(400);
                    if (!process.alive()) throw new IOException("uinput exited (code " + process.exitValue() + ")");
                    ready = true;
                    report(true, null);
                } catch (Exception e) {
                    stopNow();
                    report(false, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        });
    }

    /** Unplugs the virtual mouse. */
    void stop() {
        io.post(new Runnable() {
            @Override public void run() { stopNow(); }
        });
    }

    void quit() {
        stop();
        thread.quitSafely();
    }

    private void stopNow() {
        ready = false;
        leftHeld = false;
        try {
            if (stdin != null) stdin.close();
        } catch (IOException ignored) {
        }
        try {
            if (process != null) process.destroy();
        } catch (Exception ignored) {
        }
        stdin = null;
        process = null;
    }

    private void report(final boolean ok, final String error) {
        if (listener == null) return;
        new Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() { listener.onMouseStateChanged(ok, error); }
        });
    }

    private void write(String json) throws IOException {
        stdin.write((json + "\n").getBytes(StandardCharsets.UTF_8));
        stdin.flush();
    }

    /** Sends evdev events (type, code, value triples) followed by a SYN_REPORT. */
    private void send(final int... events) {
        if (!ready) return;
        io.post(new Runnable() {
            @Override public void run() {
                if (stdin == null) return;
                StringBuilder sb = new StringBuilder("{\"id\":1,\"command\":\"inject\",\"events\":[");
                for (int v : events) sb.append(v).append(',');
                sb.append(EV_SYN).append(",0,0]}");
                try {
                    write(sb.toString());
                } catch (IOException e) {
                    stopNow();
                    report(false, "The virtual mouse stopped (" + e.getMessage() + ")");
                }
            }
        });
    }

    private void tapButton(final int button) {
        send(EV_KEY, button, 1);
        io.postDelayed(new Runnable() {
            @Override public void run() { send(EV_KEY, button, 0); }
        }, CLICK_HOLD_MS);
    }

    // ---- PadOutput ------------------------------------------------------------------------

    @Override public void moveBy(float dx, float dy) {
        remX += dx;
        remY += dy;
        int ix = (int) remX, iy = (int) remY;
        if (ix == 0 && iy == 0) return;
        remX -= ix;
        remY -= iy;
        send(EV_REL, REL_X, ix, EV_REL, REL_Y, iy);
    }

    @Override public void click() { tapButton(BTN_LEFT); }

    /** Long press on the pad = right click, as on most laptop trackpads' secondary click. */
    @Override public void longPress() { tapButton(BTN_RIGHT); }

    @Override public void twoFingerTap() { tapButton(BTN_RIGHT); }

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

    @Override public void back() { tapButton(KEY_BACK); }

    @Override public void home() { tapButton(KEY_HOMEPAGE); }

    @Override public void recents() { tapButton(KEY_APPSELECT); }
}
