package com.thorpad;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.widget.Toast;
import android.view.Display;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import moe.shizuku.server.IRemoteProcess;

/** Closes the app on the pointer screen, like swiping it away in recent apps. Needs Shizuku. */
final class TopApp {
    private static final Pattern RECORD = Pattern.compile("u\\d+ ([\\w.]+)/\\S+ t(\\d+)");

    private TopApp() {}

    private static final String[] PC_STREAMING_APPS = {
            "com.valvesoftware.steamlink", "com.parsec.app", "com.microsoft.rdc.androidx",
            "com.microsoft.rdc.android", "com.google.chromeremotedesktop", "com.carriez.flutter_hbb",
    };

    /**
     * Whether an app streams a PC: Moonlight and its forks (which keep Moonlight's code even
     * when renamed), Steam Link, Parsec, remote desktop apps, or one the user added.
     */
    static boolean streamsPc(String pkg, String cls, java.util.Set<String> userApps) {
        if (pkg == null) return false;
        if (userApps.contains(pkg)) return true;
        String p = pkg.toLowerCase(java.util.Locale.US);
        if (p.contains("moonlight") || p.contains("limelight")) return true;
        if (cls != null && cls.startsWith("com.limelight.")) return true;
        for (String a : PC_STREAMING_APPS) if (a.equals(pkg)) return true;
        return false;
    }

    static String label(Context context, String pkg) {
        try {
            return context.getPackageManager().getApplicationLabel(
                    context.getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    /**
     * Whether another app came to the front since the given time (System.currentTimeMillis()),
     * from Android's app usage events: a cheap check that lets ThorPad skip the dumpsys in on()
     * when nothing changed. null when ThorPad isn't allowed to read usage events.
     */
    static Boolean switchedSince(Context context, long since) {
        android.app.AppOpsManager ops = (android.app.AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(),
                context.getPackageName()) != android.app.AppOpsManager.MODE_ALLOWED) return null;
        android.app.usage.UsageStatsManager usm =
                (android.app.usage.UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        android.app.usage.UsageEvents events = usm.queryEvents(since, System.currentTimeMillis());
        if (events == null) return null;
        android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED
                    && !context.getPackageName().equals(e.getPackageName())) return true;
        }
        return false;
    }

    /** Lets ThorPad read app usage events (for switchedSince), through Shizuku. Off the main thread. */
    static void allowUsageEvents(Context context) {
        ShizukuClient.runShell("appops set " + context.getPackageName() + " GET_USAGE_STATS allow");
    }

    /**
     * The app on the given display: {package, activity class}, or null. Needs Shizuku and runs
     * a shell command, so call it off the main thread.
     */
    static String[] on(int displayId) {
        String dump = output("dumpsys activity activities | grep -E 'Display #|ResumedActivity|\\* Hist'");
        Matcher m = resumedOn(dump, displayId);
        if (m == null) return null;
        String component = m.group(0).substring(m.group(0).indexOf(' ') + 1);
        String cls = component.substring(component.indexOf('/') + 1);
        cls = cls.substring(0, cls.lastIndexOf(" t"));
        if (cls.startsWith(".")) cls = m.group(1) + cls;
        return new String[] {m.group(1), cls};
    }

    static void close(final Context context) {
        final Display target = Displays.pointerDisplay(context);
        final int displayId = target != null ? target.getDisplayId() : Display.DEFAULT_DISPLAY;
        final String home = homePackage(context);
        final String me = context.getPackageName();
        new Thread(new Runnable() {
            @Override public void run() {
                String dump = output("dumpsys activity activities");
                Matcher m = resumedOn(dump, displayId);
                if (m == null) {
                    toast(context, "Couldn't find the app on the top screen");
                    return;
                }
                String pkg = m.group(1), task = m.group(2);
                // Never close ThorPad itself, the home screen or the system UI.
                if (pkg.equals(me) || pkg.equals(home) || pkg.equals("com.android.systemui")) return;
                // Swipe it out of recent apps, and make sure it's really closed.
                ShizukuClient.runShell("am stack remove " + task + " ; cmd activity remove-task " + task
                        + " ; am force-stop " + pkg);
                toast(context, "Closed " + label(context, pkg));
            }
        }, "ThorPad close app").start();
    }

    private static final Pattern RECENT = Pattern.compile("#(\\d+) type=(\\w+) [AI]=\\d+:([\\w.]+)");

    /**
     * Clears recent apps, like Clear all on the recent apps screen: every app task goes, and its
     * app is stopped. ThorPad, the home screen, the system UI and Shizuku stay.
     */
    static void closeAll(final Context context) {
        final String home = homePackage(context);
        final String me = context.getPackageName();
        new Thread(new Runnable() {
            @Override public void run() {
                StringBuilder cmd = new StringBuilder();
                java.util.Set<String> apps = new java.util.LinkedHashSet<>();
                // Each "* Recent #n: Task{… #id type=… A=uid:affinity …}" line starts a task; its
                // realActivity line names the app. The affinity is only a fallback: apps (ThorPad
                // included) can set their own.
                java.util.List<String[]> tasks = new java.util.ArrayList<>();
                for (String line : output("dumpsys activity recents").split("\n")) {
                    String t = line.trim();
                    if (t.startsWith("* Recent #")) {
                        Matcher m = RECENT.matcher(t);
                        tasks.add(m.find() ? new String[] {m.group(1), m.group(2), m.group(3)} : null);
                    } else if (t.startsWith("realActivity=") && !tasks.isEmpty() && tasks.get(tasks.size() - 1) != null) {
                        String c = t.substring(t.indexOf('=') + 1).replace("{", "");
                        int slash = c.indexOf('/');
                        if (slash > 0) tasks.get(tasks.size() - 1)[2] = c.substring(0, slash);
                    }
                }
                for (String[] task : tasks) {
                    if (task == null || !task[1].equals("standard")) continue;
                    String pkg = task[2];
                    if (pkg.startsWith(me) || pkg.equals(home) || pkg.equals("com.android.systemui")) continue;
                    cmd.append("am stack remove ").append(task[0]).append(" ; cmd activity remove-task ")
                            .append(task[0]).append(" ; ");
                    if (!pkg.equals(SetupPanel.SHIZUKU)) apps.add(pkg);
                }
                for (String pkg : apps) cmd.append("am force-stop ").append(pkg).append(" ; ");
                if (cmd.length() == 0) {
                    toast(context, Lang.t("No recent apps to close"));
                    return;
                }
                ShizukuClient.runShell(cmd.toString());
                toast(context, Lang.t("Closed all recent apps"));
            }
        }, "ThorPad close all").start();
    }

    /**
     * The resumed (on-screen) activity of the given display in a dumpsys activity dump. Android
     * versions word this differently, so any "ResumedActivity" line in that display's section
     * counts; failing that, the display's topmost activity.
     */
    private static Matcher resumedOn(String dump, int displayId) {
        int display = -1;
        Matcher first = null;
        for (String line : dump.split("\n")) {
            String t = line.trim();
            if (t.startsWith("Display #")) {
                display = parseInt(t.substring(9));
            } else if (display == displayId) {
                Matcher m = RECORD.matcher(t);
                if (!m.find()) continue;
                if (t.contains("ResumedActivity")) return m;
                if (first == null && t.startsWith("* Hist")) first = m;
            }
        }
        return first;
    }


    private static void toast(final Context context, final String text) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() { Toast.makeText(context, text, Toast.LENGTH_SHORT).show(); }
        });
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
