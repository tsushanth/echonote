import Foundation
import SwiftData

@Model
final class RecordingFolder {
    var id: UUID
    var name: String
    var dateCreated: Date
    var iconName: String
    var colorHex: String
    @Relationship(deleteRule: .nullify, inverse: \Recording.folder) var recordings: [Recording]

    init(name: String, iconName: String = "folder.fill", colorHex: String = "FF3B4F") {
        self.id = UUID()
        self.name = name
        self.dateCreated = Date()
        self.iconName = iconName
        self.colorHex = colorHex
        self.recordings = []
    }

    var recordingCount: Int {
        recordings.count
    }

    var totalDuration: TimeInterval {
        recordings.reduce(0) { $0 + $1.duration }
    }

    var formattedTotalDuration: String {
        let totalSeconds = Int(totalDuration)
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        if hours > 0 {
            return "\(hours)h \(minutes)m"
        }
        return "\(minutes)m"
    }
}
