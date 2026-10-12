import json, glob, re, unicodedata, numpy as np, soundfile as sf
from vosk import Model, KaldiRecognizer, SetLogLevel
SetLogLevel(-1)
vm = Model("models/vosk-model-small-en-us-0.15")
R = json.load(open("ml_results_small.json"))["en"]
items = [(r["wav"], r["whisper"]) for r in R]
for f in sorted(glob.glob("data/*.json"))[:2]:
    pass
def norm(w): return re.sub(r"[^\w]", "", unicodedata.normalize("NFKC", w).lower())
def vosk_words(y):
    rec = KaldiRecognizer(vm, 16000); rec.SetWords(True)
    pcm = (np.clip(y, -1, 1) * 32767).astype("<i2").tobytes()
    for i in range(0, len(pcm), 8192): rec.AcceptWaveform(pcm[i:i + 8192])
    return json.loads(rec.FinalResult()).get("result", [])
def align(a, b):
    n, m = len(a), len(b); d = [[0]*(m+1) for _ in range(n+1)]
    for i in range(n+1): d[i][0] = i
    for j in range(m+1): d[0][j] = j
    for i in range(1,n+1):
        for j in range(1,m+1): d[i][j] = min(d[i-1][j-1]+(a[i-1]!=b[j-1]), d[i-1][j]+1, d[i][j-1]+1)
    i,j,pairs=n,m,[]
    while i>0 and j>0:
        if d[i][j]==d[i-1][j-1]+(a[i-1]!=b[j-1]):
            if a[i-1]==b[j-1]: pairs.append((i-1,j-1))
            i-=1; j-=1
        elif d[i][j]==d[i-1][j]+1: i-=1
        else: j-=1
    return pairs[::-1]
def voiced_spans(y, frame=320, floor_db=-45):
    n = len(y)//frame; e = np.sqrt((y[:n*frame].reshape(n,frame)**2).mean(axis=1)+1e-12)
    db = 20*np.log10(e/ (np.percentile(e,95)+1e-9) + 1e-9)
    v = db > floor_db/ 1.0 * 0.55  # relative to loud speech level
    # smooth: fill short gaps (<150 ms), drop blips (<60 ms)
    v = v.copy()
    gap = int(0.15*16000/frame)
    i=0
    while i<n:
        if not v[i]:
            j=i
            while j<n and not v[j]: j+=1
            if 0<i and j<n and (j-i)<=gap: v[i:j]=True
            i=j
        else: i+=1
    spans=[]; i=0
    while i<n:
        if v[i]:
            j=i
            while j<n and v[j]: j+=1
            if (j-i)*frame/16000>=0.06: spans.append((i*frame/16000, j*frame/16000))
            i=j
        else: i+=1
    return spans or [(0, len(y)/16000)]
def distribute(words, spans):
    weights = np.array([max(len(w),1) for w in words], float)
    total = sum(e-s for s,e in spans); cum = np.concatenate([[0], np.cumsum(weights)])/weights.sum()*total
    def at(t):  # map voiced-time t -> real time
        acc=0
        for s,e in spans:
            if t<=acc+(e-s): return s+(t-acc)
            acc+=e-s
        return spans[-1][1]
    return [at(cum[i]) for i in range(len(words))]
errA, errB, errC = [], [], []
for wav, text in items:
    y,_ = sf.read(wav, dtype="float32"); dur = len(y)/16000
    ww = [w for w in text.split() if norm(w)]
    vw = vosk_words(y)
    if not ww or not vw: continue
    pairs = align([norm(w) for w in ww], [norm(v["word"]) for v in vw])
    n = len(ww)
    A = [dur*i/n for i in range(n)]
    B = distribute([norm(w) for w in ww], voiced_spans(y))
    # C: first/last word anchored to voiced extent, chars-proportional inside
    sp = voiced_spans(y); C = distribute([norm(w) for w in ww], [(sp[0][0], sp[-1][1])])
    for i,j in pairs:
        t = vw[j]["start"]; errA.append(abs(A[i]-t)); errB.append(abs(B[i]-t)); errC.append(abs(C[i]-t))
for name,e in [("even spread over window (current)",errA),("anchored to speech extent, by chars",errC),("proportional over voiced regions",errB)]:
    e=np.array(e); print("%-38s mean |start err| %.2fs  median %.2fs  within 0.3s: %2.0f%%  within 0.5s: %2.0f%%  (n=%d matched words)"%(name,e.mean(),np.median(e),100*(e<=0.3).mean(),100*(e<=0.5).mean(),len(e)))
