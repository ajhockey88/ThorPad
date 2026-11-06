package com.thorpad;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import moe.shizuku.server.IRemoteProcess;

/**
 * Keeps ThorPad's windows on the bottom screen to touches made on the bottom screen.
 *
 * Android can hand a window touches that didn't come from its own screen's touch panel: a
 * swipe on the top screen showing up on the trackpad, or ThorPad's own virtual mouse clicking
 * on it. Each touch says which input device made it, and Android knows which screen each
 * touch panel belongs to, so a touch whose device belongs to another screen (or isn't a touch
 * panel at all) is dropped, from its first finger down until the last finger lifts.
 */
final class TouchFilter {
    private static final Map<Integer, Integer> deviceDisplay = new HashMap<>();
    private static HandlerThread thread;
    private static Handler lookups;
    private static volatile boolean lookingUp;

    private final Context context;
    private boolean dropping;

    TouchFilter(Context context) {
        this.context = context;
        learn();
    }

    /** True if this event belongs to a touch from somewhere other than the trackpad's screen. */
    boolean drop(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            dropping = foreign(ev);
            if (dropping) {
                InputDevice d = InputDevice.getDevice(ev.getDeviceId());
                KeyboardLog.add("touch ignored: from " + (d != null ? d.getName() : "device " + ev.getDeviceId())
                        + (ev.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) ? ", another screen" : ", not a touch screen"));
            }
        }
        boolean drop = dropping;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) dropping = false;
        return drop;
    }

    private boolean foreign(MotionEvent ev) {
        // The trackpad is for fingers: a mouse (ThorPad's own, when its cursor wanders onto
        // this screen) or a gamepad's touch area isn't one.
        if (!ev.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) return true;
        Display pad = Displays.trackpadDisplay(context);
        if (pad == null) return false;
        Integer display = displayOf(ev.getDeviceId());
        if (display == null || display == pad.getDisplayId()) return false;
        // Only trust the map once it has a touch panel on the trackpad's screen too: a map that
        // puts every panel on one screen is wrong, and must never shut the trackpad off.
        synchronized (deviceDisplay) {
            return deviceDisplay.containsValue(pad.getDisplayId());
        }
    }

    /** The screen a touch panel belongs to, or null while that's not known yet. */
    private Integer displayOf(int deviceId) {
        synchronized (deviceDisplay) {
            if (deviceDisplay.containsKey(deviceId)) return deviceDisplay.get(deviceId);
        }
        Integer found = associated(deviceId);
        if (found != null) {
            synchronized (deviceDisplay) {
                deviceDisplay.put(deviceId, found);
            }
            return found;
        }
        learn(); // new device: ask Android again in the background
        return null;
    }

    /** Android's own answer, where it lets apps ask (InputDevice.getAssociatedDisplayId). */
    private static Integer associated(int deviceId) {
        InputDevice d = InputDevice.getDevice(deviceId);
        if (d == null) return null;
        try {
            Object id = InputDevice.class.getMethod("getAssociatedDisplayId").invoke(d);
            int display = (Integer) id;
            // A touch panel tied to no screen in particular works on the default one.
            return display < 0 ? Display.DEFAULT_DISPLAY : display;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Reads which screen each touch panel is mapped to from Android's input dump, through
     * Shizuku. Each device's section names its screen as "displayId=N" in its viewport line.
     */
    private static synchronized void learn() {
        if (lookingUp || !ShizukuClient.hasPermission()) return;
        lookingUp = true;
        if (thread == null) {
            thread = new HandlerThread("ThorPad touch screens");
            thread.start();
            lookups = new Handler(thread.getLooper());
        }
        lookups.post(new Runnable() {
            @Override public void run() {
                try {
                    Map<Integer, Integer> map = parse(dump());
                    synchronized (deviceDisplay) {
                        deviceDisplay.putAll(map);
                    }
                    if (!map.isEmpty()) KeyboardLog.add("touch screens: " + map);
                } finally {
                    lookingUp = false;
                }
            }
        });
    }

    private static final Pattern DEVICE = Pattern.compile("^\\s*Device (-?\\d+):");
    private static final Pattern VIEWPORT = Pattern.compile("Viewport.*?displayId=(-?\\d+)");

    static Map<Integer, Integer> parse(String dump) {
        Map<Integer, Integer> out = new HashMap<>();
        boolean reader = false;
        Integer device = null;
        for (String line : dump.split("\n")) {
            if (line.contains("Input Reader State")) {
                reader = true;
                continue;
            }
            if (!reader) continue;
            if (!line.startsWith(" ") && line.trim().length() > 0) break; // the next section
            Matcher m = DEVICE.matcher(line);
            if (m.find()) {
                device = Integer.parseInt(m.group(1));
                continue;
            }
            // A line as far out as "Device N:" (e.g. "Configuration:") ends the device's part.
            if (!line.startsWith("   ") && line.trim().length() > 0) device = null;
            if (device == null || out.containsKey(device)) continue;
            Matcher v = VIEWPORT.matcher(line);
            if (v.find()) out.put(device, Integer.parseInt(v.group(1)));
        }
        return out;
    }

    private static String dump() {
        try {
            IRemoteProcess p = ShizukuClient.newProcess(new String[] {"dumpsys", "input"});
            InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(p.getInputStream());
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
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
