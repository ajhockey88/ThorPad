package com.thorpad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import static com.thorpad.VirtualKeyboard.*;

/**
 * PC keys for streaming a PC (Moonlight and the like): Esc, F1 to F12, Windows shortcuts,
 * navigation keys and sticky Ctrl / Alt / Shift / Win. Full screen like the keyboard, with
 * keys edge to edge. A tapped modifier stays held until the next key, which fires with it.
 */
final class PcKeysView extends View {
    interface Listener {
        /** Press these keys together (modifiers first). */
        void onPcKeys(int... codes);
        void onPcKeysLetters();
        void onPcKeysClose();
    }

    private static final int NORMAL = 0, FN = 1, SHORTCUT = 2, MODIFIER = 3, ESCAPE = 4,
            LETTERS = 5, CLOSE = 6;
    private static final long REPEAT_DELAY_MS = 400, REPEAT_EVERY_MS = 55;

    private static final int KEY_BG = Color.rgb(0x24, 0x26, 0x2d);
    private static final int MOD_BG = Color.rgb(0x2c, 0x30, 0x39);
    private static final int SHORTCUT_BG = Color.rgb(0x1d, 0x2a, 0x36);
    private static final int ESC_BG = Color.rgb(0x3a, 0x2a, 0x2e);
    private static final int HELD_BG = Color.rgb(0x24, 0x50, 0x6e);
    private static final int PRESSED = Color.rgb(0x78, 0xc8, 0xff);
    private static final int STRIP = Color.rgb(0x11, 0x13, 0x17);
    private static final int CLOSE_BG = Color.rgb(0x4a, 0x22, 0x28);

    private static final class Key {
        final String label, sub;
        final int type;
        final int[] codes;
        final float weight;
        final boolean repeats;
        final RectF rect = new RectF();

        Key(String label, String sub, int type, float weight, boolean repeats, int... codes) {
            this.label = label;
            this.sub = sub;
            this.type = type;
            this.weight = weight;
            this.repeats = repeats;
            this.codes = codes;
        }
    }

    private final List<List<Key>> rows = new ArrayList<>();
    private final Key letters = new Key("⌨️ ABC", null, LETTERS, 0, false);
    private final Key close = new Key("✕ Close", null, CLOSE, 0, false);
    private final RectF stripRect = new RectF();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SparseArray<Key> down = new SparseArray<>();
    /** Modifiers tapped and waiting for the next key, in the order they were tapped. */
    private final List<Key> held = new ArrayList<>();
    private Listener listener;
    private Key repeating;
    private float rowHeight;

    private final Runnable repeat = new Runnable() {
        @Override public void run() {
            if (repeating == null) return;
            fire(repeating);
            handler.postDelayed(this, REPEAT_EVERY_MS);
        }
    };

    PcKeysView(Context context) {
        super(context);
        text.setTextAlign(Paint.Align.CENTER);

        List<Key> r1 = new ArrayList<>(), r2 = new ArrayList<>();
        r1.add(new Key("Esc", null, ESCAPE, 1, false, KEY_ESC));
        r2.add(new Key("Tab", null, NORMAL, 1, false, KEY_TAB));
        for (int i = 0; i < 12; i++) {
            (i < 6 ? r1 : r2).add(new Key("F" + (i + 1), null, FN, 1, false, KEY_F[i]));
        }
        rows.add(r1);
        rows.add(r2);
        rows.add(row(
                new Key("Alt+Tab", "switch app", SHORTCUT, 1, false, KEY_LEFTALT, KEY_TAB),
                new Key("Win+Tab", "task view", SHORTCUT, 1, false, KEY_LEFTMETA, KEY_TAB),
                new Key("Win+D", "desktop", SHORTCUT, 1, false, KEY_LEFTMETA, KEY_D),
                new Key("Alt+F4", "close app", SHORTCUT, 1, false, KEY_LEFTALT, KEY_F4),
                new Key("Ctrl+Shift+Esc", "task manager", SHORTCUT, 1.45f, false, KEY_LCTRL, KEY_LSHIFT, KEY_ESC),
                new Key("PrtSc", "screenshot", SHORTCUT, 1, false, KEY_SYSRQ)));
        rows.add(row(
                new Key("Home", null, NORMAL, 1, false, KEY_HOME),
                new Key("End", null, NORMAL, 1, false, KEY_END),
                new Key("PgUp", null, NORMAL, 1, true, KEY_PAGEUP),
                new Key("PgDn", null, NORMAL, 1, true, KEY_PAGEDOWN),
                new Key("Del", null, NORMAL, 1, true, KEY_DELETE),
                new Key("↑", null, NORMAL, 1, true, KEY_UP),
                new Key("⌫", null, NORMAL, 1, true, KEY_BKSP)));
        rows.add(row(
                new Key("Ctrl", null, MODIFIER, 1, false, KEY_LCTRL),
                new Key("Alt", null, MODIFIER, 1, false, KEY_LEFTALT),
                new Key("Shift", null, MODIFIER, 1, false, KEY_LSHIFT),
                new Key("⊞ Win", null, MODIFIER, 1, false, KEY_LEFTMETA),
                new Key("←", null, NORMAL, 1, true, KEY_LEFT),
                new Key("↓", null, NORMAL, 1, true, KEY_DOWN),
                new Key("→", null, NORMAL, 1, true, KEY_RIGHT)));
    }

    private static List<Key> row(Key... keys) {
        List<Key> out = new ArrayList<>();
        for (Key k : keys) out.add(k);
        return out;
    }

    void setListener(Listener listener) { this.listener = listener; }

    /** Lets go of held modifiers and touches, ready for next time. */
    void reset() {
        held.clear();
        down.clear();
        repeating = null;
        handler.removeCallbacks(repeat);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float stripH = h * 100f / 1080f;
        float closeW = w * 220f / 1240f, lettersW = w * 250f / 1240f;
        stripRect.set(0, 0, w - closeW - lettersW, stripH);
        letters.rect.set(stripRect.right, 0, w - closeW, stripH);
        close.rect.set(w - closeW, 0, w, stripH);
        rowHeight = (h - stripH) / rows.size();
        for (int r = 0; r < rows.size(); r++) {
            float total = 0;
            for (Key k : rows.get(r)) total += k.weight;
            float x = 0, top = stripH + r * rowHeight;
            for (Key k : rows.get(r)) {
                float kw = w * k.weight / total;
                k.rect.set(x, top, x + kw, top + rowHeight);
                x += kw;
            }
        }
    }

    // ---- Drawing --------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        float hair = Math.max(1f, getResources().getDisplayMetrics().density * 0.8f);
        fill.setColor(STRIP);
        canvas.drawRect(stripRect.left, stripRect.top, stripRect.right - hair, stripRect.bottom - hair, fill);
        drawStrip(canvas);
        drawKey(canvas, letters, hair);
        drawKey(canvas, close, hair);
        for (List<Key> row : rows) for (Key k : row) drawKey(canvas, k, hair);
    }

    private void drawStrip(Canvas canvas) {
        float h = stripRect.height();
        float x = h * 0.3f, y = stripRect.centerY();
        text.setTextAlign(Paint.Align.LEFT);
        text.setFakeBoldText(false);
        text.setTextSize(h * 0.28f);
        text.setColor(Color.rgb(0x6e, 0x73, 0x7d));
        String hint = Lang.t("PC keys");
        canvas.drawText(hint, x, baseline(y), text);
        x += text.measureText(hint) + h * 0.22f;
        if (held.isEmpty()) {
            text.setTextSize(h * 0.3f);
            canvas.drawText(Lang.t("Ctrl, Alt, Shift and Win stay held for the next key"), x, baseline(y), text);
        } else {
            StringBuilder combo = new StringBuilder();
            for (Key k : held) combo.append(k.label.replace("⊞ ", "")).append(" + ");
            text.setTextSize(h * 0.4f);
            text.setFakeBoldText(true);
            text.setColor(PRESSED);
            canvas.drawText(combo.toString(), x, baseline(y), text);
            x += text.measureText(combo.toString()) + h * 0.15f;
            text.setFakeBoldText(false);
            text.setTextSize(h * 0.28f);
            text.setColor(Color.rgb(0x8a, 0x90, 0x9b));
            canvas.drawText(Lang.t("tap a key to finish the combo"), x, baseline(y), text);
        }
        text.setTextAlign(Paint.Align.CENTER);
    }

    private float baseline(float centerY) {
        return centerY - (text.descent() + text.ascent()) / 2f;
    }

    private boolean isDown(Key k) {
        for (int i = 0; i < down.size(); i++) if (down.valueAt(i) == k) return true;
        return false;
    }

    private void drawKey(Canvas canvas, Key k, float hair) {
        int bg, fg, subFg = Color.rgb(0x8a, 0x90, 0x9b);
        float size = rowHeight * 0.24f;
        text.setFakeBoldText(false);
        switch (k.type) {
            case CLOSE:
                bg = CLOSE_BG;
                fg = Color.rgb(0xff, 0xb3, 0xba);
                size = stripRect.height() * 0.36f;
                text.setFakeBoldText(true);
                break;
            case LETTERS:
                bg = MOD_BG;
                fg = Color.rgb(0xcf, 0xd6, 0xe2);
                size = stripRect.height() * 0.34f;
                text.setFakeBoldText(true);
                break;
            case ESCAPE:
                bg = ESC_BG;
                fg = Color.rgb(0xff, 0xc7, 0xcd);
                break;
            case SHORTCUT:
                bg = SHORTCUT_BG;
                fg = Color.rgb(0xbf, 0xe6, 0xff);
                subFg = Color.rgb(0x7f, 0xa3, 0xbf);
                size = rowHeight * (k.label.length() > 10 ? 0.16f : 0.19f);
                break;
            case MODIFIER:
                boolean on = held.contains(k);
                bg = on ? HELD_BG : MOD_BG;
                fg = on ? Color.WHITE : Color.rgb(0xcf, 0xd6, 0xe2);
                subFg = Color.rgb(0xbf, 0xe6, 0xff);
                size = rowHeight * 0.22f;
                break;
            case FN:
                bg = KEY_BG;
                fg = Color.rgb(0xe6, 0xe9, 0xef);
                size = rowHeight * 0.22f;
                break;
            default:
                bg = KEY_BG;
                fg = Color.WHITE;
                if (k.label.length() == 1) size = rowHeight * 0.3f;
        }
        if (isDown(k)) {
            bg = PRESSED;
            fg = subFg = Color.BLACK;
        }
        fill.setColor(bg);
        canvas.drawRect(k.rect.left, k.rect.top, k.rect.right - hair, k.rect.bottom - hair, fill);
        text.setColor(fg);
        text.setTextSize(size);
        String sub = Lang.t(k.type == MODIFIER && held.contains(k) ? "held" : k.sub);
        if (sub == null) {
            canvas.drawText(Lang.t(k.label), k.rect.centerX(), baseline(k.rect.centerY()), text);
        } else {
            canvas.drawText(Lang.t(k.label), k.rect.centerX(), baseline(k.rect.centerY() - rowHeight * 0.09f), text);
            text.setColor(subFg);
            text.setTextSize(rowHeight * 0.12f);
            canvas.drawText(sub, k.rect.centerX(), baseline(k.rect.centerY() + rowHeight * 0.14f), text);
        }
    }

    // ---- Touch ----------------------------------------------------------------------------

    private Key keyAt(float x, float y) {
        if (close.rect.contains(x, y)) return close;
        if (letters.rect.contains(x, y)) return letters;
        if (y < stripRect.bottom) return null;
        Key best = null;
        float bestD = Float.MAX_VALUE;
        for (List<Key> row : rows) {
            for (Key k : row) {
                if (k.rect.contains(x, y)) return k;
                float dx = Math.max(Math.max(k.rect.left - x, 0), x - k.rect.right);
                float dy = Math.max(Math.max(k.rect.top - y, 0), y - k.rect.bottom);
                float d = dx * dx + dy * dy;
                if (d < bestD) {
                    bestD = d;
                    best = k; // edges snap to the nearest key
                }
            }
        }
        return best;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int idx = ev.getActionIndex();
        int id = ev.getPointerId(idx);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) requestUnbufferedDispatch(ev);
                Key k = keyAt(ev.getX(idx), ev.getY(idx));
                down.put(id, k);
                if (k != null) {
                    Haptics.play(getContext(), Haptics.KEY);
                    if (k.type != CLOSE && k.type != LETTERS) fire(k);
                    if (k.repeats && held.isEmpty()) {
                        repeating = k;
                        handler.removeCallbacks(repeat);
                        handler.postDelayed(repeat, REPEAT_DELAY_MS);
                    }
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                Key k = down.get(id);
                down.remove(id);
                if (k == repeating) {
                    repeating = null;
                    handler.removeCallbacks(repeat);
                }
                if (k != null && listener != null) {
                    if (k.type == CLOSE) listener.onPcKeysClose();
                    else if (k.type == LETTERS) listener.onPcKeysLetters();
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                down.clear();
                repeating = null;
                handler.removeCallbacks(repeat);
                invalidate();
                return true;
        }
        return true;
    }

    private void fire(Key k) {
        if (k.type == MODIFIER) {
            // Tap to hold for the next key; tap again to let go.
            if (!held.remove(k)) held.add(k);
            return;
        }
        int[] codes = k.codes;
        if (!held.isEmpty()) {
            int[] withMods = new int[held.size() + codes.length];
            for (int i = 0; i < held.size(); i++) withMods[i] = held.get(i).codes[0];
            System.arraycopy(codes, 0, withMods, held.size(), codes.length);
            codes = withMods;
            held.clear();
        }
        if (listener != null) listener.onPcKeys(codes);
    }
}
