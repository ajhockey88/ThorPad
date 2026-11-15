package com.thorpad;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Put an app on this screen": pinned apps, then every app, with search (typed on ThorPad's own
 * keyboard, docked under the grid). Tap selects, tapping again opens; hold pins or unpins.
 */
final class AppPickerView extends FrameLayout implements KeyboardView.Listener {
    interface Listener {
        void onPickerOpen(String pkg);
        void onPickerClose();
    }

    private static final int COLUMNS = 6;
    private static final Handler main = new Handler(Looper.getMainLooper());
    /** Icons stay loaded between visits; the list of apps is read again each time. */
    private static final Map<String, Drawable.ConstantState> icons = new HashMap<>();

    static final class App {
        final String pkg, label;
        App(String pkg, String label) { this.pkg = pkg; this.label = label; }
    }

    private final float d;
    private final Prefs prefs;
    private final LinearLayout grid;
    private final ScrollView scroll;
    private final TextView search, open;
    private final KeyboardView keys;
    private Listener listener;
    private List<App> apps = new ArrayList<>();
    private String selected;
    private final StringBuilder query = new StringBuilder();

    AppPickerView(Context context) {
        super(context);
        d = getResources().getDisplayMetrics().density;
        prefs = new Prefs(context);
        setBackgroundColor(Color.BLACK);
        setClickable(true); // nothing reaches the trackpad underneath

        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int) (16 * d), (int) (12 * d), (int) (16 * d), (int) (12 * d));

        LinearLayout top = new LinearLayout(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(Lang.t("Put an app on this screen"), 22, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView back = text("✕  " + Lang.t("Back"), 15, Color.rgb(0xd8, 0xdc, 0xe3));
        back.setBackground(round(Color.rgb(0x1c, 0x1e, 0x24), 18));
        back.setPadding((int) (14 * d), (int) (8 * d), (int) (16 * d), (int) (8 * d));
        back.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                Haptics.play(getContext(), Haptics.BAR);
                if (listener != null) listener.onPickerClose();
            }
        });
        top.addView(back);
        col.addView(top);

        search = text("", 15, Color.rgb(0x88, 0x8e, 0x99));
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setSingleLine(true);
        search.setPadding((int) (14 * d), 0, (int) (14 * d), 0);
        search.setBackground(round(Color.rgb(0x11, 0x13, 0x18), 12));
        search.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                Haptics.play(getContext(), Haptics.BAR);
                showKeys();
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (40 * d));
        slp.topMargin = (int) (10 * d);
        slp.bottomMargin = (int) (4 * d);
        col.addView(search, slp);

        scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(grid);
        col.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        open = text("", 18, Color.WHITE);
        open.setTypeface(Typeface.DEFAULT_BOLD);
        open.setGravity(Gravity.CENTER);
        open.setSingleLine(true);
        open.setEllipsize(TextUtils.TruncateAt.END);
        open.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (selected == null) return;
                Haptics.play(getContext(), Haptics.BAR);
                if (listener != null) listener.onPickerOpen(selected);
            }
        });
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (54 * d));
        olp.topMargin = (int) (8 * d);
        col.addView(open, olp);
        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        keys = new KeyboardView(context);
        keys.setListener(this);
        keys.setVisibility(GONE);
        addView(keys, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(getResources().getDisplayMetrics().heightPixels * KeyboardOverlay.DOCKED_HEIGHT), Gravity.BOTTOM));
    }

    void setListener(Listener l) { listener = l; }

    boolean isShowing() { return getVisibility() == VISIBLE; }

    /** Shows the picker with the app now on the bottom screen (if any) already selected. */
    void show(String current) {
        selected = current;
        query.setLength(0);
        hideKeys();
        setVisibility(VISIBLE);
        scroll.scrollTo(0, 0);
        updateSearch();
        updateOpen();
        final Context app = getContext().getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                final List<App> list = load(app);
                main.post(new Runnable() {
                    @Override public void run() {
                        apps = list;
                        build();
                    }
                });
            }
        }, "ThorPad apps").start();
    }

    void hide() {
        hideKeys();
        setVisibility(GONE);
    }

    /** Every app with a launcher icon, by name, ThorPad left out. */
    static List<App> load(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<App> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(context.getPackageName()) || !seen.add(pkg)) continue;
            CharSequence label = r.loadLabel(pm);
            out.add(new App(pkg, label != null ? label.toString() : pkg));
            synchronized (icons) {
                if (!icons.containsKey(pkg)) {
                    try {
                        Drawable dr = r.loadIcon(pm);
                        if (dr != null && dr.getConstantState() != null) icons.put(pkg, dr.getConstantState());
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }
        final java.text.Collator c = java.text.Collator.getInstance();
        Collections.sort(out, new Comparator<App>() {
            @Override public int compare(App a, App b) { return c.compare(a.label, b.label); }
        });
        return out;
    }

    private void build() {
        grid.removeAllViews();
        String q = query.toString().trim().toLowerCase(Locale.ROOT);
        if (q.length() > 0) {
            List<App> hits = new ArrayList<>();
            for (App a : apps) if (a.label.toLowerCase(Locale.ROOT).contains(q)) hits.add(a);
            section(Lang.t("RESULTS"));
            if (hits.isEmpty()) {
                TextView none = text(Lang.t("No apps match"), 14, Color.rgb(0x7a, 0x80, 0x8b));
                none.setPadding((int) (4 * d), (int) (10 * d), 0, 0);
                grid.addView(none);
            }
            rows(hits, false);
        } else {
            Map<String, App> byPkg = new HashMap<>();
            for (App a : apps) byPkg.put(a.pkg, a);
            List<App> pinned = new ArrayList<>();
            for (String p : prefs.pinnedApps()) if (byPkg.containsKey(p)) pinned.add(byPkg.get(p));
            section(Lang.t("PINNED"));
            rows(pinned, true);
            section(Lang.t("ALL APPS"));
            rows(apps, false);
        }
        updateOpen();
    }

    private void section(String name) {
        TextView t = text(name, 12, Color.rgb(0x6d, 0x9c, 0xc4));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.08f);
        t.setPadding((int) (4 * d), (int) (10 * d), 0, (int) (4 * d));
        grid.addView(t);
    }

    private void rows(List<App> list, boolean withPinTile) {
        int n = list.size() + (withPinTile ? 1 : 0);
        LinearLayout row = null;
        for (int i = 0; i < n; i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(getContext());
                grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (88 * d)));
            }
            View tile = i < list.size() ? tile(list.get(i)) : pinTile();
            row.addView(tile, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        if (row != null) {
            for (int i = n % COLUMNS; i > 0 && i < COLUMNS; i++) {
                row.addView(new View(getContext()), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
    }

    private View tile(final App a) {
        LinearLayout t = new LinearLayout(getContext());
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        boolean sel = a.pkg.equals(selected);
        if (sel) {
            GradientDrawable bg = round(Color.rgb(0x16, 0x28, 0x3a), 14);
            bg.setStroke(Math.max(1, (int) (1.5f * d)), Color.rgb(0x4b, 0x84, 0xb5));
            t.setBackground(bg);
        }
        ImageView icon = new ImageView(getContext());
        Drawable.ConstantState cs;
        synchronized (icons) { cs = icons.get(a.pkg); }
        if (cs != null) icon.setImageDrawable(cs.newDrawable(getResources()));
        icon.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 12 * d);
            }
        });
        icon.setClipToOutline(true);
        t.addView(icon, new LinearLayout.LayoutParams((int) (46 * d), (int) (46 * d)));
        TextView name = text(a.label, 12, sel ? Color.WHITE : Color.rgb(0xcc, 0xcc, 0xcc));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        name.setPadding((int) (4 * d), (int) (5 * d), (int) (4 * d), 0);
        t.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        t.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                Haptics.play(getContext(), Haptics.BAR);
                if (a.pkg.equals(selected)) {
                    if (listener != null) listener.onPickerOpen(a.pkg);
                    return;
                }
                selected = a.pkg;
                build();
            }
        });
        t.setOnLongClickListener(new OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                Haptics.play(getContext(), Haptics.HOLD);
                boolean pin = !prefs.pinnedApps().contains(a.pkg);
                prefs.setPinned(a.pkg, pin);
                Toast.makeText(getContext(), Lang.f(pin ? "Pinned %s" : "Unpinned %s", a.label), Toast.LENGTH_SHORT).show();
                build();
                return true;
            }
        });
        return t;
    }

    private View pinTile() {
        LinearLayout t = new LinearLayout(getContext());
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        TextView plus = text("＋", 22, Color.rgb(0x77, 0x77, 0x77));
        plus.setGravity(Gravity.CENTER);
        GradientDrawable bg = round(Color.rgb(0x1a, 0x1a, 0x1a), 12);
        bg.setStroke(Math.max(1, (int) d), Color.rgb(0x55, 0x55, 0x55), 4 * d, 3 * d);
        plus.setBackground(bg);
        t.addView(plus, new LinearLayout.LayoutParams((int) (46 * d), (int) (46 * d)));
        TextView name = text(Lang.t("Pin app"), 12, Color.rgb(0x77, 0x77, 0x77));
        name.setSingleLine(true);
        name.setPadding(0, (int) (5 * d), 0, 0);
        t.addView(name);
        t.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                Toast.makeText(getContext(), Lang.t("Hold any app to pin it here"), Toast.LENGTH_SHORT).show();
            }
        });
        return t;
    }

    private void updateOpen() {
        String label = null;
        for (App a : apps) if (a.pkg.equals(selected)) label = a.label;
        if (label == null && selected != null) label = TopApp.label(getContext(), selected);
        boolean ready = selected != null;
        open.setText(ready ? Lang.f("Open %s here", label) : Lang.t("Pick an app"));
        open.setTextColor(ready ? Color.WHITE : Color.rgb(0x77, 0x7d, 0x88));
        open.setBackground(round(ready ? Color.rgb(0x2f, 0x6f, 0xa8) : Color.rgb(0x1c, 0x1e, 0x24), 16));
    }

    private void updateSearch() {
        boolean typing = keys.getVisibility() == VISIBLE;
        if (query.length() == 0 && !typing) {
            search.setText("🔍  " + Lang.t("Search apps"));
            search.setTextColor(Color.rgb(0x88, 0x8e, 0x99));
        } else {
            search.setText("🔍  " + query + (typing ? "▏" : ""));
            search.setTextColor(Color.WHITE);
        }
    }

    // ---- Search keyboard ------------------------------------------------------------------------

    private final KeyOutput typing = new KeyOutput() {
        @Override public void typeChar(char c) {
            if (c == '\n') {
                hideKeys();
                return;
            }
            query.append(c);
            changed();
        }

        @Override public void special(int key) {
            if (key == BACKSPACE && query.length() > 0) {
                query.setLength(query.length() - 1);
            } else if (key == CLEAR_ALL) {
                query.setLength(0);
            } else if (key == ENTER) {
                hideKeys();
                return;
            }
            changed();
        }
    };

    private void changed() {
        updateSearch();
        build();
        scroll.scrollTo(0, 0);
    }

    private void showKeys() {
        if (keys.getVisibility() == VISIBLE) return;
        keys.reset();
        keys.setAutocorrect(null);
        keys.setSentenceHelp(false, false);
        keys.setTypeOnRelease(prefs.typeOnRelease());
        keys.setDocked(true);
        keys.setOutput(typing);
        keys.setVisibility(VISIBLE);
        updateSearch();
    }

    private void hideKeys() {
        if (keys.getVisibility() != VISIBLE) return;
        keys.setVisibility(GONE);
        keys.setOutput(null);
        updateSearch();
        // With one match left, it's the one: select it.
        String q = query.toString().trim().toLowerCase(Locale.ROOT);
        if (q.length() > 0) {
            App only = null;
            int n = 0;
            for (App a : apps) if (a.label.toLowerCase(Locale.ROOT).contains(q)) { only = a; n++; }
            if (n == 1) {
                selected = only.pkg;
                build();
            }
        }
    }

    @Override public void onCloseKeyboard() { hideKeys(); }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusDp * d);
        return g;
    }
}
