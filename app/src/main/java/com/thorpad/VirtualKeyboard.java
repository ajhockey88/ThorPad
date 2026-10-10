package com.thorpad;

/**
 * A virtual USB keyboard (US layout), plugged in only while the on-screen keyboard is open
 * so Android keeps showing its own touch keyboard on the top screen the rest of the time.
 */
final class VirtualKeyboard extends UinputDevice implements KeyOutput {
    private static final int KEY_LEFTSHIFT = 42;

    /** Linux key code for each printable ASCII character; negative = needs Shift. */
    private static final int[] ASCII = new int[128];

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
        int[] codes = new int[128];
        int n = 0;
        codes[n++] = KEY_LEFTSHIFT;
        for (int code : new int[] {28, 14, 15, 1, 105, 106, 103, 108}) codes[n++] = code;
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
        int code = specialCode(key);
        if (code != 0) press(code);
    }

    /** Down and up as two reports, queued in order so quick repeats of a key never merge. */
    private void press(int code) {
        send(EV_KEY, code, 1);
        send(EV_KEY, code, 0);
    }
}
