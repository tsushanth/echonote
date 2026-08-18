package com.kreativekoala.echonote.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class TranscriptionLanguage(
    val displayName: String,
    val modelFileName: String,
    val modelDirName: String
) {
    ENGLISH("English", "vosk-model-small-en-us-0.15.zip", "vosk-model-en"),
    SPANISH("Spanish", "vosk-model-small-es-0.42.zip", "vosk-model-es"),
    FRENCH("French", "vosk-model-small-fr-0.22.zip", "vosk-model-fr"),
    GERMAN("German", "vosk-model-small-de-0.15.zip", "vosk-model-de"),
    PORTUGUESE("Portuguese", "vosk-model-small-pt-0.3.zip", "vosk-model-pt"),
    CHINESE("Chinese", "vosk-model-small-cn-0.22.zip", "vosk-model-cn"),
    ITALIAN("Italian", "vosk-model-small-it-0.22.zip", "vosk-model-it"),
    HINDI("Hindi", "vosk-model-small-hi-0.22.zip", "vosk-model-hi"),
    JAPANESE("Japanese", "vosk-model-small-ja-0.22.zip", "vosk-model-ja"),
    KOREAN("Korean", "vosk-model-small-ko-0.22.zip", "vosk-model-ko");

    val modelUrl: String get() = "https://alphacephei.com/vosk/models/$modelFileName"
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
    val deletedAt: Long? = null
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
