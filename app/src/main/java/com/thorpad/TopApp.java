package com.thorpad;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.ParcelFileDescriptor;
import android.view.Display;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import moe.shizuku.server.IRemoteProcess;

/** Closes the app on the pointer screen, like swiping it away in recent apps. Needs Shizuku. */
final class TopApp {
    private static final Pattern RECORD = Pattern.compile("u\\d+ ([\\w.]+)/\\S* t(\\d+)");

    private TopApp() {}

    static void close(final Context context) {
        final Display target = Displays.pointerDisplay(context);
        final int displayId = target != null ? target.getDisplayId() : Display.DEFAULT_DISPLAY;
        final String home = homePackage(context);
        final String me = context.getPackageName();
        new Thread(new Runnable() {
            @Override public void run() {
                String dump = output("dumpsys activity activities");
                Matcher m = resumedOn(dump, displayId);
                if (m == null) return;
                String pkg = m.group(1), task = m.group(2);
                // Never close ThorPad itself, the home screen or the system UI.
                if (pkg.equals(me) || pkg.equals(home) || pkg.equals("com.android.systemui")) return;
                if (!ShizukuClient.runShell("am stack remove " + task)) {
                    ShizukuClient.runShell("am force-stop " + pkg);
                }
            }
        }, "ThorPad close app").start();
    }

    /** The resumed (on-screen) activity of the given display in a dumpsys activity dump. */
    private static Matcher resumedOn(String dump, int displayId) {
        int display = -1;
        for (String line : dump.split("\n")) {
            String t = line.trim();
            if (t.startsWith("Display #")) {
                display = parseInt(t.substring(9));
            } else if (display == displayId && t.startsWith("ResumedActivity:")) {
                Matcher m = RECORD.matcher(t);
                if (m.find()) return m;
            }
        }
        return null;
    }

    private static int parseInt(String s) {
        int n = 0, i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) n = n * 10 + (s.charAt(i++) - '0');
        return i == 0 ? -1 : n;
    }

    private static String homePackage(Context context) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo r = context.getPackageManager().resolveActivity(i, 0);
        return r != null && r.activityInfo != null ? r.activityInfo.packageName : "";
    }

    private static String output(String command) {
        try {
            IRemoteProcess p = ShizukuClient.newProcess(new String[] {"sh", "-c", command});
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
