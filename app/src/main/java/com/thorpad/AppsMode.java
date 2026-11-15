package com.thorpad;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Any app on the bottom screen. ThorPad opens the app there and floats a small button over it:
 * tap it for the trackpad, hold it for a menu, drag it anywhere (it snaps to the nearest edge).
 * Both windows are overlays that never take focus, so the app on the top screen keeps it.
 */
final class AppsMode {
    private AppsMode() {}

    static final String EXTRA_OPEN_APPS = "open_apps";

    private static final long FADE_AFTER_MS = 3000, HOLD_MS = 450;
    private static final float FADED = 0.4f;
    private static final Handler main = new Handler(Looper.getMainLooper());

    private static WindowManager windows;
    private static View button;
    private static WindowManager.LayoutParams buttonLp;
    private static View menu;
    private static Display shownOn;

    /** The app on the bottom screen, or null when none was put there (or it was closed). */
    static String current(Context context) { return new Prefs(context).appCurrent(); }

    /** Opens pkg on the bottom screen and shows the floating button. False if Android wouldn't. */
    static boolean open(Context context, String pkg) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
        Display target = Displays.trackpadDisplay(context);
        if (intent == null || target == null) {
            Toast.makeText(context, Lang.t("Couldn't open that app"), Toast.LENGTH_SHORT).show();
            return false;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(target.getDisplayId());
            context.startActivity(intent, options.toBundle());
        } catch (RuntimeException e) {
            KeyboardLog.add("apps: couldn't open " + pkg + ": " + e);
            Toast.makeText(context, Lang.t("Couldn't open that app"), Toast.LENGTH_SHORT).show();
            return false;
        }
        KeyboardLog.add("apps: opened " + pkg + " on display " + target.getDisplayId());
        new Prefs(context).setAppCurrent(pkg);
        final Context app = context.getApplicationContext();
        // Wait for the app to cover the trackpad, so the button doesn't flash over it.
        main.postDelayed(new Runnable() {
            @Override public void run() { showButton(app); }
        }, 350);
        return true;
    }

    /** No app on the bottom screen any more: the button goes and the trackpad's card too. */
    static void end(Context context) {
        new Prefs(context).setAppCurrent(null);
        hideButton();
    }

    /** Shows the button if an app was put on the bottom screen (the trackpad just stepped aside). */
    static void showButtonIfActive(Context context) {
        if (current(context) != null) showButton(context.getApplicationContext());
    }

    // ---- The floating button ------------------------------------------------------------------

    static void showButton(final Context context) {
        main.removeCallbacksAndMessages(null);
        if (button != null) return;
        if (!KeyboardOverlay.allowed(context)) return;
        Display display = Displays.trackpadDisplay(context);
        if (display == null) return;
        try {
            Context screen = context.createDisplayContext(display)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
            WindowManager wm = (WindowManager) screen.getSystemService(Context.WINDOW_SERVICE);
            final Prefs prefs = new Prefs(context);
            float d = screen.getResources().getDisplayMetrics().density;
            Point size = new Point();
            display.getRealSize(size);
            View v;
            WindowManager.LayoutParams lp;
            int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            if (prefs.appSwipeInstead()) {
                v = new EdgeStrip(screen, context);
                lp = new WindowManager.LayoutParams((int) (18 * d), size.y / 2,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            } else {
                int px = (int) (new int[] {44, 56, 72}[Math.max(0, Math.min(2, prefs.appButtonSize()))] * d);
                v = new FloatingButton(screen, context, size, px);
                lp = new WindowManager.LayoutParams(px, px,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.LEFT;
                int margin = (int) (6 * d);
                int x = prefs.appButtonX(), y = prefs.appButtonY();
                if (x < 0 || y < 0) {
                    x = size.x - px - margin;
                    y = Math.round(size.y * 0.4f);
                }
                lp.x = Math.max(0, Math.min(size.x - px, x));
                lp.y = Math.max(0, Math.min(size.y - px, y));
            }
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            lp.setTitle("ThorPad button");
            wm.addView(v, lp);
            windows = wm;
            button = v;
            buttonLp = lp;
            shownOn = display;
            if (v instanceof FloatingButton) ((FloatingButton) v).wake();
            KeyboardLog.add("apps: button shown on display " + display.getDisplayId());
        } catch (RuntimeException e) {
            KeyboardLog.add("apps: button failed " + e);
        }
    }

    static void hideButton() {
        main.removeCallbacksAndMessages(null);
        hideMenu();
        View v = button;
        button = null;
        if (v == null) return;
        try {
            windows.removeView(v);
        } catch (RuntimeException ignored) {
        }
    }

    /** Settings changed how the button looks: put it back up the new way. */
    static void rebuildButton(Context context) {
        if (button == null) return;
        hideButton();
        showButton(context.getApplicationContext());
    }

    private static void trackpad(Context context, boolean keyboard, boolean apps) {
        hideButton();
        LauncherActivity.openTrackpad(context, keyboard, apps);
    }

    /** Round, see-through, with a mouse on it. Tap, hold or drag. */
    private static final class FloatingButton extends View {
        private final Context app;
        private final Point screen;
        private final int px;
        private final float d;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TouchFilter filter;
        private float downX, downY;
        private int startX, startY;
        private boolean dragging, held, ignoring;

        private final Runnable fade = new Runnable() {
            @Override public void run() {
                if (new Prefs(app).appButtonFade()) animate().alpha(FADED).setDuration(400).start();
            }
        };
        private final Runnable hold = new Runnable() {
            @Override public void run() {
                held = true;
                Haptics.play(app, Haptics.HOLD);
                showMenu(app);
            }
        };

        FloatingButton(Context screenContext, Context app, Point screen, int px) {
            super(screenContext);
            this.app = app;
            this.screen = screen;
            this.px = px;
            this.d = screenContext.getResources().getDisplayMetrics().density;
            this.filter = new TouchFilter(app);
            setContentDescription(Lang.t("Trackpad"));
        }

        void wake() {
            removeCallbacks(fade);
            animate().cancel();
            setAlpha(1f);
            postDelayed(fade, FADE_AFTER_MS);
        }

        @Override protected void onDraw(Canvas c) {
            float r = getWidth() / 2f, stroke = 2 * d;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(0xdd, 0x16, 0x28, 0x3a));
            c.drawCircle(r, r, r - stroke, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(Color.rgb(0x4b, 0x84, 0xb5));
            c.drawCircle(r, r, r - stroke, paint);
            // A mouse: rounded body, a line between the buttons and a scroll wheel.
            float w = r * 0.62f, h = r * 0.92f, left = r - w / 2, top = r - h / 2;
            paint.setColor(Color.rgb(0xdd, 0xee, 0xfb));
            paint.setStrokeWidth(Math.max(1.5f * d, r * 0.08f));
            c.drawRoundRect(left, top, left + w, top + h, w / 2, w / 2, paint);
            c.drawLine(left, top + h * 0.42f, left + w, top + h * 0.42f, paint);
            c.drawLine(r, top, r, top + h * 0.42f, paint);
        }

        @Override public boolean onTouchEvent(MotionEvent ev) {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) ignoring = filter.drop(ev);
            else if (ignoring) {
                filter.drop(ev);
                return true;
            }
            if (ignoring) return true;
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    wake();
                    removeCallbacks(fade);
                    downX = ev.getRawX();
                    downY = ev.getRawY();
                    startX = buttonLp.x;
                    startY = buttonLp.y;
                    dragging = held = false;
                    postDelayed(hold, HOLD_MS);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = ev.getRawX() - downX, dy = ev.getRawY() - downY;
                    if (!dragging && !held && Math.hypot(dx, dy) > 10 * d) {
                        dragging = true;
                        removeCallbacks(hold);
                    }
                    if (dragging) {
                        buttonLp.x = clamp(startX + Math.round(dx), screen.x - px);
                        buttonLp.y = clamp(startY + Math.round(dy), screen.y - px);
                        move();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    removeCallbacks(hold);
                    if (dragging) {
                        drop();
                    } else if (!held) {
                        Haptics.play(app, Haptics.BAR);
                        trackpad(app, false, false);
                        return true;
                    }
                    postDelayed(fade, FADE_AFTER_MS);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(hold);
                    if (dragging) drop();
                    postDelayed(fade, FADE_AFTER_MS);
                    return true;
            }
            return true;
        }

        private int clamp(int v, int max) { return Math.max(0, Math.min(max, v)); }

        private void move() {
            try {
                windows.updateViewLayout(this, buttonLp);
            } catch (RuntimeException ignored) {
            }
        }

        /** Let go after a drag: snap to the nearest side edge (unless turned off) and remember. */
        private void drop() {
            dragging = false;
            if (new Prefs(app).appButtonSnap()) {
                int margin = (int) (6 * d);
                buttonLp.x = buttonLp.x + px / 2 < screen.x / 2 ? margin : screen.x - px - margin;
                buttonLp.y = clamp(buttonLp.y, screen.y - px - margin);
                buttonLp.y = Math.max(margin, buttonLp.y);
                move();
            }
            new Prefs(app).setAppButtonAt(buttonLp.x, buttonLp.y);
        }
    }

    /** Swipe gesture instead of the button: a thin strip on the right edge. */
    private static final class EdgeStrip extends View {
        private final Context app;
        private final float d;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TouchFilter filter;
        private float downX, downY;
        private boolean fired, ignoring;

        private final Runnable hold = new Runnable() {
            @Override public void run() {
                fired = true;
                Haptics.play(app, Haptics.HOLD);
                showMenu(app);
            }
        };

        EdgeStrip(Context screenContext, Context app) {
            super(screenContext);
            this.app = app;
            this.d = screenContext.getResources().getDisplayMetrics().density;
            this.filter = new TouchFilter(app);
        }

        @Override protected void onDraw(Canvas c) {
            // A faint handle so the edge can be found.
            paint.setColor(Color.argb(70, 120, 200, 255));
            float w = 3 * d, h = getHeight() * 0.35f, x = getWidth() - w - 2 * d, y = (getHeight() - h) / 2;
            c.drawRoundRect(x, y, x + w, y + h, w, w, paint);
        }

        @Override public boolean onTouchEvent(MotionEvent ev) {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) ignoring = filter.drop(ev);
            else if (ignoring) {
                filter.drop(ev);
                return true;
            }
            if (ignoring) return true;
            if (action == MotionEvent.ACTION_DOWN) {
                downX = ev.getRawX();
                downY = ev.getRawY();
                fired = false;
                postDelayed(hold, HOLD_MS + 150);
            } else if (action == MotionEvent.ACTION_MOVE && !fired) {
                float dx = ev.getRawX() - downX, dy = ev.getRawY() - downY;
                if (Math.hypot(dx, dy) > 10 * d) removeCallbacks(hold);
                if (-dx > 36 * d) {
                    fired = true;
                    Haptics.play(app, Haptics.BAR);
                    trackpad(app, false, false);
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                removeCallbacks(hold);
            }
            return true;
        }
    }

    // ---- Hold menu ----------------------------------------------------------------------------

    static void hideMenu() {
        View m = menu;
        menu = null;
        if (m == null) return;
        try {
            windows.removeView(m);
        } catch (RuntimeException ignored) {
        }
    }

    private static void showMenu(final Context app) {
        if (menu != null || windows == null || shownOn == null) return;
        try {
            Context screen = app.createDisplayContext(shownOn)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
            float d = screen.getResources().getDisplayMetrics().density;
            Point size = new Point();
            shownOn.getRealSize(size);

            FrameLayout dim = new FrameLayout(screen);
            dim.setBackgroundColor(Color.argb(0x73, 0, 0, 0));
            final TouchFilter filter = new TouchFilter(app);
            dim.setOnTouchListener(new View.OnTouchListener() {
                @Override public boolean onTouch(View v, MotionEvent ev) {
                    if (filter.drop(ev)) return true;
                    if (ev.getActionMasked() == MotionEvent.ACTION_UP) hideMenu();
                    return true;
                }
            });

            LinearLayout pill = new LinearLayout(screen);
            pill.setGravity(Gravity.CENTER_VERTICAL);
            int pad = (int) (5 * d);
            pill.setPadding(pad, pad, pad, pad);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(0x16, 0x18, 0x1e));
            bg.setStroke(Math.max(1, (int) (0.8f * d)), Color.rgb(0x2e, 0x34, 0x40));
            bg.setCornerRadius(30 * d);
            pill.setBackground(bg);
            pill.setElevation(16 * d);
            pill.addView(item(screen, app, "🖱️", "Trackpad", true, false, new Runnable() {
                @Override public void run() { trackpad(app, false, false); }
            }));
            pill.addView(item(screen, app, "⌨️", "Keyboard", false, false, new Runnable() {
                @Override public void run() { trackpad(app, true, false); }
            }));
            pill.addView(item(screen, app, "📱", "Switch app", false, false, new Runnable() {
                @Override public void run() { trackpad(app, false, true); }
            }));
            pill.addView(item(screen, app, "✕", "Close app", false, true, new Runnable() {
                @Override public void run() { closeApp(app); }
            }));

            // Beside the button when there's room, otherwise centered above or below it.
            pill.measure(View.MeasureSpec.makeMeasureSpec(size.x, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(size.y, View.MeasureSpec.AT_MOST));
            int pw = pill.getMeasuredWidth(), ph = pill.getMeasuredHeight(), gap = (int) (10 * d);
            FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
            int bx, by, bw, bh;
            if (button instanceof FloatingButton) {
                bx = buttonLp.x; by = buttonLp.y; bw = buttonLp.width; bh = buttonLp.height;
            } else {
                bx = size.x; by = size.y / 2; bw = 0; bh = 0;
            }
            int cy = by + bh / 2;
            boolean onRight = bx + bw / 2 >= size.x / 2;
            int x = onRight ? bx - gap - pw : bx + bw + gap;
            if (x >= 0 && x + pw <= size.x) {
                plp.leftMargin = x;
                plp.topMargin = Math.max(gap, Math.min(size.y - ph - gap, cy - ph / 2));
            } else {
                plp.leftMargin = Math.max(0, (size.x - pw) / 2);
                plp.topMargin = cy - bh / 2 - gap - ph >= 0 ? cy - bh / 2 - gap - ph
                        : Math.min(size.y - ph, cy + bh / 2 + gap);
            }
            dim.addView(pill, plp);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            lp.setTitle("ThorPad button menu");
            windows.addView(dim, lp);
            menu = dim;
            // The button stays on top of the dimmed app.
            if (button != null) {
                windows.removeView(button);
                windows.addView(button, buttonLp);
                if (button instanceof FloatingButton) ((FloatingButton) button).wake();
            }
        } catch (RuntimeException e) {
            KeyboardLog.add("apps: menu failed " + e);
        }
    }

    private static View item(Context screen, final Context app, String icon, String label, boolean primary,
                             boolean red, final Runnable action) {
        float d = screen.getResources().getDisplayMetrics().density;
        TextView t = new TextView(screen);
        t.setText(icon + "  " + Lang.t(label));
        t.setTextSize(15);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(red ? Color.rgb(0xff, 0x7b, 0x7b) : primary ? Color.rgb(0xcf, 0xe6, 0xff) : Color.rgb(0xdd, 0xdd, 0xdd));
        t.setPadding((int) (14 * d), 0, (int) (14 * d), 0);
        t.setMinHeight((int) (46 * d));
        if (primary) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(0x1c, 0x35, 0x50));
            bg.setCornerRadius(23 * d);
            t.setBackground(bg);
        }
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Haptics.play(app, Haptics.BAR);
                hideMenu();
                action.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (46 * d));
        lp.setMargins((int) (2 * d), 0, (int) (2 * d), 0);
        t.setLayoutParams(lp);
        return t;
    }

    private static void closeApp(Context app) {
        String pkg = current(app);
        Display display = shownOn;
        if (pkg != null && display != null && ShizukuClient.hasPermission()) {
            TopApp.closeApp(app, pkg, display.getDisplayId());
            Toast.makeText(app, Lang.f("Closed %s", TopApp.label(app, pkg)), Toast.LENGTH_SHORT).show();
        }
        end(app);
        LauncherActivity.openTrackpad(app, false, false);
    }
}
