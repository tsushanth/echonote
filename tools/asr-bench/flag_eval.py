"""Does "Vosk disagrees with the main engine" mark the main engine's wrong words? (feature: Mark uncertain words)

Run from the bench folder (needs ml_results_small.json, ml_extra.json, esb_small.json, results/vosk.json, data/*.json):
    python flag_eval.py [bench_dir]

Primary engine per language = what EnginePlan picks for Accurate. Second opinion = Vosk small (the app's own model).
A primary word is FLAGGED when the alignment (Levenshtein) of primary vs Vosk words pairs it with no equal Vosk word.
A primary word is WRONG when the alignment of primary vs reference pairs it with no equal reference word
(substitutions and insertions; words the engine dropped altogether cannot be marked and are not counted).
Normalisation = NFKC, lower case, punctuation removed; zh/ja are compared per character (no spaces).
Same logic as app/src/main/kotlin/.../service/UncertainWords.kt.
"""
import json, re, sys, os, unicodedata, glob

BENCH = sys.argv[1] if len(sys.argv) > 1 else "."
os.chdir(BENCH)

def norm(s):
    s = unicodedata.normalize("NFKC", s).lower()
    return "".join(" " if unicodedata.category(c)[0] in "PS" else c for c in s)

def toks(s, chars):
    s = norm(s)
    if chars:
        return [c for c in s if not c.isspace()]
    return s.split()

def align_equal(a, b):
    """For each index of a: True when the Levenshtein alignment pairs it with an equal element of b."""
    n, m = len(a), len(b)
    d = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(n + 1): d[i][0] = i
    for j in range(m + 1): d[0][j] = j
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            d[i][j] = min(d[i-1][j-1] + (a[i-1] != b[j-1]), d[i-1][j] + 1, d[i][j-1] + 1)
    i, j, ok = n, m, [False] * n
    while i > 0 or j > 0:
        if i > 0 and j > 0 and d[i][j] == d[i-1][j-1] + (a[i-1] != b[j-1]):
            ok[i-1] = a[i-1] == b[j-1]; i -= 1; j -= 1
        elif i > 0 and d[i][j] == d[i-1][j] + 1:
            i -= 1
        else:
            j -= 1
    return ok

# (label, language, chars?, primary engine, [(ref, primary, vosk)])
sets = []
small = json.load(open("ml_results_small.json")); extra = json.load(open("ml_extra.json"))
for lang in ["en", "pt", "ja", "ko"]:
    sets.append((lang, lang in ("ja",), "Whisper small", [(r["ref"], r["whisper"], r["vosk"]) for r in small[lang]]))
for lang in ["de", "es", "fr"]:
    sets.append((lang, False, "NeMo FC", [(r["ref"], e["text"], r["vosk"]) for r, e in zip(small[lang], extra[lang]["rows"])]))
sets.append(("zh", True, "SenseVoice", [(r["ref"], e["text"], r["vosk"]) for r, e in zip(small["zh"], extra["zh"]["rows"])]))
for lang in ["it", "hi"]:  # primary = Vosk itself: no independent second opinion, shown for completeness only
    sets.append((lang, False, "Vosk", None))
# 120 real English meeting/call/talk clips (ESB)
esb = json.load(open("esb_small.json")); vosk = json.load(open("results/vosk.json"))
items = []
for f in sorted(glob.glob("data/*.json")): items += json.load(open(f))
sets.append(("en-esb", False, "Whisper small", [(it["ref"], esb[it["wav"]]["small"], vosk[it["wav"]]["hyp"]) for it in items if it["wav"] in esb]))

print("%-7s %-14s %6s %8s %9s %12s %14s %8s" % ("lang", "primary", "words", "primWER%", "flagged%", "wrong|flag%", "wrong|unflag%", "caught%"))
for lang, chars, prim, rows in sets:
    if rows is None:
        print("%-7s %-14s  (primary is Vosk: no second opinion)" % (lang, prim)); continue
    N = F = W = FW = 0
    for ref, p, v in rows:
        r, pt, vt = toks(ref, chars), toks(p, chars), toks(v, chars)
        right = align_equal(pt, r); agree = align_equal(pt, vt)
        for ok, ag in zip(right, agree):
            N += 1; W += (not ok); F += (not ag); FW += (not ok) and (not ag)
    fr = 100 * F / N; wf = 100 * FW / F if F else 0; wu = 100 * (W - FW) / (N - F) if N > F else 0
    print("%-7s %-14s %6d %8.1f %9.1f %12.1f %14.1f %8.1f   ratio %.1fx" % (lang, prim, N, 100 * W / N, fr, wf, wu, 100 * FW / W if W else 0, wf / wu if wu else float("inf")))
