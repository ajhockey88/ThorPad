"""Builds app/src/main/assets/words.txt, the autocorrect dictionary.

Needs `pip install wordfreq` and a spelling word list (Debian/Ubuntu: `apt install wamerican`).

Which words count as correctly spelled comes from the SCOWL-based American English list
(/usr/share/dict/american-english), plus very common informal words such as "ok" or
"gonna". How common each word is comes from wordfreq (data CC BY-SA 4.0,
https://github.com/rspeer/wordfreq). Web frequency lists alone contain common misspellings
("teh", "alot"), so they don't decide what is a word.

Output lines are "word<TAB>zipf*10", most common first.
"""
import re
import sys
from wordfreq import top_n_list, zipf_frequency

WORD_LIST = "/usr/share/dict/american-english"
INFORMAL_MIN_ZIPF = 4.0   # web words not in the spelling list must be at least this common
SPELLED_MIN_ZIPF = 1.0    # spelling-list words with no frequency data are left out
PATTERN = re.compile(r"[a-z]+('[a-z]+)?")


def main(path):
    words = {}
    with open(WORD_LIST, encoding="utf-8") as f:
        for line in f:
            w = line.strip().lower()
            if PATTERN.fullmatch(w) and (len(w) > 1 or w in ("a", "i")):
                z = zipf_frequency(w, "en")
                if z >= SPELLED_MIN_ZIPF:
                    words[w] = z
    for w in top_n_list("en", 60000):
        if w not in words and PATTERN.fullmatch(w) and len(w) > 1:
            z = zipf_frequency(w, "en")
            if z >= INFORMAL_MIN_ZIPF:
                words[w] = z
    ordered = sorted(words.items(), key=lambda kv: -kv[1])
    with open(path, "w") as out:
        out.write("# English words for autocorrect. Spellings: SCOWL (american-english); "
                  "frequencies: wordfreq (CC BY-SA 4.0).\n")
        for w, z in ordered:
            out.write("%s\t%d\n" % (w, round(z * 10)))
    print("wrote %d words" % len(ordered))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/words.txt")
