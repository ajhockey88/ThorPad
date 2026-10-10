package com.thorpad;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** The last keyboard open / close events, saved with Settings' diagnostics for bug reports. */
final class KeyboardLog {
    private static final int SIZE = 120;
    private static final String[] lines = new String[SIZE];
    private static int next;

    private KeyboardLog() {}

    static synchronized void add(String event) {
        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        lines[next] = time + "  " + event;
        next = (next + 1) % SIZE;
    }

    /** Writes the log to the app's folder on shared storage and returns the file. */
    static synchronized File save(Context context) throws Exception {
        File dir = context.getExternalFilesDir(null);
        File f = new File(dir, "thorpad-keyboard-log.txt");
        try (FileOutputStream out = new FileOutputStream(f)) {
            for (int i = 0; i < SIZE; i++) {
                String l = lines[(next + i) % SIZE];
                if (l != null) out.write((l + "\n").getBytes("UTF-8"));
            }
        }
        return f;
    }
}
