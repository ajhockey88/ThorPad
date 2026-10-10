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
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** Sliders and switches for pointer speed, scrolling and screen layout. */
public class SettingsActivity extends Activity {
    private Prefs prefs;
    private LinearLayout list;
    private float d;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        d = getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * d);
        list.setPadding(pad, pad, pad, pad);
        scroll.addView(list);

        TextView title = text("ThorPad settings", 20);
        list.addView(title);

        slider("Pointer speed", Prefs.KEY_SENSITIVITY, prefs.sensitivity(), 0.5f, 5f, false);
        slider("Pointer acceleration", Prefs.KEY_ACCELERATION, prefs.acceleration(), 0f, 2f, false);
        slider("Scroll speed", Prefs.KEY_SCROLL_SPEED, prefs.scrollSpeed(), 0.5f, 5f, false);
        slider("Pointer size", Prefs.KEY_POINTER_SIZE, prefs.pointerSize(), 0.6f, 2.5f, true);
        toggle("Classic scroll direction (fingers down scrolls down, like a mouse wheel)",
                Prefs.KEY_INVERT_SCROLL, prefs.invertScroll(), false);
        toggle("Swap screens (pointer on bottom, trackpad on top). Reopen ThorPad after changing.",
                Prefs.KEY_SWAP_SCREENS, prefs.swapScreens(), true);

        list.addView(text("\nKeyboard", 17));
        toggle("Autocorrect (fixes a misspelled word when you finish it; tap the suggestion "
                + "to keep what you typed, or press backspace right after to undo)",
                Prefs.KEY_AUTOCORRECT, prefs.autocorrect(), false);
        toggle("Type when the finger lifts (lets you slide to the right key; off = types the "
                + "instant you touch, most responsive)",
                Prefs.KEY_TYPE_ON_RELEASE, prefs.typeOnRelease(), false);
        keyboardChooser();

        hapticsSection();

        TextView help = text("\nGestures\n"
                + "• One finger: move the pointer\n"
                + "• Tap: click\n"
                + "• Tap, then quickly touch again, pause a moment, and slide: drag\n"
                + "• Press and hold: long press\n"
                + "• Two fingers up or down: scroll\n"
                + "• Two-finger tap: back", 14);
        help.setTextColor(Color.argb(170, 255, 255, 255));
        list.addView(help);

        updateSection();

        FrameLayout root = new FrameLayout(this);
        root.addView(scroll);
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshKeyboardLabel();
    }

    // ---- Which keyboard opens for top-screen text boxes ------------------------------------

    private TextView keyboardLabel;

    private void keyboardChooser() {
        keyboardLabel = text("", 15);
        list.addView(keyboardLabel);
        TextView hint = text("Pick ThorPad keyboard and the keyboard on the bottom screen opens by "
                + "itself whenever a text box is selected on the top screen, with no other keyboard "
                + "popping up.", 13);
        hint.setTextColor(Color.argb(170, 255, 255, 255));
        list.addView(hint);
        Button choose = new Button(this);
        choose.setText("Choose keyboard");
        choose.setAllCaps(false);
        choose.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showKeyboardList(); }
        });
        list.addView(choose, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private InputMethodManager imm() {
        return (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    }

    private String currentKeyboardId() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
    }

    private void refreshKeyboardLabel() {
        if (keyboardLabel == null) return;
        String name = "unknown";
        String current = currentKeyboardId();
        for (InputMethodInfo info : imm().getInputMethodList()) {
            if (info.getId().equals(current)) name = info.loadLabel(getPackageManager()).toString();
        }
        keyboardLabel.setText("Keyboard for text boxes on the top screen: " + name);
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
                .setTitle("Keyboard for text boxes")
                .setSingleChoiceItems(names, checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        useKeyboard(all.get(which));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void useKeyboard(final InputMethodInfo info) {
        if (ShizukuClient.hasPermission()) {
            // Shizuku can switch keyboards directly.
            final String id = info.getId();
            new Thread(new Runnable() {
                @Override public void run() {
                    ShizukuClient.runShell("ime enable " + id + " && ime set " + id);
                    runOnUiThread(new Runnable() {
                        @Override public void run() { refreshKeyboardLabel(); }
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
            Toast.makeText(this, "Turn on " + info.loadLabel(getPackageManager())
                    + " here, then come back and choose it again.", Toast.LENGTH_LONG).show();
            Intent i = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
    }

    // ---- Updates -------------------------------------------------------------------------------

    private void updateSection() {
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        list.addView(text("\nUpdates", 17));
        final TextView status = text("ThorPad " + version, 14);
        status.setTextColor(Color.argb(190, 255, 255, 255));
        list.addView(status);
        final Button check = new Button(this);
        check.setText("Check for updates");
        check.setAllCaps(false);
        check.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                check.setEnabled(false);
                new Updater(SettingsActivity.this, new Updater.Listener() {
                    @Override public void onUpdateStatus(String s, boolean done) {
                        status.setText(s);
                        if (done) check.setEnabled(true);
                    }
                }).checkAndInstall();
            }
        });
        list.addView(check, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // Room so the preview pill never covers the button.
        list.addView(new View(this), new LinearLayout.LayoutParams(1, (int) (50 * d)));
    }

    // ---- Haptics -------------------------------------------------------------------------------

    private TextView previewPill;

    private final Runnable hidePill = new Runnable() {
        @Override public void run() { previewPill.setVisibility(View.GONE); }
    };

    private void hapticsSection() {
        list.addView(text("\nHaptics", 17));
        TextView hint = text("Pick a feel and strength for each action. Every change plays a preview.", 13);
        hint.setTextColor(Color.argb(170, 255, 255, 255));
        list.addView(hint);
        for (int a = 0; a < Haptics.ACTION_NAMES.length; a++) hapticRow(a);
    }

    private void hapticRow(final int action) {
        final SharedPreferences sp = prefs.raw();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight((int) (46 * d));

        TextView name = new TextView(this);
        name.setText(Haptics.ACTION_NAMES[action]);
        name.setTextSize(15);
        name.setTextColor(Color.WHITE);
        row.addView(name, new LinearLayout.LayoutParams((int) (150 * d), ViewGroup.LayoutParams.WRAP_CONTENT));

        final Spinner feel = new Spinner(this, Spinner.MODE_DROPDOWN);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, Haptics.FEEL_NAMES) {
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

        final SeekBar strength = new SeekBar(this);
        strength.setMax(100);
        strength.setProgress(Haptics.strength(sp, action));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, (int) (40 * d), 1f);
        slp.leftMargin = (int) (12 * d);
        row.addView(strength, slp);

        final TextView pct = new TextView(this);
        pct.setTextSize(14);
        pct.setTextColor(Color.rgb(0xaa, 0xb3, 0xc0));
        pct.setGravity(Gravity.END);
        pct.setText(strength.getProgress() + "%");
        row.addView(pct, new LinearLayout.LayoutParams((int) (48 * d), ViewGroup.LayoutParams.WRAP_CONTENT));

        feel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == Haptics.feel(sp, action)) return; // not a change
                sp.edit().putInt(Haptics.feelKey(action), position).apply();
                preview(position, strength.getProgress());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        strength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                pct.setText(p + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                sp.edit().putInt(Haptics.strengthKey(action), sb.getProgress()).apply();
                preview(feel.getSelectedItemPosition(), sb.getProgress());
            }
        });
        list.addView(row);
    }

    private void preview(int feel, int strength) {
        Haptics.play(this, feel, strength);
        previewPill.setText(feel == Haptics.OFF || strength == 0
                ? "Off" : "▶ Preview: " + Haptics.FEEL_NAMES[feel] + ", " + strength + "%");
        previewPill.setVisibility(View.VISIBLE);
        previewPill.removeCallbacks(hidePill);
        previewPill.postDelayed(hidePill, 1500);
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.WHITE);
        t.setPadding(0, (int) (10 * d), 0, (int) (4 * d));
        return t;
    }

    private void slider(final String label, final String key, float value,
                        final float min, final float max, final boolean reloadPointer) {
        final TextView t = text(label + ": " + fmt(value), 15);
        list.addView(t);
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(Math.round((value - min) / (max - min) * 100));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                float v = min + (max - min) * p / 100f;
                t.setText(label + ": " + fmt(v));
                prefs.raw().edit().putFloat(key, v).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                if (reloadPointer) reload();
            }
        });
        list.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (40 * d)));
    }

    private void toggle(String label, final String key, boolean value, final boolean reloadPointer) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(Color.WHITE);
        sw.setTextSize(15);
        sw.setChecked(value);
        sw.setPadding(0, (int) (12 * d), 0, (int) (12 * d));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                SharedPreferences.Editor e = prefs.raw().edit();
                e.putBoolean(key, checked);
                e.apply();
                if (reloadPointer) reload();
            }
        });
        list.addView(sw);
    }

    private static void reload() {
        ThorPadService s = ThorPadService.get();
        if (s != null) s.reloadPointer();
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }
}
