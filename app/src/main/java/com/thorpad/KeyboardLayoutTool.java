package com.thorpad;

import android.content.Context;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.security.MessageDigest;

import moe.shizuku.server.IRemoteProcess;

/**
 * Gives ThorPad's virtual keyboard a keyboard layout (English US), so Android doesn't post its
 * "Configure physical keyboard" notification, with a sound, every time the keyboard plugs in.
 * Android only lets the shell set layouts, so {@link #setUp} runs {@link #main} as the shell
 * user through Shizuku and app_process. It runs once the mouse is plugged in, before the
 * keyboard ever is (Android keys saved layouts by vendor and product id), and again when the
 * keyboard plugs in, to check it took.
 */
final class KeyboardLayoutTool {
    private static final int VENDOR = 4660, PRODUCT = 22138; // as VirtualKeyboard registers
    private static final String NAME = "ThorPad Keyboard";
    private static final String LAYOUT = "keyboard_layout_english_us";

    /** Sets the layout once (remembered in Prefs). Call off the main thread. */
    static void setUp(Context context) {
        Prefs prefs = new Prefs(context);
        if (prefs.keyboardLayoutSet() || !ShizukuClient.hasPermission()) return;
        String result = "";
        try {
            String apk = context.getApplicationInfo().sourceDir;
            IRemoteProcess p = ShizukuClient.newProcess(new String[] {"sh", "-c",
                    "CLASSPATH='" + apk + "' app_process /system/bin " + KeyboardLayoutTool.class.getName() + " 2>&1"});
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    new ParcelFileDescriptor.AutoCloseInputStream(p.getInputStream())));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line).append('\n');
            p.waitFor();
            p.destroy();
            result = sb.toString().trim();
        } catch (Throwable e) {
            result = "failed: " + e;
        }
        KeyboardLog.add("keyboard layout: " + result);
        // Done for good once the plugged-in keyboard itself is seen with its layout.
        if (result.startsWith("ok plugged")) prefs.setKeyboardLayoutSet();
    }

    /** Runs as the shell user: sets ThorPad Keyboard's layout and prints "ok ..." or the error. */
    public static void main(String[] args) {
        try {
            IBinder b = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "input");
            Object im = Class.forName("android.hardware.input.IInputManager$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null, b);
            Class<?> imClass = Class.forName("android.hardware.input.IInputManager");
            Class<?> idClass = Class.forName("android.hardware.input.InputDeviceIdentifier");

            // The plugged-in keyboard's own identifier if it's there, otherwise one built the
            // same way Android builds it.
            Object id = null;
            String which = "plugged";
            int[] ids = (int[]) imClass.getMethod("getInputDeviceIds").invoke(im);
            for (int i : ids) {
                android.view.InputDevice d = (android.view.InputDevice)
                        imClass.getMethod("getInputDevice", int.class).invoke(im, i);
                if (d != null && NAME.equals(d.getName())) {
                    id = android.view.InputDevice.class.getMethod("getIdentifier").invoke(d);
                    break;
                }
            }
            if (id == null) {
                which = "built";
                id = idClass.getConstructor(String.class, int.class, int.class)
                        .newInstance(sha1(String.format(":%04x:%04x:", VENDOR, PRODUCT)), VENDOR, PRODUCT);
            }

            Method getCurrent = imClass.getMethod("getCurrentKeyboardLayoutForInputDevice", idClass);
            String current = (String) getCurrent.invoke(im, id);
            if (current != null) {
                System.out.println("ok " + which + " already " + current);
                return;
            }
            String layout = null;
            Object[] layouts = (Object[]) imClass.getMethod("getKeyboardLayouts").invoke(im);
            for (Object l : layouts) {
                String desc = (String) l.getClass().getMethod("getDescriptor").invoke(l);
                if (desc != null && desc.endsWith("/" + LAYOUT)) layout = desc;
            }
            if (layout == null) {
                System.out.println("no English (US) layout among " + layouts.length);
                return;
            }
            try {
                imClass.getMethod("addKeyboardLayoutForInputDevice", idClass, String.class).invoke(im, id, layout);
            } catch (Throwable ignored) {
                // not on every Android version; setting the current layout is what counts
            }
            imClass.getMethod("setCurrentKeyboardLayoutForInputDevice", idClass, String.class).invoke(im, id, layout);
            current = (String) getCurrent.invoke(im, id);
            System.out.println(layout.equals(current) ? "ok " + which + " set " + layout : "not saved, current " + current);
        } catch (Throwable e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            System.out.println("error " + cause);
        }
    }

    private static String sha1(String s) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte x : h) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
