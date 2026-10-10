package com.thorpad;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What autocorrect knows about English, kept free of Android so it can be tested on a computer.
 *
 * A finished word is swapped for the most likely intended text. Likelihood weighs how common a
 * word is (words.txt), how well it follows the word before it (pairs.txt), and how big a slip
 * it would take to type it wrong, with neighbouring keys counting as small slips. Beyond single
 * words it also splits words that ran together, including a letter typed by grazing the space
 * bar ("seebyou" becomes "see you"), adds apostrophes, and fixes commonly confused words when
 * the word before makes the mistake plain ("more then" becomes "more than").
 */
final class WordEngine {
    private static final String ROWS = "qwertyuiop|asdfghjkl|zxcvbnm";
    private static final float[][] KEY_POS = new float[26][];
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";
    /** Letters just above the space bar: hitting one instead of space joins two words. */
    private static final String NEAR_SPACE = "cvbnm";
    private static final Map<String, String> CONTRACTIONS = new HashMap<>();
    /** Chat shorthand that isn't in the dictionary but is typed on purpose. */
    private static final java.util.Set<String> SLANG = new java.util.HashSet<>(Arrays.asList(
            "omw", "brb", "lol", "lmao", "lmfao", "rofl", "idk", "idc", "imo", "imho", "tbh", "smh",
            "btw", "thx", "pls", "plz", "ty", "tyvm", "np", "gg", "ggs", "wp", "afk", "irl", "ngl",
            "fr", "rn", "wyd", "hbu", "ikr", "nvm", "ttyl", "gtg", "g2g", "jk", "bc", "cuz", "ya",
            "yea", "nah", "gonna", "wanna", "gotta", "kinda", "sorta", "lemme", "gimme", "ur", "u",
            "r", "k", "kk", "ok", "okk", "hmu", "fyi", "asap", "dm", "dms", "pc", "gpu", "cpu",
            "fps", "ui", "wtf", "omg", "ofc", "bruh", "lmk", "tho", "thru", "gn", "gm", "ily", "pog"));
    /** Contractions that are also words: only fixed where the contraction is what's meant. */
    private static final Map<String, String> MAYBE_CONTRACTIONS = new HashMap<>();
    /** Words often typed for one another, fixed only when the word before makes it plain. */
    private static final String[][] CONFUSED = {
            {"their", "there"}, {"then", "than"}, {"lose", "loose"}, {"affect", "effect"},
            {"weather", "whether"}, {"accept", "except"}, {"quite", "quiet"}, {"advice", "advise"},
            {"breath", "breathe"}, {"loose", "lose"}, {"of", "off"}, {"were", "where"},
    };

    static {
        String[] rows = ROWS.split("\\|");
        float[] offset = {0f, 0.5f, 1.5f};
        for (int r = 0; r < rows.length; r++) {
            for (int i = 0; i < rows[r].length(); i++) {
                KEY_POS[rows[r].charAt(i) - 'a'] = new float[] {i + offset[r], r};
            }
        }
        String[] pairs = {
                "i", "I", "im", "I'm", "ive", "I've", "dont", "don't", "doesnt", "doesn't",
                "didnt", "didn't", "cant", "can't", "wont", "won't", "isnt", "isn't", "arent", "aren't",
                "wasnt", "wasn't", "werent", "weren't", "havent", "haven't", "hasnt", "hasn't",
                "hadnt", "hadn't", "couldnt", "couldn't", "wouldnt", "wouldn't", "shouldnt", "shouldn't",
                "mustnt", "mustn't", "youre", "you're", "youve", "you've", "youll", "you'll",
                "youd", "you'd", "theyre", "they're", "theyve", "they've", "theyll", "they'll",
                "theyd", "they'd", "weve", "we've", "thats", "that's", "whats", "what's",
                "wheres", "where's", "theres", "there's", "itll", "it'll", "aint", "ain't",
                "yall", "y'all", "whos", "who's", "hows", "how's", "whens", "when's", "whys", "why's",
                "heres", "here's", "shouldve", "should've", "wouldve", "would've", "couldve", "could've",
                "mightve", "might've", "mustve", "must've", "neednt", "needn't", "dunno", "dunno",
                "alot", "a lot", "infront", "in front", "eachother", "each other", "noone", "no one",
                "atleast", "at least", "aswell", "as well", "incase", "in case", "alright", "alright",
                "thankyou", "thank you", "everytime", "every time", "ofcourse", "of course",
        };
        for (int i = 0; i < pairs.length; i += 2) CONTRACTIONS.put(pairs[i], pairs[i + 1]);
        String[] maybe = {
                "ill", "I'll", "id", "I'd", "hes", "he's", "shes", "she's", "its", "it's",
                "lets", "let's", "well", "we'll", "were", "we're", "wed", "we'd", "shell", "she'll",
                "hed", "he'd", "whos", "who's",
        };
        for (int i = 0; i < maybe.length; i += 2) MAYBE_CONTRACTIONS.put(maybe[i], maybe[i + 1]);
    }

    private final HashMap<String, Integer> ids = new HashMap<>(180000);
    /** How common each word is, as zipf x 10 (70 = once in every ten thousand words). */
    private byte[] zipf = new byte[0];
    /** Word pairs, as (first id << 20 | second id), sorted, with -10 log10 P(second | first). */
    private long[] pairKeys = new long[0];
    private byte[] pairScores = new byte[0];

    void loadWords(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16);
        byte[] z = new byte[150000];
        int n = 0;
        String line;
        while ((line = r.readLine()) != null) {
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            String w = line.substring(0, tab);
            if (ids.containsKey(w)) continue;
            if (n == z.length) z = Arrays.copyOf(z, n * 2);
            z[n] = (byte) Math.min(127, Integer.parseInt(line.substring(tab + 1)));
            ids.put(w, n++);
        }
        zipf = Arrays.copyOf(z, n);
    }

    void loadPairs(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16);
        long[] keys = new long[250000];
        byte[] scores = new byte[250000];
        int n = 0;
        String line;
        while ((line = r.readLine()) != null) {
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            int sp = line.indexOf(' '), tab = line.indexOf('\t');
            if (sp <= 0 || tab <= sp) continue;
            Integer a = ids.get(line.substring(0, sp)), b = ids.get(line.substring(sp + 1, tab));
            if (a == null || b == null) continue;
            if (n == keys.length) {
                keys = Arrays.copyOf(keys, n * 2);
                scores = Arrays.copyOf(scores, n * 2);
            }
            keys[n] = key(a, b);
            scores[n++] = (byte) Integer.parseInt(line.substring(tab + 1));
        }
        // Sort by key, keeping each score with its key.
        long[] packed = new long[n];
        for (int i = 0; i < n; i++) packed[i] = keys[i] << 8 | (scores[i] & 0xff);
        Arrays.sort(packed);
        long[] k = new long[n];
        byte[] s = new byte[n];
        for (int i = 0; i < n; i++) {
            k[i] = packed[i] >>> 8;
            s[i] = (byte) (packed[i] & 0xff);
        }
        pairKeys = k;
        pairScores = s;
    }

    private static long key(int a, int b) { return (long) a << 20 | b; }

    boolean known(String w) { return ids.containsKey(w); }

    /** zipf x 10, or -1 for an unknown word. */
    private int z(String w) {
        Integer id = ids.get(w);
        return id == null ? -1 : zipf[id];
    }

    /** -10 log10 P(second | first), or -1 if the pair isn't known. */
    private int pair(String first, String second) {
        if (first == null) return -1;
        Integer a = ids.get(first), b = ids.get(second);
        if (a == null || b == null) return -1;
        int at = Arrays.binarySearch(pairKeys, key(a, b));
        return at < 0 ? -1 : pairScores[at];
    }

    /** How likely a word is here, in zipf units, lifted when it often follows prev. */
    private float likely(String prev, String w) {
        float zw = z(w) / 10f;
        int p = pair(prev, w);
        if (p < 0) return zw;
        // How much more likely w is after prev than anywhere, capped so context can tip a
        // close call but never invent a word out of a big slip.
        float lift = (-p / 10f) - (zw - 9f);
        return zw + 0.5f * Math.max(0f, Math.min(3f, lift));
    }

    /**
     * The correction for a finished word, or null to leave it as typed. prev is the word
     * before it in the same sentence (lowercase), or null at the start of one.
     */
    String correct(String prev, String word) {
        if (word == null || word.isEmpty()) return null;
        String lw = word.toLowerCase(Locale.US);
        for (int i = 0; i < lw.length(); i++) {
            char c = lw.charAt(i);
            if ((c < 'a' || c > 'z') && c != '\'') return null;
        }
        // A capital inside a word (iPhone, McDonald) is on purpose: leave it.
        for (int i = 1; i < word.length(); i++) {
            if (Character.isUpperCase(word.charAt(i)) && Character.isLowerCase(word.charAt(i - 1))) return null;
        }
        String contraction = CONTRACTIONS.get(lw);
        if (contraction != null) return fixed(word, contraction);
        String maybe = MAYBE_CONTRACTIONS.get(lw);
        if (maybe != null) return contractionInContext(prev, word, lw, maybe);
        if (lw.length() < 2 || SLANG.contains(lw)) return null;

        if (known(lw)) return realWordFix(prev, word, lw);

        // Unknown: the best single word, or the best way to split it into words.
        Best best = new Best();
        Map<String, Float> first = new HashMap<>();
        edits(lw, 0f, first, false);
        for (Map.Entry<String, Float> e : first.entrySet()) consider(best, prev, e.getKey(), e.getValue());
        if (lw.length() >= 4 && lw.length() <= 20) {
            Map<String, Float> second = new HashMap<>();
            for (Map.Entry<String, Float> e : first.entrySet()) {
                if (e.getValue() <= 1.0f) edits(e.getKey(), e.getValue(), second, true);
            }
            for (Map.Entry<String, Float> e : second.entrySet()) consider(best, prev, e.getKey(), e.getValue());
        }
        Best split = split(prev, lw);
        if (split != null && split.score > best.score) best = split;
        if (best.word == null) return null;
        return fixed(word, best.word);
    }

    private static String fixed(String typed, String fix) {
        if (fix.equals(typed)) return null;
        String out = fix.startsWith("I") ? fix : matchCase(typed, fix);
        return out.equals(typed) ? null : out;
    }

    /** Whether prev starts a clause, where "ill" means I'll and "its" means it's. */
    private static boolean startish(String prev) {
        return prev == null || prev.equals("and") || prev.equals("but") || prev.equals("so")
                || prev.equals("then") || prev.equals("if") || prev.equals("think") || prev.equals("because")
                || prev.equals("cause") || prev.equals("when") || prev.equals("hope") || prev.equals("guess")
                || prev.equals("said") || prev.equals("know") || prev.equals("maybe") || prev.equals("yes")
                || prev.equals("yeah") || prev.equals("ok") || prev.equals("okay") || prev.equals("oh");
    }

    /** "ill" at the start of a sentence (or after and/so/think...) is I'll; elsewhere, ill. */
    private String contractionInContext(String prev, String word, String lw, String maybe) {
        switch (lw) {
            case "ill": case "id": case "hes": case "shes": case "hed":
            case "its": case "lets": case "whos":
                return startish(prev) ? fixed(word, maybe) : null;
            default:
                // well/were/wed/shell are common words in their own right: leave them.
                return null;
        }
    }

    /** A real word that is probably a slip for another: confused pairs, or a rare near-miss. */
    private String realWordFix(String prev, String word, String lw) {
        if (prev != null) {
            for (String[] pairOf : CONFUSED) {
                if (!pairOf[0].equals(lw)) continue;
                String alt = pairOf[1];
                int typed = pair(prev, lw), other = pair(prev, alt);
                if (other < 0 || other > 15) continue;
                // Only when the typed word is far less likely after prev (about 6x or more).
                if (typed < 0 ? other <= 8 : typed - other >= 8) return fixed(word, alt);
            }
        }
        int zt = z(lw);
        if (zt >= 20 || lw.length() < 3) return null;
        // A very rare word one easy slip away from a common one ("teh", "adn"): the common
        // word was meant.
        Map<String, Float> first = new HashMap<>();
        edits(lw, 0f, first, true);
        String bestWord = null;
        float bestScore = -1e9f;
        for (Map.Entry<String, Float> e : first.entrySet()) {
            if (e.getValue() > 0.5f) continue;
            float zc = likely(prev, e.getKey());
            if (zc * 10 < 45 || zc * 10 - zt < 35) continue;
            float s = zc - 3f * e.getValue();
            if (s > bestScore) {
                bestScore = s;
                bestWord = e.getKey();
            }
        }
        return bestWord == null ? null : fixed(word, bestWord);
    }

    private static final class Best {
        String word;
        float score = -1e9f;
    }

    private void consider(Best best, String prev, String candidate, float cost) {
        int zc = z(candidate);
        if (zc < 20) return; // ignore very rare words as corrections
        // Frequency counts, but not so much that a common short word beats an obvious slip.
        float score = 0.6f * likely(prev, candidate) - 3.0f * cost;
        if (score > best.score) {
            best.score = score;
            best.word = candidate;
        }
    }

    /**
     * Splits words that ran together: a missed space ("helloworld"), or a letter typed by
     * grazing the space bar ("seebyou"). Each piece must be a common word.
     */
    private Best split(String prev, String w) {
        int n = w.length();
        if (n < 4 || n > 60) return null;
        final float join = 0.6f, graze = 0.5f;
        float[] best = new float[n + 1];
        int[] pieceStart = new int[n + 1]; // for a piece ending at i: where it started
        int[] prevEnd = new int[n + 1];    // for a piece starting at j: where the last piece ended
        float[] startScore = new float[n + 1];
        int[] startFrom = new int[n + 1];
        Arrays.fill(best, Float.NEGATIVE_INFINITY);
        Arrays.fill(startScore, Float.NEGATIVE_INFINITY);
        startScore[0] = 0f;
        startFrom[0] = -1;
        for (int j = 0; j < n; j++) {
            // Every piece ending at j is known by now: the next piece can start right there
            // (a missed space) or one letter on (a letter typed instead of space).
            if (j > 0 && best[j] != Float.NEGATIVE_INFINITY) {
                float s = best[j] - join * 2f;
                if (s > startScore[j]) {
                    startScore[j] = s;
                    prevEnd[j] = j;
                }
                // Not after a one-letter piece: "imgoing" is "I'm going", not "I going".
                if (j + 1 < n && NEAR_SPACE.indexOf(w.charAt(j)) >= 0 && j - pieceStart[j] > 1) {
                    float g = best[j] - graze * 2f;
                    if (g > startScore[j + 1]) {
                        startScore[j + 1] = g;
                        prevEnd[j + 1] = j;
                    }
                }
            }
            if (startScore[j] == Float.NEGATIVE_INFINITY) continue;
            for (int i = j + 1; i <= n && i - j <= 24; i++) {
                int zp = pieceZ(w.substring(j, i));
                if (zp < 0) continue;
                float s = startScore[j] + (zp / 10f - 9f); // log10 of how often the word is used
                if (s > best[i]) {
                    best[i] = s;
                    pieceStart[i] = j;
                }
            }
        }
        if (best[n] == Float.NEGATIVE_INFINITY || pieceStart[n] == 0) return null;
        // Walk back to collect the pieces.
        StringBuilder out = new StringBuilder();
        float minZ = 99f, costs = 0f;
        int i = n, pieces = 0;
        String last = null;
        float context = 0f;
        while (i > 0) {
            int j = pieceStart[i];
            String piece = w.substring(j, i);
            String shown = CONTRACTIONS.containsKey(piece) ? CONTRACTIONS.get(piece) : piece;
            if (j == 0 && MAYBE_CONTRACTIONS.containsKey(piece) && startish(prev)
                    && !piece.equals("well") && !piece.equals("were") && !piece.equals("wed")
                    && !piece.equals("shell")) {
                shown = MAYBE_CONTRACTIONS.get(piece); // "letsgo" is "let's go"
            }
            out.insert(0, shown);
            minZ = Math.min(minZ, pieceZ(piece) / 10f);
            if (last != null && pair(piece, last) >= 0) context += 0.3f;
            last = piece;
            pieces++;
            if (j == 0) break;
            int e = prevEnd[j];
            costs += e == j ? join : graze;
            out.insert(0, ' ');
            i = e;
        }
        if (pieces < 2) return null;
        if (pair(prev, last) >= 0) context += 0.3f;
        Best b = new Best();
        b.word = out.toString();
        b.score = 0.6f * minZ - 3.0f * costs + context;
        return b;
    }

    /** How common a piece of a split word is (zipf x 10), or -1 if it can't be a piece. */
    private int pieceZ(String piece) {
        if (piece.length() == 1 && !piece.equals("a") && !piece.equals("i")) return -1;
        if (CONTRACTIONS.containsKey(piece) && piece.length() > 1) return 55; // "im going"
        int zp = z(piece);
        if (piece.length() == 2 && zp < 45) return -1; // "re", "ow": too easy to land on by chance
        return zp < 30 ? -1 : zp;
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

    static String matchCase(String typed, String fix) {
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
