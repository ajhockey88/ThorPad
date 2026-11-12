package com.thorpad;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/**
 * Does the uninstall the user confirmed in {@link Uninstaller#confirm}, ticking off each step:
 * keyboard, accessibility service, virtual devices, permissions, files and settings, then
 * removes ThorPad (silently through Shizuku, otherwise with Android's uninstall prompt).
 * The trackpad is stopped while this is on screen, so its virtual mouse and keyboard are unplugged.
 */
public final class UninstallActivity extends Activity {
    static final String EXTRA_SHIZUKU = "also_shizuku";
    private static final int CARD = Color.rgb(0x11, 0x13, 0x18), SUB = Color.rgb(0x8a, 0x90, 0x9b),
            OK_BG = Color.rgb(0x1d, 0x3a, 0x28), OK = Color.rgb(0x7f, 0xe0, 0xa0),
            NOW_BG = Color.rgb(0x16, 0x28, 0x3a), NOW_EDGE = Color.rgb(0x2b, 0x5f, 0x8a),
            LATER_BG = Color.rgb(0x22, 0x25, 0x2c);

    private float d;
    private LinearLayout page;
    private TextView title, lead;
    private final LinearLayout[] rows = new LinearLayout[6];
    private final TextView[] labels = new TextView[6];
    private final TextView[] badges = new TextView[6];
    private boolean alsoShizuku, started, promptShown;
    private String keyboardName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Lang.init(this);
        d = getResources().getDisplayMetrics().density;
        alsoShizuku = getIntent().getBooleanExtra(EXTRA_SHIZUKU, false);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(px(60), px(26), px(60), px(20));
        scroll.addView(page);
        setContentView(scroll);

        title = Uninstaller.text(this, Lang.t("Uninstalling ThorPad…"), 22, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        page.addView(title);
        lead = Uninstaller.text(this, Lang.t("Keep this screen open, it takes a few seconds"), 13, SUB);
        lead.setPadding(0, px(3), 0, px(14));
        page.addView(lead);

        InputMethodInfo back = Uninstaller.keyboardToRestore(this);
        keyboardName = back != null ? back.loadLabel(getPackageManager()).toString() : null;
        String[] steps = {
                keyboardName != null ? Lang.f("Keyboard switched back to %s", keyboardName)
                        : Lang.t("ThorPad Keyboard turned off"),
                Lang.t("Accessibility service turned off"),
                Lang.t("Virtual mouse and keyboard removed"),
                Lang.t("Taking back permissions"),
                Lang.t("Deleting logs and settings"),
                alsoShizuku ? Lang.t("Removing ThorPad and Shizuku") : Lang.t("Removing ThorPad"),
        };
        for (int i = 0; i < steps.length; i++) addRow(i, steps[i]);
        setStep(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!started) {
            started = true;
            new Thread(new Runnable() {
                @Override public void run() { work(); }
            }, "ThorPad uninstall").start();
        } else if (promptShown) {
            // Back from Android's uninstall prompt and still here: the user said no.
            cancelled();
        }
    }

    // --- The work --------------------------------------------------------------------------------

    private void work() {
        final boolean shizuku = ShizukuClient.hasPermission();
        Prefs prefs = new Prefs(this);
        // Stop ThorPad from switching the keyboard back while it's being removed.
        prefs.raw().edit().putBoolean(Prefs.KEY_KEEP_KEYBOARD, false).commit();
        prefs.setWantsThorPadKeyboard(false);
        String pkg = getPackageName();

        // 1. Keyboard: back to the previous one, and ThorPad's turned off. Without Shizuku,
        //    Android picks another keyboard by itself once ThorPad is removed.
        String mine = KeyboardKeeper.thorPadId(this);
        InputMethodInfo back = Uninstaller.keyboardToRestore(this);
        if (shizuku && mine != null) {
            if (back != null) {
                String id = back.getId();
                ShizukuClient.runShell("ime enable " + q(id) + "; ime set " + q(id));
                if (!id.equals(KeyboardKeeper.current(this))) {
                    ShizukuClient.runShell("settings put secure default_input_method " + q(id));
                }
            }
            ShizukuClient.runShell("ime disable " + q(mine));
        }
        log("keyboard now " + KeyboardKeeper.current(this));
        setStep(1);

        // 2. Accessibility service off.
        runOnUiThread(new Runnable() {
            @Override public void run() {
                ThorPadService s = ThorPadService.get();
                if (s != null) s.disableSelf();
            }
        });
        if (shizuku && Uninstaller.accessibilityOn(this)) {
            String rest = Uninstaller.accessibilityWithoutThorPad(this);
            if (rest.isEmpty()) {
                ShizukuClient.runShell("settings delete secure enabled_accessibility_services; "
                        + "settings put secure accessibility_enabled 0");
            } else {
                ShizukuClient.runShell("settings put secure enabled_accessibility_services " + q(rest));
            }
        }
        setStep(2);

        // 3. Virtual devices: unplugged when the trackpad stopped, but make sure no ThorPad
        //    "uinput -" process is left (Android hides on-screen keyboards while a keyboard
        //    device is plugged in), then forget the saved layout.
        if (shizuku) {
            ShizukuClient.runShell("for p in $(pidof uinput); do "
                    + "[ \"$(tr '\\0' ' ' < /proc/$p/cmdline)\" = 'uinput - ' ] && kill $p; done; true");
            log("layout: " + KeyboardLayoutTool.remove(this));
        }
        setStep(3);

        // 4. Permissions Shizuku gave (draw over apps, usage access) back to Android's defaults.
        if (shizuku) ShizukuClient.runShell("appops reset " + pkg);
        setStep(4);

        // 5. Diagnostic files in Download, then ThorPad's own settings and files.
        if (shizuku) {
            ShizukuClient.runShell("cd /sdcard/Download && rm -f thorpad-keyboard-log.txt thorpad-windows.txt "
                    + "thorpad-anr.txt thorpad-screen-*.png");
        }
        prefs.raw().edit().clear().commit();
        deleteTree(getCacheDir());
        deleteTree(getExternalFilesDir(null));
        deleteTree(getExternalCacheDir());
        setStep(5);

        // 6. Remove ThorPad (and Shizuku if asked).
        if (shizuku) {
            // Run apart from ThorPad and Shizuku's server, which both go away mid-way.
            String cmd = "sleep 1; pm uninstall " + pkg
                    + (alsoShizuku ? "; sleep 1; pm uninstall " + SetupPanel.SHIZUKU : "");
            boolean ok = ShizukuClient.runShell("c=" + q(cmd) + "; (setsid sh -c \"$c\" || nohup sh -c \"$c\") "
                    + "</dev/null >/dev/null 2>&1 &");
            if (ok) return; // ThorPad is about to be gone
        }
        runOnUiThread(new Runnable() {
            @Override public void run() { askAndroid(); }
        });
    }

    /** No Shizuku: Android's own uninstall prompt, for Shizuku first (underneath) if asked. */
    private void askAndroid() {
        if (alsoShizuku && Uninstaller.installed(this, SetupPanel.SHIZUKU)) {
            Intent s = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + SetupPanel.SHIZUKU));
            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(s); } catch (Exception ignored) { }
        }
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName()));
        promptShown = true;
        try {
            startActivity(i);
        } catch (Exception e) {
            cancelled();
        }
    }

    private void cancelled() {
        promptShown = false;
        title.setText(Lang.t("ThorPad wasn't removed"));
        lead.setText(Lang.t("Everything else is undone. Tap Uninstall to try again."));
        LinearLayout buttons = new LinearLayout(this);
        buttons.setPadding(0, px(12), 0, 0);
        TextView close = Uninstaller.pill(this, Lang.t("Close"), LATER_BG, Color.rgb(0xdd, 0xdd, 0xdd), d);
        TextView again = Uninstaller.pill(this, Lang.t("Uninstall"), Uninstaller.RED, Color.WHITE, d);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = px(6);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l2.leftMargin = px(6);
        buttons.addView(close, l1);
        buttons.addView(again, l2);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finishAffinity(); }
        });
        again.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                page.removeView((View) v.getParent());
                askAndroid();
            }
        });
        page.addView(buttons);
    }

    private static void deleteTree(File f) {
        if (f == null) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) {
            deleteTree(k);
            k.delete();
        }
    }

    private static String q(String s) { return "'" + s.replace("'", "'\\''") + "'"; }

    private static void log(String s) { KeyboardLog.add("uninstall: " + s); }

    // --- Rows ------------------------------------------------------------------------------------

    private void addRow(int i, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(14), px(11), px(14), px(11));
        TextView badge = Uninstaller.text(this, String.valueOf(i + 1), 14, SUB);
        badge.setGravity(Gravity.CENTER);
        row.addView(badge, new LinearLayout.LayoutParams(px(26), px(26)));
        TextView t = Uninstaller.text(this, label, 16, SUB);
        t.setPadding(px(12), 0, 0, 0);
        row.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px(7);
        page.addView(row, lp);
        rows[i] = row;
        labels[i] = t;
        badges[i] = badge;
    }

    /** Steps before n are done, n is running, the rest are waiting. */
    private void setStep(final int n) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                for (int i = 0; i < rows.length; i++) {
                    boolean done = i < n, now = i == n;
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(CARD);
                    bg.setCornerRadius(13 * d);
                    if (now) bg.setStroke(Math.max(1, (int) (1.5f * d)), NOW_EDGE);
                    rows[i].setBackground(bg);
                    GradientDrawable circle = new GradientDrawable();
                    circle.setShape(GradientDrawable.OVAL);
                    circle.setColor(done ? OK_BG : now ? NOW_BG : LATER_BG);
                    badges[i].setBackground(circle);
                    badges[i].setText(done ? "✓" : now ? "…" : String.valueOf(i + 1));
                    badges[i].setTextColor(done ? OK : now ? Color.rgb(0x6e, 0xc1, 0xff) : SUB);
                    labels[i].setTextColor(done || now ? Color.WHITE : SUB);
                }
            }
        });
    }

    private int px(float dp) { return Uninstaller.px(d, dp); }
}
