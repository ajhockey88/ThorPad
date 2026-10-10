package com.thorpad;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * iPhone-style autocorrect: when a word is finished, an unknown word is swapped for the most
 * likely intended word. Likelihood weighs how common a word is (assets/words.txt) against how
 * big a slip it would take to type it wrong, with neighbouring keys counting as small slips.
 * Words the user insists on are remembered and never corrected again.
 */
final class Autocorrect {
    private static final String ROWS = "qwertyuiop|asdfghjkl|zxcvbnm";
    private static final float[][] KEY_POS = new float[26][];
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";
    private static final Map<String, String> CONTRACTIONS = new HashMap<>();

    static {
        String[] rows = ROWS.split("\\|");
        float[] offset = {0f, 0.5f, 1.5f};
        for (int r = 0; r < rows.length; r++) {
            for (int i = 0; i < rows[r].length(); i++) {
                KEY_POS[rows[r].charAt(i) - 'a'] = new float[] {i + offset[r], r};
            }
        }
        String[] pairs = {
                "i", "I", "im", "I'm", "ive", "I've", "id", "I'd", "dont", "don't", "doesnt", "doesn't",
                "didnt", "didn't", "cant", "can't", "wont", "won't", "isnt", "isn't", "arent", "aren't",
                "wasnt", "wasn't", "werent", "weren't", "havent", "haven't", "hasnt", "hasn't",
                "hadnt", "hadn't", "couldnt", "couldn't", "wouldnt", "wouldn't", "shouldnt", "shouldn't",
                "mustnt", "mustn't", "youre", "you're", "youve", "you've", "youll", "you'll",
                "theyre", "they're", "theyve", "they've", "theyll", "they'll", "weve", "we've",
                "thats", "that's", "whats", "what's", "wheres", "where's", "theres", "there's",
                "hes", "he's", "shes", "she's", "itll", "it'll", "lets", "let's", "aint", "ain't",
                "yall", "y'all", "alot", "a lot", "whos", "who's", "hows", "how's", "ill", "I'll",
        };
        for (int i = 0; i < pairs.length; i += 2) CONTRACTIONS.put(pairs[i], pairs[i + 1]);
        // "ill", "lets", "id", "hes", "wont", "cant" are also words; only fix them when typed
        // in a way the real word rarely is. Keep the clear wins, drop the ambiguous ones.
        CONTRACTIONS.remove("ill");
        CONTRACTIONS.remove("lets");
        CONTRACTIONS.remove("id");
        CONTRACTIONS.remove("hes");
    }

    private static Autocorrect instance;

    private final Map<String, Integer> freq = new HashMap<>(180000);
    private final Set<String> learned = new HashSet<>();
    private final SharedPreferences store;
    private volatile boolean loaded;

    private Autocorrect(Context context) {
        store = context.getSharedPreferences("thorpad_words", Context.MODE_PRIVATE);
        Set<String> saved = store.getStringSet("learned", null);
        if (saved != null) learned.addAll(saved);
    }

    /** Shared instance; the word list loads in the background the first time. */
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

    /** Drops the shared word list to free memory; the next get() loads it again. */
    static synchronized void release() {
        instance = null;
    }

    private void load(Context context) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                context.getAssets().open("words.txt"), StandardCharsets.UTF_8), 1 << 16)) {
            String line;
            // Filled in place, with no second copy. Nothing reads it until `loaded` is set, and it
            // never changes after that, so lookups need no lock.
            while ((line = in.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                freq.put(line.substring(0, tab), Integer.parseInt(line.substring(tab + 1)));
            }
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

    /** The correction for a finished word, or null to leave it as typed. */
    String correct(String word) {
        if (word == null || word.isEmpty()) return null;
        String lw = word.toLowerCase(Locale.US);
        for (int i = 0; i < lw.length(); i++) {
            char c = lw.charAt(i);
            if ((c < 'a' || c > 'z') && c != '\'') return null;
        }
        synchronized (this) {
            if (learned.contains(lw)) return null;
        }
        String contraction = CONTRACTIONS.get(lw);
        if (contraction != null) {
            if (contraction.equals(word)) return null;
            return lw.equals("i") || contraction.startsWith("I") ? contraction : matchCase(word, contraction);
        }
        if (!loaded || lw.length() < 2 || known(lw)) return null;

        Best best = new Best();
        Map<String, Float> first = new HashMap<>();
        edits(lw, 0f, first, false);
        for (Map.Entry<String, Float> e : first.entrySet()) consider(best, e.getKey(), e.getValue());
        if (lw.length() >= 4 && lw.length() <= 14) {
            Map<String, Float> second = new HashMap<>();
            for (Map.Entry<String, Float> e : first.entrySet()) {
                if (e.getValue() <= 1.0f) edits(e.getKey(), e.getValue(), second, true);
            }
            for (Map.Entry<String, Float> e : second.entrySet()) consider(best, e.getKey(), e.getValue());
        }
        if (best.word == null) return null;
        return matchCase(word, best.word);
    }

    private boolean known(String w) {
        return freq.containsKey(w);
    }

    private static final class Best {
        String word;
        float score = -1e9f;
    }

    private void consider(Best best, String candidate, float cost) {
        Integer f = freq.get(candidate);
        if (f == null || f < 20) return; // ignore very rare words as corrections
        // Frequency counts, but not so much that a common short word beats an obvious slip.
        float score = 0.6f * (f / 10f) - 3.0f * cost;
        if (score > best.score) {
            best.score = score;
            best.word = candidate;
        }
    }

    /** All one-slip variants of w, each with the cheapest cost of the slip that explains it. */
    private void edits(String w, float base, Map<String, Float> out, boolean knownOnly) {
        int n = w.length();
        for (int i = 0; i <= n; i++) {
            String a = w.substring(0, i), b = w.substring(i);
            if (!b.isEmpty()) {
                // Typed an extra letter (doubled letters are an easy slip).
                boolean doubled = i > 0 && w.charAt(i - 1) == b.charAt(0);
                put(out, a + b.substring(1), base + (doubled ? 0.5f : 0.8f), knownOnly);
            }
            if (b.length() > 1) {
                put(out, a + b.charAt(1) + b.charAt(0) + b.substring(2), base + 0.45f, knownOnly); // swapped
            }
            for (int k = 0; k < 26; k++) {
                char c = ALPHABET.charAt(k);
                if (!b.isEmpty() && c != b.charAt(0)) {
                    float cost = adjacent(b.charAt(0), c) ? 0.5f : 1.0f;
                    put(out, a + c + b.substring(1), base + cost, knownOnly);
                }
                boolean doubling = (!b.isEmpty() && b.charAt(0) == c) || (i > 0 && w.charAt(i - 1) == c);
                put(out, a + c + b, base + (doubling ? 0.5f : 0.9f), knownOnly); // missed a letter
            }
            if (!b.isEmpty()) put(out, a + '\'' + b, base + 0.4f, knownOnly); // missed apostrophe
        }
    }

    private void put(Map<String, Float> out, String s, float cost, boolean knownOnly) {
        if (knownOnly && !known(s)) return; // second slips: keep only real words
        Float old = out.get(s);
        if (old == null || cost < old) out.put(s, cost);
    }

    private static boolean adjacent(char a, char b) {
        if (a < 'a' || a > 'z' || b < 'a' || b > 'z') return false;
        float[] p = KEY_POS[a - 'a'], q = KEY_POS[b - 'a'];
        float dx = p[0] - q[0], dy = p[1] - q[1];
        return dx * dx + dy * dy <= 1.6f;
    }

    private static String matchCase(String typed, String fix) {
        boolean allUpper = typed.length() > 1;
        for (int i = 0; i < typed.length(); i++) {
            if (Character.isLowerCase(typed.charAt(i))) { allUpper = false; break; }
        }
        if (allUpper) return fix.toUpperCase(Locale.US);
        if (Character.isUpperCase(typed.charAt(0))) {
            return Character.toUpperCase(fix.charAt(0)) + fix.substring(1);
        }
        return fix;
    }
}
