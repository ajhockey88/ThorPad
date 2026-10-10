package com.thorpad;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Fills the bottom screen with the trackpad and a slim row of system buttons.
 *
 * Output goes to a virtual USB mouse when Shizuku is available (real mouse moves, clicks
 * and wheel notches, which games in GameNative need), and otherwise to touch gestures
 * through the accessibility service.
 */
public class TrackpadActivity extends Activity
        implements ShizukuClient.Listener, UinputDevice.StateListener, KeyboardView.Listener,
        ThorPadIme.Listener {
    static final String EXTRA_OPEN_KEYBOARD = "open_keyboard";

    private TrackpadView pad;
    private View setupPanel;
    private TextView mouseStatus;
    private Button shizukuButton;
    private TextView modeLabel;
    private VirtualMouse mouse;
    private VirtualKeyboard keyboardDevice;
    private KeyboardView keyboard;
    private String mouseError;
    private boolean started;
    /** Open the keyboard as soon as the trackpad is up (asked for by the ThorPad keyboard). */
    private boolean keyboardRequested;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Never take input focus: the app on the top screen keeps the keyboard and gamepad,
        // and Back / Home / Recents act on the top screen instead of on the trackpad.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);

        float d = getResources().getDisplayMetrics().density;
        mouse = new VirtualMouse(d, this);
        keyboardDevice = new VirtualKeyboard(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding((int) (6 * d), (int) (6 * d), (int) (6 * d), 0);
        bar.addView(barButton("Keyboard", new View.OnClickListener() {
            @Override public void onClick(View v) { openKeyboard(); }
        }), weight());
        bar.addView(barButton("Home", new View.OnClickListener() {
            @Override public void onClick(View v) { PadOutput o = currentOutput(); if (o != null) o.home(); }
        }), weight());
        bar.addView(barButton("Apps", new View.OnClickListener() {
            @Override public void onClick(View v) { PadOutput o = currentOutput(); if (o != null) o.recents(); }
        }), weight());
        bar.addView(barButton("Settings", new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(TrackpadActivity.this, SettingsActivity.class)); }
        }), weight());
        bar.addView(barButton("Close", new View.OnClickListener() {
            @Override public void onClick(View v) { finishAndRemoveTask(); }
        }), weight());
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (46 * d)));

        FrameLayout padArea = new FrameLayout(this);
        pad = new TrackpadView(this);
        padArea.addView(pad, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        modeLabel = new TextView(this);
        modeLabel.setTextColor(Color.argb(90, 255, 255, 255));
        modeLabel.setTextSize(12);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        mlp.topMargin = (int) (14 * d);
        padArea.addView(modeLabel, mlp);
        padArea.addView(buildSetupPanel(d), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(padArea, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // The keyboard covers the whole screen, bar included, while it's open.
        FrameLayout frame = new FrameLayout(this);
        frame.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        keyboard = new KeyboardView(this);
        keyboard.setListener(this);
        keyboard.setVisibility(View.GONE);
        frame.addView(keyboard, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(frame);
        keyboardRequested = getIntent().getBooleanExtra(EXTRA_OPEN_KEYBOARD, false);
        ShizukuClient.addListener(this);
    }

    @Override
    protected void onDestroy() {
        ShizukuClient.removeListener(this);
        mouse.quit();
        keyboardDevice.quit();
        super.onDestroy();
    }

    private static LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(4, 0, 4, 0);
        return lp;
    }

    private Button barButton(String text, final View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(Color.argb(200, 255, 255, 255));
        b.setPadding(0, 0, 0, 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(28, 28, 32));
        bg.setCornerRadius(10 * getResources().getDisplayMetrics().density);
        b.setBackground(bg);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Haptics.play(TrackpadActivity.this, Haptics.BAR);
                listener.onClick(v);
            }
        });
        return b;
    }

    private TextView panelText(String s, int sp, int alpha) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.argb(alpha, 255, 255, 255));
        return t;
    }

    private Button panelButton(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private View buildSetupPanel(float d) {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.setClickable(true); // swallow touches so the pad underneath stays inert
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * d);
        panel.setPadding(pad, pad, pad, pad);
        scroll.addView(panel);

        panel.addView(panelText("Real mouse mode (needed for GameNative games)", 16, 255));
        mouseStatus = panelText("", 14, 190);
        panel.addView(mouseStatus);
        shizukuButton = panelButton("Allow ThorPad in Shizuku", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (mouseError != null) {
                    mouseError = null;
                    refresh();
                } else if (ShizukuClient.isRunning()) {
                    ShizukuClient.requestPermission();
                } else {
                    Intent i = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                    if (i == null) {
                        i = new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/RikkaApps/Shizuku/releases"));
                    }
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { startActivity(i); } catch (RuntimeException ignored) { }
                }
            }
        });
        panel.addView(shizukuButton);

        TextView or = panelText("\nOr touch mode (no Shizuku; fine for normal Android apps, "
                + "but games in GameNative won't get real mouse or scroll-wheel input)", 15, 255);
        panel.addView(or);
        panel.addView(panelText("Turn on \"ThorPad trackpad\" in Accessibility settings. If Android "
                + "says the setting is restricted: Settings > Apps > ThorPad > ⋮ > "
                + "Allow restricted settings, then try again.", 14, 190));
        LinearLayout row = new LinearLayout(this);
        row.addView(panelButton("Accessibility settings", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        }));
        row.addView(panelButton("ThorPad app info", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        }));
        panel.addView(row);

        setupPanel = scroll;
        return scroll;
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        ThorPadIme.setListener(this);
        refresh();
        if (keyboardRequested) {
            keyboardRequested = false;
            if (keyboard.getVisibility() != View.VISIBLE) openKeyboard();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        refresh();
    }

    @Override
    protected void onStop() {
        super.onStop();
        started = false;
        ThorPadIme.setListener(null);
        // Unplug the virtual mouse and hide the overlay pointer while the trackpad is away.
        mouse.stop();
        onCloseKeyboard();
        ThorPadService s = ThorPadService.get();
        if (s != null) s.hidePointer();
        pad.setOutput(null);
    }

    @Override
    public void onShizukuChanged() {
        mouseError = null;
        if (!ShizukuClient.hasPermission()) mouse.stop();
        refresh();
    }

    @Override
    public void onDeviceStateChanged(UinputDevice device, boolean ready, String error) {
        if (device == mouse) {
            if (!ready && error != null) mouseError = error;
            refresh();
        } else if (device == keyboardDevice && !ready && keyboard.getVisibility() == View.VISIBLE) {
            // The virtual keyboard failed: type through the accessibility service if it's on.
            keyboardDeviceFailed = true;
        }
    }

    // ---- On-screen keyboard -----------------------------------------------------------------

    private void openKeyboard() {
        Prefs prefs = new Prefs(this);
        keyboard.reset();
        keyboard.setTypeOnRelease(prefs.typeOnRelease());
        keyboard.setAutocorrect(prefs.autocorrect() ? Autocorrect.get(this) : null);
        keyboardDeviceFailed = false;
        // With the ThorPad keyboard picked in Android, keys go straight into the text box.
        // Otherwise, in mouse mode, plug in a virtual keyboard so keys arrive like a real USB
        // keyboard (which games need).
        if (ThorPadIme.active() == null && mouse.isReady()) keyboardDevice.start();
        keyboard.setOutput(typing);
        pad.setOutput(null);
        keyboard.setVisibility(View.VISIBLE);
    }

    /** A text box on the top screen asked for a keyboard, and ThorPad is the keyboard. */
    @Override
    public void onKeyboardWanted() {
        if (!started || keyboard.getVisibility() == View.VISIBLE) return;
        openKeyboard();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra(EXTRA_OPEN_KEYBOARD, false)) keyboardRequested = true;
    }

    private boolean keyboardDeviceFailed;

    /** Picks where each key goes, as the selected text box can change while typing. */
    private final KeyOutput typing = new KeyOutput() {
        @Override public void typeChar(char c) {
            KeyOutput o = keyTarget();
            if (o != null) o.typeChar(c);
        }

        @Override public void special(int key) {
            KeyOutput o = keyTarget();
            if (o != null) o.special(key);
        }
    };

    private KeyOutput keyTarget() {
        KeyOutput ime = ThorPadIme.active();
        if (ime != null) return ime;
        if (mouse.isReady() && !keyboardDeviceFailed) {
            if (!keyboardDevice.isStartingOrReady()) keyboardDevice.start(); // keys queue meanwhile
            return keyboardDevice;
        }
        return touchTyping();
    }

    private KeyOutput touchTyping() {
        return isServiceEnabled() ? ThorPadService.get() : null;
    }

    @Override
    public void onCloseKeyboard() {
        if (keyboard.getVisibility() != View.VISIBLE) return;
        keyboard.setVisibility(View.GONE);
        keyboard.setOutput(null);
        keyboardDevice.stop(); // unplug, so the top screen's own touch keyboard works again
        refresh();
    }

    private PadOutput currentOutput() {
        if (mouse.isReady()) return mouse;
        ThorPadService s = ThorPadService.get();
        return s != null && isServiceEnabled() ? s : null;
    }

    private void refresh() {
        if (!started) return;

        boolean shizukuReady = ShizukuClient.hasPermission();
        if (shizukuReady && !mouse.isReady() && mouseError == null) mouse.start();

        ThorPadService service = isServiceEnabled() ? ThorPadService.get() : null;
        PadOutput out;
        if (mouse.isReady()) {
            out = mouse;
            if (service != null) service.hidePointer(); // Android draws the real mouse cursor
            modeLabel.setText("Mouse mode");
        } else if (service != null && !shizukuReady) {
            out = service;
            service.showPointer();
            modeLabel.setText("Touch mode");
        } else if (service != null && mouseError != null) {
            out = service;
            service.showPointer();
            modeLabel.setText("Touch mode (virtual mouse failed)");
        } else {
            out = null; // waiting for the virtual mouse, or nothing set up yet
            modeLabel.setText(shizukuReady && mouseError == null ? "Starting mouse…" : "");
        }
        pad.setOutput(keyboard.getVisibility() == View.VISIBLE ? null : out);

        if (mouseError != null) {
            mouseStatus.setText("The virtual mouse couldn't start: " + mouseError);
            shizukuButton.setText("Try again");
            shizukuButton.setVisibility(View.VISIBLE);
        } else if (!ShizukuClient.isRunning()) {
            mouseStatus.setText("Install Shizuku, start it (Wireless debugging works without a PC), "
                    + "then reopen ThorPad.");
            shizukuButton.setText("Open Shizuku");
            shizukuButton.setVisibility(View.VISIBLE);
        } else if (!shizukuReady) {
            mouseStatus.setText("Shizuku is running. Allow ThorPad to use it.");
            shizukuButton.setText("Allow ThorPad in Shizuku");
            shizukuButton.setVisibility(View.VISIBLE);
        } else {
            mouseStatus.setText("Ready.");
            shizukuButton.setVisibility(View.GONE);
        }

        boolean waitingForMouse = shizukuReady && mouseError == null && !mouse.isReady();
        setupPanel.setVisibility(out != null || waitingForMouse ? View.GONE : View.VISIBLE);
        if (out == null) {
            // Keep checking while the user sets things up in other apps.
            setupPanel.removeCallbacks(recheck);
            setupPanel.postDelayed(recheck, 1000);
        }
    }

    private final Runnable recheck = new Runnable() {
        @Override public void run() { refresh(); }
    };

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName me = new ComponentName(this, ThorPadService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        for (String s : splitter) {
            if (me.equals(ComponentName.unflattenFromString(s))) return true;
        }
        return false;
    }
}
