package com.thorpad;

/**
 * A virtual USB keyboard (US layout), plugged in only while the on-screen keyboard is open
 * so Android keeps showing its own touch keyboard on the top screen the rest of the time.
 */
final class VirtualKeyboard extends UinputDevice implements KeyOutput {
    private static final int KEY_LEFTSHIFT = 42;
    private static final int KEY_LEFTCTRL = 29;
    private static final int KEY_A = 30;
    private static final int KEY_BACKSPACE = 14;
    /** Linux key codes for PC keys and shortcuts. */
    static final int KEY_ESC = 1, KEY_TAB = 15, KEY_D = 32, KEY_LEFTALT = 56, KEY_LEFTMETA = 125,
            KEY_LCTRL = 29, KEY_LSHIFT = 42, KEY_F4 = 62, KEY_SYSRQ = 99,
            KEY_HOME = 102, KEY_UP = 103, KEY_PAGEUP = 104, KEY_LEFT = 105, KEY_RIGHT = 106,
            KEY_END = 107, KEY_DOWN = 108, KEY_PAGEDOWN = 109, KEY_INSERT = 110, KEY_DELETE = 111,
            KEY_BKSP = 14;
    /** F1 to F12. */
    static final int[] KEY_F = {59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 87, 88};

    /** Linux key code for each printable ASCII character; negative = needs Shift. */
    private static final int[] ASCII = new int[160];

    static {
        String letters = "abcdefghijklmnopqrstuvwxyz";
        int[] letterCodes = {30, 48, 46, 32, 18, 33, 34, 35, 23, 36, 37, 38, 50,
                49, 24, 25, 16, 19, 31, 20, 22, 47, 17, 45, 21, 44};
        for (int i = 0; i < 26; i++) {
            ASCII[letters.charAt(i)] = letterCodes[i];
            ASCII[Character.toUpperCase(letters.charAt(i))] = -letterCodes[i];
        }
        String digits = "1234567890";
        String shifted = "!@#$%^&*()";
        for (int i = 0; i < 10; i++) {
            ASCII[digits.charAt(i)] = 2 + i;
            ASCII[shifted.charAt(i)] = -(2 + i);
        }
        put('-', '_', 12);
        put('=', '+', 13);
        put('[', '{', 26);
        put(']', '}', 27);
        put(';', ':', 39);
        put('\'', '"', 40);
        put('`', '~', 41);
        put('\\', '|', 43);
        put(',', '<', 51);
        put('.', '>', 52);
        put('/', '?', 53);
        ASCII[' '] = 57;
    }

    private static void put(char plain, char withShift, int code) {
        ASCII[plain] = code;
        ASCII[withShift] = -code;
    }

    private static int specialCode(int key) {
        switch (key) {
            case ENTER: return 28;
            case BACKSPACE: return 14;
            case TAB: return 15;
            case ESCAPE: return 1;
            case LEFT: return 105;
            case RIGHT: return 106;
            case UP: return 103;
            case DOWN: return 108;
            default: return 0;
        }
    }

    private static int[] allKeyCodes() {
        int[] codes = new int[160];
        int n = 0;
        codes[n++] = KEY_LEFTSHIFT;
        codes[n++] = KEY_LEFTCTRL;
        for (int code : new int[] {28, 14, 15, 1, 105, 106, 103, 108, KEY_LEFTALT, KEY_LEFTMETA,
                KEY_SYSRQ, KEY_HOME, KEY_PAGEUP, KEY_END, KEY_PAGEDOWN, KEY_INSERT, KEY_DELETE}) codes[n++] = code;
        for (int code : KEY_F) codes[n++] = code;
        boolean[] seen = new boolean[256];
        for (int v : ASCII) {
            int c = Math.abs(v);
            if (c != 0 && !seen[c]) {
                seen[c] = true;
                codes[n++] = c;
            }
        }
        int[] out = new int[n];
        System.arraycopy(codes, 0, out, 0, n);
        return out;
    }

    VirtualKeyboard(StateListener listener) {
        super("ThorPad Keyboard", 22138,
                bits(UI_SET_EVBIT, EV_KEY) + "," + bits(UI_SET_KEYBIT, allKeyCodes()),
                listener);
    }

    @Override public void typeChar(char c) {
        if (c >= 128 || ASCII[c] == 0) return;
        int v = ASCII[c];
        if (v < 0) {
            send(EV_KEY, KEY_LEFTSHIFT, 1, EV_KEY, -v, 1);
            send(EV_KEY, -v, 0, EV_KEY, KEY_LEFTSHIFT, 0);
        } else {
            press(v);
        }
    }

    @Override public void special(int key) {
        if (key == CLEAR_ALL) {
            // Select all, then delete the selection.
            send(EV_KEY, KEY_LEFTCTRL, 1, EV_KEY, KEY_A, 1);
            send(EV_KEY, KEY_A, 0, EV_KEY, KEY_LEFTCTRL, 0);
            press(KEY_BACKSPACE);
            return;
        }
        int code = specialCode(key);
        if (code != 0) press(code);
    }

    /** A shortcut: presses the keys in order (modifiers first), then lets go in reverse. */
    void combo(int... codes) {
        for (int c : codes) send(EV_KEY, c, 1);
        for (int i = codes.length - 1; i >= 0; i--) send(EV_KEY, codes[i], 0);
    }

    /** Down and up as two reports, queued in order so quick repeats of a key never merge. */
    private void press(int code) {
        send(EV_KEY, code, 1);
        send(EV_KEY, code, 0);
    }
}
