import json, glob, collections
import jiwer
from whisper_normalizer.english import EnglishTextNormalizer
norm = EnglishTextNormalizer()
items = []
for f in sorted(glob.glob("data/*.json")):
    items += [dict(x, subset=f.split("/")[-1][:-5]) for x in json.load(open(f))]
names = ["vosk", "whisper", "moonshine"]
res = {n: json.load(open(f"results/{n}.json")) for n in names}

def words(s):
    return norm(s).split()

def align(ref, hyp):
    """Levenshtein alignment; returns list of (ref_idx|None, hyp_idx|None)."""
    n, m = len(ref), len(hyp)
    d = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(n + 1): d[i][0] = i
    for j in range(m + 1): d[0][j] = j
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            d[i][j] = min(d[i-1][j-1] + (ref[i-1] != hyp[j-1]), d[i-1][j] + 1, d[i][j-1] + 1)
    i, j, ops = n, m, []
    while i > 0 or j > 0:
        if i > 0 and j > 0 and d[i][j] == d[i-1][j-1] + (ref[i-1] != hyp[j-1]):
            ops.append((i-1, j-1)); i -= 1; j -= 1
        elif i > 0 and d[i][j] == d[i-1][j] + 1:
            ops.append((i-1, None)); i -= 1
        else:
            ops.append((None, j-1)); j -= 1
    return ops[::-1]

def rover(pivot, others):
    """Majority vote across pivot + others, using the pivot as the backbone; ties keep the pivot."""
    maps = []
    for o in others:
        sub, ins, gap = {}, collections.defaultdict(list), -1
        for pi, oj in align(pivot, o):
            if pi is not None:
                sub[pi] = o[oj] if oj is not None else None
                gap = pi
            else:
                ins[gap].append(o[oj])
        maps.append((sub, ins))
    out = []
    def insert_at(g):
        lists = [tuple(m[1].get(g, [])) for m in maps]
        # insert words that a majority (of all engines) agree on
        cnt = collections.Counter(l for l in lists if l)
        for l, c in cnt.items():
            if c + 0 >= (len(others) + 1) // 2 + (0 if len(others) % 2 == 0 else 0) and c >= 2:
                out.extend(l); break
    insert_at(-1)
    for i, w in enumerate(pivot):
        votes = collections.Counter([w] + [m[0].get(i) for m in maps])
        best, c = max(votes.items(), key=lambda kv: (kv[1], kv[0] == w))
        if best is not None: out.append(best)
        insert_at(i)
    return out

def corpus_wer(refs, hyps):
    pairs = [(r, h) for r, h in zip(refs, hyps) if r]
    return jiwer.wer([" ".join(r) for r, _ in pairs], [" ".join(h) if h else "" for _, h in pairs])

rows = collections.defaultdict(lambda: collections.defaultdict(lambda: ([], [])))
def add(sub, system, ref, hyp):
    for key in (sub, "ALL"):
        rows[key][system][0].append(ref); rows[key][system][1].append(hyp)

flag_stats = collections.Counter()
for it in items:
    ref = words(it["ref"])
    if not ref: continue
    H = {n: words(res[n][it["wav"]]["hyp"]) for n in names}
    for n in names: add(it["subset"], n, ref, H[n])
    add(it["subset"], "ROVER(all 3)", ref, rover(H["whisper"], [H["vosk"], H["moonshine"]]))
    add(it["subset"], "ROVER(whisper+moonshine+vosk, moonshine pivot)", ref, rover(H["moonshine"], [H["whisper"], H["vosk"]]))
    best = min(names, key=lambda n: jiwer.wer(" ".join(ref), " ".join(H[n])) if H[n] else 1.0)
    add(it["subset"], "oracle best-per-utterance (not achievable)", ref, H[best])
    # disagreement flagging: whisper words that neither other engine agrees with
    w_ops = align(ref, H["whisper"])
    correct = {j: (i is not None and ref[i] == H["whisper"][j]) for i, j in w_ops if j is not None}
    agree = []
    for o in ("vosk", "moonshine"):
        a = set()
        for pi, oj in align(H["whisper"], H[o]):
            if pi is not None and oj is not None and H["whisper"][pi] == H[o][oj]: a.add(pi)
        agree.append(a)
    for j, ok in correct.items():
        flagged = j not in agree[0] and j not in agree[1]
        flag_stats[(flagged, ok)] += 1

systems = list(rows["ALL"].keys())
subs = sorted(k for k in rows if k != "ALL") + ["ALL"]
print("WER % (lower is better), normalized with the Whisper English normalizer")
print("%-52s" % "system" + "".join("%11s" % s for s in subs))
for s in systems:
    print("%-52s" % s + "".join("%11.1f" % (100 * corpus_wer(*rows[k][s])) for k in subs))
n_words = sum(len(r) for r in rows["ALL"]["whisper"][0])
print("\nreference words:", n_words, " utterances:", len(rows["ALL"]["whisper"][0]))
tot_f = flag_stats[(True, True)] + flag_stats[(True, False)]
tot_u = flag_stats[(False, True)] + flag_stats[(False, False)]
print("whisper words flagged (neither other engine agrees): %d (%.1f%% of words); of those wrong: %.1f%%" % (
    tot_f, 100 * tot_f / (tot_f + tot_u), 100 * flag_stats[(True, False)] / max(tot_f, 1)))
print("whisper words not flagged: wrong: %.1f%%" % (100 * flag_stats[(False, False)] / max(tot_u, 1)))

# ---- paired bootstrap over utterances: is voting really better than whisper alone? ----
import random
def per_utt(system):
    out = []
    for r, h in zip(*rows["ALL"][system]):
        if not r: continue
        o = jiwer.process_words(" ".join(r), " ".join(h) if h else "")
        out.append((o.substitutions + o.deletions + o.insertions, len(r)))
    return out
W, R, V = per_utt("whisper"), per_utt("ROVER(all 3)"), per_utt("vosk")
random.seed(0)
diffs = []
for _ in range(5000):
    idx = [random.randrange(len(W)) for _ in W]
    nw = sum(W[i][0] for i in idx); nr = sum(R[i][0] for i in idx); tot = sum(W[i][1] for i in idx)
    diffs.append(100 * (nw - nr) / tot)
diffs.sort()
print("\nWER improvement of ROVER(all 3) over whisper alone, points: mean %.2f, 95%% CI [%.2f, %.2f]; better in %.0f%% of resamples" % (
    sum(diffs) / len(diffs), diffs[125], diffs[4875], 100 * sum(d > 0 for d in diffs) / len(diffs)))
tot_err = sum(e for e, _ in W)
print("whisper total word errors: %d; errors inside the %d flagged words: %d (%.0f%% of all errors)" % (
    tot_err, tot_f, flag_stats[(True, False)], 100 * flag_stats[(True, False)] / tot_err))

# ---- flagging with a single second engine (Vosk is already in the app) ----
for other in ("vosk", "moonshine"):
    st = collections.Counter()
    for it in items:
        ref = words(it["ref"])
        if not ref: continue
        hw, ho = words(res["whisper"][it["wav"]]["hyp"]), words(res[other][it["wav"]]["hyp"])
        ok = {j: (i is not None and ref[i] == hw[j]) for i, j in align(ref, hw) if j is not None}
        agree = {pi for pi, oj in align(hw, ho) if pi is not None and oj is not None and hw[pi] == ho[oj]}
        for j, good in ok.items():
            st[(j not in agree, good)] += 1
    f = st[(True, True)] + st[(True, False)]; u = st[(False, True)] + st[(False, False)]
    print("flag by %-9s: %4d words flagged (%.1f%%), %.1f%% of them wrong; unflagged wrong %.1f%%; catches %.0f%% of whisper errors" % (
        other, f, 100 * f / (f + u), 100 * st[(True, False)] / f, 100 * st[(False, False)] / u, 100 * st[(True, False)] / tot_err))
