"""Reads out/scores.json, out/decision.json (decide.py), out/vosk_pins_*.json and writes the language enum entries and
LanguageSupport.kt into the worktree given as argv[1]. Existing-language numbers are the 2026-10-11/12 benchmark."""
import json, sys, re, glob, collections
WT = sys.argv[1]
SC = json.load(open("out/scores.json")); S = collections.defaultdict(dict)
for s in SC: S[s["lang"]][s["engine"]] = s
D = json.load(open("out/decision.json"))
VP = {}
for f in glob.glob("out/vosk_pins*.json"): VP.update(json.load(open(f)))
# code: (ENUM, English, native, bcp47, whisperCode, vosk zip (new languages only), dir)
EXIST = [
 ("en","ENGLISH","English","English","en-US",None,"vosk-model-small-en-us-0.15.zip",41205931),
 ("es","SPANISH","Spanish","Español","es-ES",None,"vosk-model-small-es-0.42.zip",39817833),
 ("fr","FRENCH","French","Français","fr-FR",None,"vosk-model-small-fr-0.22.zip",42233323),
 ("de","GERMAN","German","Deutsch","de-DE",None,"vosk-model-small-de-0.15.zip",46499967),
 ("pt","PORTUGUESE","Portuguese","Português","pt-PT",None,"vosk-model-small-pt-0.3.zip",32453112),
 ("zh","CHINESE","Chinese","中文","zh-CN",None,"vosk-model-small-cn-0.22.zip",43898754),
 ("it","ITALIAN","Italian","Italiano","it-IT",None,"vosk-model-small-it-0.22.zip",49665141),
 ("hi","HINDI","Hindi","हिन्दी","hi-IN",None,"vosk-model-small-hi-0.22.zip",44458845),
 ("ja","JAPANESE","Japanese","日本語","ja-JP",None,"vosk-model-small-ja-0.22.zip",49704573),
 ("ko","KOREAN","Korean","한국어","ko-KR",None,"vosk-model-small-ko-0.22.zip",86914329),
]
EXDIR = {"en":"vosk-model-en","es":"vosk-model-es","fr":"vosk-model-fr","de":"vosk-model-de","pt":"vosk-model-pt","zh":"vosk-model-cn","it":"vosk-model-it","hi":"vosk-model-hi","ja":"vosk-model-ja","ko":"vosk-model-ko"}
NEW = {  # code: (ENUM, English, native, bcp47, whisperCode, vosk zip)
 "ar":("ARABIC","Arabic","العربية","ar",None,None),
 "ru":("RUSSIAN","Russian","Русский","ru-RU",None,"vosk-model-small-ru-0.22"),
 "tr":("TURKISH","Turkish","Türkçe","tr-TR",None,"vosk-model-small-tr-0.3"),
 "id":("INDONESIAN","Indonesian","Bahasa Indonesia","id-ID",None,None),
 "vi":("VIETNAMESE","Vietnamese","Tiếng Việt","vi-VN",None,None),
 "th":("THAI","Thai","ไทย","th-TH",None,None),
 "pl":("POLISH","Polish","Polski","pl-PL",None,"vosk-model-small-pl-0.22"),
 "nl":("DUTCH","Dutch","Nederlands","nl-NL",None,"vosk-model-small-nl-0.22"),
 "uk":("UKRAINIAN","Ukrainian","Українська","uk-UA",None,"vosk-model-small-uk-v3-small"),
 "fa":("PERSIAN","Persian","فارسی","fa-IR",None,"vosk-model-small-fa-0.42"),
 "sv":("SWEDISH","Swedish","Svenska","sv-SE",None,"vosk-model-small-sv-rhasspy-0.15"),
 "cs":("CZECH","Czech","Čeština","cs-CZ",None,None),
 "el":("GREEK","Greek","Ελληνικά","el-GR",None,None),
 "ro":("ROMANIAN","Romanian","Română","ro-RO",None,None),
 "hu":("HUNGARIAN","Hungarian","Magyar","hu-HU",None,None),
 "ms":("MALAY","Malay","Bahasa Melayu","ms-MY",None,None),
 "tl":("TAGALOG","Tagalog","Tagalog","fil-PH","tl",None),
 "bn":("BENGALI","Bengali","বাংলা","bn-IN",None,None),
 "ta":("TAMIL","Tamil","தமிழ்","ta-IN",None,None),
 "te":("TELUGU","Telugu","తెలుగు","te-IN",None,None),
 "sw":("SWAHILI","Swahili","Kiswahili","sw-KE",None,None),
 "bg":("BULGARIAN","Bulgarian","Български","bg-BG",None,None),
 "hr":("CROATIAN","Croatian","Hrvatski","hr-HR",None,None),
 "da":("DANISH","Danish","Dansk","da-DK",None,None),
 "fi":("FINNISH","Finnish","Suomi","fi-FI",None,None),
 "sk":("SLOVAK","Slovak","Slovenčina","sk-SK",None,None),
 "ca":("CATALAN","Catalan","Català","ca-ES",None,"vosk-model-small-ca-0.4"),
 "kk":("KAZAKH","Kazakh","Қазақша","kk-KZ",None,"vosk-model-small-kz-0.42"),
 "uz":("UZBEK","Uzbek","Oʻzbekcha","uz-UZ",None,"vosk-model-small-uz-0.22"),
 "sl":("SLOVENIAN","Slovenian","Slovenščina","sl-SI",None,None),
 "ka":("GEORGIAN","Georgian","ქართული","ka-GE",None,"vosk-model-small-ka-0.42"),
}
NOT_OFFERED = {"he":("Hebrew","עברית",None), "ur":("Urdu","اردو","HINDI")}
EXISTING_PICKS = {
 "en":[("WHISPER_SMALL",8.9),("NEMO_FAST_CONFORMER",16.2),("VOSK",24.0)], "es":[("NEMO_FAST_CONFORMER",6.9),("VOSK",11.5)],
 "fr":[("NEMO_FAST_CONFORMER",11.5),("VOSK",19.4)], "de":[("NEMO_FAST_CONFORMER",9.1),("VOSK",23.8)], "pt":[("WHISPER_SMALL",6.3),("VOSK",29.6)],
 "zh":[("SENSE_VOICE",11.9),("VOSK",24.2)], "it":[("VOSK",9.0)], "hi":[("VOSK",16.4)], "ja":[("WHISPER_SMALL",20.0),("VOSK",22.2)], "ko":[("WHISPER_SMALL",22.0),("VOSK",29.8)]}
EXISTING_VOSK = {"en":24.0,"es":11.5,"fr":19.4,"de":23.8,"pt":29.6,"zh":24.2,"it":9.0,"hi":16.4,"ja":22.2,"ko":29.8}

def vosk_pack(code):
    zipname = NEW[code][5]
    v = S[code].get("vosk")
    if not zipname or not v or v["err"] > 50: return None, None
    z = zipname + ".zip"; p = VP[z]
    return f'VoskPackage("{z}", {p["size"]:_}, "{p["sha256"]}")', v["err"]

enum = []
for code, e, eng, nat, tag, wc, z, size in EXIST:
    enum.append(f'    {e}("{eng}", "{nat}", "{tag}", VoskPackage("{z}", {size:_}), "{EXDIR[code]}"),')
vosk_err_kt = {}
for code, (e, eng, nat, tag, wc, zname) in NEW.items():
    if D.get(code) is None: continue
    pack, verr = vosk_pack(code)
    if verr is not None: vosk_err_kt[e] = verr
    wcs = f', "{wc}"' if wc else ""
    enum.append(f'    {e}("{eng}", "{nat}", "{tag}", {pack or "null"}, "vosk-model-{code}"{wcs}),')
for c in EXIST: vosk_err_kt[c[1]] = EXISTING_VOSK[c[0]]

# --- Recording.kt: replace the entry block
p = f"{WT}/android/app/src/main/kotlin/com/kreativekoala/echonote/data/model/Recording.kt"; src = open(p).read()
a = src.index("    ENGLISH("); b = src.index("    ;", a)
src = src[:a] + "\n".join(enum) + "\n" + src[b:]
open(p, "w").write(src)

# --- LanguageSupport.kt
def pick_lines(code):
    if code in EXISTING_PICKS: return [f"EnginePick(AsrEngine.{a}, {e})" for a, e in EXISTING_PICKS[code]]
    return [f"EnginePick(AsrEngine.{c['app']}, {c['err']:.1f})" for c in D[code]]
rows = []
names = {c[0]: c[1] for c in EXIST}; names.update({c: v[0] for c, v in NEW.items()})
for code in [c[0] for c in EXIST] + [c for c in NEW if D.get(c)]:
    rows.append(f"        TranscriptionLanguage.{names[code]} to listOf({', '.join(pick_lines(code))}),")
no = []
for code, (eng, nat, closest) in NOT_OFFERED.items():
    assert D.get(code) is None, code
    best = min(s["err"] for s in S[code].values())
    no.append(f'        NotOffered("{eng}", "{nat}", {best:.1f}, {("TranscriptionLanguage." + closest) if closest else "null"}),')
vrows = [f"        TranscriptionLanguage.{k} to {v:.1f}," for k, v in vosk_err_kt.items()]
kt = f'''package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptionLanguage

/** A language that was measured and did not reach the 50% bar. [closest] is the supported language nearest to it, if there is one. */
data class NotOffered(val displayName: String, val nativeName: String, val bestErrorPct: Double, val closest: TranscriptionLanguage?)

/**
 * GENERATED by tools/asr-bench/lang_gen.py from the FLEURS benchmark (docs/LANGUAGES.md has the method, the numbers with
 * confidence intervals and what was not measured). Do not edit by hand: re-run the benchmark and the generator.
 *
 * Error rates are % on 25 FLEURS read-speech clips per language (word error rate; character error rate for Chinese,
 * Japanese, Korean and Thai). Read speech is easier than real recordings, so real-world errors will be higher.
 * Picks are best first; the first pick that fits the phone's memory runs.
 */
object LanguageSupport {{
    private val PICKS: Map<TranscriptionLanguage, List<EnginePick>> = mapOf(
{chr(10).join(rows)}
    )

    /** What the small Vosk model (Fast) scored, for the languages that have one. */
    private val VOSK_ERROR: Map<TranscriptionLanguage, Double> = mapOf(
{chr(10).join(vrows)}
    )

    val NOT_OFFERED: List<NotOffered> = listOf(
{chr(10).join(no)}
    )

    /**
     * Languages held below the tier their read-speech score earns, because a second test showed a weakness the first
     * missed: Arabic scores 18% on standard-Arabic read speech but about 66% on Egyptian-dialect broadcast speech (MGB-3).
     */
    private val LEVEL_CAP: Map<TranscriptionLanguage, SupportLevel> = mapOf(
        TranscriptionLanguage.ARABIC to SupportLevel.EXPERIMENTAL
    )

    /** The tier shown for [pick]: what its error rate earns, lowered by [LEVEL_CAP] where a second test found a weakness. */
    fun levelFor(language: TranscriptionLanguage, pick: EnginePick): SupportLevel {{
        val byError = pick.level
        val cap = LEVEL_CAP[language]
        return if (cap != null && cap.ordinal > byError.ordinal) cap else byError
    }}

    /** True when the language needs the "formal speech works, dialect does not" warning. */
    fun hasDialectWarning(language: TranscriptionLanguage): Boolean = language in LEVEL_CAP

    fun picks(language: TranscriptionLanguage): List<EnginePick> = PICKS.getValue(language)

    fun voskErrorPct(language: TranscriptionLanguage): Double? = VOSK_ERROR[language]
}}
'''
open(f"{WT}/android/app/src/main/kotlin/com/kreativekoala/echonote/service/LanguageSupport.kt", "w").write(kt)
print("wrote", len(enum), "enum entries")
