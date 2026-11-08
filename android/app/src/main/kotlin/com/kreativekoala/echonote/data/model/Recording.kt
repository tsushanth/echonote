package com.kreativekoala.echonote.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A Vosk small model, a zip on alphacephei.com. The ten languages that shipped first are fetched as they always
 * were (size for display only, no pin); every language added since is pinned by size and SHA-256, because Vosk
 * parses the model natively.
 */
data class VoskPackage(val zipName: String, val sizeBytes: Long = 0L, val sha256: String? = null) {
    val url: String get() = "https://alphacephei.com/vosk/models/$zipName"
    val sizeMb: Int get() = if (sizeBytes > 0) ((sizeBytes + 500_000) / 1_000_000).toInt() else 0
}

/**
 * A language the app can transcribe. [displayName] is English, [nativeName] is how speakers write it (shown in the
 * picker). Which engine runs it, how well, and whether the phone can run it is in
 * [com.kreativekoala.echonote.service.LanguageSupport] and [com.kreativekoala.echonote.service.EnginePlan].
 * [vosk] is null for languages with no usable Fast (Vosk) model. The enum NAME is stored in settings and in
 * recordings, so entries are never renamed or removed.
 */
enum class TranscriptionLanguage(
    val displayName: String,
    val nativeName: String,
    val bcp47Tag: String,
    val vosk: VoskPackage?,
    val modelDirName: String,
    /** Code given to Whisper when it differs from the first part of [bcp47Tag]. */
    val whisperCode: String? = null
) {
    ENGLISH("English", "English", "en-US", VoskPackage("vosk-model-small-en-us-0.15.zip", 41_205_931), "vosk-model-en"),
    SPANISH("Spanish", "Español", "es-ES", VoskPackage("vosk-model-small-es-0.42.zip", 39_817_833), "vosk-model-es"),
    FRENCH("French", "Français", "fr-FR", VoskPackage("vosk-model-small-fr-0.22.zip", 42_233_323), "vosk-model-fr"),
    GERMAN("German", "Deutsch", "de-DE", VoskPackage("vosk-model-small-de-0.15.zip", 46_499_967), "vosk-model-de"),
    PORTUGUESE("Portuguese", "Português", "pt-PT", VoskPackage("vosk-model-small-pt-0.3.zip", 32_453_112), "vosk-model-pt"),
    CHINESE("Chinese", "中文", "zh-CN", VoskPackage("vosk-model-small-cn-0.22.zip", 43_898_754), "vosk-model-cn"),
    ITALIAN("Italian", "Italiano", "it-IT", VoskPackage("vosk-model-small-it-0.22.zip", 49_665_141), "vosk-model-it"),
    HINDI("Hindi", "हिन्दी", "hi-IN", VoskPackage("vosk-model-small-hi-0.22.zip", 44_458_845), "vosk-model-hi"),
    JAPANESE("Japanese", "日本語", "ja-JP", VoskPackage("vosk-model-small-ja-0.22.zip", 49_704_573), "vosk-model-ja"),
    KOREAN("Korean", "한국어", "ko-KR", VoskPackage("vosk-model-small-ko-0.22.zip", 86_914_329), "vosk-model-ko"),
    ARABIC("Arabic", "العربية", "ar", null, "vosk-model-ar"),
    RUSSIAN("Russian", "Русский", "ru-RU", VoskPackage("vosk-model-small-ru-0.22.zip", 46_236_750, "961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4"), "vosk-model-ru"),
    TURKISH("Turkish", "Türkçe", "tr-TR", VoskPackage("vosk-model-small-tr-0.3.zip", 36_855_784, "8c8d07cec1bce31add14967c16891c84152cdf76d391e33c08278119b9ea96e5"), "vosk-model-tr"),
    INDONESIAN("Indonesian", "Bahasa Indonesia", "id-ID", null, "vosk-model-id"),
    VIETNAMESE("Vietnamese", "Tiếng Việt", "vi-VN", null, "vosk-model-vi"),
    THAI("Thai", "ไทย", "th-TH", null, "vosk-model-th"),
    POLISH("Polish", "Polski", "pl-PL", VoskPackage("vosk-model-small-pl-0.22.zip", 52_979_372, "c4cd16498ea544f446f9e9a55cbd602b71cfe5a2b6f2b0834d81e1b6fce15f0d"), "vosk-model-pl"),
    DUTCH("Dutch", "Nederlands", "nl-NL", VoskPackage("vosk-model-small-nl-0.22.zip", 40_441_176, "039811c3b829de64e4f123a9f684a53784005b212a346ac0b899dc7efce2ed0a"), "vosk-model-nl"),
    UKRAINIAN("Ukrainian", "Українська", "uk-UA", VoskPackage("vosk-model-small-uk-v3-small.zip", 143_914_407, "ee05b6a8c796c53c61cd1c821d38e897e5bd67b4d691d8797ec8f9e5100d4247"), "vosk-model-uk"),
    PERSIAN("Persian", "فارسی", "fa-IR", VoskPackage("vosk-model-small-fa-0.42.zip", 53_431_220, "977cb5faa538f3a835ccfd35f5f6d8284b5c450b89c700b9bd4736b66536ad46"), "vosk-model-fa"),
    SWEDISH("Swedish", "Svenska", "sv-SE", VoskPackage("vosk-model-small-sv-rhasspy-0.15.zip", 303_504_931, "5b54f2ca8cacf9766588d05f6c8ca7e8921f054f050726e29d3516e78aefe054"), "vosk-model-sv"),
    CZECH("Czech", "Čeština", "cs-CZ", null, "vosk-model-cs"),
    GREEK("Greek", "Ελληνικά", "el-GR", null, "vosk-model-el"),
    ROMANIAN("Romanian", "Română", "ro-RO", null, "vosk-model-ro"),
    HUNGARIAN("Hungarian", "Magyar", "hu-HU", null, "vosk-model-hu"),
    MALAY("Malay", "Bahasa Melayu", "ms-MY", null, "vosk-model-ms"),
    TAGALOG("Tagalog", "Tagalog", "fil-PH", null, "vosk-model-tl", "tl"),
    BENGALI("Bengali", "বাংলা", "bn-IN", null, "vosk-model-bn"),
    TAMIL("Tamil", "தமிழ்", "ta-IN", null, "vosk-model-ta"),
    TELUGU("Telugu", "తెలుగు", "te-IN", null, "vosk-model-te"),
    SWAHILI("Swahili", "Kiswahili", "sw-KE", null, "vosk-model-sw"),
    BULGARIAN("Bulgarian", "Български", "bg-BG", null, "vosk-model-bg"),
    CROATIAN("Croatian", "Hrvatski", "hr-HR", null, "vosk-model-hr"),
    DANISH("Danish", "Dansk", "da-DK", null, "vosk-model-da"),
    FINNISH("Finnish", "Suomi", "fi-FI", null, "vosk-model-fi"),
    SLOVAK("Slovak", "Slovenčina", "sk-SK", null, "vosk-model-sk"),
    CATALAN("Catalan", "Català", "ca-ES", VoskPackage("vosk-model-small-ca-0.4.zip", 43_405_881, "99f90bfff5c2b187c705ddbc20aaa2600eddc359466f404404f1db6029fba5d4"), "vosk-model-ca"),
    KAZAKH("Kazakh", "Қазақша", "kk-KZ", VoskPackage("vosk-model-small-kz-0.42.zip", 59_697_294, "b6d6bfe6195c866805bd18b1f56723e4d2ba4866e81df1481006d0c5704fda4f"), "vosk-model-kk"),
    UZBEK("Uzbek", "Oʻzbekcha", "uz-UZ", VoskPackage("vosk-model-small-uz-0.22.zip", 51_061_189, "ffb77328df64f222a3fac6e368e0baff9dc1258f11b6b5ca5deaa82e5f688e6a"), "vosk-model-uz"),
    SLOVENIAN("Slovenian", "Slovenščina", "sl-SI", null, "vosk-model-sl"),
    GEORGIAN("Georgian", "ქართული", "ka-GE", VoskPackage("vosk-model-small-ka-0.42.zip", 45_682_310, "a4aa25c879c479428694d6839bbdfcaf1e879361f34f6a9301d750e95cc63b6d"), "vosk-model-ka"),
    ;

    /** File name of the Vosk zip, or null when the language has no Fast model. */
    val modelFileName: String? get() = vosk?.zipName
    val hasVosk: Boolean get() = vosk != null

    /** Right-to-left script (Arabic, Persian, Urdu, Hebrew). */
    val isRtl: Boolean get() = bcp47Tag.substringBefore('-') in RTL_CODES

    /** The language code Whisper and SenseVoice take. */
    val engineCode: String get() = whisperCode ?: bcp47Tag.substringBefore('-')

    companion object {
        private val RTL_CODES = setOf("ar", "fa", "ur", "he")
    }
}

enum class AudioFormat(val extension: String, val displayName: String) {
    UNCOMPRESSED("m4a", "Uncompressed (M4A)"),
    COMPRESSED("m4a", "Compressed (M4A)")
}

enum class RecordingQuality(
    val displayName: String,
    val sampleRate: Int,
    val bitRate: Int
) {
    LOW("Low", 12000, 32000),
    MEDIUM("Medium", 24000, 64000),
    HIGH("High", 44100, 128000),
    MAXIMUM("Maximum", 48000, 256000)
}

@Entity(
    tableName = "recordings",
    foreignKeys = [
        ForeignKey(
            entity = RecordingFolder::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("folderId")]
)
data class Recording(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val dateCreated: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis(),
    val duration: Long = 0L, // milliseconds
    val fileUri: String,
    val fileSize: Long = 0L,
    val audioFormat: AudioFormat = AudioFormat.COMPRESSED,
    val quality: RecordingQuality = RecordingQuality.HIGH,
    val isFavorite: Boolean = false,
    val isStereo: Boolean = false,
    val isEnhanced: Boolean = false,
    val hasVocalLayer: Boolean = false,
    val locationName: String? = null,
    val transcript: String? = null,
    val transcriptSegmentsJson: String? = null,
    val waveformData: ByteArray? = null,
    val folderId: String? = null,
    val deletedAt: Long? = null,
    // Voice data contribution (opt-in): language snapshot taken at transcribe time,
    // when the contribution upload succeeded, and whether an upload is outstanding.
    val contributionLanguage: TranscriptionLanguage? = null,
    val contributedAt: Long? = null,
    val pendingContribution: Boolean = false
) {
    val formattedDuration: String
        get() {
            val totalSeconds = duration / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Recording) return false
        return id == other.id &&
                title == other.title &&
                isFavorite == other.isFavorite &&
                isEnhanced == other.isEnhanced &&
                transcript == other.transcript &&
                folderId == other.folderId &&
                locationName == other.locationName &&
                duration == other.duration &&
                fileSize == other.fileSize
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + isFavorite.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (transcript?.hashCode() ?: 0)
        result = 31 * result + (folderId?.hashCode() ?: 0)
        return result
    }
}
