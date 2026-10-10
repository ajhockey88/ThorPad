#!/usr/bin/env python3
"""Builds app/src/main/assets/pairs.txt: which word tends to follow which, for context-aware
corrections ("more then" -> "more than", picking "their" or "there").

Source: SymSpell's English bigram and word frequency lists (MIT, from symspellpy), which come
from Google Books Ngram data (CC BY 3.0). Usage:
  pip download symspellpy --no-deps && unzip symspellpy-*.whl
  python3 tools/make_pairs.py symspellpy/frequency_bigramdictionary_en_243_342.txt \
      symspellpy/frequency_dictionary_en_82_765.txt app/src/main/assets/words.txt
Each line is "first second<TAB>score", score = round(-10 * log10 P(second | first)).
"""
import math
import sys

bigrams, unigrams, words_path = sys.argv[1:4]
words = set()
for line in open(words_path, encoding="utf-8"):
    if line and not line.startswith("#"):
        words.add(line.split("\t")[0])
uni = {}
for line in open(unigrams, encoding="utf-8"):
    w, c = line.split()
    uni[w] = int(c)
out = []
for line in open(bigrams, encoding="utf-8"):
    a, b, c = line.split()
    if a not in words or b not in words or a not in uni:
        continue
    p = min(1.0, int(c) / uni[a])
    out.append((a, b, max(0, min(80, round(-10 * math.log10(p))))))
out.sort()
with open("app/src/main/assets/pairs.txt", "w", encoding="utf-8") as f:
    f.write("# Word pairs for context-aware corrections. Data: SymSpell (MIT) from Google Books Ngram (CC BY 3.0).\n")
    for a, b, s in out:
        f.write(f"{a} {b}\t{s}\n")
print(len(out), "pairs")
