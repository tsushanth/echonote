"""NeMo vs Vosk vs Whisper small on English: 120 real (ESB) clips and 25 FLEURS clips, one normalizer.

Run from the bench dir (the one holding data/, ml/, results/, ml_results*.json, ml_extra.json, esb_small.json):
    python lowram_en_eval.py [--examples N]
Reuses the transcripts the other scripts saved (run.py -> results/vosk.json, esb_small.py -> esb_small.json,
ml_run.py/ml_extra.py -> ml_results*.json, ml_extra.json). Prints WER per source under
(a) the Whisper EnglishTextNormalizer (spelled numbers, "mr"/"mister", British spellings folded) and
(b) the plain normalizer ml_score2.py uses for FLEURS (NFKC, lowercase, punctuation to spaces),
plus paired bootstrap of Vosk minus NeMo, per-clip-length buckets, and the worst NeMo clips.
"""
import json, glob, re, sys, random, unicodedata, collections, jiwer
from whisper_normalizer.english import EnglishTextNormalizer
EN = EnglishTextNormalizer()
def plain(s):
    s = unicodedata.normalize("NFKC", s).lower()
    s = "".join(" " if unicodedata.category(c)[0] in "PS" else c for c in s)
    return re.sub(r"\s+", " ", s).strip()
NORMS = {"whisper-english-normalizer": lambda s: EN(s), "plain": plain}

rows = []  # (source, ref, {engine: hyp}, dur)
vosk = json.load(open("results/vosk.json")); es = json.load(open("esb_small.json"))
for f in sorted(glob.glob("data/*.json")):
    src = f.split("/")[-1][:-5]
    for x in json.load(open(f)):
        rows.append((src, x["ref"], {"vosk": vosk[x["wav"]]["hyp"], "whisper-small": es[x["wav"]]["small"], "nemo": es[x["wav"]]["nemo"]}, x["dur"]))
S = json.load(open("ml_results_small.json"))["en"]; B = json.load(open("ml_results.json"))["en"]; X = json.load(open("ml_extra.json"))["en"]["rows"]
for b, s, x in zip(B, S, X):
    rows.append(("fleurs", b["ref"], {"vosk": b["vosk"], "whisper-small": s["whisper"], "nemo": x["text"]}, b["dur"]))

def wer(rs, eng, nf):
    pairs = [(nf(r), nf(h[eng])) for _, r, h, _ in rs]
    pairs = [(a, b) for a, b in pairs if a]
    return 100 * jiwer.wer([a for a, _ in pairs], [b for _, b in pairs])
def errs(rs, eng, nf):
    out = []
    for _, r, h, _ in rs:
        a = nf(r)
        if a:
            o = jiwer.process_words(a, nf(h[eng])); out.append((o.substitutions + o.deletions + o.insertions, len(a.split())))
    return out
srcs = sorted({r[0] for r in rows})
for name, nf in NORMS.items():
    print("\n== normalizer:", name)
    print("%-14s" % "source", *["%14s" % e for e in ("vosk", "whisper-small", "nemo")])
    for s in srcs + ["ESB all 120", "all 145"]:
        sub = [r for r in rows if (r[0] == s or (s == "ESB all 120" and r[0] != "fleurs") or s == "all 145")]
        print("%-14s" % s, *["%14.1f" % wer(sub, e, nf) for e in ("vosk", "whisper-small", "nemo")])
    random.seed(0)
    for grp, sub in (("ESB120", [r for r in rows if r[0] != "fleurs"]), ("FLEURS25", [r for r in rows if r[0] == "fleurs"])):
        V, N, W = errs(sub, "vosk", nf), errs(sub, "nemo", nf), errs(sub, "whisper-small", nf)
        for lab, A in (("vosk", V), ("whisper-small", W)):
            d = []
            for _ in range(4000):
                ix = [random.randrange(len(N)) for _ in N]
                d.append(100 * (sum(A[i][0] for i in ix) - sum(N[i][0] for i in ix)) / sum(N[i][1] for i in ix))
            d.sort(); print("  %s: %s minus NeMo WER pts %.1f  95%% CI [%.1f, %.1f]" % (grp, lab, sum(d) / len(d), d[100], d[3900]))
print("\n== by clip length (Whisper normalizer), clips / vosk / whisper-small / nemo")
for lo, hi in ((0, 10), (10, 20), (20, 30)):
    sub = [r for r in rows if lo <= r[3] < hi]
    if sub: print("%2d-%2ds n=%3d" % (lo, hi, len(sub)), *["%6.1f" % wer(sub, e, NORMS["whisper-english-normalizer"]) for e in ("vosk", "whisper-small", "nemo")])
print("max clip duration: %.1f s (app window 28 s)" % max(r[3] for r in rows))
# error categories for NeMo vs Whisper small under the plain normalizer, FLEURS: ref tokens that are digits
fl = [r for r in rows if r[0] == "fleurs"]
dig = sum(1 for r in fl if re.search(r"\d", r[1]))
print("\nFLEURS clips whose reference contains digits: %d / %d" % (dig, len(fl)))
n = int(sys.argv[sys.argv.index("--examples") + 1]) if "--examples" in sys.argv else 8
nf = NORMS["whisper-english-normalizer"]
worst = sorted(rows, key=lambda r: -(jiwer.wer(nf(r[1]), nf(r[2]["nemo"])) - jiwer.wer(nf(r[1]), nf(r[2]["whisper-small"]))) if nf(r[1]) else 0)[:n]
for s, ref, h, d in worst:
    print("\n[%s %.0fs] REF   %s\n  NEMO  %s\n  SMALL %s" % (s, d, ref[:300], h["nemo"][:300], h["whisper-small"][:300]))
