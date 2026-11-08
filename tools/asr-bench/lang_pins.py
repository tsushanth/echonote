"""Downloads each release archive, hashes the archive and the files taken out of it (streamed, nothing kept), checks the
archive against the release's checksum.txt, and prints the pins the app uses. Usage: lang_pins.py NAME..."""
import os, sys, hashlib, tarfile, bz2, json, urllib.request
REL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"
sums = {l.split("\t")[0]: l.split("\t")[1].strip() for l in urllib.request.urlopen(f"{REL}/checksum.txt").read().decode().splitlines() if "\t" in l}
out = {}
for name in sys.argv[1:]:
    url = f"{REL}/{name}.tar.bz2"; h = hashlib.sha256(); size = 0
    tmp = f"models/_pin.tar.bz2"
    with urllib.request.urlopen(url) as r, open(tmp, "wb") as f:
        while True:
            b = r.read(1 << 20)
            if not b: break
            h.update(b); f.write(b); size += len(b)
    files = {}
    with tarfile.open(tmp, "r:bz2") as t:
        for m in t:
            if not m.isfile(): continue
            n = m.name.split("/")[-1]
            if n.endswith((".onnx", ".txt", ".ort")) and "test_wavs" not in m.name and n not in ("README.md",):
                d = hashlib.sha256(); s = 0; fh = t.extractfile(m)
                while True:
                    b = fh.read(1 << 20)
                    if not b: break
                    d.update(b); s += len(b)
                files[n] = (s, d.hexdigest())
    os.remove(tmp)
    ok = sums.get(name + ".tar.bz2") == h.hexdigest()
    out[name] = {"url": url, "size": size, "sha256": h.hexdigest(), "matches_release_checksum_txt": ok, "files": files}
    print(name, size, h.hexdigest(), "checksum.txt match:", ok, flush=True)
json.dump(out, open("out/pins_" + sys.argv[1][:30] + ".json", "w"), indent=1)
