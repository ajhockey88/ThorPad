package com.thorpad;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import moe.shizuku.server.IRemoteProcess;

/**
 * Checks GitHub for a newer ThorPad release and, if there is one, downloads its APK and
 * installs it: silently through Shizuku when it's allowed, otherwise with Android's own
 * install prompt.
 */
final class Updater {
    static final String LATEST = "https://api.github.com/repos/ajhockey88/ThorPad/releases/latest";
    static final String ALL = "https://api.github.com/repos/ajhockey88/ThorPad/releases?per_page=50";

    interface Listener {
        /** Progress text for the settings screen. done = the check is over. */
        void onUpdateStatus(String status, boolean done);

        /** A newer version is out: show what's new and let the user decide. */
        void onUpdateAvailable(Release release);
    }

    /** A newer ThorPad release found on GitHub. */
    static final class Release {
        final String version, current, notes, apkUrl;
        final long apkSize;

        Release(String version, String current, String notes, String apkUrl, long apkSize) {
            this.version = version;
            this.current = current;
            this.notes = notes;
            this.apkUrl = apkUrl;
            this.apkSize = apkSize;
        }
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    Updater(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    /** Looks for a newer release; reports it through onUpdateAvailable. */
    void check() { check(false); }

    /**
     * reinstall = also offer the latest release when it's the version already installed, for
     * when a release's APK was replaced without a new version number.
     */
    void check(final boolean reinstall) {
        background(new Task() {
            @Override public void run() throws Exception {
                post(Lang.t("Checking for updates…"), false);
                JSONObject release = new JSONObject(readAll(open(LATEST)));
                String tag = release.optString("tag_name", "");
                String latest = tag.startsWith("v") ? tag.substring(1) : tag;
                String mine = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
                int cmp = compareVersions(latest, mine);
                if (cmp < 0 || (cmp == 0 && !reinstall)) {
                    post(Lang.f("ThorPad %s is up to date.", mine), true);
                    return;
                }
                String url = null;
                long size = 0;
                JSONArray assets = release.optJSONArray("assets");
                for (int i = 0; assets != null && i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (a.optString("name").endsWith(".apk")) {
                        url = a.optString("browser_download_url");
                        size = a.optLong("size");
                    }
                }
                if (url == null) {
                    post(Lang.f("Version %s is out, but its release has no APK yet.", latest), true);
                    return;
                }
                String notes = release.optString("body", "");
                if (cmp > 0) {
                    try {
                        notes = notesSince(mine, latest, notes);
                    } catch (Exception ignored) {
                        // Just the newest release's notes, then.
                    }
                }
                final Release r = new Release(latest, mine, notes, url, size);
                post(Lang.f(cmp == 0 ? "Ready to reinstall ThorPad %s." : "ThorPad %s is available.", latest), true);
                main.post(new Runnable() {
                    @Override public void run() { listener.onUpdateAvailable(r); }
                });
            }
        });
    }

    /** Downloads and installs a release found by check(). */
    void install(final Release r) {
        background(new Task() {
            @Override public void run() throws Exception {
                File apk = new File(context.getCacheDir(), "ThorPad-update.apk");
                download(r.apkUrl, apk, r.version);
                if (ShizukuClient.hasPermission()) {
                    post(Lang.f("Installing %s… ThorPad will close and can be reopened.", r.version), false);
                    if (installWithShizuku(apk)) return; // ThorPad is replaced and restarted
                }
                post(Lang.f("Installing %s… confirm on the install prompt.", r.version), true);
                installWithPrompt(apk, context.getPackageName());
            }
        });
    }

    /**
     * Downloads the APK from another app's latest GitHub release (e.g. Shizuku during setup) and
     * opens Android's install prompt for it.
     */
    void installApp(final String latestApi, final String name, final String pkg) {
        background(new Task() {
            @Override public void run() throws Exception {
                post(Lang.f("Downloading %s… %s%%", name, 0), false);
                JSONObject release = new JSONObject(readAll(open(latestApi)));
                String url = null;
                JSONArray assets = release.optJSONArray("assets");
                for (int i = 0; assets != null && i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (a.optString("name").endsWith(".apk")) url = a.optString("browser_download_url");
                }
                if (url == null) throw new IOException("no APK in the latest release");
                File apk = new File(context.getCacheDir(), name + ".apk");
                download(url, apk, name);
                post(Lang.f("Installing %s… confirm on the install prompt.", name), true);
                installWithPrompt(apk, pkg);
            }
        }, "Install failed: %s");
    }

    /**
     * The notes of every release newer than the installed version, newest first, each under a
     * "# ThorPad x" header, so someone a few versions behind sees everything they're getting.
     */
    private String notesSince(String mine, String latest, String latestNotes) throws Exception {
        JSONArray all = new JSONArray(readAll(open(ALL)));
        java.util.List<String[]> found = new java.util.ArrayList<>();
        for (int i = 0; i < all.length(); i++) {
            JSONObject rel = all.getJSONObject(i);
            if (rel.optBoolean("draft") || rel.optBoolean("prerelease")) continue;
            String tag = rel.optString("tag_name", "");
            String v = tag.startsWith("v") ? tag.substring(1) : tag;
            if (compareVersions(v, mine) > 0 && compareVersions(v, latest) <= 0) {
                found.add(new String[] {v, rel.optString("body", "")});
            }
        }
        if (found.size() <= 1) return latestNotes;
        java.util.Collections.sort(found, new java.util.Comparator<String[]>() {
            @Override public int compare(String[] a, String[] b) { return compareVersions(b[0], a[0]); }
        });
        StringBuilder sb = new StringBuilder();
        for (String[] rel : found) {
            sb.append("# ThorPad ").append(rel[0]).append('\n').append(rel[1]).append("\n\n");
        }
        return sb.toString();
    }

    private interface Task { void run() throws Exception; }

    private void background(final Task task) { background(task, "Update failed: %s"); }

    private void background(final Task task, final String failure) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    task.run();
                } catch (Exception e) {
                    post(Lang.f(failure, e.getMessage() != null ? e.getMessage() : e), true);
                }
            }
        }, "ThorPad update").start();
    }

    private void download(String url, File to, String version) throws IOException {
        HttpURLConnection c = open(url);
        long total = c.getContentLengthLong();
        InputStream in = c.getInputStream();
        OutputStream out = new FileOutputStream(to);
        try {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int lastPct = -1;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                int pct = total > 0 ? (int) (done * 100 / total) : -1;
                if (pct != lastPct && pct >= 0) {
                    lastPct = pct;
                    post(Lang.f("Downloading %s… %s%%", version, pct), false);
                }
            }
        } finally {
            out.close();
            in.close();
        }
    }

    /** pm install, fed the APK on stdin, as the Shizuku (shell) user: no prompt. */
    private boolean installWithShizuku(File apk) {
        try {
            IRemoteProcess p = ShizukuClient.newProcess(new String[] {
                    "pm", "install", "-r", "-S", String.valueOf(apk.length())});
            OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(p.getOutputStream());
            InputStream in = new FileInputStream(apk);
            try {
                copy(in, out);
            } finally {
                in.close();
                out.close();
            }
            String result = readAll(new ParcelFileDescriptor.AutoCloseInputStream(p.getInputStream()));
            p.waitFor();
            if (result.contains("Success")) return true;
            post(Lang.f("Shizuku couldn't install it (%s), trying Android's installer.", result.trim()), false);
        } catch (Exception e) {
            // Fall back to the normal install prompt.
        }
        return false;
    }

    /** Android's installer: it asks the user to confirm (and, the first time, to allow ThorPad). */
    private void installWithPrompt(File apk, String pkg) throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(pkg);
        int id = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(id);
        try {
            OutputStream out = session.openWrite(apk.getName(), 0, apk.length());
            InputStream in = new FileInputStream(apk);
            try {
                copy(in, out);
                session.fsync(out);
            } finally {
                in.close();
                out.close();
            }
            Intent done = new Intent(context, InstallReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(context, id, done,
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            session.commit(pi.getIntentSender());
        } finally {
            session.close();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        if (url.startsWith("https://api.github.com/")) c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "ThorPad");
        if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
        return c;
    }

    private static String readAll(HttpURLConnection c) throws IOException {
        return readAll(c.getInputStream());
    }

    private static String readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        try {
            copy(in, b);
        } finally {
            in.close();
        }
        return b.toString("UTF-8");
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    /** Compares dotted versions like 1.10 and 1.9 by number. */
    static int compareVersions(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0;
            int q = i < y.length ? num(y[i]) : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    private static int num(String s) {
        int n = 0;
        for (int i = 0; i < s.length() && Character.isDigit(s.charAt(i)); i++) n = n * 10 + (s.charAt(i) - '0');
        return n;
    }

    private void post(final String status, final boolean done) {
        main.post(new Runnable() {
            @Override public void run() { listener.onUpdateStatus(status, done); }
        });
    }
}
