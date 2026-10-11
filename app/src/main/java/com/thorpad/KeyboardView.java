package com.thorpad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * A full-screen keyboard for the bottom screen. Keys touch edge to edge with only a hairline
 * between them, so the whole screen is target area.
 *
 * For responsiveness a key types the moment a finger lands on it, every finger is tracked on
 * its own (pressing the next key before lifting the last still counts), touch events are read
 * unbatched, and each press flashes and buzzes so you can tell it registered. An optional
 * "type on release" setting instead types when the finger lifts, so sliding onto the right
 * key fixes a near miss. Backspace repeats while held, and while it is held the space bar
 * turns into a red "Clear all" key.
 */
final class KeyboardView extends View {
    interface Listener { void onCloseKeyboard(); }

    private static final int CHAR = 0, SHIFT = 1, BACKSPACE = 2, ENTER = 3, SPACE = 4,
            PAGE = 5, SPECIAL = 6, CLOSE = 7;
    private static final int LETTERS = 0, SYMBOLS = 1, MORE = 2;
    private static final int STYLE_KEY = 0, STYLE_MOD = 1, STYLE_ENTER = 2;

    private static final long REPEAT_DELAY_MS = 400, REPEAT_EVERY_MS = 55;
    private static final long CAPS_LOCK_DOUBLE_TAP_MS = 350;
    private static final long FLASH_MS = 90;
    private static final long DOUBLE_SPACE_MS = 1500;

    private static final int BG = Color.BLACK;
    private static final int KEY = Color.rgb(0x24, 0x26, 0x2d);
    private static final int MOD = Color.rgb(0x2c, 0x30, 0x39);
    private static final int ENTER_BG = Color.rgb(0x1f, 0x4d, 0x6b);
    private static final int PRESSED = Color.rgb(0x78, 0xc8, 0xff);
    private static final int LOCKED = Color.rgb(0x24, 0x50, 0x6e);
    private static final int STRIP = Color.rgb(0x11, 0x13, 0x17);
    private static final int CLOSE_BG = Color.rgb(0x4a, 0x22, 0x28);

    private static final class Key {
        final String label;
        final int type;
        final char ch;
        final int value; // page number or KeyOutput special key
        final float weight;
        final int style;
        final RectF rect = new RectF();

        Key(String label, int type, char ch, int value, float weight, int style) {
            this.label = label;
            this.type = type;
            this.ch = ch;
            this.value = value;
            this.weight = weight;
            this.style = style;
        }
    }

    private final List<List<List<Key>>> pages = new ArrayList<>();
    private final Key closeKey = new Key("✕ Close", CLOSE, (char) 0, 0, 0, STYLE_KEY);
    private final RectF stripRect = new RectF();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SparseArray<Key> down = new SparseArray<>();
    private final StringBuilder typed = new StringBuilder();

    private int page = LETTERS;
    private int shift; // 0 off, 1 next letter only, 2 caps lock
    private long lastShiftTap;
    private float rowHeight;
    private Key repeating;
    private KeyOutput output;
    private Listener listener;
    private boolean typeOnRelease;
    private boolean doubleSpacePeriod = true, autoCaps = true;
    /** Shift is on only because a sentence is starting, not because it was pressed. */
    private boolean autoShift;
    private long lastSpaceAt;
    private Key flash;

    // Autocorrect state. The suggestion for the word being typed is worked out in the
    // background and shown in the strip; tapping the strip keeps the word as typed.
    private Autocorrect autocorrect;
    private HandlerThread acThread;
    private Handler acHandler;
    private String suggestion, suggestionFor;
    private final RectF pillRect = new RectF();
    /** The last correction made, so an immediate backspace can undo it. */
    private String fixOriginal, fixApplied, fixBoundary;

    private final Runnable endFlash = new Runnable() {
        @Override public void run() {
            flash = null;
            invalidate();
        }
    };

    private final Runnable repeat = new Runnable() {
        @Override public void run() {
            if (repeating == null) return;
            fire(repeating);
            handler.postDelayed(this, REPEAT_EVERY_MS);
        }
    };

    KeyboardView(Context context) {
        super(context);
        setHapticFeedbackEnabled(true);
        text.setTextAlign(Paint.Align.CENTER);

        pages.add(rows(
                chars("qwertyuiop"),
                chars("asdfghjkl"),
                join(mod("⇧", SHIFT, 0), chars("zxcvbnm"), mod("⌫", BACKSPACE, KeyOutput.BACKSPACE)),
                bottomRow("?123", SYMBOLS)));
        pages.add(rows(
                chars("1234567890"),
                chars("@#$%&-+()"),
                join(mod("=\\<", PAGE, MORE), chars("*\"':;!?"), mod("⌫", BACKSPACE, KeyOutput.BACKSPACE)),
                bottomRow("ABC", LETTERS)));
        pages.add(rows(
                chars("~`|\\{}[]<>"),
                join(special("Esc", KeyOutput.ESCAPE), special("Tab", KeyOutput.TAB), chars("=_^/"),
                        special("←", KeyOutput.LEFT), special("↑", KeyOutput.UP),
                        special("→", KeyOutput.RIGHT)),
                join(mod("?123", PAGE, SYMBOLS), chars("'\":;!?"), special("↓", KeyOutput.DOWN),
                        mod("⌫", BACKSPACE, KeyOutput.BACKSPACE)),
                bottomRow("ABC", LETTERS)));
    }

    void setOutput(KeyOutput output) {
        this.output = output;
        if (output != null) autoCapitalize();
    }

    void setListener(Listener listener) { this.listener = listener; }

    void setTypeOnRelease(boolean value) { typeOnRelease = value; }

    /** Two spaces type ". ", and sentences start with a capital. */
    void setSentenceHelp(boolean doubleSpace, boolean caps) {
        doubleSpacePeriod = doubleSpace;
        autoCaps = caps;
    }

    /** null turns autocorrect off. */
    void setAutocorrect(Autocorrect autocorrect) {
        this.autocorrect = autocorrect;
        if (autocorrect != null && acThread == null) {
            acThread = new HandlerThread("ThorPadAutocorrect");
            acThread.start();
            acHandler = new Handler(acThread.getLooper());
        } else if (autocorrect == null && acThread != null) {
            acThread.quitSafely();
            acThread = null;
            acHandler = null;
        }
        clearSuggestion();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (acThread != null) {
            acThread.quitSafely();
            acThread = null;
            acHandler = null;
        }
    }

    /** Clears the preview and returns to letters, ready for the next time it opens. */
    void reset() {
        typed.setLength(0);
        clearSuggestion();
        fixOriginal = null;
        page = LETTERS;
        shift = 0;
        autoShift = false;
        lastSpaceAt = 0;
        cancelTouches();
        invalidate();
    }

    // ---- Layout definitions ---------------------------------------------------------------

    @SafeVarargs
    private static List<List<Key>> rows(List<Key>... rows) {
        List<List<Key>> out = new ArrayList<>();
        for (List<Key> r : rows) out.add(r);
        return out;
    }

    private static List<Key> chars(String s) {
        List<Key> out = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            out.add(new Key(String.valueOf(c), CHAR, c, 0, 1f, STYLE_KEY));
        }
        return out;
    }

    private static List<Key> mod(String label, int type, int value) {
        List<Key> out = new ArrayList<>();
        out.add(new Key(label, type, (char) 0, value, 1.45f, STYLE_MOD));
        return out;
    }

    private static List<Key> special(String label, int key) {
        List<Key> out = new ArrayList<>();
        out.add(new Key(label, SPECIAL, (char) 0, key, 1f, STYLE_MOD));
        return out;
    }

    @SafeVarargs
    private static List<Key> join(List<Key>... parts) {
        List<Key> out = new ArrayList<>();
        for (List<Key> p : parts) out.addAll(p);
        return out;
    }

    private static List<Key> bottomRow(String pageLabel, int target) {
        return join(mod(pageLabel, PAGE, target),
                chars(","),
                java.util.Collections.singletonList(new Key("space", SPACE, ' ', 0, 4f, STYLE_KEY)),
                chars("."),
                java.util.Collections.singletonList(
                        new Key("⏎ Enter", ENTER, (char) 0, KeyOutput.ENTER, 1.6f, STYLE_ENTER)));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float stripH = h * 100f / 1080f;
        float closeW = w * 260f / 1240f;
        stripRect.set(0, 0, w - closeW, stripH);
        closeKey.rect.set(w - closeW, 0, w, stripH);
        rowHeight = (h - stripH) / 4f;
        for (List<List<Key>> rows : pages) {
            for (int r = 0; r < rows.size(); r++) {
                List<Key> row = rows.get(r);
                float total = 0;
                for (Key k : row) total += k.weight;
                float x = 0, top = stripH + r * rowHeight;
                for (Key k : row) {
                    float kw = w * k.weight / total;
                    k.rect.set(x, top, x + kw, top + rowHeight);
                    x += kw;
                }
            }
        }
    }

    // ---- Drawing --------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(BG);
        float hair = Math.max(1f, getResources().getDisplayMetrics().density * 0.8f);

        fill.setColor(STRIP);
        canvas.drawRect(stripRect.left, stripRect.top, stripRect.right - hair, stripRect.bottom - hair, fill);
        drawPreview(canvas);
        drawKey(canvas, closeKey, hair);

        for (List<Key> row : pages.get(page)) {
            for (Key k : row) drawKey(canvas, k, hair);
        }
    }

    private boolean isDown(Key k) {
        if (k == flash) return true;
        for (int i = 0; i < down.size(); i++) if (down.valueAt(i) == k) return true;
        return false;
    }

    private void drawKey(Canvas canvas, Key k, float hair) {
        boolean pressed = isDown(k);
        int bg, fg;
        float size;
        if (k.type == CLOSE) {
            bg = CLOSE_BG;
            fg = Color.rgb(0xff, 0xb3, 0xba);
            size = stripRect.height() * 0.38f;
            text.setFakeBoldText(true);
        } else if (k.style == STYLE_ENTER) {
            bg = ENTER_BG;
            fg = Color.rgb(0xbf, 0xe6, 0xff);
            size = rowHeight * 0.18f;
            text.setFakeBoldText(false);
        } else if (k.style == STYLE_MOD) {
            bg = (k.type == SHIFT && shift == 2) ? LOCKED : MOD;
            fg = (k.type == SHIFT && shift > 0) ? Color.WHITE : Color.rgb(0xcf, 0xd6, 0xe2);
            size = k.label.length() > 2 ? rowHeight * 0.18f : rowHeight * 0.24f;
            text.setFakeBoldText(k.type == SHIFT && shift > 0);
        } else if (k.type == SPACE && repeating != null) {
            // While backspace is held, the space bar becomes "Clear all".
            bg = Color.rgb(0x3a, 0x1f, 0x23);
            fg = Color.rgb(0xff, 0x9a, 0x9a);
            size = rowHeight * 0.17f;
            text.setFakeBoldText(true);
        } else if (k.type == SPACE) {
            bg = KEY;
            fg = Color.rgb(0x8a, 0x90, 0x9b);
            size = rowHeight * 0.13f;
            text.setFakeBoldText(false);
        } else {
            bg = KEY;
            fg = Color.WHITE;
            size = rowHeight * 0.29f;
            text.setFakeBoldText(false);
        }
        if (pressed) {
            bg = PRESSED;
            fg = Color.BLACK;
        }
        fill.setColor(bg);
        canvas.drawRect(k.rect.left, k.rect.top, k.rect.right - hair, k.rect.bottom - hair, fill);
        text.setColor(fg);
        text.setTextSize(size);
        String label = k.type == SPACE && repeating != null ? "Clear all" : k.label;
        if (k.type == CHAR && shift > 0 && page == LETTERS) label = label.toUpperCase();
        float cy = k.rect.centerY() - (text.descent() + text.ascent()) / 2f;
        canvas.drawText(label, k.rect.centerX(), cy, text);
    }

    private void drawPreview(Canvas canvas) {
        float h = stripRect.height();
        float x = h * 0.3f;
        float cy = stripRect.centerY();
        text.setTextAlign(Paint.Align.LEFT);
        text.setFakeBoldText(false);

        text.setTextSize(h * 0.28f);
        text.setColor(Color.rgb(0x6e, 0x73, 0x7d));
        String hint = output == null ? "Not connected to the top screen" : "Typing to top screen";
        canvas.drawText(hint, x, cy - (text.descent() + text.ascent()) / 2f, text);
        x += text.measureText(hint) + h * 0.22f;

        float right = stripRect.right - h * 0.3f;
        pillRect.setEmpty();
        if (suggestion != null) {
            // "→ fix ✕" pill: autocorrect will use this at the next space; tap to keep yours.
            text.setTextSize(h * 0.36f);
            text.setFakeBoldText(true);
            String pill = "\u2192 " + suggestion + "  \u2715";
            float pw = text.measureText(pill) + h * 0.5f;
            pillRect.set(stripRect.right - h * 0.12f - pw, h * 0.14f, stripRect.right - h * 0.12f, h * 0.86f);
            fill.setColor(MOD);
            canvas.drawRoundRect(pillRect, h * 0.2f, h * 0.2f, fill);
            text.setColor(PRESSED);
            canvas.drawText(pill, pillRect.left + h * 0.25f, cy - (text.descent() + text.ascent()) / 2f, text);
            text.setFakeBoldText(false);
            right = pillRect.left - h * 0.2f;
        }

        text.setTextSize(h * 0.46f);
        text.setColor(Color.rgb(0xe8, 0xe8, 0xe8));
        float room = right - x;
        String shown = typed.toString().replace('\n', '↵').replace('\t', '⇥');
        // Keep as much of the end as fits, measured in one pass.
        int fits = text.breakText(shown, false, Math.max(0, room), null);
        if (fits < shown.length()) shown = shown.substring(shown.length() - fits);
        canvas.drawText(shown, x, cy - (text.descent() + text.ascent()) / 2f, text);
        float caretX = x + text.measureText(shown) + 3;
        fill.setColor(PRESSED);
        canvas.drawRect(caretX, cy - h * 0.26f, caretX + Math.max(3f, h * 0.04f), cy + h * 0.26f, fill);
        text.setTextAlign(Paint.Align.CENTER);
    }

    // ---- Touch ----------------------------------------------------------------------------

    private Key keyAt(float x, float y) {
        if (closeKey.rect.contains(x, y)) return closeKey;
        if (y < closeKey.rect.bottom) return null; // the preview strip isn't a key
        for (List<Key> row : pages.get(page)) {
            for (Key k : row) {
                if (k.rect.contains(x, y)) return k;
            }
        }
        // Edges: snap to the nearest key so the very border still counts.
        Key best = null;
        float bestD = Float.MAX_VALUE;
        for (List<Key> row : pages.get(page)) {
            for (Key k : row) {
                float dx = Math.max(Math.max(k.rect.left - x, 0), x - k.rect.right);
                float dy = Math.max(Math.max(k.rect.top - y, 0), y - k.rect.bottom);
                float d = dx * dx + dy * dy;
                if (d < bestD) {
                    bestD = d;
                    best = k;
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
                if (k == null && suggestion != null && ev.getY(idx) < stripRect.bottom) {
                    rejectSuggestion();
                    Haptics.play(getContext(), Haptics.KEY);
                }
                if (repeating != null && k != null && k.type == SPACE) {
                    // While backspace is held the space bar reads "Clear all": tapping it
                    // wipes the text, and the held backspace stops repeating.
                    down.put(id, null);
                    clearAll();
                    invalidate();
                    return true;
                }
                if (k != null) {
                    Haptics.play(getContext(), Haptics.KEY);
                    flashKey(k);
                    if (k.type == BACKSPACE) {
                        // Delete right away, then keep deleting while held.
                        fire(k);
                        repeating = k;
                        handler.removeCallbacks(repeat);
                        handler.postDelayed(repeat, REPEAT_DELAY_MS);
                    } else if (!typeOnRelease && k.type != CLOSE) {
                        fire(k);
                    }
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < ev.getPointerCount(); i++) {
                    int pid = ev.getPointerId(i);
                    Key was = down.get(pid);
                    if (!typeOnRelease || (was != null && was.type == BACKSPACE)) continue; // already typed
                    Key now = keyAt(ev.getX(i), ev.getY(i));
                    if (now != was) {
                        down.put(pid, now);
                        invalidate();
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                Key k = down.get(id);
                down.remove(id);
                if (k != null) {
                    if (k.type == BACKSPACE) {
                        if (repeating == k) {
                            repeating = null;
                            handler.removeCallbacks(repeat);
                        }
                    } else if (typeOnRelease || k.type == CLOSE) {
                        fire(k);
                    }
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                cancelTouches();
                invalidate();
                return true;
        }
        return true;
    }

    // ---- Autocorrect ----------------------------------------------------------------------

    private static boolean isBoundary(char c) {
        return c == ' ' || c == '.' || c == ',' || c == '!' || c == '?' || c == ';' || c == ':';
    }

    private static boolean isWordChar(char c) {
        return Character.isLetter(c) || c == '\'';
    }

    /** The word just before the cursor, as far as ThorPad has seen it typed. */
    private String currentWord() {
        int i = typed.length();
        while (i > 0 && isWordChar(typed.charAt(i - 1))) i--;
        String w = typed.substring(i);
        while (w.startsWith("'")) w = w.substring(1);
        return w;
    }

    /** False in password, email and web address boxes: type exactly what's pressed there. */
    private boolean correctionsAllowed() {
        return !(output instanceof TextContext) || ((TextContext) output).allowsCorrections();
    }

    /**
     * The word before the one being typed, lowercase, if nothing but spaces sits between them;
     * null at the start of a sentence or after other punctuation. Reads the text box itself when
     * the keyboard opened partway through a sentence.
     */
    private String previousWord() {
        String w = previousWord(typed, false);
        if (w != null) return w.isEmpty() ? null : w;
        if (output instanceof TextContext) {
            CharSequence before = ((TextContext) output).textBefore(80);
            if (before != null) {
                w = previousWord(before, true);
                return w == null || w.isEmpty() ? null : w;
            }
        }
        return null;
    }

    /** "" for "no word before", null for "ran out of text before finding out". */
    private static String previousWord(CharSequence t, boolean whole) {
        int i = t.length();
        while (i > 0 && isWordChar(t.charAt(i - 1))) i--; // the word being typed
        int spaces = i;
        while (i > 0 && t.charAt(i - 1) == ' ') i--;
        if (i == 0) return whole ? "" : null;
        if (i == spaces && spaces != t.length()) return ""; // no space before the current word
        int end = i;
        while (i > 0 && isWordChar(t.charAt(i - 1))) i--;
        if (end == i) return ""; // punctuation before it
        if (i == 0 && !whole) return null; // the word may go on before what ThorPad saw
        return t.subSequence(i, end).toString().toLowerCase(java.util.Locale.US);
    }

    /**
     * Turns shift on by itself where a sentence starts (as the text box asks), and back off if
     * it was only on for that and the sentence start was deleted.
     */
    private void autoCapitalize() {
        if (!autoCaps || page != LETTERS || shift == 2 || !(output instanceof TextContext)) return;
        int n = typed.length();
        char last = n > 0 ? typed.charAt(n - 1) : '\n';
        boolean cap = false;
        if (last == ' ' || last == '\n') {
            TextContext ctx = (TextContext) output;
            // An Android text box knows best; without one (a game), go by what was typed here.
            cap = ctx.textBefore(1) != null ? ctx.capitalizeNext() : n > 0 && sentenceEnded(typed);
        }
        if (cap && shift == 0) {
            shift = 1;
            autoShift = true;
        } else if (!cap && shift == 1 && autoShift) {
            shift = 0;
            autoShift = false;
        }
        invalidate();
    }

    /** Whether the text ends with a sentence's end (". ", "!\n"...) and then only spaces. */
    private static boolean sentenceEnded(CharSequence t) {
        int i = t.length();
        while (i > 0 && t.charAt(i - 1) == ' ') i--;
        if (i < t.length() && i > 0) {
            char c = t.charAt(i - 1);
            return c == '.' || c == '!' || c == '?';
        }
        return i > 0 && t.charAt(i - 1) == '\n';
    }

    private void typeText(String s) {
        typed.append(s);
        if (typed.length() > 200) typed.delete(0, typed.length() - 200);
        if (output != null) {
            for (int i = 0; i < s.length(); i++) output.typeChar(s.charAt(i));
        }
    }

    private void deleteChars(int n) {
        for (int i = 0; i < n; i++) {
            if (typed.length() > 0) typed.setLength(typed.length() - 1);
            if (output != null) output.special(KeyOutput.BACKSPACE);
        }
    }

    /** At the end of a word, swaps it for its correction. boundary is null for Enter. */
    private void autocorrectWord(String boundary) {
        String word = currentWord();
        String fix = null;
        if (autocorrect != null && !word.isEmpty() && correctionsAllowed()) {
            fix = word.equals(suggestionFor) ? suggestion : autocorrect.correct(previousWord(), word);
        }
        clearSuggestion();
        if (fix == null || fix.equals(word)) return;
        deleteChars(word.length());
        typeText(fix);
        if (boundary != null) {
            fixOriginal = word;
            fixApplied = fix;
            fixBoundary = boundary;
        }
    }

    /** Backspace right after a correction puts back what was typed, and learns it. */
    private boolean undoCorrection() {
        String tail = fixApplied + fixBoundary;
        String original = fixOriginal;
        fixOriginal = null;
        if (typed.length() < tail.length() || !typed.toString().endsWith(tail)) return false;
        deleteChars(tail.length());
        typeText(original);
        if (autocorrect != null) autocorrect.learn(original);
        clearSuggestion();
        return true;
    }

    private void rejectSuggestion() {
        if (autocorrect != null && suggestionFor != null) autocorrect.learn(suggestionFor);
        clearSuggestion();
    }

    private void clearSuggestion() {
        suggestion = null;
        suggestionFor = null;
        invalidate();
    }

    /** Works out, off the main thread, what the word being typed would be corrected to. */
    private void updateSuggestion() {
        final String word = currentWord();
        if (autocorrect == null || acHandler == null || word.isEmpty() || !correctionsAllowed()) {
            clearSuggestion();
            return;
        }
        final String prev = previousWord();
        if (suggestion != null && !word.equals(suggestionFor)) {
            suggestion = null;
            suggestionFor = null;
            invalidate();
        }
        final Autocorrect ac = autocorrect;
        acHandler.removeCallbacksAndMessages(null);
        acHandler.post(new Runnable() {
            @Override public void run() {
                final String fix = ac.correct(prev, word);
                handler.post(new Runnable() {
                    @Override public void run() {
                        if (!word.equals(currentWord())) return;
                        suggestionFor = word;
                        suggestion = fix != null && !fix.equals(word) ? fix : null;
                        invalidate();
                    }
                });
            }
        });
    }

    /** Lights a key briefly so even the quickest tap visibly registers. */
    private void flashKey(Key k) {
        flash = k;
        handler.removeCallbacks(endFlash);
        handler.postDelayed(endFlash, FLASH_MS);
    }

    private void clearAll() {
        flashKey(repeating);
        repeating = null;
        handler.removeCallbacks(repeat);
        typed.setLength(0);
        fixOriginal = null;
        clearSuggestion();
        if (output != null) output.special(KeyOutput.CLEAR_ALL);
        Haptics.play(getContext(), Haptics.CLEAR_ALL);
    }

    private void cancelTouches() {
        down.clear();
        repeating = null;
        handler.removeCallbacks(repeat);
    }

    private boolean startsText() {
        return typed.length() == 0 && autoCaps && output instanceof TextContext;
    }

    /**
     * A second quick space right after a word types ". " in place of the first, like phones do,
     * and the next word starts with a capital.
     */
    /** Only a password box gets two plain spaces: everywhere else they're a period. */
    private boolean periodAllowed() {
        return !(output instanceof TextContext) || !((TextContext) output).isPassword();
    }

    private boolean doubleSpace() {
        int n = typed.length();
        if (!doubleSpacePeriod || n < 1 || typed.charAt(n - 1) != ' ') return false;
        long since = SystemClock.uptimeMillis() - lastSpaceAt;
        char before = n >= 2 ? typed.charAt(n - 2) : '?';
        if (since > DOUBLE_SPACE_MS || !Character.isLetterOrDigit(before) && before != ')'
                && before != '"' && before != '\'' || !periodAllowed()) {
            KeyboardLog.add("double space skipped: since=" + since + " before=" + (int) before
                    + " allowed=" + periodAllowed());
            return false;
        }
        deleteChars(1);
        typeText(". ");
        lastSpaceAt = 0;
        fixOriginal = null;
        clearSuggestion();
        autoCapitalize();
        return true;
    }

    private void fire(Key k) {
        boolean undoable = fixOriginal != null;
        if (k.type != BACKSPACE) fixOriginal = null; // only an immediate backspace undoes a fix
        switch (k.type) {
            case CLOSE:
                cancelTouches();
                if (listener != null) listener.onCloseKeyboard();
                return;
            case PAGE:
                page = k.value;
                shift = 0;
                autoShift = false;
                cancelTouches();
                if (page == LETTERS) autoCapitalize();
                break;
            case SHIFT: {
                autoShift = false;
                long now = SystemClock.uptimeMillis();
                if (shift == 0) shift = 1;
                else if (shift == 1 && now - lastShiftTap < CAPS_LOCK_DOUBLE_TAP_MS) shift = 2;
                else shift = 0;
                lastShiftTap = now;
                break;
            }
            case BACKSPACE:
                if (undoable && undoCorrection()) break;
                fixOriginal = null;
                if (typed.length() > 0) typed.setLength(typed.length() - 1);
                if (output != null) output.special(KeyOutput.BACKSPACE);
                lastSpaceAt = 0;
                updateSuggestion();
                autoCapitalize();
                break;
            case ENTER:
                autocorrectWord(null);
                typed.setLength(0);
                fixOriginal = null;
                if (output != null) output.special(KeyOutput.ENTER);
                lastSpaceAt = 0;
                autoCapitalize();
                break;
            case SPECIAL:
                if (k.value == KeyOutput.TAB) typed.append('\t');
                if (output != null) output.special(k.value);
                break;
            case SPACE:
                if (doubleSpace()) break;
                // fall through
            case CHAR: {
                char c = k.ch;
                if (k.type == CHAR && page == LETTERS && shift == 0 && Character.isLetter(c)
                        && startsText()) {
                    // The first letter in the box: the box may only now say it wants a capital.
                    autoCapitalize();
                }
                if (page == LETTERS && shift > 0) c = Character.toUpperCase(c);
                if (isBoundary(c)) autocorrectWord(String.valueOf(c));
                typeText(String.valueOf(c));
                if (shift == 1 && k.type == CHAR) {
                    shift = 0;
                    autoShift = false;
                }
                if (k.type == SPACE) {
                    lastSpaceAt = SystemClock.uptimeMillis();
                    autoCapitalize();
                } else {
                    lastSpaceAt = 0;
                    if (c == '.' || c == '!' || c == '?') autoShift = false;
                }
                updateSuggestion();
                break;
            }
        }
        invalidate();
    }
}
