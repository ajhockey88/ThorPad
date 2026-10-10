package com.thorpad;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * iPhone-style autocorrect: when a word is finished, it is swapped for what was most likely
 * meant (see {@link WordEngine}). Words the user insists on are remembered and never
 * corrected again.
 */
final class Autocorrect {
    private static Autocorrect instance;

    private final WordEngine engine = new WordEngine();
    private final Set<String> learned = new HashSet<>();
    private final SharedPreferences store;
    private volatile boolean loaded;

    private Autocorrect(Context context) {
        store = context.getSharedPreferences("thorpad_words", Context.MODE_PRIVATE);
        Set<String> saved = store.getStringSet("learned", null);
        if (saved != null) learned.addAll(saved);
    }

    /** Shared instance; the word lists load in the background the first time. */
    static synchronized Autocorrect get(Context context) {
        if (instance == null) {
            final Autocorrect ac = new Autocorrect(context.getApplicationContext());
            final Context app = context.getApplicationContext();
            instance = ac;
            new Thread(new Runnable() {
                @Override public void run() { ac.load(app); }
            }, "ThorPadWords").start();
        }
        return instance;
    }

    /** Drops the shared word lists to free memory; the next get() loads them again. */
    static synchronized void release() {
        instance = null;
    }

    private void load(Context context) {
        try {
            // Filled in place. Nothing reads the engine until `loaded` is set, and it never
            // changes after that, so lookups need no lock.
            engine.loadWords(context.getAssets().open("words.txt"));
            engine.loadPairs(context.getAssets().open("pairs.txt"));
            loaded = true;
        } catch (Exception ignored) {
        }
    }

    /** Remembers a word so it is never corrected. */
    synchronized void learn(String word) {
        String w = word.toLowerCase(Locale.US);
        if (w.isEmpty() || !learned.add(w)) return;
        store.edit().putStringSet("learned", new HashSet<>(learned)).apply();
    }

    /**
     * The correction for a finished word, or null to leave it as typed. prev is the word before
     * it in the same sentence, or null at the start of one.
     */
    String correct(String prev, String word) {
        if (word == null || word.isEmpty() || !loaded) return null;
        synchronized (this) {
            if (learned.contains(word.toLowerCase(Locale.US))) return null;
        }
        return engine.correct(prev == null ? null : prev.toLowerCase(Locale.US), word);
    }
}
