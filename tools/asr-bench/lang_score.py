import json, glob, re, unicodedata, random, os, sys, collections
CHAR_LANGS = {"zh", "ja", "ko", "th"}           # CER, spaces removed
RTL_MARKS = re.compile("[ؐ-ًؚ-ٰٟـ֑-ׇۖ-ۭ]")
DIG = {}
for base in (0x0660, 0x06f0, 0x0966, 0x09e6, 0x0be6, 0x0c66, 0x0e50):
    for i in range(10): DIG[chr(base + i)] = str(i)
def norm(s, lang, fold=False):
    s = unicodedata.normalize("NFKC", s).lower()
    if lang in ("ar", "fa", "ur", "he"): s = RTL_MARKS.sub("", s)
    s = "".join(DIG.get(c, c) for c in s)
    if fold and lang in ("ar", "fa", "ur"):
        s = re.sub("[أإآٱ]", "ا", s).replace("ى", "ي").replace("ة", "ه").replace("ی", "ي").replace("ک", "ك")
    s = "".join(" " if unicodedata.category(c)[0] in "PS" else c for c in s)
    s = re.sub(r"\s+", " ", s).strip()
    return s.replace(" ", "") if lang in CHAR_LANGS else s
def toks(s, lang): return list(s) if lang in CHAR_LANGS else s.split()
def edit(a, b):
    n, m = len(a), len(b); prev = list(range(m + 1))
    for i in range(1, n + 1):
        cur = [i] + [0] * m
        for j in range(1, m + 1): cur[j] = min(prev[j - 1] + (a[i - 1] != b[j - 1]), prev[j] + 1, cur[j - 1] + 1)
        prev = cur
    return prev[m]
def score_file(f, fold=False):
    d = json.load(open(f)); lang = d["lang"].split("-")[0][:2]
    errs, ns = [], []
    for r in d["rows"]:
        ref = toks(norm(r["ref"], lang, fold), lang); hyp = toks(norm(r["text"], lang, fold), lang)
        if not ref: continue
        errs.append(edit(ref, hyp)); ns.append(len(ref))
    tot = sum(errs) / sum(ns) * 100
    rng = random.Random(1); idx = range(len(errs)); bs = []
    for _ in range(1000):
        s = [rng.choice(idx) for _ in idx]; bs.append(sum(errs[i] for i in s) / sum(ns[i] for i in s) * 100)
    bs.sort()
    rtf = sum(r["sec"] for r in d["rows"]) / sum(r["dur"] for r in d["rows"])
    return {"engine": d["engine"], "lang": d["lang"], "err": tot, "lo": bs[25], "hi": bs[975], "n": len(errs), "words": sum(ns), "rtf": rtf,
            "peak_mb": d["peak_rss_mb"], "empty": sum(1 for r in d["rows"] if not r["text"].strip()), "errs": errs, "ns": ns}
if __name__ == "__main__":
    fold = "--fold" in sys.argv
    S = [score_file(f, fold) for f in sorted(glob.glob("out/res/*.json"))]
    json.dump(S, open("out/scores_fold.json" if fold else "out/scores.json", "w"))
    by = collections.defaultdict(dict)
    for s in S: by[s["lang"]][s["engine"]] = s
    engines = sorted({s["engine"] for s in S})
    print("lang " + " ".join("%12s" % e[:12] for e in engines))
    for lang in sorted(by):
        print("%-5s" % lang + " ".join(("%12s" % ("%.1f" % by[lang][e]["err"])) if e in by[lang] else "%12s" % "-" for e in engines))
