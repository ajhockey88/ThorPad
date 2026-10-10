package com.thorpad;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import moe.shizuku.server.IRemoteProcess;

/**
 * A virtual input device made with Android's `uinput` shell tool, run through Shizuku.
 * Android treats it like a plugged-in USB device. The device exists while the tool runs;
 * closing its input unplugs it.
 */
class UinputDevice {
    static final int EV_SYN = 0, EV_KEY = 1, EV_REL = 2;
    // uinput ioctl numbers as the `uinput` tool's "configuration" expects them.
    static final int UI_SET_EVBIT = 100, UI_SET_KEYBIT = 101, UI_SET_RELBIT = 102;

    interface StateListener { void onDeviceStateChanged(UinputDevice device, boolean ready, String error); }

    private final String registerJson;
    private final StateListener listener;
    private final HandlerThread thread;
    final Handler io;

    private IRemoteProcess process;
    private OutputStream stdin;
    private volatile boolean ready;
    private volatile boolean starting;

    /**
     * @param config ready-made "configuration" entries, e.g. {"type":100,"data":[1,2]}
     */
    UinputDevice(String name, int productId, String config, StateListener listener) {
        this.listener = listener;
        registerJson = "{\"id\":1,\"command\":\"register\",\"name\":\"" + name + "\","
                + "\"vid\":4660,\"pid\":" + productId + ",\"bus\":\"usb\",\"configuration\":["
                + config + "]}";
        thread = new HandlerThread(name);
        thread.start();
        io = new Handler(thread.getLooper());
    }

    static String bits(int type, int... codes) {
        StringBuilder sb = new StringBuilder("{\"type\":").append(type).append(",\"data\":[");
        for (int i = 0; i < codes.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(codes[i]);
        }
        return sb.append("]}").toString();
    }

    boolean isReady() { return ready; }

    /** Ready, or plugging in right now. Input sent meanwhile is queued, not dropped. */
    boolean isStartingOrReady() { return ready || starting; }

    void start() {
        if (starting || ready) return;
        starting = true;
        io.post(new Runnable() {
            @Override public void run() {
                try {
                    process = ShizukuClient.newProcess(new String[] {"uinput", "-"});
                    ParcelFileDescriptor pfd = process.getOutputStream();
                    stdin = new ParcelFileDescriptor.AutoCloseOutputStream(pfd);
                    write(registerJson);
                    // Give Android a moment to register the new device before sending input.
                    SystemClock.sleep(400);
                    if (!process.alive()) throw new IOException("uinput exited (code " + process.exitValue() + ")");
                    ready = true;
                    starting = false;
                    report(true, null);
                } catch (Exception e) {
                    stopNow();
                    report(false, e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        });
    }

    /** Unplugs the device. */
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
        starting = false;
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
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() { listener.onDeviceStateChanged(UinputDevice.this, ok, error); }
        });
    }

    private void write(String json) throws IOException {
        stdin.write((json + "\n").getBytes(StandardCharsets.UTF_8));
        stdin.flush();
    }

    /** Sends evdev events (type, code, value triples) followed by a SYN_REPORT. */
    void send(final int... events) {
        if (!isStartingOrReady()) return;
        io.post(new Runnable() {
            @Override public void run() { inject(events); }
        });
    }

    /** Writes events straight away. Only on the io thread. */
    final void inject(int... events) {
        if (stdin == null) return;
        StringBuilder sb = new StringBuilder("{\"id\":1,\"command\":\"inject\",\"events\":[");
        for (int v : events) sb.append(v).append(',');
        sb.append(EV_SYN).append(",0,0]}");
        try {
            write(sb.toString());
        } catch (IOException e) {
            stopNow();
            report(false, "The virtual device stopped (" + e.getMessage() + ")");
        }
    }

    /** Presses and releases a key or button. */
    void tap(final int code, long holdMs) {
        send(EV_KEY, code, 1);
        io.postDelayed(new Runnable() {
            @Override public void run() { send(EV_KEY, code, 0); }
        }, holdMs);
    }
}
