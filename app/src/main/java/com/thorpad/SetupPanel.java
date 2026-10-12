package com.thorpad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * First-run setup as a checklist: every step on one screen, the current one highlighted, each with
 * a button that does the work (downloads Shizuku, asks for its permission, switches keyboards).
 * Steps tick themselves off as TrackpadActivity re-checks once a second.
 */
final class SetupPanel extends ScrollView implements Updater.Listener {
    interface Host {
        /** The virtual mouse failed; clear the error and try it again. */
        void retryMouse();

        /** Something changed here (e.g. Skip): re-check right away. */
        void refreshSetup();
    }

    static final String SHIZUKU = "moe.shizuku.privileged.api";
    private static final String SHIZUKU_LATEST = "https://api.github.com/repos/RikkaApps/Shizuku/releases/latest";
    private static final int CARD = Color.rgb(0x11, 0x13, 0x18), NOW = Color.rgb(0x12, 0x26, 0x3a),
            NOW_EDGE = Color.rgb(0x2f, 0x6d, 0xa3), NEXT = Color.rgb(0x0c, 0x0d, 0x11),
            NEXT_EDGE = Color.rgb(0x16, 0x18, 0x1d), BLUE = Color.rgb(0x2f, 0x86, 0xd6),
            LINK = Color.rgb(0x78, 0xc8, 0xff), SUB = Color.rgb(0x8a, 0x90, 0x9b),
            DIM = Color.rgb(0x7c, 0x82, 0x8d), GHOST = Color.rgb(0x1c, 0x1e, 0x24);
    private static final int DONE = 0, CURRENT = 1, LATER = 2;
    private static final Pattern PERCENT = Pattern.compile("(\\d+)\\s*%");

    private final Activity activity;
    private final Host host;
    private final float d;
    private final LinearLayout page;
    private boolean touchMode, skipped, downloading;
    private int downloadPct;
    private String downloadStatus;
    private String shown = "";

    SetupPanel(Activity activity, Host host) {
        super(activity);
        this.activity = activity;
        this.host = host;
        d = activity.getResources().getDisplayMetrics().density;
        setBackgroundColor(Color.BLACK);
        setClickable(true); // swallow touches so the pad underneath stays inert
        page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(px(30), px(26), px(30), px(26));
        addView(page);
    }

    /** The user tapped Skip for now on the keyboard step. */
    boolean skipped() { return skipped; }

    boolean keyboardInUse() {
        InputMethodInfo mine = thorPadKeyboard();
        return mine != null && mine.getId().equals(currentKeyboardId());
    }

    /**
     * Redraws the steps for the current state. ready = the trackpad works (mouse or touch mode),
     * so only the keyboard step is left.
     */
    void update(boolean shizukuReady, String mouseError, boolean touchOn, boolean ready) {
        boolean installed = installed(SHIZUKU) || ShizukuClient.isRunning();
        boolean running = ShizukuClient.isRunning();
        boolean keyboard = keyboardInUse();
        String state = touchMode + "," + installed + "," + running + "," + shizukuReady + "," + mouseError
                + "," + touchOn + "," + ready + "," + keyboard + "," + downloading + "," + downloadPct
                + "," + downloadStatus + "," + developerOptions();
        if (state.equals(shown)) return;
        shown = state;
        page.removeAllViews();

        LinearLayout top = new LinearLayout(activity);
        top.setGravity(Gravity.BOTTOM);
        LinearLayout words = new LinearLayout(activity);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(Lang.t("Welcome to ThorPad 👋"), 23, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        words.addView(title);
        TextView lead = text(Lang.t(touchMode
                ? "Touch mode works in regular Android apps. Games won't get a real mouse."
                : "Four quick steps for the real mouse. Each button does the work for you."), 14, SUB);
        lead.setPadding(0, px(4), 0, 0);
        words.addView(lead);
        top.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(ghost("🌐 " + Lang.name() + " ▾", true, new View.OnClickListener() {
            @Override public void onClick(View v) { pickLanguage(); }
        }));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = px(14);
        page.addView(top, tlp);

        int n = 1;
        if (!touchMode) {
            int s1 = installed ? DONE : CURRENT;
            int s2 = running ? DONE : installed ? CURRENT : LATER;
            boolean allowed = shizukuReady && mouseError == null;
            int s3 = allowed ? DONE : running ? CURRENT : LATER;
            step(n++, s1, installed ? "Shizuku installed" : "Get Shizuku",
                    "Lets ThorPad act as a real USB mouse and keyboard", installed ? null
                    : primary(downloading ? Lang.t("Downloading…") : Lang.t("Download"), !downloading,
                    new View.OnClickListener() {
                        @Override public void onClick(View v) { downloadShizuku(); }
                    }), installed ? null : progress());
            View startButtons = null;
            if (!running && installed) {
                LinearLayout row = new LinearLayout(activity);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(ghost(Lang.t(developerOptions() ? "Wireless debugging" : "Developer options"),
                        false, new View.OnClickListener() {
                            @Override public void onClick(View v) { openDeveloperOptions(); }
                        }));
                View open = primary(Lang.t("Open Shizuku"), true, new View.OnClickListener() {
                    @Override public void onClick(View v) { openShizuku(); }
                });
                LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                olp.leftMargin = px(10);
                row.addView(open, olp);
                startButtons = row;
            }
            step(n++, s2, running ? "Shizuku running" : "Start Shizuku", developerOptions()
                    ? "Turn on Wireless debugging, then tap Start in Shizuku. ThorPad notices by itself."
                    : "First turn on developer options: tap Build number 7 times. Then start Shizuku with Wireless debugging.",
                    startButtons, null);
            String allowSub = mouseError != null ? Lang.f("The virtual mouse couldn't start: %s", mouseError)
                    : Lang.t("One tap in Shizuku's prompt");
            step(n++, s3, allowed ? "ThorPad allowed" : "Allow ThorPad", allowSub, allowed ? null
                    : button(mouseError != null ? Lang.t("Try again") : Lang.t("Allow"), s3 == CURRENT,
                    new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            if (mouseError != null) host.retryMouse();
                            else ShizukuClient.requestPermission();
                        }
                    }), null);
        } else {
            step(n++, touchOn ? DONE : LATER, "Allow restricted settings",
                    "Only if Android says the setting is restricted: tap ⋮ at the top right, then Allow restricted settings",
                    touchOn ? null : ghost(Lang.t("ThorPad app info"), false, new View.OnClickListener() {
                        @Override public void onClick(View v) { openAppInfo(); }
                    }), null);
            step(n++, touchOn ? DONE : CURRENT, touchOn ? "Touch mode on" : "Turn on ThorPad trackpad",
                    "In Accessibility settings, turn on ThorPad trackpad", touchOn ? null
                    : primary(Lang.t("Accessibility settings"), true, new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            start(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        }
                    }), null);
        }
        step(n, keyboard ? DONE : ready ? CURRENT : LATER, keyboard ? "ThorPad keyboard in use" : "Use ThorPad keyboard",
                "Text boxes on the top screen open the keyboard here", keyboard ? null
                : button(Lang.t("Turn on"), ready, new View.OnClickListener() {
                    @Override public void onClick(View v) { useThorPadKeyboard(); }
                }), null);

        LinearLayout foot = new LinearLayout(activity);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(0, px(8), 0, 0);
        TextView other;
        if (touchMode) {
            other = text(Lang.t("Use the real mouse instead"), 13, LINK);
        } else {
            String a = Lang.t("No Shizuku?") + " ", b = Lang.t("Use touch mode instead"),
                    c = " " + Lang.t("(fine for regular apps, not games)");
            SpannableString s = new SpannableString(a + b + c);
            s.setSpan(new ForegroundColorSpan(LINK), a.length(), a.length() + b.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            other = text("", 13, SUB);
            other.setText(s);
        }
        other.setPadding(0, px(8), 0, px(8));
        other.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Haptics.play(activity, Haptics.BAR);
                touchMode = !touchMode;
                shown = "";
                host.refreshSetup();
            }
        });
        foot.addView(other, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (ready) {
            TextView skip = text(Lang.t("Skip for now"), 13, LINK);
            skip.setPadding(px(12), px(8), 0, px(8));
            skip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Haptics.play(activity, Haptics.BAR);
                    skipped = true;
                    host.refreshSetup();
                }
            });
            foot.addView(skip);
        }
        page.addView(foot);
    }

    // --- Steps -------------------------------------------------------------------------------

    private void step(int n, int state, String title, String sub, View right, View under) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(15), px(12), px(15), px(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(13 * d);
        bg.setColor(state == CURRENT ? NOW : state == DONE ? CARD : NEXT);
        bg.setStroke(px(1), state == CURRENT ? NOW_EDGE : state == DONE ? CARD : NEXT_EDGE);
        row.setBackground(bg);

        TextView badge = text(state == DONE ? "✓" : String.valueOf(n), 16,
                state == DONE ? Color.rgb(0x8f, 0xf0, 0xb0) : state == CURRENT ? Color.WHITE : Color.rgb(0x6b, 0x71, 0x7c));
        badge.setGravity(Gravity.CENTER);
        if (state == CURRENT) badge.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        if (state == DONE) circle.setColor(Color.rgb(0x1f, 0x51, 0x32));
        else if (state == CURRENT) circle.setColor(BLUE);
        else circle.setStroke(px(1.5f), Color.rgb(0x3a, 0x3e, 0x48));
        badge.setBackground(circle);
        row.addView(badge, new LinearLayout.LayoutParams(px(31), px(31)));

        LinearLayout words = new LinearLayout(activity);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(Lang.t(title), 17, state == LATER ? DIM : Color.WHITE));
        TextView s = text(Lang.t(sub), 12.5f, SUB);
        s.setPadding(0, px(2), 0, 0);
        words.addView(s);
        if (under != null) words.addView(under);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wlp.leftMargin = px(14);
        wlp.rightMargin = px(10);
        row.addView(words, wlp);
        if (right != null) row.addView(right);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px(8);
        page.addView(row, lp);
    }

    /** The bar and text under Get Shizuku while it downloads, or what the installer said. */
    private View progress() {
        if (downloadStatus == null) return null;
        LinearLayout box = new LinearLayout(activity);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(0, px(9), 0, 0);
        if (downloading) {
            ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress(downloadPct);
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, px(8), 1f);
            blp.rightMargin = px(10);
            box.addView(bar, blp);
        }
        box.addView(text(downloadStatus, 12, Color.rgb(0x9f, 0xd2, 0xf5)));
        return box;
    }

    private TextView primary(String label, boolean enabled, View.OnClickListener l) {
        return button(label, enabled, l);
    }

    /** A filled blue button when it's this step's turn, a dim one otherwise. */
    private TextView button(String label, boolean active, final View.OnClickListener l) {
        TextView b = text(label, 15, active ? Color.WHITE : DIM);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine();
        b.setPadding(px(17), px(11), px(17), px(11));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20 * d);
        bg.setColor(active ? BLUE : GHOST);
        b.setBackground(bg);
        if (active) b.setOnClickListener(tapped(l));
        return b;
    }

    private TextView ghost(String label, boolean chip, View.OnClickListener l) {
        TextView b = text(label, chip ? 15 : 14, Color.rgb(0xcf, 0xe9, 0xff));
        b.setGravity(Gravity.CENTER);
        b.setSingleLine();
        b.setPadding(px(15), px(10), px(15), px(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20 * d);
        bg.setColor(GHOST);
        b.setBackground(bg);
        b.setOnClickListener(tapped(l));
        return b;
    }

    private View.OnClickListener tapped(final View.OnClickListener l) {
        return new View.OnClickListener() {
            @Override public void onClick(View v) {
                Haptics.play(activity, Haptics.BAR);
                l.onClick(v);
            }
        };
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(activity);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private int px(float dp) { return (int) (dp * d); }

    // --- What the buttons do -----------------------------------------------------------------

    private void downloadShizuku() {
        downloading = true;
        downloadPct = 0;
        downloadStatus = Lang.f("Downloading %s… %s%%", "Shizuku", 0);
        host.refreshSetup();
        new Updater(activity, this).installApp(SHIZUKU_LATEST, "Shizuku", SHIZUKU);
    }

    @Override public void onUpdateStatus(String status, boolean done) {
        downloadStatus = status;
        Matcher m = PERCENT.matcher(status);
        if (!done && m.find()) downloadPct = Integer.parseInt(m.group(1));
        if (done) downloading = false;
        host.refreshSetup();
    }

    @Override public void onUpdateAvailable(Updater.Release release) { }

    private void openShizuku() {
        Intent i = activity.getPackageManager().getLaunchIntentForPackage(SHIZUKU);
        if (i == null) i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases"));
        start(i);
    }

    private boolean developerOptions() {
        return Settings.Global.getInt(activity.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
    }

    private void openDeveloperOptions() {
        if (developerOptions()) {
            start(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } else {
            start(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
            Toast.makeText(activity, Lang.t("Tap Build number 7 times to turn on developer options"),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openAppInfo() {
        start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + activity.getPackageName())));
    }

    private void start(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(i);
        } catch (RuntimeException e) {
            Toast.makeText(activity, String.valueOf(e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean installed(String pkg) {
        try {
            activity.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void pickLanguage() {
        new AlertDialog.Builder(activity)
                .setTitle(Lang.t("Keyboard language"))
                .setSingleChoiceItems(Lang.NAMES, Lang.indexOf(Lang.code()), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (Lang.CODES[which].equals(Lang.code())) return;
                        new Prefs(activity).setLanguage(Lang.CODES[which]);
                        Autocorrect.release(); // the next keyboard loads the new dictionary
                        activity.recreate();
                    }
                })
                .setNegativeButton(Lang.t("Cancel"), null)
                .show();
    }

    private InputMethodManager imm() {
        return (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
    }

    private String currentKeyboardId() {
        return Settings.Secure.getString(activity.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
    }

    private InputMethodInfo thorPadKeyboard() {
        for (InputMethodInfo info : imm().getInputMethodList()) {
            if (info.getPackageName().equals(activity.getPackageName())) return info;
        }
        return null;
    }

    /** With Shizuku: switches keyboards directly. Without: Android's keyboard list, then its picker. */
    private void useThorPadKeyboard() {
        final InputMethodInfo mine = thorPadKeyboard();
        if (mine == null) return;
        if (ShizukuClient.hasPermission()) {
            final String id = mine.getId();
            new Thread(new Runnable() {
                @Override public void run() {
                    ShizukuClient.runShell("ime enable " + id + " && ime set " + id);
                    if (!id.equals(currentKeyboardId())) {
                        // Some firmware ignores "ime set": write Android's setting directly.
                        ShizukuClient.runShell("settings put secure default_input_method " + id);
                    }
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (!id.equals(currentKeyboardId())) imm().showInputMethodPicker();
                            host.refreshSetup();
                        }
                    });
                }
            }, "ThorPad set keyboard").start();
            return;
        }
        boolean enabled = false;
        for (InputMethodInfo e : imm().getEnabledInputMethodList()) {
            if (e.getId().equals(mine.getId())) enabled = true;
        }
        if (enabled) imm().showInputMethodPicker();
        else start(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
    }
}
