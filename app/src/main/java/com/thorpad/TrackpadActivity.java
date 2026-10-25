package com.thorpad;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
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
        ThorPadIme.Listener, PcKeysView.Listener, TrackpadView.PcShortcuts {
    static final String EXTRA_OPEN_KEYBOARD = "open_keyboard";

    private TrackpadView pad;
    private SetupPanel setupPanel;
    /** Settings > Keyboard button: a small shortcut to the keyboard in the trackpad's corner. */
    private View keyButton;
    /** Setup was needed this time, so it stays up for its last step (the keyboard) once the pad works. */
    private boolean setupShown;
    private TextView modeLabel;
    private VirtualMouse mouse;
    private VirtualKeyboard keyboardDevice;
    private KeyboardView keyboard;
    private PcKeysView pcKeys;
    private Button modeButton;
    private String mouseError;
    private boolean started;
    /** Open the keyboard as soon as the trackpad is up (asked for by the ThorPad keyboard). */
    private boolean keyboardRequested;
    /**
     * ThorPad came onto the bottom screen only because a text box wanted the keyboard, so when
     * the keyboard closes it steps aside and the bottom screen goes back to what was there.
     */
    private boolean stepAsideAfterKeyboard;
    /** The keyboard was opened by a text box (not the Keyboard button), so it closes with it. */
    private boolean keyboardForTextBox;

    // ---- Tap outside the text box ----------------------------------------------------------
    // Most apps keep a text box focused when you tap elsewhere, so nothing tells ThorPad you
    // left it. While the keyboard is open for a text box (and Shizuku is on), ThorPad watches
    // the touchscreens: a tap that isn't on its own screen and doesn't move the text box's
    // cursor or focus a text box is a tap outside it, and the keyboard closes.

    private static final long OWN_TOUCH_SLACK_MS = 250, TAP_SETTLE_MS = 350;
    private long lastOwnDownAt;
    private final TouchWatcher touchWatcher = new TouchWatcher(new TouchWatcher.Listener() {
        @Override public void onTap(String device, final long downAt) {
            if (Math.abs(downAt - lastOwnDownAt) < OWN_TOUCH_SLACK_MS) return; // on ThorPad's screen
            idleHandler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (Math.abs(downAt - lastOwnDownAt) < OWN_TOUCH_SLACK_MS) return;
                    if (ThorPadIme.lastTextBoxActivity >= downAt) return; // the tap was in a text box
                    KeyboardLog.add("trackpad: tap outside the text box");
                    if (keyboardForTextBox && keyboard.getVisibility() == View.VISIBLE) onCloseKeyboard();
                }
            }, TAP_SETTLE_MS);
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Lang.init(this);
        builtLanguage = Lang.code();
        // Never take input focus: the app on the top screen keeps the keyboard and gamepad,
        // and Back / Home / Recents act on the top screen instead of on the trackpad.
        // No FLAG_KEEP_SCREEN_ON: the Thor sleeps on its usual timer, and touching the trackpad
        // counts as using it.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);

        float d = getResources().getDisplayMetrics().density;
        mouse = new VirtualMouse(d, this);
        keyboardDevice = new VirtualKeyboard(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        // The menu buttons stay hidden; a swipe down from the top edge slides in a floating
        // pill of them.
        LinearLayout bar = new LinearLayout(this);
        menuBar = bar;
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding((int) (4 * d), (int) (4 * d), (int) (4 * d), (int) (4 * d));
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(Color.rgb(0x11, 0x13, 0x18));
        barBg.setStroke(Math.max(1, (int) (0.8f * d)), Color.rgb(0x22, 0x26, 0x2f));
        barBg.setCornerRadius(26 * d);
        bar.setBackground(barBg);
        bar.setElevation(14 * d);
        bar.setVisibility(View.GONE);
        bar.addView(barButton("⌨️", "Keyboard", new View.OnClickListener() {
            @Override public void onClick(View v) {
                keyboardForTextBox = false;
                stepAsideAfterKeyboard = false;
                openKeyboard();
            }
        }), weight());
        bar.addView(separator(d));
        modeButton = barButton(null, "", new View.OnClickListener() {
            @Override public void onClick(View v) { showModeMenu(); }
        });
        // The mode button stands out: it shows which controls the gestures use.
        GradientDrawable modeBg = new GradientDrawable();
        modeBg.setColor(Color.rgb(0x16, 0x28, 0x3a));
        modeBg.setCornerRadius(22 * d);
        modeButton.setBackground(modeBg);
        modeButton.setTextColor(Color.rgb(0xbf, 0xe6, 0xff));
        bar.addView(modeButton, weight());
        bar.addView(separator(d));
        bar.addView(barButton("🎛️", "PC keys", new View.OnClickListener() {
            @Override public void onClick(View v) { openPcKeys(); }
        }), weight());
        bar.addView(separator(d));
        bar.addView(barButton("⚙️", "Settings", new View.OnClickListener() {
            @Override public void onClick(View v) {
                hideMenu();
                startActivity(new Intent(TrackpadActivity.this, SettingsActivity.class));
            }
        }), weight());
        bar.addView(separator(d));
        Button close = barButton("✕", "Close", new View.OnClickListener() {
            @Override public void onClick(View v) { finishAndRemoveTask(); }
        });
        close.setTextColor(Color.rgb(0xff, 0xb3, 0xba));
        bar.addView(close, weight());

        FrameLayout padArea = new FrameLayout(this);
        pad = new TrackpadView(this);
        pad.setPcShortcuts(this);
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
        // A small pill at the top: the hint that the menu is a swipe down away.
        View handle = new View(this);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(Color.argb(70, 255, 255, 255));
        pill.setCornerRadius(3 * d);
        handle.setBackground(pill);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                (int) (44 * d), (int) (5 * d), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hlp.topMargin = (int) (6 * d);
        padArea.addView(handle, hlp);
        swipeHint = buildSwipeHint(d);
        FrameLayout.LayoutParams shp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        shp.topMargin = (int) (38 * d);
        padArea.addView(swipeHint, shp);
        keyButton = new KeyButton(this);
        keyButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Haptics.play(TrackpadActivity.this, Haptics.BAR);
                keyboardForTextBox = false;
                stepAsideAfterKeyboard = false;
                openKeyboard();
            }
        });
        int kb = (int) (42 * d);
        FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams(kb, kb, Gravity.BOTTOM | Gravity.END);
        klp.rightMargin = klp.bottomMargin = (int) (16 * d);
        padArea.addView(keyButton, klp);
        padArea.addView(buildSetupPanel(d), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(padArea, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // The keyboard covers the whole screen, bar included, while it's open (or the bottom
        // half, with Stretch to full screen off).
        FrameLayout frame = new FrameLayout(this);
        frame.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (52 * d), Gravity.TOP);
        blp.setMargins((int) (10 * d), (int) (10 * d), (int) (10 * d), 0);
        frame.addView(bar, blp);
        keyboard = new KeyboardView(this);
        keyboard.setListener(this);
        keyboard.setVisibility(View.GONE);
        frame.addView(keyboard, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pcKeys = new PcKeysView(this);
        pcKeys.setListener(this);
        pcKeys.setVisibility(View.GONE);
        frame.addView(pcKeys, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(frame);
        keyboardRequested = getIntent().getBooleanExtra(EXTRA_OPEN_KEYBOARD, false);
        stepAsideAfterKeyboard = keyboardRequested;
        ShizukuClient.addListener(this);
    }

    // ---- Dim when idle --------------------------------------------------------------------

    private static final long DIM_AFTER_MS = 30000;
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private long lastTouchAt;
    private boolean dimmed;
    /** The touch that wakes a dimmed screen only wakes it; its later events are dropped too. */
    private boolean swallowTouch;

    private final Runnable dimCheck = new Runnable() {
        @Override public void run() {
            long left = lastTouchAt + DIM_AFTER_MS - SystemClock.uptimeMillis();
            if (left > 0) {
                idleHandler.postDelayed(this, left); // touched since: check again later
            } else if (started && new Prefs(TrackpadActivity.this).dimWhenIdle()) {
                setDimmed(true);
            }
        }
    };

    private void setDimmed(boolean dim) {
        if (dimmed == dim) return;
        dimmed = dim;
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = dim ? 0.01f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
        getWindow().getDecorView().setAlpha(dim ? 0.25f : 1f);
    }

    private void restartIdleTimer() {
        lastTouchAt = SystemClock.uptimeMillis();
        idleHandler.removeCallbacks(dimCheck);
        idleHandler.postDelayed(dimCheck, DIM_AFTER_MS);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // Only a timestamp per event; the timer itself is only reset when it fires.
        lastTouchAt = SystemClock.uptimeMillis();
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && dimmed) {
            setDimmed(false);
            restartIdleTimer();
            swallowTouch = true;
        }
        if (swallowTouch) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) swallowTouch = false;
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            lastOwnDownAt = SystemClock.uptimeMillis();
        }
        if (action == MotionEvent.ACTION_DOWN) checkTopApp(false);
        if (handleMenuTouch(ev)) return true;
        return super.dispatchTouchEvent(ev);
    }

    // ---- Swipe-down menu --------------------------------------------------------------------

    private static final long MENU_HIDE_MS = 5000;
    private LinearLayout menuBar;
    /** A touch that started on the top edge: it opens the menu and never moves the pointer. */
    private boolean edgeTouch;
    private float edgeStartY;

    private final Runnable menuTimeout = new Runnable() {
        @Override public void run() {
            if (modeMenu != null && modeMenu.isShowing()) {
                idleHandler.postDelayed(this, MENU_HIDE_MS);
            } else {
                hideMenu();
            }
        }
    };

    private boolean menuShowing() {
        return menuBar != null && menuBar.getVisibility() == View.VISIBLE;
    }

    // The menu is hidden, so each time ThorPad opens a pill says where it is, for 10 seconds.
    private static final long SWIPE_HINT_MS = 10000;
    private View swipeHint;

    private final Runnable fadeSwipeHint = new Runnable() {
        @Override public void run() { hideSwipeHint(); }
    };

    private View buildSwipeHint(final float d) {
        LinearLayout pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding((int) (10 * d), (int) (6 * d), (int) (13 * d), (int) (6 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(23, 120, 200, 255));
        bg.setStroke(Math.max(1, (int) (0.8f * d)), Color.argb(56, 120, 200, 255));
        bg.setCornerRadius(20 * d);
        pill.setBackground(bg);
        // A double chevron pointing down, the second one fainter.
        View chevron = new View(this) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.6f * d);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                float w = getWidth(), h = getHeight();
                for (int i = 0; i < 2; i++) {
                    p.setColor(Color.argb(i == 0 ? 190 : 95, 120, 200, 255));
                    float y = h * (0.25f + 0.25f * i);
                    Path path = new Path();
                    path.moveTo(w * 0.25f, y);
                    path.lineTo(w * 0.5f, y + h * 0.25f);
                    path.lineTo(w * 0.75f, y);
                    c.drawPath(path, p);
                }
            }
        };
        pill.addView(chevron, new LinearLayout.LayoutParams((int) (14 * d), (int) (14 * d)));
        TextView t = new TextView(this);
        t.setText(Lang.t("Swipe down from the top for the menu"));
        t.setTextSize(12);
        t.setTextColor(Color.argb(158, 255, 255, 255));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = (int) (6 * d);
        pill.addView(t, tlp);
        pill.postDelayed(fadeSwipeHint, SWIPE_HINT_MS);
        return pill;
    }

    private void hideSwipeHint() {
        if (swipeHint == null || swipeHint.getVisibility() != View.VISIBLE) return;
        swipeHint.removeCallbacks(fadeSwipeHint);
        swipeHint.animate().alpha(0f).setDuration(600).withEndAction(new Runnable() {
            @Override public void run() { swipeHint.setVisibility(View.GONE); }
        }).start();
    }

    private void showMenu() {
        if (menuShowing()) return;
        hideSwipeHint();
        Haptics.play(this, Haptics.BAR);
        menuBar.setVisibility(View.VISIBLE);
        float h = 70 * getResources().getDisplayMetrics().density;
        menuBar.setTranslationY(-h);
        menuBar.animate().translationY(0).setDuration(160).start();
        idleHandler.removeCallbacks(menuTimeout);
        idleHandler.postDelayed(menuTimeout, MENU_HIDE_MS);
    }

    private void hideMenu() {
        idleHandler.removeCallbacks(menuTimeout);
        if (!menuShowing()) return;
        if (modeMenu != null) modeMenu.dismiss();
        float h = menuBar.getBottom() + 8 * getResources().getDisplayMetrics().density;
        menuBar.animate().translationY(-h).setDuration(140).withEndAction(new Runnable() {
            @Override public void run() { menuBar.setVisibility(View.GONE); }
        }).start();
    }

    /** Edge swipes open the menu; a touch on the trackpad while it's open only closes it. */
    private boolean handleMenuTouch(MotionEvent ev) {
        if ((keyboard.getVisibility() == View.VISIBLE && keyboardFull) || pcKeys.getVisibility() == View.VISIBLE) return false;
        float d = getResources().getDisplayMetrics().density;
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            edgeTouch = false;
            // Touches come in window coordinates; the screen's content may start lower.
            int[] at = new int[2];
            ((View) menuBar.getParent()).getLocationInWindow(at);
            float y = ev.getY() - at[1];
            if (menuShowing()) {
                if (y <= menuBar.getBottom()) {
                    idleHandler.removeCallbacks(menuTimeout);
                    idleHandler.postDelayed(menuTimeout, MENU_HIDE_MS);
                    return false; // a menu button
                }
                hideMenu();
                swallowTouch = true; // this touch only closes the menu
                return true;
            }
            if (y < 24 * d) {
                edgeTouch = true;
                edgeStartY = ev.getY();
                return true;
            }
            return false;
        }
        if (!edgeTouch) return false;
        if (action == MotionEvent.ACTION_MOVE && ev.getY() - edgeStartY > 30 * d) {
            showMenu();
        } else if (action == MotionEvent.ACTION_UP) {
            showMenu(); // a tap on the edge opens it too
            edgeTouch = false;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            edgeTouch = false;
        }
        return true;
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // ThorPad is out of sight: give back the autocorrect word list (it reloads when needed).
        if (level >= TRIM_MEMORY_UI_HIDDEN) Autocorrect.release();
    }

    // ---- PC mode ----------------------------------------------------------------------------
    // While a PC is streamed (Moonlight, a fork of it, Steam Link...), gestures send Windows
    // shortcuts through the virtual keyboard. Which app is on the top screen is checked when a
    // touch starts (at most every few seconds), never in the background.

    private static final long TOP_CHECK_EVERY_MS = 3000;
    /** With usage events to say nothing changed, a check costs almost nothing, so it can run more often. */
    private static final long TOP_CHECK_GATED_MS = 1000;
    private HandlerThread topThread;
    private Handler topHandler;
    private long lastTopCheck;
    /** Wall-clock time of the last dumpsys, for asking Android which apps came up since. */
    private volatile long lastTopDump;
    private volatile boolean usageEvents, usageAsked;
    private String topPackage, topLabel;
    private boolean topStreamsPc;

    private void checkTopApp(final boolean force) {
        if (!mouse.isReady() || new Prefs(this).pcMode() != Prefs.PC_AUTO) return;
        long now = SystemClock.uptimeMillis();
        if (!force && now - lastTopCheck < (usageEvents ? TOP_CHECK_GATED_MS : TOP_CHECK_EVERY_MS)) return;
        lastTopCheck = now;
        if (topThread == null) {
            topThread = new HandlerThread("ThorPad top app");
            topThread.start();
            topHandler = new Handler(topThread.getLooper());
        }
        Display target = Displays.pointerDisplay(this);
        final int displayId = target != null ? target.getDisplayId() : Display.DEFAULT_DISPLAY;
        topHandler.post(new Runnable() {
            @Override public void run() {
                // dumpsys makes Android list every activity, which is the costly part of this
                // check. Usage events say whether any app came up since the last one; when none
                // did, the answer can't have changed.
                // The first check has no "since" to ask about, so it just looks.
                long since = lastTopDump > 0 ? lastTopDump - 1000 : System.currentTimeMillis();
                Boolean switched = TopApp.switchedSince(TrackpadActivity.this, since);
                if (switched == null && !usageAsked) {
                    usageAsked = true;
                    TopApp.allowUsageEvents(TrackpadActivity.this);
                    switched = TopApp.switchedSince(TrackpadActivity.this, since);
                }
                usageEvents = switched != null;
                if (!force && lastTopDump > 0 && Boolean.FALSE.equals(switched)) return;
                lastTopDump = System.currentTimeMillis();
                final String[] top = TopApp.on(displayId);
                // No clear answer (mid-switch, or ThorPad itself): look again next time.
                if (top == null || top[0].equals(getPackageName())) lastTopDump = 0;
                runOnUiThread(new Runnable() {
                    @Override public void run() { onTopApp(top); }
                });
            }
        });
    }

    private void onTopApp(String[] top) {
        if (top == null || top[0].equals(getPackageName())) return; // keep the last answer
        topPackage = top[0];
        topLabel = TopApp.label(this, top[0]);
        topStreamsPc = TopApp.streamsPc(top[0], top[1], new Prefs(this).pcApps());
        refresh();
    }

    @Override
    public boolean pcActive() {
        if (!mouse.isReady()) return false; // PC shortcuts go through the virtual keyboard
        switch (new Prefs(this).pcMode()) {
            case Prefs.PC_ALWAYS: return true;
            case Prefs.PC_NEVER: return false;
            default: return topStreamsPc;
        }
    }

    @Override
    public void pcKeys(int... codes) {
        if (!mouse.isReady()) return;
        if (!keyboardDevice.isStartingOrReady()) keyboardDevice.start(); // keys queue meanwhile
        keyboardDevice.combo(codes);
    }

    /** The virtual keyboard stays plugged in while something might type on it. */
    private void updateKeyboardDevice() {
        boolean needed = mouse.isReady() && (keyboard.getVisibility() == View.VISIBLE
                || pcKeys.getVisibility() == View.VISIBLE || pcActive());
        if (needed && ThorPadIme.active() == null) {
            if (!keyboardDevice.isStartingOrReady()) keyboardDevice.start();
        } else if (!needed && keyboardDevice.isStartingOrReady()) {
            keyboardDevice.stop(); // unplug, so the top screen's own touch keyboard works again
        }
    }

    private void openPcKeys() {
        hideMenu();
        if (!mouse.isReady()) {
            android.widget.Toast.makeText(this, Lang.t("PC keys need mouse mode (Shizuku)"),
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        pcKeys.reset();
        pad.setOutput(null);
        pcKeys.setVisibility(View.VISIBLE);
        updateKeyboardDevice();
    }

    @Override
    public void onPcKeys(int... codes) { pcKeys(codes); }

    @Override
    public void onPcKeysLetters() {
        pcKeys.setVisibility(View.GONE);
        openKeyboard();
    }

    @Override
    public void onPcKeysClose() {
        pcKeys.setVisibility(View.GONE);
        pcKeys.reset();
        updateKeyboardDevice();
        refresh();
    }

    private String modeButtonText() {
        int mode = new Prefs(this).pcMode();
        if (mode == Prefs.PC_ALWAYS) return "🖥️ PC ▾";
        if (mode == Prefs.PC_NEVER) return "📱 Android ▾";
        return topStreamsPc ? "🖥️ Auto: PC ▾" : "✨ Auto ▾";
    }

    private PopupWindow modeMenu;

    private void showModeMenu() {
        final Prefs prefs = new Prefs(this);
        float d = getResources().getDisplayMetrics().density;
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(0x1c, 0x1f, 0x26));
        bg.setStroke(Math.max(1, (int) d), Color.rgb(0x2e, 0x34, 0x40));
        bg.setCornerRadius(12 * d);
        list.setBackground(bg);
        list.setClipToOutline(true);
        int mode = prefs.pcMode();
        list.addView(menuRow("✨ Auto", "PC controls when Moonlight, Steam Link, Parsec or another PC streaming app is on the top screen", mode == Prefs.PC_AUTO, new Runnable() {
            @Override public void run() { setPcMode(Prefs.PC_AUTO); }
        }));
        list.addView(menuRow("📱 Android", "Recent apps, close app, Android back", mode == Prefs.PC_NEVER,
                new Runnable() {
                    @Override public void run() { setPcMode(Prefs.PC_NEVER); }
                }));
        list.addView(menuRow("🖥️ PC", "Task view, desktop, Alt+Tab, Alt+← back", mode == Prefs.PC_ALWAYS,
                new Runnable() {
                    @Override public void run() { setPcMode(Prefs.PC_ALWAYS); }
                }));
        if (topPackage != null) {
            final String pkg = topPackage;
            final boolean added = prefs.pcApps().contains(pkg);
            if (added || !topStreamsPc) {
                list.addView(menuRow(Lang.f(added ? "✕ Stop PC for %s" : "➕ Always PC for %s", topLabel),
                        added ? "Use Android controls for this app again"
                                : "Treat this app as a PC streaming app in Auto", false, new Runnable() {
                            @Override public void run() {
                                prefs.setPcApp(pkg, !added);
                                topStreamsPc = !added;
                                setPcMode(Prefs.PC_AUTO);
                            }
                        }));
            }
        }
        list.addView(menuRow("🏠 Go home", "Android home screen on the top screen", false, new Runnable() {
            @Override public void run() {
                PadOutput o = currentOutput();
                if (o != null) o.home();
            }
        }));
        int w = (int) (getResources().getDisplayMetrics().widthPixels * 0.47f);
        // Not focusable: the app on the top screen keeps focus (and its mouse capture).
        modeMenu = new PopupWindow(list, w, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        modeMenu.setOutsideTouchable(true);
        modeMenu.setElevation(16 * d);
        modeMenu.showAsDropDown(modeButton, 0, (int) (4 * d));
    }

    private View menuRow(String title, String detail, boolean selected, final Runnable action) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (12 * d);
        row.setPadding(p, (int) (10 * d), p, (int) (10 * d));
        if (selected) row.setBackgroundColor(Color.rgb(0x1f, 0x3a, 0x52));
        TextView t = new TextView(this);
        t.setText(selected ? Lang.t(title) + "   ✓" : Lang.t(title));
        t.setTextSize(16);
        t.setTextColor(selected ? Color.WHITE : Color.rgb(0xe8, 0xe8, 0xe8));
        row.addView(t);
        TextView s = new TextView(this);
        s.setText(Lang.t(detail));
        s.setTextSize(11);
        s.setTextColor(selected ? Color.rgb(0x9f, 0xc3, 0xdd) : Color.rgb(0x8a, 0x90, 0x9b));
        row.addView(s);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Haptics.play(TrackpadActivity.this, Haptics.BAR);
                if (modeMenu != null) modeMenu.dismiss();
                action.run();
            }
        });
        return row;
    }

    private void setPcMode(int mode) {
        new Prefs(this).setPcMode(mode);
        if (mode == Prefs.PC_AUTO) checkTopApp(true);
        refresh();
    }

    @Override
    protected void onDestroy() {
        if (topThread != null) topThread.quitSafely();
        ShizukuClient.removeListener(this);
        mouse.quit();
        keyboardDevice.quit();
        super.onDestroy();
    }

    private static LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(2, 0, 2, 0);
        return lp;
    }

    /** The thin line between two buttons in the menu pill. */
    private View separator(float d) {
        View v = new View(this);
        v.setBackgroundColor(Color.rgb(0x22, 0x26, 0x2f));
        v.setLayoutParams(new LinearLayout.LayoutParams(Math.max(1, (int) (0.8f * d)), (int) (20 * d)));
        return v;
    }

    /** A menu button: an emoji, then its label in the keyboard language. */
    private Button barButton(String icon, String text, final View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(icon == null ? Lang.t(text) : icon + "  " + Lang.t(text));
        b.setAllCaps(false);
        b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setTextSize(14);
        b.setTextColor(Color.rgb(0xd8, 0xdc, 0xe3));
        b.setPadding(0, 0, 0, 0);
        b.setStateListAnimator(null);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.TRANSPARENT);
        bg.setCornerRadius(22 * getResources().getDisplayMetrics().density);
        b.setBackground(bg);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Haptics.play(TrackpadActivity.this, Haptics.BAR);
                listener.onClick(v);
            }
        });
        return b;
    }

    private View buildSetupPanel(float d) {
        setupPanel = new SetupPanel(this, new SetupPanel.Host() {
            @Override public void retryMouse() {
                mouseError = null;
                refresh();
            }

            @Override public void refreshSetup() { refresh(); }
        });
        return setupPanel;
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        ThorPadIme.setListener(this);
        KeyboardKeeper.check(this);
        getContentResolver().registerContentObserver(
                android.provider.Settings.Secure.getUriFor(android.provider.Settings.Secure.DEFAULT_INPUT_METHOD),
                false, keyboardChanged);
        refresh();
        // Opened by a text box: don't interrupt typing with an update.
        if (!keyboardRequested) quietUpdateCheck();
        if (keyboardRequested) {
            keyboardRequested = false;
            if (keyboard.getVisibility() != View.VISIBLE) {
                keyboardForTextBox = true;
                openKeyboard();
                if (mouse.isReady()) touchWatcher.start();
            }
        }
        restartIdleTimer();
    }

    /**
     * Checked each time ThorPad opens (a new screen after Close), and when it comes back after an
     * hour or more. Android keeps ThorPad's process around after Close, so this can't be once
     * per process.
     */
    private boolean updateChecked;
    private static long lastUpdateCheckAt;
    private static final long UPDATE_RECHECK_MS = 60 * 60 * 1000;
    private static final String SKIPPED_UPDATE = "skipped_update";

    /**
     * Looks for a newer release when ThorPad starts and says nothing unless there is one. "Not
     * now" stops it asking again about that version; Settings > Check for updates still offers it.
     */
    private void quietUpdateCheck() {
        long now = SystemClock.elapsedRealtime();
        if (updateChecked && lastUpdateCheckAt != 0 && now - lastUpdateCheckAt < UPDATE_RECHECK_MS) return;
        updateChecked = true;
        lastUpdateCheckAt = now;
        final android.content.SharedPreferences sp = new Prefs(this).raw();
        final boolean[] installing = {false};
        final Updater[] updater = new Updater[1];
        updater[0] = new Updater(this, new Updater.Listener() {
            @Override public void onUpdateStatus(String status, boolean done) {
                // Silent while checking (even offline); once the user picked Update, say how it goes.
                if (!installing[0] && !done && !status.equals(Lang.t("Checking for updates…"))) {
                    installing[0] = true;
                    android.widget.Toast.makeText(TrackpadActivity.this, Lang.t("Downloading the update…"),
                            android.widget.Toast.LENGTH_SHORT).show();
                } else if (installing[0] && done) {
                    android.widget.Toast.makeText(TrackpadActivity.this, status,
                            android.widget.Toast.LENGTH_LONG).show();
                }
            }

            @Override public void onUpdateAvailable(final Updater.Release r) {
                if (!started || isFinishing() || r.version.equals(sp.getString(SKIPPED_UPDATE, null))) return;
                UpdateDialog.show(TrackpadActivity.this, r, updater[0], new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dialog, int which) {
                        sp.edit().putString(SKIPPED_UPDATE, r.version).apply();
                    }
                });
            }
        });
        updater[0].check();
    }

    /** The language the buttons were made in; changing it in Settings rebuilds the screen. */
    private String builtLanguage;

    @Override
    protected void onResume() {
        super.onResume();
        if (!Lang.code().equals(builtLanguage)) {
            recreate();
            return;
        }
        checkTopApp(true);
        refresh();
        pad.invalidate(); // the hints setting may have changed
        keyButton.setVisibility(new Prefs(this).keyboardButton() ? View.VISIBLE : View.GONE);
        mouse.smooth = new Prefs(this).smoothScroll();
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
        getContentResolver().unregisterContentObserver(keyboardChanged);
        stepAsideAfterKeyboard = false;
        ThorPadIme.setListener(null);
        setupPanel.removeCallbacks(recheck);
        idleHandler.removeCallbacks(dimCheck);
        setDimmed(false);
        // Unplug the virtual mouse and hide the overlay pointer while the trackpad is away.
        mouse.stop();
        onCloseKeyboard();
        if (pcKeys.getVisibility() == View.VISIBLE) onPcKeysClose();
        hideMenu();
        keyboardDevice.stop();
        ThorPadService s = ThorPadService.get();
        if (s != null) s.hidePointer();
        pad.setOutput(null);
    }

    @Override
    public void onShizukuChanged() {
        KeyboardKeeper.check(this);
        mouseError = null;
        if (!ShizukuClient.hasPermission()) mouse.stop();
        refresh();
    }

    @Override
    public void onDeviceStateChanged(UinputDevice device, boolean ready, String error) {
        if (ready && (device == mouse || device == keyboardDevice) && !new Prefs(this).keyboardLayoutSet()) {
            final Context app = getApplicationContext();
            new Thread(new Runnable() {
                @Override public void run() { KeyboardLayoutTool.setUp(app); }
            }).start();
        }
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
        hideMenu();
        Prefs prefs = new Prefs(this);
        keyboard.reset();
        keyboard.setTypeOnRelease(prefs.typeOnRelease());
        keyboard.setSentenceHelp(prefs.doubleSpacePeriod(), prefs.autoCaps());
        keyboard.setAutocorrect(prefs.autocorrect() ? Autocorrect.get(this) : null);
        keyboardDeviceFailed = false;
        // With the ThorPad keyboard picked in Android, keys go straight into the text box.
        // Otherwise, in mouse mode, plug in a virtual keyboard so keys arrive like a real USB
        // keyboard (which games need).
        if (ThorPadIme.active() == null && mouse.isReady()) keyboardDevice.start();
        keyboard.setOutput(typing);
        // Settings > Keyboard > Stretch to full screen. Off, the keyboard takes the bottom half
        // and the trackpad above it keeps working.
        keyboardFull = prefs.stretchKeyboard();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) keyboard.getLayoutParams();
        View frame = (View) keyboard.getParent();
        int h = frame.getHeight() > 0 ? frame.getHeight() : getResources().getDisplayMetrics().heightPixels;
        lp.height = keyboardFull ? ViewGroup.LayoutParams.MATCH_PARENT : Math.round(h * 0.52f);
        lp.gravity = Gravity.BOTTOM;
        keyboard.setLayoutParams(lp);
        if (keyboardFull) pad.setOutput(null);
        keyboard.setVisibility(View.VISIBLE);
    }

    /** A text box on the top screen asked for a keyboard, and ThorPad is the keyboard. */
    @Override
    public void onKeyboardWanted() {
        KeyboardLog.add("trackpad: keyboard wanted, started=" + started + " open=" + (keyboard.getVisibility() == View.VISIBLE));
        if (!started || keyboard.getVisibility() == View.VISIBLE) return;
        if (pcKeys.getVisibility() == View.VISIBLE) {
            pcKeys.setVisibility(View.GONE);
            pcKeys.reset();
        }
        keyboardForTextBox = true;
        openKeyboard();
        if (mouse.isReady()) touchWatcher.start();
    }

    /** The text box that opened the keyboard is gone: put the keyboard away too. */
    @Override
    public void onKeyboardDone() {
        KeyboardLog.add("trackpad: done, forTextBox=" + keyboardForTextBox + " open=" + (keyboard.getVisibility() == View.VISIBLE));
        if (keyboardForTextBox && keyboard.getVisibility() == View.VISIBLE) onCloseKeyboard();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra(EXTRA_OPEN_KEYBOARD, false)) {
            keyboardRequested = true;
            // Only when ThorPad wasn't already showing; if it was, it stays.
            if (!started) stepAsideAfterKeyboard = true;
        }
    }

    private boolean keyboardDeviceFailed;

    /** Android's keyboard changed while ThorPad is open: switch back if it dropped ThorPad's. */
    private final android.database.ContentObserver keyboardChanged =
            new android.database.ContentObserver(new android.os.Handler(android.os.Looper.getMainLooper())) {
                @Override public void onChange(boolean selfChange) { KeyboardKeeper.check(TrackpadActivity.this); }
            };
    /** The open keyboard fills the screen; when it only takes the bottom half, the trackpad still works. */
    private boolean keyboardFull = true;

    /** Picks where each key goes, as the selected text box can change while typing. */
    private final KeyOutput typing = new Typing();

    private final class Typing implements KeyOutput, TextContext {
        // Only an Android text box (through ThorPad's Android keyboard) can answer these.
        @Override public boolean capitalizeNext() {
            ThorPadIme ime = ThorPadIme.active();
            return ime != null && ime.capitalizeNext();
        }

        @Override public boolean allowsCorrections() {
            ThorPadIme ime = ThorPadIme.active();
            return ime == null || ime.allowsCorrections();
        }

        @Override public boolean isPassword() {
            ThorPadIme ime = ThorPadIme.active();
            return ime != null && ime.isPassword();
        }

        @Override public CharSequence textBefore(int n) {
            ThorPadIme ime = ThorPadIme.active();
            return ime != null ? ime.textBefore(n) : null;
        }

        @Override public void typeChar(char c) {
            KeyOutput o = keyTarget();
            if (o != null) o.typeChar(c);
        }

        @Override public void special(int key) {
            KeyOutput o = keyTarget();
            if (o != null) o.special(key);
        }
    }

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
        KeyboardLog.add("trackpad: keyboard closed");
        keyboardForTextBox = false;
        touchWatcher.stop();
        ThorPadIme.bottomKeyboardClosed();
        keyboard.setVisibility(View.GONE);
        keyboard.setOutput(null);
        keyboard.setAutocorrect(null); // lets the word list be freed while the keyboard is shut
        updateKeyboardDevice();
        refresh();
        if (stepAsideAfterKeyboard && started) {
            stepAsideAfterKeyboard = false;
            KeyboardLog.add("trackpad: stepping aside for the previous app");
            moveTaskToBack(true);
        }
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
            modeLabel.setText("Android"); // the real mouse: the usual case, so no need to say so
        } else if (service != null && !shizukuReady) {
            out = service;
            service.showPointer();
            modeLabel.setText("Android · " + Lang.t("Touch mode"));
        } else if (service != null && mouseError != null) {
            out = service;
            service.showPointer();
            modeLabel.setText("Android · " + Lang.t("Touch mode (virtual mouse failed)"));
        } else {
            out = null; // waiting for the virtual mouse, or nothing set up yet
            modeLabel.setText(shizukuReady && mouseError == null ? Lang.t("Starting mouse…") : "");
        }
        boolean covered = (keyboard.getVisibility() == View.VISIBLE && keyboardFull)
                || pcKeys.getVisibility() == View.VISIBLE;
        pad.setOutput(covered ? null : out);
        boolean pc = pcActive();
        pad.setPcHints(pc);
        modeButton.setText(modeButtonText());
        if (pc && out == mouse) {
            modeLabel.setText("PC" + (new Prefs(this).pcMode() == Prefs.PC_AUTO
                    && topLabel != null ? " · " + topLabel : ""));
        }
        updateKeyboardDevice();

        boolean waitingForMouse = shizukuReady && mouseError == null && !mouse.isReady();
        boolean needed = out == null && !waitingForMouse;
        if (needed) setupShown = true;
        boolean show = needed || (setupShown && !setupPanel.skipped() && !setupPanel.keyboardInUse());
        if (show) setupPanel.update(shizukuReady, mouseError, service != null, out != null);
        setupPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && started) {
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

    /** A faint round button with a keyboard outline, drawn so it stays quiet on the black pad. */
    private static final class KeyButton extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        KeyButton(Context c) {
            super(c);
            setContentDescription(Lang.t("Keyboard"));
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(isPressed() ? Color.argb(40, 255, 255, 255) : Color.argb(15, 255, 255, 255));
            canvas.drawCircle(w / 2, h / 2, w / 2 - d, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(d);
            paint.setColor(Color.argb(30, 255, 255, 255));
            canvas.drawCircle(w / 2, h / 2, w / 2 - d, paint);
            // The keyboard: an outline, two rows of dots and a space bar.
            float kw = w * 0.5f, kh = h * 0.32f, x0 = (w - kw) / 2, y0 = (h - kh) / 2;
            paint.setColor(Color.argb(140, 255, 255, 255));
            paint.setStrokeWidth(1.2f * d);
            r.set(x0, y0, x0 + kw, y0 + kh);
            canvas.drawRoundRect(r, 2 * d, 2 * d, paint);
            paint.setStyle(Paint.Style.FILL);
            float dot = 0.9f * d;
            for (int i = 0; i < 5; i++) canvas.drawCircle(x0 + kw * (0.18f + 0.16f * i), y0 + kh * 0.3f, dot, paint);
            canvas.drawCircle(x0 + kw * 0.18f, y0 + kh * 0.55f, dot, paint);
            canvas.drawCircle(x0 + kw * 0.82f, y0 + kh * 0.55f, dot, paint);
            paint.setStrokeWidth(1.2f * d);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(x0 + kw * 0.32f, y0 + kh * 0.78f, x0 + kw * 0.68f, y0 + kh * 0.78f, paint);
        }

        @Override protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate(); // pressed look
        }
    }
}
