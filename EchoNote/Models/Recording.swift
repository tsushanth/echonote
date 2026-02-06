import Foundation
import SwiftData

enum AudioFormat: String, Codable, CaseIterable {
    case uncompressed = "wav"
    case compressed = "m4a"

    var displayName: String {
        switch self {
        case .uncompressed: return "Uncompressed (WAV)"
        case .compressed: return "Compressed (M4A)"
        }
    }

    var fileExtension: String { rawValue }
}

enum RecordingQuality: String, Codable, CaseIterable {
    case low
    case medium
    case high
    case maximum

    var displayName: String {
        switch self {
        case .low: return "Low"
        case .medium: return "Medium"
        case .high: return "High"
        case .maximum: return "Maximum"
        }
    }

    var sampleRate: Double {
        switch self {
        case .low: return 12000
        case .medium: return 24000
        case .high: return 44100
        case .maximum: return 48000
        }
    }

    var bitRate: Int {
        switch self {
        case .low: return 32000
        case .medium: return 64000
        case .high: return 128000
        case .maximum: return 256000
        }
    }
}

@Model
final class Recording {
    var id: UUID
    var title: String
    var dateCreated: Date
    var dateModified: Date
    var duration: TimeInterval
    var fileURL: String
    var fileSize: Int64
    var audioFormat: AudioFormat
    var quality: RecordingQuality
    var isFavorite: Bool
    var isStereo: Bool
    var isEnhanced: Bool
    var hasVocalLayer: Bool
    var locationName: String?
    var transcript: String?
    var waveformData: Data?
    var folder: RecordingFolder?
    @Relationship(deleteRule: .cascade) var bookmarks: [Bookmark]

    init(
        title: String,
        fileURL: String,
        duration: TimeInterval = 0,
        fileSize: Int64 = 0,
        audioFormat: AudioFormat = .compressed,
        quality: RecordingQuality = .high,
        isStereo: Bool = false,
        locationName: String? = nil
    ) {
        self.id = UUID()
        self.title = title
        self.dateCreated = Date()
        self.dateModified = Date()
        self.duration = duration
        self.fileURL = fileURL
        self.fileSize = fileSize
        self.audioFormat = audioFormat
        self.quality = quality
        self.isFavorite = false
        self.isStereo = isStereo
        self.isEnhanced = false
        self.hasVocalLayer = false
        self.locationName = locationName
        self.transcript = nil
        self.waveformData = nil
        self.folder = nil
        self.bookmarks = []
    }

    var formattedDuration: String {
        let hours = Int(duration) / 3600
        let minutes = (Int(duration) % 3600) / 60
        let seconds = Int(duration) % 60
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, seconds)
        }
        return String(format: "%d:%02d", minutes, seconds)
    }

    var formattedFileSize: String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: fileSize)
    }

    var formattedDate: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: dateCreated)
    }

    var actualFileURL: URL {
        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        return documentsPath.appendingPathComponent(fileURL)
    }
}
