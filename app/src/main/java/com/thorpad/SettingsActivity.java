package com.thorpad;

import android.app.Activity;
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
        toggle("Tap to click", Prefs.KEY_TAP_TO_CLICK, prefs.tapToClick(), false);
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

        TextView help = text("\nGestures\n"
                + "• One finger: move the pointer\n"
                + "• Tap: click\n"
                + "• Tap, then touch again and slide: drag\n"
                + "• Press and hold: long press\n"
                + "• Two fingers up or down: scroll\n"
                + "• Two-finger tap: back", 14);
        help.setTextColor(Color.argb(170, 255, 255, 255));
        list.addView(help);

        setContentView(scroll);
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
