import json, re, unicodedata, collections, jiwer, os
B = json.load(open("ml_results.json"))
S = json.load(open("ml_results_small.json")) if os.path.exists("ml_results_small.json") else {}
X = json.load(open("ml_extra.json"))
NOSPACE = ("zh", "ja", "ko")
def norm(s, nospace):
    s = unicodedata.normalize("NFKC", s).lower()
    s = "".join(" " if unicodedata.category(c)[0] in "PS" else c for c in s)
    s = re.sub(r"\s+", " ", s).strip()
    return s.replace(" ", "") if nospace else s
def toks(s, nospace): return list(s) if nospace else s.split()

def align(ref, hyp):
    n, m = len(ref), len(hyp)
    d = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(n + 1): d[i][0] = i
    for j in range(m + 1): d[0][j] = j
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            d[i][j] = min(d[i-1][j-1] + (ref[i-1] != hyp[j-1]), d[i-1][j] + 1, d[i][j-1] + 1)
    i, j, ops = n, m, []
    while i > 0 or j > 0:
        if i > 0 and j > 0 and d[i][j] == d[i-1][j-1] + (ref[i-1] != hyp[j-1]): ops.append((i-1, j-1)); i -= 1; j -= 1
        elif i > 0 and d[i][j] == d[i-1][j] + 1: ops.append((i-1, None)); i -= 1
        else: ops.append((None, j-1)); j -= 1
    return ops[::-1]

def rover(pivot, others):
    maps = []
    for o in others:
        sub, ins, gap = {}, collections.defaultdict(list), -1
        for pi, oj in align(pivot, o):
            if pi is not None: sub[pi] = o[oj] if oj is not None else None; gap = pi
            else: ins[gap].append(o[oj])
        maps.append((sub, ins))
    out = []
    def insert_at(g):
        cnt = collections.Counter(tuple(m[1].get(g, [])) for m in maps)
        for l, c in cnt.items():
            if l and c >= 2: out.extend(l); break
    insert_at(-1)
    for i, w in enumerate(pivot):
        votes = collections.Counter([w] + [m[0].get(i) for m in maps])
        best, c = max(votes.items(), key=lambda kv: (kv[1], kv[0] == w))
        if best is not None: out.append(best)
        insert_at(i)
    return out

def err(refs, hyps, nospace):
    pairs = [(r, h) for r, h in zip(refs, hyps) if r]
    f = jiwer.cer if nospace else jiwer.wer
    return 100 * f([r for r, _ in pairs], [h for _, h in pairs])

print("Error rate % (WER; CER for zh/ja), FLEURS 25 utterances per language, lower is better")
print("%-4s %7s %9s %9s %11s | %s" % ("lang", "vosk", "whisp-b", "whisp-s", "3rd family", "ROVER 3 (pivot = best single)"))
for lang in B:
    ns = lang in NOSPACE
    refs = [norm(r["ref"], ns) for r in B[lang]]
    eng = {"vosk": [norm(r["vosk"], ns) for r in B[lang]], "whisper-base": [norm(r["whisper"], ns) for r in B[lang]]}
    if lang in S: eng["whisper-small"] = [norm(r["whisper"], ns) for r in S[lang]]
    if lang in X: eng[X[lang]["engine"]] = [norm(r["text"], ns) for r in X[lang]["rows"]]
    sc = {k: err(refs, v, ns) for k, v in eng.items()}
    third = X[lang]["engine"] if lang in X else None
    cell = lambda k: ("%7.1f" % sc[k]) if k in sc else "      -"
    rv = "-"
    # diverse trio: vosk + best whisper + third family
    if third:
        wbest = min([k for k in ("whisper-base", "whisper-small") if k in sc], key=lambda k: sc[k])
        trio = ["vosk", wbest, third]
        pivot = min(trio, key=lambda k: sc[k])
        voted = []
        for i in range(len(refs)):
            p = toks(eng[pivot][i], ns); o = [toks(eng[k][i], ns) for k in trio if k != pivot]
            voted.append(" ".join(rover(p, o)) if not ns else "".join(rover(p, o)))
        rv = "%.1f  (%s)  vs best single %.1f" % (err(refs, voted, ns), "+".join(trio), min(sc[k] for k in trio))
    print("%-4s %s %s %s %11s | %s" % (lang, cell("vosk"), cell("whisper-base"), cell("whisper-small"),
          ("%.1f %s" % (sc[third], third[:5])) if third else "-", rv))
