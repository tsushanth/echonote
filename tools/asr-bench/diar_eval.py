import pickle, random, itertools, os, sys, numpy as np, sherpa_onnx
utts = pickle.load(open("diar_utts.pkl", "rb"))
spks = sorted(utts)
rng = random.Random(7)
GAP = np.zeros(8000, dtype="float32")
mixes = []
for k in range(16):
    n = 2 if k % 2 == 0 else 3
    chosen = rng.sample(spks, n)
    if n == 2:
        order = [(chosen[0], 0), (chosen[1], 0), (chosen[0], 1), (chosen[1], 1 if len(utts[chosen[1]]) > 1 else 0)]
    else:
        order = [(chosen[0], 0), (chosen[1], 0), (chosen[2], 0), (chosen[0], 1)]
    audio, ref, t = [], [], 0.0
    for s, i in order:
        y = utts[s][i]
        ref.append((t, t + len(y) / 16000, s))
        audio += [y, GAP]; t += len(y) / 16000 + 0.5
    mixes.append((np.concatenate(audio), ref, n))

def make(th):
    cfg = sherpa_onnx.OfflineSpeakerDiarizationConfig(
        segmentation=sherpa_onnx.OfflineSpeakerSegmentationModelConfig(
            pyannote=sherpa_onnx.OfflineSpeakerSegmentationPyannoteModelConfig(model="models/segmentation.onnx"), num_threads=2),
        embedding=sherpa_onnx.SpeakerEmbeddingExtractorConfig(model=os.environ.get("EMB","models/embedding.onnx"), num_threads=2),
        clustering=sherpa_onnx.FastClusteringConfig(num_clusters=-1, threshold=th), min_duration_on=0.3, min_duration_off=0.5)
    return sherpa_onnx.OfflineSpeakerDiarization(cfg)

def score(segs, ref, total):
    fr = 100
    nref = int(total * fr) + 1
    R = np.full(nref, -1, dtype=int); H = np.full(nref, -1, dtype=int)
    names = sorted({s for _, _, s in ref}); rid = {s: i for i, s in enumerate(names)}
    for a, b, s in ref: R[int(a * fr):int(b * fr)] = rid[s]
    for s in segs: H[int(s.start * fr):int(s.end * fr)] = s.speaker
    hl = sorted({int(x) for x in H if x >= 0})
    speech = R >= 0
    from scipy.optimize import linear_sum_assignment
    C = np.zeros((max(len(hl), 1), len(names)))
    for i, h in enumerate(hl):
        for j in range(len(names)):
            C[i, j] = int(((H == h) & (R == j)).sum())
    ri, ci = linear_sum_assignment(-C)
    best = int(C[ri, ci].sum())
    return 1 - best / max(int(speech.sum()), 1), len(hl)

print("threshold  speaker-count exact  mean |count err|  error (share of speech on wrong/no speaker)")
for th in [float(x) for x in os.environ.get('TH','0.5,0.7,0.9').split(',')]:
    sd = make(th); exact = 0; cerr = 0; errs = []; by_n = {2: [], 3: []}
    for y, ref, n in mixes:
        segs = sd.process(y).sort_by_start_time()
        e, k = score(segs, ref, len(y) / 16000)
        exact += (k == n); cerr += abs(k - n); errs.append(e); by_n[n].append(e)
    print("  %.2f      %2d/%d              %.2f             %.1f%%  (2-spk %.1f%%, 3-spk %.1f%%)" % (
        th, exact, len(mixes), cerr / len(mixes), 100 * np.mean(errs), 100 * np.mean(by_n[2]), 100 * np.mean(by_n[3])), flush=True)
