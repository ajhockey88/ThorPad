"""Builds assets/words-<lang>.txt for the non-English keyboards from wordfreq.

Each line is "word<TAB>zipf x 10", most common first, like words.txt. Only words made of the
language's letters (plus an apostrophe) are kept, down to zipf 2.4, at most 100k words.

    python3 -m pip install wordfreq && python3 tools/make_lang_words.py
"""
import os
import wordfreq

LETTERS = "abcdefghijklmnopqrstuvwxyz"
EXTRA = {
    "es": "ñáéíóúü",
    "fr": "àâæçéèêëîïôœùûüÿ",
    "de": "äöüß",
    "pt": "ãáàâçéêíõóôú",
    "it": "àèéìíòóù",
}
NAMES = {"es": "Spanish", "fr": "French", "de": "German", "pt": "Portuguese", "it": "Italian"}
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")

for lang, extra in EXTRA.items():
    allowed = set(LETTERS + extra + "'")
    rows = []
    for w in wordfreq.iter_wordlist(lang, "large"):
        z = wordfreq.zipf_frequency(w, lang)
        if z < 2.4 or len(rows) >= 100000:
            break
        if not set(w) <= allowed or w.startswith("'") or w.endswith("'"):
            continue
        if len(w) == 1 and z < 5.0:
            continue  # stray letters
        rows.append((w, round(z * 10)))
    path = os.path.join(OUT, "words-%s.txt" % lang)
    with open(path, "w", encoding="utf-8") as f:
        f.write("# %s words for autocorrect. Frequencies: wordfreq (CC BY-SA 4.0).\n" % NAMES[lang])
        for w, z in rows:
            f.write("%s\t%d\n" % (w, z))
    print(lang, len(rows), os.path.getsize(path))
