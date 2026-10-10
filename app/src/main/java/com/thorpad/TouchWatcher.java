package com.thorpad;

import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import moe.shizuku.server.IRemoteProcess;

/**
 * Reports taps on the Thor's touchscreens while running, by reading their raw input events
 * through Shizuku. Used only while the keyboard is open for a text box, so a tap outside the
 * text box can close it. Which screen a tap was on is left to the caller.
 */
final class TouchWatcher {
    interface Listener {
        /** A short touch ended on a touchscreen. downAt is in SystemClock.uptimeMillis(). */
        void onTap(String device, long downAt);
    }

    private static final long TAP_MAX_MS = 400;
    // struct input_event on 64-bit Android: 16 bytes of time, then type, code and value.
    private static final int EVENT_SIZE = 24;
    private static final int EV_KEY = 1, EV_ABS = 3, BTN_TOUCH = 0x14a, ABS_MT_TRACKING_ID = 0x39;

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<IRemoteProcess> processes = new ArrayList<>();
    private volatile boolean running;

    TouchWatcher(Listener listener) { this.listener = listener; }

    synchronized void start() {
        if (running || !ShizukuClient.hasPermission()) return;
        running = true;
        new Thread(new Runnable() {
            @Override public void run() {
                for (String device : touchscreens()) {
                    if (!running) return;
                    watch(device);
                }
            }
        }, "ThorPad touch watch").start();
    }

    synchronized void stop() {
        running = false;
        for (IRemoteProcess p : processes) {
            try {
                p.destroy();
            } catch (Exception ignored) {
            }
        }
        processes.clear();
    }

    /** The input devices that report multi-touch positions, e.g. /dev/input/event3. */
    private static List<String> touchscreens() {
        List<String> out = new ArrayList<>();
        String current = null;
        for (String line : run("getevent -p").split("\n")) {
            if (line.startsWith("add device")) {
                int at = line.indexOf("/dev/input/");
                current = at >= 0 ? line.substring(at).trim() : null;
            } else if (current != null && line.contains(" 0035 ")) { // ABS_MT_POSITION_X
                out.add(current);
                current = null;
            }
        }
        return out;
    }

    private void watch(final String device) {
        final IRemoteProcess p;
        try {
            // cat passes events on as they come; getevent's output can sit in a buffer.
            p = ShizukuClient.newProcess(new String[] {"cat", device});
        } catch (Exception e) {
            return;
        }
        synchronized (this) {
            if (!running) {
                try {
                    p.destroy();
                } catch (Exception ignored) {
                }
                return;
            }
            processes.add(p);
        }
        new Thread(new Runnable() {
            @Override public void run() { read(device, p); }
        }, "ThorPad touch " + device).start();
    }

    private void read(final String device, IRemoteProcess p) {
        boolean touching = false;
        long downAt = 0;
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(p.getInputStream())) {
            byte[] buf = new byte[EVENT_SIZE * 64];
            int have = 0;
            while (running) {
                int n = in.read(buf, have, buf.length - have);
                if (n <= 0) break;
                have += n;
                int off = 0;
                for (; off + EVENT_SIZE <= have; off += EVENT_SIZE) {
                    int type = (buf[off + 16] & 0xff) | (buf[off + 17] & 0xff) << 8;
                    int code = (buf[off + 18] & 0xff) | (buf[off + 19] & 0xff) << 8;
                    int value = (buf[off + 20] & 0xff) | (buf[off + 21] & 0xff) << 8
                            | (buf[off + 22] & 0xff) << 16 | (buf[off + 23] & 0xff) << 24;
                    boolean down = (type == EV_KEY && code == BTN_TOUCH && value == 1)
                            || (type == EV_ABS && code == ABS_MT_TRACKING_ID && value >= 0 && !touching);
                    boolean up = (type == EV_KEY && code == BTN_TOUCH && value == 0)
                            || (type == EV_ABS && code == ABS_MT_TRACKING_ID && value < 0 && touching);
                    if (down && !touching) {
                        touching = true;
                        downAt = SystemClock.uptimeMillis();
                    } else if (up && touching) {
                        touching = false;
                        final long at = downAt;
                        if (SystemClock.uptimeMillis() - at <= TAP_MAX_MS) {
                            main.post(new Runnable() {
                                @Override public void run() { if (running) listener.onTap(device, at); }
                            });
                        }
                    }
                }
                System.arraycopy(buf, off, buf, 0, have - off);
                have -= off;
            }
        } catch (Exception ignored) {
        }
    }

    private static String run(String command) {
        try {
            IRemoteProcess p = ShizukuClient.newProcess(new String[] {"sh", "-c", command});
            InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(p.getInputStream());
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            p.waitFor();
            p.destroy();
            return b.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
