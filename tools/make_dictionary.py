"""Builds app/src/main/assets/words.txt, the autocorrect dictionary.

Needs `pip install wordfreq` and a spelling word list (Debian/Ubuntu: `apt install wamerican-huge`).

Which words count as correctly spelled comes from the SCOWL-based "huge" American English list
(/usr/share/dict/american-english-huge), plus common informal words, names and brands seen on
the web ("ok", "gonna", "minecraft"), plus ThorPad's own extras (gaming and handheld words).
Common misspellings that are frequent on the web ("alot", "isnt") are kept out so they still
get corrected. How common each word is comes from wordfreq (data CC BY-SA 4.0,
https://github.com/rspeer/wordfreq). Web frequency lists alone contain common misspellings
("teh", "alot"), so they don't decide what is a word.

Output lines are "word<TAB>zipf*10", most common first.
"""
import re
import sys
from wordfreq import top_n_list, zipf_frequency

WORD_LIST = "/usr/share/dict/american-english-huge"
INFORMAL_MIN_ZIPF = 3.4   # web words not in the spelling list must be at least this common
INFORMAL_TOP_N = 150000
# Frequent on the web, but misspellings (mostly contractions without the apostrophe).
NOT_WORDS = set("""
alot wich atleast everytime isnt youre wasnt theyre arent havent couldnt wouldnt shouldnt
doesnt didnt dont cant im ive thats whats wont weve youve youll theyll hasnt hadnt werent
""".split())
# Words ThorPad users type that general lists miss, with the frequency to give them.
EXTRAS = {
    "thor": 3.5, "ayn": 3.0, "gamenative": 3.0, "winlator": 3.0, "shizuku": 3.0,
    "retroarch": 3.0, "emulator": 3.5, "emulators": 3.2, "emulation": 3.4, "handheld": 3.6,
    "handhelds": 3.2, "trackpad": 3.4, "touchpad": 3.3, "joystick": 3.4, "joysticks": 3.0,
    "dpad": 3.0, "bluetooth": 4.0, "wifi": 4.2, "screenshot": 3.8, "screenshots": 3.6,
    "fps": 3.8, "rom": 3.4, "roms": 3.2, "apk": 3.4, "apks": 3.0, "github": 3.6,
    "android": 4.4, "snapdragon": 3.2, "steamdeck": 3.0, "proton": 3.4, "dolphin": 3.6,
    "yuzu": 3.0, "ppsspp": 3.0, "duckstation": 3.0, "aethersx": 2.8, "nethersx": 2.8,
}

SPELLED_MIN_ZIPF = 1.0    # spelling-list words with no frequency data are left out
PATTERN = re.compile(r"[a-z]+('[a-z]+)?")


def main(path):
    words = {}
    with open(WORD_LIST, encoding="utf-8") as f:
        for line in f:
            w = line.strip().lower()
            if PATTERN.fullmatch(w) and (len(w) > 1 or w in ("a", "i")) and w not in NOT_WORDS:
                z = zipf_frequency(w, "en")
                if z >= SPELLED_MIN_ZIPF:
                    words[w] = z
    for w in top_n_list("en", INFORMAL_TOP_N):
        if w not in words and w not in NOT_WORDS and PATTERN.fullmatch(w) and len(w) > 1:
            z = zipf_frequency(w, "en")
            if z >= INFORMAL_MIN_ZIPF:
                words[w] = z
    for w, z in EXTRAS.items():
        words[w] = max(words.get(w, 0), z)
    ordered = sorted(words.items(), key=lambda kv: -kv[1])
    with open(path, "w") as out:
        out.write("# English words for autocorrect. Spellings: SCOWL (american-english-huge); "
                  "frequencies: wordfreq (CC BY-SA 4.0).\n")
        for w, z in ordered:
            out.write("%s\t%d\n" % (w, round(z * 10)))
    print("wrote %d words" % len(ordered))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/words.txt")
