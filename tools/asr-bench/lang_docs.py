"""Writes docs/LANGUAGES.md into the worktree given as argv[1] from out/scores.json, out/decision.json, out/perf*.jsonl."""
import json, sys, collections, glob, re
WT = sys.argv[1]
exec(open("lang_gen.py").read().split("# --- Recording.kt")[0].split("WT = sys.argv[1]")[1].replace("D = json.load", "D = json.load"))  # reuse tables (EXIST, NEW, picks, S, D)
SHIPM = {"WHISPER_SMALL": ("Whisper small", "MIT"), "NEMO_MULTILINGUAL": ("NeMo FastConformer 10-language", "CC-BY-4.0 (assumed, see below)"),
 "STARLING": ("Starling multilingual (streaming Zipformer)", "Apache-2.0"), "ZIPFORMER_RU": ("Zipformer Russian", "Apache-2.0"),
 "ZIPFORMER_VI": ("Zipformer Vietnamese", "Apache-2.0"), "STREAMING_BN": ("Zipformer Bengali (streaming)", "Apache-2.0"),
 "DOLPHIN_BASE": ("Dolphin base CTC", "Apache-2.0"), "OMNILINGUAL_300M": ("Omnilingual 300M CTC", "Apache-2.0"),
 "VOSK": ("Vosk small", "Apache-2.0 (Swedish: MIT)"),
 "NEMO_FAST_CONFORMER": ("NeMo FastConformer en/de/es/fr", "CC-BY-4.0"), "SENSE_VOICE": ("SenseVoice", "see NOTICE")}
ENG2KEY = {"WHISPER_SMALL": "whisper-small", "NEMO_MULTILINGUAL": "nemo10", "STARLING": "stream-zipformer-multi", "ZIPFORMER_RU": "zipformer-ru", "ZIPFORMER_VI": "zipformer-vi",
 "STREAMING_BN": "stream-zipformer-bn", "DOLPHIN_BASE": "dolphin-base", "OMNILINGUAL_300M": "omnilingual-300m", "VOSK": "vosk"}
MB = {"WHISPER_SMALL": 375, "NEMO_MULTILINGUAL": 102, "STARLING": 259, "ZIPFORMER_RU": 60, "ZIPFORMER_VI": 60, "STREAMING_BN": 87, "DOLPHIN_BASE": 81, "OMNILINGUAL_300M": 293,
      "NEMO_FAST_CONFORMER": 103, "SENSE_VOICE": 237, "VOSK": 0}
CHAR = {"zh", "ja", "ko", "th"}
def lvl(e): return "Supported" if e <= 30 else "Experimental"
rows = []
codes = [c[0] for c in EXIST] + [c for c in NEW if D.get(c)]
names = {c[0]: (c[2], c[3]) for c in EXIST}; names.update({c: (v[1], v[2]) for c, v in NEW.items()})
def vz(code):
    if code in EXISTING_VOSK: return "%.1f" % EXISTING_VOSK[code]
    v = S[code].get("vosk"); z = NEW[code][5] if code in NEW else None
    return ("%.1f" % v["err"]) if (v and z and v["err"] <= 50) else ("- (%.0f%%, not offered)" % v["err"] if v and v["err"] > 50 else "none")
for code in codes:
    if code in EXISTING_PICKS:
        pk = [(a, e, None) for a, e in EXISTING_PICKS[code]]
    else:
        pk = [(c["app"], c["err"], c) for c in D[code]]
    a, e, c = pk[0]
    eng = SHIPM[a][0] + (f" ({MB[a]} MB{', optional large download' if MB[a] > 400 else ''})" if a != "VOSK" else " (Fast model)")
    low = "same" if (len(pk) == 1 or pk[0][0] in ("NEMO_FAST_CONFORMER", "SENSE_VOICE")) and True else None
    big = a in ("WHISPER_SMALL", "STARLING", "OMNILINGUAL_300M")
    if big:
        sm = [x for x in pk[1:]]
        low = (SHIPM[sm[0][0]][0] + " %.1f%%" % sm[0][1]) if sm else "needs a bigger phone"
    else: low = "same"
    ci = ""
    if c is not None: ci = " [%.1f-%.1f]" % (c["lo"], c["hi"])
    elif code not in EXISTING_PICKS: pass
    status = lvl(e)
    if code == "ar": status = "Experimental (dialect)"
    rows.append((names[code][0], names[code][1], eng, "%.1f%s%s" % (e, ci, "*" if code in CHAR else ""), status, low, vz(code), SHIPM[a][1]))
rows.sort(key=lambda r: (0 if r[4] == "Supported" else 1, r[0]))
md = []
md.append("""# Languages and engines

What ClearVoice can transcribe on the phone, with which engine, how well it did in our test, and what we could not measure.
Generated from `tools/asr-bench` (see "Method"); the routing table in code is `LanguageSupport.kt`, generated from the same numbers.

**Read this first.** Every number below is from **FLEURS read speech** (people reading Wikipedia-style sentences into a good
microphone). Real recordings (noise, crosstalk, accents, dialect, phone calls) are harder: expect more mistakes than the
table shows. The test is 25 short clips (about 5 minutes) per language, so a single number carries several points of noise
(95% intervals in the table are from a bootstrap over clips). Nothing here was run on a phone; speed and memory are Mac
CPU numbers.

""")
md.append("## Result\n")
md.append(f"Languages the picker offers: **{len(codes)}** (10 before this change, {len(codes) - 10} added). Measured and not offered: Hebrew, Urdu (see below).\n")
md.append("Error % is word error rate, or character error rate (marked `*`) for Chinese, Japanese, Korean and Thai. "
          "\"Fast\" is the small Vosk model, scored on the same clips. The first ten languages keep their October 2026 numbers (tools/asr-bench/README.md); "
          "the rest are new. Languages with a big engine fall back to the smaller one on phones that report less than 5 GB of RAM.\n")
md.append("| Language | Native name | Accurate engine | Error % [95% CI] | Status | Phones under 5 GB RAM | Fast (Vosk) error % | Licence |\n|---|---|---|---|---|---|---|---|")
for r in rows: md.append("| " + " | ".join(r) + " |")
open(WT + "/docs/LANGUAGES.md", "w").write("\n".join(md) + "\n")
print(len(rows), "rows")
