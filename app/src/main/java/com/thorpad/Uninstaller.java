package com.thorpad;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * "Uninstall everything": undoes what ThorPad changed on the system (Android's keyboard, the
 * accessibility service, the virtual keyboard's saved layout, app-op permissions, diagnostic
 * files, its own settings) and then removes ThorPad, and Shizuku too if asked.
 * {@link #confirm} asks first; {@link UninstallActivity} does the work and shows each step.
 */
final class Uninstaller {
    static final int RED = Color.rgb(0xc6, 0x28, 0x28), RED_TEXT = Color.rgb(0xff, 0x8a, 0x80);

    private Uninstaller() {}

    /** The confirm dialog from the mockup: what happens, an opt-in Shizuku checkbox, Cancel / Uninstall. */
    static void confirm(final Activity a) {
        Haptics.play(a, Haptics.BAR);
        float d = a.getResources().getDisplayMetrics().density;
        final Dialog dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(d, 22), px(d, 20), px(d, 22), px(d, 20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(0x11, 0x13, 0x18));
        bg.setStroke(Math.max(1, (int) d), Color.rgb(0x2a, 0x2e, 0x37));
        bg.setCornerRadius(16 * d);
        box.setBackground(bg);

        TextView title = text(a, Lang.t("Uninstall ThorPad?"), 20, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);
        InputMethodInfo back = keyboardToRestore(a);
        String keyboard = back != null && thorPadKeyboardInUse(a)
                ? Lang.f("Your keyboard goes back to %s,", back.loadLabel(a.getPackageManager())) + " "
                : "";
        TextView body = text(a, keyboard + Lang.t("everything ThorPad changed is undone, and then ThorPad "
                + "removes itself. This can't be undone."), 14, Color.rgb(0xa3, 0xa8, 0xb2));
        if (keyboard.isEmpty()) {
            String s = body.getText().toString();
            body.setText(Character.toUpperCase(s.charAt(0)) + s.substring(1));
        }
        body.setLineSpacing(0, 1.2f);
        body.setPadding(0, px(d, 8), 0, px(d, 14));
        box.addView(body);

        final CheckBox alsoShizuku = new CheckBox(a);
        if (installed(a, SetupPanel.SHIZUKU)) {
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(d, 10), px(d, 10), px(d, 14), px(d, 10));
            GradientDrawable rbg = new GradientDrawable();
            rbg.setColor(Color.rgb(0x0a, 0x0b, 0x0e));
            rbg.setCornerRadius(11 * d);
            row.setBackground(rbg);
            alsoShizuku.setChecked(false);
            alsoShizuku.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(0x9a, 0xa0, 0xaa)));
            row.addView(alsoShizuku);
            LinearLayout words = new LinearLayout(a);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setPadding(px(d, 6), 0, 0, 0);
            words.addView(text(a, Lang.t("Also uninstall Shizuku"), 15, Color.WHITE));
            words.addView(text(a, Lang.t("Leave this off if other apps use Shizuku"), 12, Color.rgb(0x8a, 0x90, 0x9b)));
            row.addView(words);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { alsoShizuku.toggle(); }
            });
            box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout buttons = new LinearLayout(a);
        buttons.setPadding(0, px(d, 16), 0, 0);
        TextView cancel = pill(a, Lang.t("Cancel"), Color.rgb(0x22, 0x25, 0x2c), Color.rgb(0xdd, 0xdd, 0xdd), d);
        TextView go = pill(a, Lang.t("Uninstall"), RED, Color.WHITE, d);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.rightMargin = px(d, 6);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l2.leftMargin = px(d, 6);
        buttons.addView(cancel, l1);
        buttons.addView(go, l2);
        box.addView(buttons);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Haptics.play(a, Haptics.BAR);
                dialog.dismiss();
            }
        });
        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Haptics.play(a, Haptics.BAR);
                dialog.dismiss();
                Intent i = new Intent(a, UninstallActivity.class);
                i.putExtra(UninstallActivity.EXTRA_SHIZUKU, alsoShizuku.isChecked());
                a.startActivity(i);
            }
        });

        dialog.setContentView(box);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (a.getResources().getDisplayMetrics().widthPixels * 0.72f);
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.72f);
        }
        dialog.show();
    }

    // --- Keyboards ------------------------------------------------------------------------------

    static boolean thorPadKeyboardInUse(Context c) {
        String id = KeyboardKeeper.thorPadId(c);
        return id != null && id.equals(KeyboardKeeper.current(c));
    }

    /**
     * The keyboard to switch back to: the one in use before ThorPad's if it's still installed,
     * else the first other keyboard that's turned on, else any other (system ones first).
     */
    static InputMethodInfo keyboardToRestore(Context c) {
        InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
        String mine = c.getPackageName();
        String previous = new Prefs(c).previousKeyboard();
        List<InputMethodInfo> all = imm.getInputMethodList();
        if (previous != null) {
            for (InputMethodInfo i : all) if (i.getId().equals(previous) && isKeyboard(i)) return i;
        }
        // An on-screen keyboard that's turned on, the device's own (system) one first; never
        // voice typing, which also counts as a "keyboard" to Android but shows no keys.
        InputMethodInfo best = null;
        for (InputMethodInfo i : imm.getEnabledInputMethodList()) {
            if (i.getPackageName().equals(mine) || !isKeyboard(i)) continue;
            if (isSystem(i)) return i;
            if (best == null) best = i;
        }
        if (best != null) return best;
        for (InputMethodInfo i : all) {
            if (i.getPackageName().equals(mine) || !isKeyboard(i)) continue;
            if (isSystem(i)) return i;
            if (best == null) best = i;
        }
        return best;
    }

    /** A keyboard with keys: has a "keyboard" subtype, or no subtypes and isn't a voice service. */
    static boolean isKeyboard(InputMethodInfo i) {
        int n = i.getSubtypeCount();
        if (n == 0) return !i.getId().toLowerCase(java.util.Locale.US).contains("voice");
        for (int k = 0; k < n; k++) {
            if ("keyboard".equals(i.getSubtypeAt(k).getMode())) return true;
        }
        return false;
    }

    private static boolean isSystem(InputMethodInfo i) {
        return (i.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    /** The accessibility services list without ThorPad's, or "" if nothing else is left. */
    static String accessibilityWithoutThorPad(Context c) {
        String on = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (on == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String s : on.split(":")) {
            if (s.isEmpty() || s.startsWith(c.getPackageName() + "/")) continue;
            if (sb.length() > 0) sb.append(':');
            sb.append(s);
        }
        return sb.toString();
    }

    static boolean accessibilityOn(Context c) {
        String on = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return on != null && on.contains(c.getPackageName() + "/");
    }

    static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // --- Views ------------------------------------------------------------------------------------

    static TextView text(Context c, String s, int sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    static TextView pill(Context c, String s, int bgColor, int color, float d) {
        TextView t = text(c, s, 16, color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(d, 12), px(d, 13), px(d, 12), px(d, 13));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(30 * d);
        t.setBackground(bg);
        return t;
    }

    static int px(float d, float dp) { return (int) (dp * d + 0.5f); }
}
