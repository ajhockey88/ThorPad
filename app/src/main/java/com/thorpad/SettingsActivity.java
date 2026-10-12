package com.thorpad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.Toast;
import java.util.List;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.widget.PopupWindow;
import android.view.MotionEvent;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** ThorPad settings: section tabs on the left, the chosen section's settings on the right. */
public class SettingsActivity extends Activity {
    private Prefs prefs;
    private LinearLayout list;
    private float d;

    // Tabs down the left; the chosen section's settings on the right, as cards.
    private static final String[] TABS = {"🖱️ Trackpad", "⌨️ Keyboard", "📳 Haptics", "👆 Gestures", "🔄 Updates"};
    private static final int BG = Color.BLACK, SIDE = Color.rgb(0x0c, 0x0d, 0x11), CARD = Color.rgb(0x11, 0x13, 0x18),
            ACCENT = Color.rgb(0x78, 0xc8, 0xff), SUB = Color.rgb(0x8a, 0x90, 0x9b);
    /** The tab on show, kept when Settings rebuilds itself after a language change. */
    private static int tab;
    private LinearLayout page;
    private ScrollView scroll;
    private final TextView[] tabViews = new TextView[TABS.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Lang.init(this);
        prefs = new Prefs(this);
        d = getResources().getDisplayMetrics().density;
        // Settings has no text boxes: never let a keyboard window cover it.
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        getWindow().getDecorView().setBackgroundColor(BG);

        LinearLayout split = new LinearLayout(this);
        split.setBackgroundColor(BG);

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setBackgroundColor(SIDE);
        side.setPadding((int) (8 * d), (int) (14 * d), (int) (8 * d), (int) (12 * d));
        TextView title = new TextView(this);
        title.setText(Lang.t("Settings"));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        title.setPadding((int) (8 * d), 0, 0, (int) (14 * d));
        diagnosticsOnLongPress(title);
        side.addView(title);
        for (int i = 0; i < TABS.length; i++) {
            final int which = i;
            TextView t = new TextView(this);
            String label = TABS[i];
            int space = label.indexOf(' ');
            t.setText(label.substring(0, space + 1) + Lang.t(label.substring(space + 1)));
            t.setTextSize(16);
            t.setSingleLine(true);
            t.setPadding((int) (12 * d), (int) (11 * d), (int) (8 * d), (int) (11 * d));
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (tab == which) return;
                    Haptics.play(SettingsActivity.this, Haptics.BAR);
                    showTab(which);
                }
            });
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.bottomMargin = (int) (4 * d);
            side.addView(t, tlp);
            tabViews[i] = t;
        }
        side.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        TextView version = new TextView(this);
        version.setText("ThorPad " + versionName());
        version.setTextSize(12);
        version.setTextColor(Color.rgb(0x5d, 0x63, 0x6e));
        version.setPadding((int) (8 * d), 0, 0, 0);
        side.addView(version);
        int sideW = (int) (getResources().getDisplayMetrics().widthPixels * 0.27f);
        split.addView(side, new LinearLayout.LayoutParams(sideW, ViewGroup.LayoutParams.MATCH_PARENT));
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(0x1a, 0x1d, 0x24));
        split.addView(line, new LinearLayout.LayoutParams(Math.max(1, (int) (0.8f * d)), ViewGroup.LayoutParams.MATCH_PARENT));

        scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding((int) (18 * d), (int) (16 * d), (int) (18 * d), (int) (16 * d));
        scroll.addView(page);
        split.addView(scroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        FrameLayout root = new FrameLayout(this);
        root.addView(split);
        previewPill = new TextView(this);
        previewPill.setTextSize(15);
        previewPill.setTextColor(Color.rgb(0xbf, 0xe6, 0xff));
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(Color.rgb(0x1d, 0x2a, 0x36));
        pillBg.setCornerRadius(30 * d);
        previewPill.setBackground(pillBg);
        previewPill.setPadding((int) (14 * d), (int) (7 * d), (int) (14 * d), (int) (7 * d));
        previewPill.setVisibility(View.GONE);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        plp.bottomMargin = (int) (14 * d);
        root.addView(previewPill, plp);
        setContentView(root);
        showTab(tab);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private void showTab(int which) {
        tab = which;
        for (int i = 0; i < tabViews.length; i++) {
            boolean sel = i == which;
            TextView t = tabViews[i];
            t.setTextColor(sel ? Color.WHITE : Color.rgb(0xaa, 0xb0, 0xbb));
            if (sel) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.rgb(0x16, 0x28, 0x3a));
                bg.setCornerRadius(10 * d);
                t.setBackground(bg);
            } else {
                t.setBackground(null);
            }
        }
        page.removeAllViews();
        list = page;
        keyboardLabel = null;
        switch (which) {
            case 0:
                header("Trackpad", "How the pointer moves and scrolls on the top screen");
                slider("Pointer speed", Prefs.KEY_SENSITIVITY, prefs.sensitivity(), 0.5f, 5f, false);
                slider("Pointer acceleration", Prefs.KEY_ACCELERATION, prefs.acceleration(), 0f, 2f, false);
                slider("Scroll speed", Prefs.KEY_SCROLL_SPEED, prefs.scrollSpeed(), 0.5f, 5f, false);
                slider("Pointer size", Prefs.KEY_POINTER_SIZE, prefs.pointerSize(), 0.6f, 2.5f, true);
                toggle("Classic scroll direction", "Fingers down scrolls down, like a mouse wheel",
                        Prefs.KEY_INVERT_SCROLL, prefs.invertScroll(), false);
                toggle("Swap screens", "Pointer on the bottom, trackpad on top. Reopen ThorPad after changing.",
                        Prefs.KEY_SWAP_SCREENS, prefs.swapScreens(), true);
                toggle("Dim when idle", "After 30 seconds without a touch, to save battery; the first touch only wakes it",
                        Prefs.KEY_DIM_WHEN_IDLE, prefs.dimWhenIdle(), false);
                toggle("Gesture hints", "Show the gesture cheat sheet on the trackpad",
                        Prefs.KEY_SHOW_HINTS, prefs.showHints(), false);
                break;
            case 1:
                header("Keyboard", "Typing, spelling and which keyboard Android uses");
                languagePicker();
                toggle("Autocorrect", "Fixes misspelled and run-together words when you finish them. Tap the "
                        + "suggestion to keep what you typed, or press backspace right after to undo.",
                        Prefs.KEY_AUTOCORRECT, prefs.autocorrect(), false);
                toggle("Double-space period", "Two quick spaces type \". \"",
                        Prefs.KEY_DOUBLE_SPACE, prefs.doubleSpacePeriod(), false);
                toggle("Auto-capitals", "A capital letter at the start of each sentence, never in email or password boxes",
                        Prefs.KEY_AUTO_CAPS, prefs.autoCaps(), false);
                toggle("Type when the finger lifts", "Lets you slide to the right key. Off types the instant you "
                        + "touch, which is quickest", Prefs.KEY_TYPE_ON_RELEASE, prefs.typeOnRelease(), false);
                keyboardChooser();
                refreshKeyboardLabel();
                break;
            case 2:
                header("Haptics", "Pick a feel and strength for each action.");
                hapticsSection();
                break;
            case 3: {
                header("Gestures", "What each touch does on the trackpad");
                LinearLayout c = card();
                // The help text's first lines are its own heading, which the tab already shows.
                String help = Lang.t("\nGestures\n"
                        + "• One finger: move the pointer\n"
                        + "• Tap: click\n"
                        + "• Tap, then quickly touch again, pause a moment, and slide: drag\n"
                        + "• Press and hold: long press\n"
                        + "• Two fingers up or down: scroll\n"
                        + "• Two fingers swiped right: back; swiped left: forward\n"
                        + "• Three fingers swiped up: recent apps; swiped down: close the top-screen app\n"
                        + "• Two-finger tap: back\n"
                        + "• Swipe down from the top edge: show the menu buttons");
                help = help.substring(help.indexOf('\n', 1) + 1);
                TextView t = plain(help, 15, Color.rgb(0xd8, 0xdc, 0xe3));
                t.setLineSpacing(6 * d, 1f);
                c.addView(t);
                break;
            }
            default:
                header("Updates", "Get the newest ThorPad from GitHub");
                updateSection();
                break;
        }
        scroll.scrollTo(0, 0);
    }

    private void header(String title, String lead) {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView h = plain(Lang.t(title), 22, Color.WHITE);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        diagnosticsOnLongPress(h);
        top.addView(h, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        // Back, top right: closes Settings.
        TextView back = chip("←  " + Lang.t("Back"));
        back.setTextSize(15);
        back.setTextColor(Color.rgb(0xd8, 0xdc, 0xe3));
        GradientDrawable bbg = new GradientDrawable();
        bbg.setColor(Color.rgb(0x1c, 0x1e, 0x24));
        bbg.setCornerRadius(18 * d);
        back.setBackground(bbg);
        back.setPadding((int) (14 * d), (int) (7 * d), (int) (16 * d), (int) (7 * d));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Haptics.play(SettingsActivity.this, Haptics.BAR);
                finish();
            }
        });
        top.addView(back);
        page.addView(top);
        TextView l = plain(Lang.t(lead), 13, Color.rgb(0x7a, 0x80, 0x8b));
        l.setPadding(0, (int) (2 * d), 0, (int) (12 * d));
        page.addView(l);
    }

    /** A rounded card on the page; returned so a section can fill it. */
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding((int) (14 * d), (int) (12 * d), (int) (14 * d), (int) (12 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(12 * d);
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (7 * d);
        page.addView(c, lp);
        return c;
    }

    private TextView plain(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    /** The value chip at the end of a slider row. */
    private TextView chip(String s) {
        TextView t = plain(s, 14, Color.rgb(0x9f, 0xd2, 0xf5));
        t.setGravity(Gravity.CENTER);
        t.setMinWidth((int) (42 * d));
        t.setPadding((int) (8 * d), (int) (4 * d), (int) (8 * d), (int) (4 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(0x16, 0x22, 0x2d));
        bg.setCornerRadius(9 * d);
        t.setBackground(bg);
        return t;
    }

    private void diagnosticsOnLongPress(View v) {
        // Long-press a heading: saves screenshots and the window list for bug reports.
        v.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View view) {
                saveDiagnostics();
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshKeyboardLabel();
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
    }

    // ---- Keyboard language ---------------------------------------------------------------------

    private void languagePicker() {
        LinearLayout c = card();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(plain(Lang.t("Language"), 16, Color.WHITE));
        words.addView(plain(Lang.t("Changes the letters on the keyboard, the spelling dictionary, and "
                + "ThorPad's buttons and settings.") + " " + Lang.t("Hold a letter for its accents."), 12, SUB));
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView pick = chip("🌐 " + Lang.name() + " ▾");
        pick.setTextSize(15);
        pick.setPadding((int) (12 * d), (int) (7 * d), (int) (12 * d), (int) (7 * d));
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle(Lang.t("Keyboard language"))
                        .setSingleChoiceItems(Lang.NAMES, Lang.indexOf(Lang.code()),
                                new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        if (Lang.CODES[which].equals(Lang.code())) return;
                                        prefs.setLanguage(Lang.CODES[which]);
                                        Autocorrect.release(); // the next keyboard loads the new dictionary
                                        recreate();
                                    }
                                })
                        .setNegativeButton(Lang.t("Cancel"), null)
                        .show();
            }
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.leftMargin = (int) (12 * d);
        row.addView(pick, plp);
        c.addView(row);
    }

    // ---- Which keyboard opens for top-screen text boxes ------------------------------------

    private TextView keyboardLabel;
    private Button useThorPad;

    private void keyboardChooser() {
        list = card();
        keyboardLabel = plain("", 16, Color.WHITE);
        list.addView(keyboardLabel);
        TextView hint = text("This is Android's own keyboard setting for the whole Thor. With ThorPad "
                + "keyboard in use, the keyboard on the bottom screen opens by itself whenever a text "
                + "box is selected on the top screen, with no other keyboard popping up.", 12);
        hint.setTextColor(SUB);
        hint.setPadding(0, (int) (2 * d), 0, (int) (4 * d));
        list.addView(hint);
        LinearLayout row = new LinearLayout(this);
        useThorPad = new Button(this);
        useThorPad.setAllCaps(false);
        useThorPad.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                InputMethodInfo mine = thorPadKeyboard();
                if (mine != null) useKeyboard(mine);
            }
        });
        row.addView(useThorPad);
        Button choose = new Button(this);
        choose.setText(Lang.t("Other keyboard"));
        choose.setAllCaps(false);
        choose.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showKeyboardList(); }
        });
        row.addView(choose);
        list.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        list = page;
    }

    private InputMethodManager imm() {
        return (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    }

    private String currentKeyboardId() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
    }

    private InputMethodInfo thorPadKeyboard() {
        for (InputMethodInfo info : imm().getInputMethodList()) {
            if (info.getPackageName().equals(getPackageName())) return info;
        }
        return null;
    }

    private void refreshKeyboardLabel() {
        if (keyboardLabel == null) return;
        String name = Lang.t("unknown");
        String current = currentKeyboardId();
        for (InputMethodInfo info : imm().getInputMethodList()) {
            if (info.getId().equals(current)) name = info.loadLabel(getPackageManager()).toString();
        }
        InputMethodInfo mine = thorPadKeyboard();
        boolean inUse = mine != null && mine.getId().equals(current);
        keyboardLabel.setText(Lang.f("Android keyboard: %s", name) + (inUse ? " ✅" : ""));
        useThorPad.setText(Lang.t(inUse ? "ThorPad keyboard in use" : "Use ThorPad keyboard"));
        useThorPad.setEnabled(!inUse && mine != null);
    }

    private void showKeyboardList() {
        final List<InputMethodInfo> all = imm().getInputMethodList();
        String current = currentKeyboardId();
        String[] names = new String[all.size()];
        int checked = -1;
        for (int i = 0; i < all.size(); i++) {
            names[i] = all.get(i).loadLabel(getPackageManager()).toString();
            if (all.get(i).getId().equals(current)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(Lang.t("Keyboard for text boxes"))
                .setSingleChoiceItems(names, checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        useKeyboard(all.get(which));
                    }
                })
                .setNegativeButton(Lang.t("Cancel"), null)
                .show();
    }

    private void useKeyboard(final InputMethodInfo info) {
        if (ShizukuClient.hasPermission()) {
            // Shizuku can switch keyboards directly.
            final String id = info.getId();
            new Thread(new Runnable() {
                @Override public void run() {
                    ShizukuClient.runShell("ime enable " + id + " && ime set " + id);
                    if (!id.equals(currentKeyboardId())) {
                        // Some firmware ignores "ime set": write Android's setting directly.
                        ShizukuClient.runShell("settings put secure default_input_method " + id);
                    }
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            refreshKeyboardLabel();
                            if (!id.equals(currentKeyboardId())) {
                                // Still not switched: fall back to Android's own picker.
                                imm().showInputMethodPicker();
                            } else {
                                Toast.makeText(SettingsActivity.this, Lang.f("%s is now Android's keyboard",
                                        info.loadLabel(getPackageManager())), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }, "ThorPad set keyboard").start();
            return;
        }
        boolean enabled = false;
        for (InputMethodInfo e : imm().getEnabledInputMethodList()) {
            if (e.getId().equals(info.getId())) enabled = true;
        }
        if (enabled) {
            // Android only lets the user switch keyboards, through its own picker.
            imm().showInputMethodPicker();
        } else {
            Toast.makeText(this, Lang.f("Turn on %s here, then come back and choose it again.",
                    info.loadLabel(getPackageManager())), Toast.LENGTH_LONG).show();
            Intent i = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
    }

    private void saveDiagnostics() {
        if (!ShizukuClient.hasPermission()) {
            Toast.makeText(this, Lang.t("Needs Shizuku"), Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, Lang.t("Saving screenshots…"), Toast.LENGTH_SHORT).show();
        String log = "";
        try {
            log = "cp '" + KeyboardLog.save(this).getAbsolutePath() + "' /sdcard/Download/; ";
        } catch (Exception ignored) {
        }
        final String copyLog = log;
        new Thread(new Runnable() {
            @Override public void run() {
                final boolean ok = ShizukuClient.runShell(
                        "d=/sdcard/Download; " + copyLog
                        + "dumpsys window windows > $d/thorpad-windows.txt; "
                        // What ThorPad was stuck on, if it ever showed "not responding".
                        + "dumpsys dropbox --print data_app_anr > $d/thorpad-anr.txt 2>&1; "
                        + "i=0; for id in $(dumpsys SurfaceFlinger --display-id | sed -n 's/^Display \\([0-9]*\\).*/\\1/p'); do "
                        + "i=$((i+1)); screencap -d $id -p $d/thorpad-screen-$i.png; done; "
                        + "screencap -p $d/thorpad-screen-main.png; true");
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        Toast.makeText(SettingsActivity.this, ok
                                ? Lang.t("Saved thorpad-screen, thorpad-windows and thorpad-anr files to Download")
                                : Lang.t("Couldn't save the screenshots"), Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "ThorPad diagnostics").start();
    }

    // ---- Updates -------------------------------------------------------------------------------

    private void updateSection() {
        list = card();
        final TextView status = plain("ThorPad " + versionName(), 16, Color.WHITE);
        status.setPadding(0, 0, 0, (int) (6 * d));
        list.addView(status);
        final Button check = new Button(this);
        check.setText(Lang.t("Check for updates"));
        check.setAllCaps(false);
        check.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                check.setEnabled(false);
                final Updater[] updater = new Updater[1];
                updater[0] = new Updater(SettingsActivity.this, new Updater.Listener() {
                    @Override public void onUpdateStatus(String s, boolean done) {
                        status.setText(s);
                        if (done) check.setEnabled(true);
                    }

                    @Override public void onUpdateAvailable(Updater.Release r) {
                        UpdateDialog.show(SettingsActivity.this, r, updater[0], null);
                    }
                });
                updater[0].check();
            }
        });
        list.addView(check, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Installs the latest release again even if it's this version, e.g. after its APK
        // was replaced on GitHub without a new version number.
        final Button reinstall = new Button(this);
        reinstall.setText(Lang.t("Reinstall"));
        reinstall.setAllCaps(false);
        reinstall.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                reinstall.setEnabled(false);
                final Updater[] updater = new Updater[1];
                updater[0] = new Updater(SettingsActivity.this, new Updater.Listener() {
                    @Override public void onUpdateStatus(String s, boolean done) {
                        status.setText(s);
                        if (done) reinstall.setEnabled(true);
                    }

                    @Override public void onUpdateAvailable(Updater.Release r) {
                        UpdateDialog.show(SettingsActivity.this, r, updater[0], null);
                    }
                });
                updater[0].check(true);
            }
        });
        list.addView(reinstall, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        list = page;
    }

    // ---- Haptics -------------------------------------------------------------------------------

    private TextView previewPill;

    private final Runnable hidePill = new Runnable() {
        @Override public void run() { previewPill.setVisibility(View.GONE); }
    };

    private void hapticsSection() {
        if (!Haptics.hasStrengthControl(this)) {
            TextView motor = text("This device's vibration motor can't buzz softer or harder, so strength "
                    + "changes how long each buzz lasts instead.", 13);
            motor.setTextColor(SUB);
            list.addView(motor);
        }
        list = card();
        for (int a = 0; a < Haptics.ACTION_NAMES.length; a++) hapticRow(a);
        list = page;
        // Room so the preview pill never covers the last row.
        page.addView(new View(this), new LinearLayout.LayoutParams(1, (int) (50 * d)));
    }

    private void hapticRow(final int action) {
        final SharedPreferences sp = prefs.raw();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight((int) (46 * d));

        TextView name = new TextView(this);
        name.setText(Lang.t(Haptics.ACTION_NAMES[action]));
        name.setTextSize(15);
        name.setTextColor(Color.WHITE);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final Spinner feel = new Spinner(this, Spinner.MODE_DROPDOWN);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, Haptics.feelNames()) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getView(position, convertView, parent);
                t.setTextColor(Color.rgb(0xcf, 0xe9, 0xff));
                t.setTextSize(15);
                return t;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        feel.setAdapter(adapter);
        feel.setSelection(Haptics.feel(sp, action), false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(0x1c, 0x1e, 0x24));
        bg.setCornerRadius(8 * d);
        feel.setBackground(bg);
        row.addView(feel, new LinearLayout.LayoutParams((int) (110 * d), (int) (36 * d)));

        // Strength: a chip showing the %, which opens a vertical slider under it.
        final int[] strength = {Haptics.strength(sp, action)};
        final TextView pct = chip(strength[0] + "%  ▾");
        pct.setTextSize(15);
        pct.setPadding((int) (10 * d), (int) (7 * d), (int) (10 * d), (int) (7 * d));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams((int) (76 * d), ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.leftMargin = (int) (10 * d);
        row.addView(pct, plp);
        pct.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                strengthPopup(pct, strength[0], new StrengthListener() {
                    @Override public void onStrength(int value, boolean done) {
                        strength[0] = value;
                        pct.setText(value + "%  ▾");
                        if (done) {
                            sp.edit().putInt(Haptics.strengthKey(action), value).apply();
                            preview(feel.getSelectedItemPosition(), value);
                        }
                    }
                });
            }
        });

        feel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == Haptics.feel(sp, action)) return; // not a change
                sp.edit().putInt(Haptics.feelKey(action), position).apply();
                preview(position, strength[0]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        list.addView(row);
    }

    private interface StrengthListener {
        /** done = the finger lifted: save and play a preview. */
        void onStrength(int value, boolean done);
    }

    /** A small panel under the chip: the % on top and a tall vertical slider (0 at the bottom). */
    private void strengthPopup(View anchor, int value, final StrengthListener listener) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(0, (int) (10 * d), 0, (int) (12 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(0x16, 0x19, 0x1f));
        bg.setStroke(Math.max(1, (int) (0.8f * d)), Color.rgb(0x2a, 0x2f, 0x3a));
        bg.setCornerRadius(14 * d);
        box.setBackground(bg);
        final TextView shown = plain(value + "%", 17, Color.rgb(0xbf, 0xe6, 0xff));
        shown.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(shown);
        final int[] v = {value};
        View slider = new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                float cx = getWidth() / 2f, r = 12 * d, top = r + 2 * d, bottom = getHeight() - r - 2 * d;
                float y = bottom - (bottom - top) * v[0] / 100f;
                paint.setColor(Color.rgb(0x2e, 0x32, 0x3b));
                c.drawRoundRect(cx - 3.5f * d, top, cx + 3.5f * d, bottom, 3.5f * d, 3.5f * d, paint);
                paint.setColor(ACCENT);
                c.drawRoundRect(cx - 3.5f * d, y, cx + 3.5f * d, bottom, 3.5f * d, 3.5f * d, paint);
                paint.setColor(Color.argb(38, 120, 200, 255));
                c.drawCircle(cx, y, r + 6 * d, paint);
                paint.setColor(Color.rgb(0xbf, 0xe6, 0xff));
                c.drawCircle(cx, y, r, paint);
            }
            @Override public boolean onTouchEvent(MotionEvent ev) {
                float r = 12 * d, top = r + 2 * d, bottom = getHeight() - r - 2 * d;
                int value = Math.round((bottom - ev.getY()) / (bottom - top) * 100);
                value = Math.max(0, Math.min(100, value));
                int action = ev.getActionMasked();
                if (value != v[0]) {
                    v[0] = value;
                    shown.setText(value + "%");
                    listener.onStrength(value, false);
                    invalidate();
                }
                if (action == MotionEvent.ACTION_UP) listener.onStrength(v[0], true);
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
        };
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams((int) (60 * d), (int) (170 * d));
        slp.topMargin = (int) (6 * d);
        slp.bottomMargin = (int) (4 * d);
        box.addView(slider, slp);
        box.addView(plain("▶", 12, SUB));
        PopupWindow popup = new PopupWindow(box, Math.max(anchor.getWidth(), (int) (76 * d)),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setElevation(16 * d);
        popup.showAsDropDown(anchor, 0, (int) (4 * d));
    }

    private void preview(int feel, int strength) {
        Haptics.play(this, feel, strength);
        previewPill.setText(feel == Haptics.OFF || strength == 0
                ? Lang.t("Off") : Lang.f("▶ Preview: %s", Lang.t(Haptics.FEEL_NAMES[feel]) + ", " + strength + "%"));
        previewPill.setVisibility(View.VISIBLE);
        previewPill.removeCallbacks(hidePill);
        previewPill.postDelayed(hidePill, 1500);
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(Lang.t(s));
        t.setTextSize(sp);
        t.setTextColor(Color.WHITE);
        t.setPadding(0, (int) (6 * d), 0, (int) (4 * d));
        return t;
    }

    private void slider(final String label, final String key, float value,
                        final float min, final float max, final boolean reloadPointer) {
        LinearLayout c = card();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(plain(Lang.t(label), 16, Color.WHITE),
                new LinearLayout.LayoutParams((int) (150 * d), ViewGroup.LayoutParams.WRAP_CONTENT));
        final TextView value_ = chip(fmt(value));
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(Math.round((value - min) / (max - min) * 100));
        tint(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                float v = min + (max - min) * p / 100f;
                value_.setText(fmt(v));
                prefs.raw().edit().putFloat(key, v).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                if (reloadPointer) reload();
            }
        });
        row.addView(bar, new LinearLayout.LayoutParams(0, (int) (36 * d), 1f));
        row.addView(value_);
        c.addView(row);
    }

    private void tint(SeekBar bar) {
        bar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(0x2e, 0x32, 0x3b)));
        bar.setThumbTintList(ColorStateList.valueOf(Color.rgb(0xbf, 0xe6, 0xff)));
    }

    /** A card with a title, a gray line under it, and a switch on the right; the whole card toggles. */
    private void toggle(String title, String sub, final String key, boolean value, final boolean reloadPointer) {
        LinearLayout c = card();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(plain(Lang.t(title), 16, Color.WHITE));
        TextView s = plain(Lang.t(sub), 12, SUB);
        s.setPadding(0, (int) (2 * d), 0, 0);
        words.addView(s);
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch sw = new Switch(this);
        sw.setChecked(value);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[] {Color.rgb(0xbf, 0xe6, 0xff), Color.rgb(0x9a, 0xa0, 0xab)}));
        sw.setTrackTintList(new ColorStateList(states, new int[] {Color.rgb(0x2b, 0x6e, 0x99), Color.rgb(0x3a, 0x3e, 0x48)}));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                SharedPreferences.Editor e = prefs.raw().edit();
                e.putBoolean(key, checked);
                e.apply();
                if (reloadPointer) reload();
            }
        });
        LinearLayout.LayoutParams swp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swp.leftMargin = (int) (12 * d);
        row.addView(sw, swp);
        c.addView(row);
        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sw.toggle(); }
        });
    }

    private static void reload() {
        ThorPadService s = ThorPadService.get();
        if (s != null) s.reloadPointer();
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }
}
