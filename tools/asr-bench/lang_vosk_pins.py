"""Downloads the Vosk small zips the app pins and prints/writes size and SHA-256 (streamed, nothing kept)."""
import hashlib, json, urllib.request
names = ["vosk-model-small-tr-0.3","vosk-model-small-ca-0.4","vosk-model-small-kz-0.42","vosk-model-small-uz-0.22","vosk-model-small-ka-0.42","vosk-model-small-fa-0.42","vosk-model-small-nl-0.22","vosk-model-small-sv-rhasspy-0.15","vosk-model-small-ru-0.22","vosk-model-small-pl-0.22","vosk-model-small-uk-v3-small"]
out = {}
for n in names:
    h = hashlib.sha256(); size = 0
    with urllib.request.urlopen(f"https://alphacephei.com/vosk/models/{n}.zip") as r:
        while True:
            b = r.read(1 << 20)
            if not b: break
            h.update(b); size += len(b)
    out[n + ".zip"] = {"size": size, "sha256": h.hexdigest()}; print(n, size, h.hexdigest(), flush=True)
json.dump(out, open("out/vosk_pins.json", "w"), indent=1)
