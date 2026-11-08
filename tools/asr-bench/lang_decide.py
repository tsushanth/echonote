"""Turns out/scores.json into the per-language engine choice (the written rule is in docs/LANGUAGES.md and EnginePlan.kt).
Writes out/decision.json. Usage: python lang_decide.py"""
import json, collections
SC = json.load(open("out/scores.json"))
S = collections.defaultdict(dict)
for s in SC: S[s["lang"]][s["engine"]] = s

# engine -> (AsrEngine name, archive MB the user downloads, minRam class "small"|"big", licence)
SHIP = {
 "whisper-small": ("WHISPER_SMALL", 375, "big", "MIT"),
 "nemo10": ("NEMO_MULTILINGUAL", 102, "small", "CC-BY-4.0 (NVIDIA NeMo; NGC page not machine-readable, verify)"),
 "stream-zipformer-multi": ("STARLING", 259, "big", "Apache-2.0"),
 "zipformer-ru": ("ZIPFORMER_RU", 60, "small", "Apache-2.0"),
 "zipformer-vi": ("ZIPFORMER_VI", 60, "small", "Apache-2.0"),
 "stream-zipformer-bn": ("STREAMING_BN", 87, "small", "Apache-2.0"),
 "dolphin-base": ("DOLPHIN_BASE", 81, "small", "Apache-2.0"),
 "omnilingual-300m": ("OMNILINGUAL_300M", 293, "big", "Apache-2.0"),
}
# Vosk small zips that are fine to ship (licence stated Apache-2.0 or MIT on alphacephei.com/vosk/models) and their size in MB.
# Not shipped: ar (small-ar-0.3 has no licence stated), cs/te/vi (fail the bar anyway).
VOSK_OK = {"tr": 37, "ca": 43, "kk": 60, "uz": 51, "ka": 46, "fa": 53, "nl": 40, "sv": 304, "pl": 53, "uk": 144, "ru": 46,
           "it": 50, "hi": 44}
BUDGET_MB = 400; SUP = 30.0; EXP = 50.0; TIE = 2.0
EXISTING = {"en","es","fr","de","pt","zh","it","hi","ja","ko"}

def decide(lang):
    cands = []
    for eng, (app, mb, ram, lic) in SHIP.items():
        s = S[lang].get(eng)
        if s and s["err"] <= EXP: cands.append(dict(engine=eng, app=app, mb=mb, ram=ram, err=s["err"], lo=s["lo"], hi=s["hi"], rtf=s["rtf"]))
    v = S[lang].get("vosk")
    if v and lang in VOSK_OK and v["err"] <= EXP:
        cands.append(dict(engine="vosk", app="VOSK", mb=VOSK_OK[lang], ram="small", err=v["err"], lo=v["lo"], hi=v["hi"], rtf=v["rtf"]))
    inb = [c for c in cands if c["mb"] <= BUDGET_MB]; over = [c for c in cands if c["mb"] > BUDGET_MB]
    primary = None
    if inb:
        best = min(c["err"] for c in inb)
        near = [c for c in inb if c["err"] <= best + TIE]
        primary = min(near, key=lambda c: (c["ram"] == "big", c["mb"] if c["engine"] != "vosk" else 0, c["err"]))
    if (primary is None or primary["err"] > SUP):
        ob = [c for c in over if c["err"] <= SUP]
        if ob: primary = min(ob, key=lambda c: c["err"]); primary["optional_large"] = True
    if primary is None: return None
    picks = [primary]
    if primary["ram"] == "big":
        small = [c for c in cands if c["ram"] == "small" and c is not primary]
        if small: picks.append(min(small, key=lambda c: c["err"]))
    return picks

if __name__ == "__main__":
    out = {}
    for lang in sorted(S):
        if lang in ("ar-tn", "arEG") or lang in EXISTING: continue
        d = decide(lang)
        out[lang] = d
        print(lang, "->", "NOT OFFERED (best %.1f)" % min(s["err"] for s in S[lang].values()) if d is None else
              " | ".join("%s %.1f%s" % (c["app"], c["err"], " [big]" if c["ram"]=="big" else "") + (" LARGE" if c.get("optional_large") else "") for c in d))
    json.dump(out, open("out/decision.json", "w"), indent=1)
