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
def LIC(pk, code):
    out = []
    for a, *_ in pk:
        l = ("MIT" if code == "sv" else "Apache-2.0") if a == "VOSK" else SHIPM[a][1]
        if l not in out: out.append(l)
    return " / ".join(out)
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
    big = a in ("WHISPER_SMALL", "STARLING", "OMNILINGUAL_300M", "ZIPFORMER_RU", "ZIPFORMER_VI")
    if big:
        sm = [x for x in pk[1:]]
        low = (SHIPM[sm[0][0]][0] + " %.1f%%" % sm[0][1]) if sm else "needs a bigger phone"
    else: low = "same"
    ci = ""
    if c is not None: ci = " [%.1f-%.1f]" % (c["lo"], c["hi"])
    elif code not in EXISTING_PICKS: pass
    status = lvl(e)
    if code == "ar": status = "Experimental (dialect)"
    rows.append((names[code][0], names[code][1], eng, "%.1f%s%s" % (e, ci, "*" if code in CHAR else ""), status, low, vz(code), LIC(pk, code)))
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

# ---------- rest of the document
perf = {}
for f in glob.glob("out/perf*.jsonl"):
    for l in open(f):
        if l.startswith("{"):
            d = json.loads(l); perf[d["engine"]] = d
def pf(e, k, fmt="%d"):
    return (fmt % perf[e][k]) if e in perf else "-"
md.append("""
Notes on the table:
* **Status** follows the written rule: error at most 30% is Supported, 30 to 50% is Experimental (the app says so, with the
  number), above 50% is not offered. Uzbek (29.1, interval reaches 36.6), Georgian and Tamil sit on or near a line; treat
  their label as soft. **Arabic** earns "Supported" on standard Arabic read speech (18.4%) but is held at Experimental: the
  same engines scored 66 to 73% on Egyptian-dialect broadcast speech (below).
* **Phones under 5 GB RAM**: the engine that runs instead when the phone reports less than 5e9 bytes of RAM (a 6 GB phone
  reports about 5.7e9, a 4 GB phone about 3.8e9). "needs a bigger phone" means every engine for the language is a big one; the
  picker lists the language under "Needs a bigger phone" and transcription says so instead of running something that gets killed.
* **Fast (Vosk)** is the existing default engine. For languages where Vosk scored above 50% or has no model, Fast is not
  offered and the Accurate engine is always used (the settings screen says so). Vosk small models in the first ten languages
  are unpinned downloads as before; every Vosk model added here is pinned by size and SHA-256.
* The Vosk row of Catalan, Kazakh, Persian, Georgian and Uzbek is the only engine we shipped for them. Vosk lists its own
  FLEURS results for Persian, Kazakh and Georgian on its model page, so it is possible those models were tuned against FLEURS;
  we cannot tell, and the number may flatter them.
""")
md.append("""## How the app uses this

* **Picker** (Settings, Transcription Language): native name first, a Supported or Experimental badge, the Accurate download size and the
  Fast size before anything is downloaded, a note when this phone has less memory than the best model needs, and a section for
  languages that need a bigger phone. Languages that were measured and failed (Hebrew, Urdu) are listed greyed with the closest
  available language. Arabic carries a plain warning that dialect speech is much worse.
* **Quality setting.** Fast (Vosk) stays the default and is untouched for the first ten languages. For a new language with no Fast model,
  or whose Fast model scored above 50%, the Accurate engine is always used and the settings screen says so.
* **Memory.** Below 5e9 bytes of reported RAM an engine marked big is skipped for the next one in the language's list; if nothing
  is left, transcription stops with a message that the language needs a phone with 6 GB or more.
* **Word times.** Every engine except Whisper returns token times, used as they are (leading spaces mark word starts, so Arabic words
  and Thai pieces group correctly). Whisper has none; `distributeOverSpeech` spreads its words over the speech. Thai, Lao, Khmer and
  Burmese pieces are treated like Chinese characters: each piece is a timed word and pieces join with no spaces, so the phrase spaces
  an engine puts in Thai are not kept. The Vietnamese Zipformer writes capitals; the app lower-cases them.
* **Right-to-left.** The transcript view is one Compose text per paragraph, which takes its direction from the first strong letter.
  SRT and VTT cues that contain Arabic, Persian, Urdu or Hebrew letters get a right-to-left mark so other players lay them out right
  to left even when a cue starts with a digit or a Latin word. Search ignores Arabic and Hebrew vowel marks, folds alef/yeh/teh
  marbuta variants, and searches Thai character by character. Arabic and Urdu question marks and full stops end a sentence.
* **Not changed:** the live transcript (Vosk only, so it says it is unavailable for languages with no Fast model), "Mark uncertain
  words" (stays off for the new languages: it was only measured for seven), voice-data contribution (new languages upload with their
  language tag like the others; the raw transcript of an Experimental language is likely to contain errors).

""")
md.append("## Engines considered\n")
md.append("Sizes are what the user downloads (the release archive, or the int8 files for Whisper). RAM is the peak resident memory of a fresh Mac Python process decoding three ~28 s windows with 4 threads (about 50 MB of that is Python); it is not a phone number. The only phone measurements ever made are the old ones in tools/asr-bench/README.md (Whisper small about 1.3 GB, NeMo about 0.38 GB on a Pixel 9 Pro).\n")
md.append("| Engine | Languages | Download | Mac peak RAM | Speed (x real time, Mac) | Licence | Word times | Verdict |\n|---|---|---|---|---|---|---|---|")
INV = [
 ("Vosk small", "about 20: ar, ca, cs, fa, hi, it, ka, kk, ky, nl, pl, ru, sv, te, tg, tr, uk, uz, vi + the first ten", "31-304 MB each", pf("vosk","peak_rss_mb"), pf("vosk","rtf","%.2f"), "Apache-2.0 / MIT (Arabic small-ar-0.3: not stated)", "yes", "Shipped for 11 languages; not for Arabic (licence unstated)"),
 ("Whisper tiny / base / small / medium (int8)", "about 99", "104 / 160 / 375 / 945 MB", "base %s, small %s" % (pf("whisper-base","peak_rss_mb"), pf("whisper-small","peak_rss_mb")), "base %s, small %s" % (pf("whisper-base","rtf","%.2f"), pf("whisper-small","rtf","%.2f")), "MIT", "no (int8 export has no cross-attention; WordTiming spreads words over the speech)", "Small shipped; tiny and base not integrated; medium over budget"),
 ("SenseVoice (int8)", "zh, yue, en, ja, ko", "237 MB", "-", "-", "see NOTICE.md", "yes", "Existing (zh)"),
 ("NeMo FastConformer CTC en/de/es/fr", "en, de, es, fr", "103 MB", "509 (old run)", "-", "CC-BY-4.0", "yes", "Existing"),
 ("NeMo FastConformer CTC 10-language", "be, de, en, es, fr, hr, it, pl, ru, uk", "102 MB", pf("nemo10","peak_rss_mb"), pf("nemo10","rtf","%.2f"), "CC-BY-4.0 assumed: NVIDIA NGC page not machine-readable; same family as the model already shipped. Verify before release", "yes", "Shipped for pl, uk. Croatian scored 88%: the model lists it but FLEURS Croatian fails"),
 ("Parakeet TDT 0.6B v3 (int8)", "25 European", "487 MB", pf("parakeet-v3","peak_rss_mb"), pf("parakeet-v3","rtf","%.2f"), "CC-BY-4.0", "yes (token and duration)", "Not shipped: over the 400 MB budget and only Danish would need it (Whisper small already gives Danish 34.8%). Best Italian (3.4%), Dutch (12.1%), Polish (9.5%), Bulgarian (12.9%) of anything tested: a candidate for a later optional download"),
 ("Starling streaming Zipformer", "ar, en, id, ja, ru, th, vi, zh", "259 MB", pf("stream-zipformer-multi","peak_rss_mb"), pf("stream-zipformer-multi","rtf","%.2f"), "Apache-2.0 (training data licences not audited)", "yes", "Shipped for ar, id (big-phone engine)"),
 ("Zipformer Russian (Vosk project)", "ru", "60 MB", pf("zipformer-ru","peak_rss_mb"), pf("zipformer-ru","rtf","%.2f"), "Apache-2.0", "yes", "Shipped"),
 ("GigaAM v3 CTC Russian", "ru", "163 MB", "397 (short clips)", "0.04", "MIT", "yes", "Not shipped: 8.8% vs Zipformer 9.5%, inside the 2 point tie rule, and 3x larger"),
 ("Zipformer Vietnamese (zzasdf)", "vi", "60 MB", pf("zipformer-vi","peak_rss_mb"), pf("zipformer-vi","rtf","%.2f"), "Apache-2.0", "yes", "Shipped (writes capitals, lower-cased by the app)"),
 ("Zipformer Vietnamese 30M (hynt)", "vi", "26 MB", "344 (short clips)", "0.01", "CC-BY-NC-ND-4.0", "yes", "NOT shippable: non-commercial, no derivatives. 8.2% would have been the smallest model"),
 ("Zipformer Thai (GigaSpeech2)", "th", "696 MB archive (157 MB int8 files)", "670 (short clips)", "-", "not checked", "yes", "Not integrated: 11.0% vs Dolphin base 14.2% (within noise), HF repo gated, archive is mostly fp32"),
 ("Streaming Zipformer Bengali (Vosk project)", "bn", "87 MB", pf("stream-zipformer-bn","peak_rss_mb"), pf("stream-zipformer-bn","rtf","%.2f"), "Apache-2.0", "yes", "Shipped"),
 ("Dolphin base CTC (int8)", "40 Eastern languages (ar, bn, fa, hi, id, ms, th, tl, ur, vi, ru ...; no Turkish)", "81 MB", pf("dolphin-base","peak_rss_mb"), pf("dolphin-base","rtf","%.2f"), "Apache-2.0", "yes", "Shipped: Thai, and the small-phone engine for Arabic, Indonesian, Malay, Tagalog"),
 ("Dolphin small CTC (int8)", "same", "192 MB", pf("dolphin-small","peak_rss_mb"), pf("dolphin-small","rtf","%.2f"), "Apache-2.0", "yes", "Not shipped: Arabic 20.4% vs Starling 18.4%, Malay 26.2% vs Whisper 23.0%; no language only it covers"),
 ("Omnilingual ASR 300M CTC (int8)", "1,600 (measured on 30)", "293 MB", pf("omnilingual-300m","peak_rss_mb"), pf("omnilingual-300m","rtf","%.2f"), "Apache-2.0", "yes", "Shipped for the languages nothing smaller covers: bg, hr, cs, fi, ro, sk, sl, sw, tl, hu, el, ta, te"),
 ("Moonshine base (ar, uk, vi, es, ja, zh); tiny (ja, ko)", "see left", "30-119 MB", "about 500", "-", "Moonshine Community License: NON-COMMERCIAL for non-English", "n/a", "NOT shippable. Also scored 83% (ar), 69% (uk), 90% (vi) in our run"),
 ("Qwen3-ASR 0.6B (int8)", "52", "879 MB", "-", "-", "Apache-2.0", "-", "Not benchmarked: over budget"),
 ("Cohere Transcribe 14-language", "14", "1.7 GB", "-", "-", "-", "-", "Not benchmarked: over budget"),
 ("Whisper turbo / distil-large / large", "about 99", "564 MB and up", "-", "-", "MIT", "no", "Not benchmarked: over budget"),
 ("FireRed ASR, FunASR-nano, TeleSpeech, Paraformer, WeNet", "zh/en (and dialects)", "-", "-", "-", "-", "-", "Not relevant to the new languages"),
]
for r in INV: md.append("| " + " | ".join(str(x) for x in r) + " |")
md.append("\nTimestamps were checked directly (`lang_timestamps.py`): every engine above except Whisper returns one time per token; the streaming engines return them through the online recognizer.\n")

# matrix
md.append("## All measurements\n")
md.append("Error % on the 25 FLEURS clips (CER for th). `-` = not run. Vosk columns include models we do not ship (Czech, Telugu, Vietnamese failed the bar). Whisper small is close to useless on bn (100.8), ta (80.6), sw (74.9) and hi (90.7); it was not run on Telugu.\n")
cols = ["vosk","whisper-small","whisper-base","whisper-medium","stream-zipformer-multi","dolphin-base","dolphin-small","omnilingual-300m","nemo10","parakeet-v3","zipformer-ru","zipformer-vi","zipformer-th","stream-zipformer-bn","gigaam-v3-ru"]
md.append("| lang | " + " | ".join(cols) + " |\n|" + "---|" * (len(cols) + 1))
allcodes = [c for c in S if c not in ("ar-tn", "arEG")]
for code in sorted(allcodes):
    md.append("| " + code + " | " + " | ".join(("%.1f" % S[code][e]["err"]) if e in S[code] else "-" for e in cols) + " |")

# Arabic
md.append("\n## Arabic, honestly\n")
ar = {e: S["ar"][e] for e in S["ar"]}; eg = S.get("arEG", {})
md.append("Standard Arabic (FLEURS ar_eg: formal sentences read aloud) and Egyptian dialect (MGB-3 test, 26 clips taken across the broadcast genres, 4-20 s, dialect spelling in the reference with tags removed):\n")
md.append("| engine | standard Arabic read speech | Egyptian dialect broadcast |\n|---|---|---|")
for e in ["stream-zipformer-multi","omnilingual-300m","whisper-medium","dolphin-small","whisper-small","vosk","dolphin-base"]:
    a = ("%.1f [%.0f-%.0f]" % (ar[e]["err"], ar[e]["lo"], ar[e]["hi"])) if e in ar else "-"
    b = ("%.1f [%.0f-%.0f]" % (eg[e]["err"], eg[e]["lo"], eg[e]["hi"])) if e in eg else "-"
    md.append(f"| {e} | {a} | {b} |")
if "ar-tn" in S: md.append("| vosk Tunisian (ar-tn) on standard Arabic | %.1f | - |" % S["ar-tn"]["vosk"]["err"])
md.append("""
* Standard Arabic is usable (about 1 word in 5 wrong). Everyday dialect is not: two thirds of the words wrong, for every engine.
  Part of that is spelling (MGB-3 writes dialect words its own way, so a recogniser that outputs the standard spelling is
  marked wrong), but not all of it: reading the output, the best engines give the gist of a sentence with many words wrong, merged or dropped (the nine words of one clip came back as four), which is readable by someone who knows what was said and not much more.
* **Gulf, Levantine and Maghrebi dialects were not measured.** No open, ungated test set for them was reachable (Casablanca and
  the Saudi set are gated by hand approval; the commercial conversational sets list no files). A user in the UAE speaking
  Gulf Arabic should expect something between the two columns at best. This is why Arabic is labelled Experimental in the app.
* Whisper medium scored 15.4% on standard Arabic but is 945 MB and needs a much bigger phone; not offered.
* Vosk's only small Arabic model has no stated licence, and the Tunisian one scores 75% on standard Arabic; neither ships.
* Output has no diacritics and no punctuation from the streaming engine. Numbers come out as digits from Starling, as words
  from Dolphin.
""")
md.append("## Measured and not offered\n")
md.append("""| Language | Best result | Why |
|---|---|---|
| Hebrew | 50.9% (Omnilingual); Whisper small 58.2% | Over the 50% bar. Nothing close to offer. |
| Urdu | 51.8% (Whisper small); Omnilingual 68.8%, Dolphin 56.6% | Over the bar. Closest available: Hindi (spoken Urdu and Hindi are close; the script differs, so the Hindi transcript is Devanagari) |
""")
md.append("""## Re-check of Hindi and Italian

Hindi: Vosk 16.4% is still the best (same as October). Whisper small 90.7% (identical to October), Omnilingual 30.5%, Dolphin base 35.2%.
Italian: Vosk 8.8%, NeMo 10-language 8.3%, Whisper small 9.8% (identical to October), Omnilingual 9.3%, and **Parakeet v3 3.4%**.
Italian and Hindi routing is unchanged in this change. Parakeet is the one real improvement found for an existing language; it needs
the 487 MB download and a 6 GB phone, so it is left for a decision (see the Parakeet row).
""")
md.append("""## Method

* **Data.** Google FLEURS test split, read through the Hugging Face parquet conversion; the first 25 utterances of 4 to 25 seconds per
  language (about 5 minutes). Egyptian Arabic: MGB-3 test, 2 clips from each of 13 row groups. Test sets were kept out of the repository.
* **Engines.** The exact files the app downloads (hashes checked byte for byte), run with sherpa-onnx 1.13.8 (the version of the
  AAR in the app) and 4 threads, or Vosk small. Whisper with `language=` set and token timestamps requested, as in the app.
  Streaming models were fed a whole window plus one second of silence.
* **Scoring.** NFKC, lower case, punctuation and symbols to spaces; Arabic, Persian, Urdu and Hebrew vowel marks and Arabic
  tatweel removed; Arabic-Indic and Indic digits mapped to ASCII; no other normalisation (no spelling-out of numbers, no alef
  folding in the headline number). Word error rate, or character error rate with spaces removed for zh, ja, ko, th. 95% interval:
  bootstrap over clips, 1,000 resamples. Folding alef/teh marbuta/yeh variants (the `--fold` option) moves Arabic scores by 0 to 5 points.
* **Choosing.** Per language: candidates are shippable engines (licence permits it, integrated, at most 50%). The lowest error
  wins, except that among engines within 2 points of the best (25 clips cannot separate them) the one needing the least memory,
  then the smaller download, wins. Engines over 400 MB are used only if nothing within budget reaches 30%. If the winner needs
  more than 5e9 bytes of RAM, the best small engine is listed as the fallback for smaller phones.

### Limits

* Read speech overstates real accuracy, for every language. The Mac is not a phone; the speeds in the engine table were
  measured with other jobs running on the machine (load average 7 to 15 on 10 cores) and are indicative only.
* 25 clips is small: intervals of plus or minus 5 to 10 points are normal, so rankings inside that are not real.
* Numbers: FLEURS references keep digits; some engines print numbers as words (Dolphin), which costs them points that real users may not mind.
* Contamination: Whisper, Omnilingual and several other models may have seen FLEURS-like text or audio in training. We cannot check.
* The old numbers for the first ten languages are from the October run (same data source, same scorer); they were not re-run except Hindi and Italian.

## What was not measured

* Anything on a phone: memory, speed, battery, and whether the Kotlin configuration of each new engine loads the model. The new engine
  code paths (offline transducer, streaming transducer, Dolphin, Omnilingual) compile against the pinned sherpa-onnx 1.13.8 API but have
  not been run on a device.
* Noisy, accented, telephone or multi-speaker speech in any new language; dialects other than Egyptian Arabic.
* Speaker labels, "mark uncertain words" and the summary for the new languages (mark-uncertain stays off for them; summaries have no
  stopword lists for them; the summariser's sentence splitting for Thai, which has no spaces, is untested).
* Whisper small on Telugu (it scored 81 to 101% on Bengali and Tamil), Whisper base/tiny beyond nine major languages, and Dolphin on languages it does not list; the matrix shows every cell that was run.
* Whisper medium/turbo/large, Qwen3-ASR, Cohere Transcribe (over the size budget).
* Hebrew and Urdu beyond the engines listed; Punjabi, Gujarati, Marathi, Nepali, Sinhala, Burmese, Khmer, Lao, Amharic and the other
  languages Omnilingual or Dolphin nominally cover (no time; no engine of ours was checked on them).
* Play listing text and screenshots for the new markets are a separate task and are not part of this change.
* Licences: the NeMo 10-language model's licence (NVIDIA NGC page) and the training-data licences behind Starling, Dolphin,
  Omnilingual and the Zipformer models were not audited; the model-card licences are what is recorded.
""")
open(WT + "/docs/LANGUAGES.md", "w").write("\n".join(md) + "\n")
print(len(rows), "rows")
