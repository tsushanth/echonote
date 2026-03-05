import Foundation
import SwiftData

@Model
final class Bookmark {
    var id: UUID
    var timestamp: TimeInterval
    var note: String
    var dateCreated: Date
    var recording: Recording?

    init(timestamp: TimeInterval, note: String = "", recording: Recording? = nil) {
        self.id = UUID()
        self.timestamp = timestamp
        self.note = note
        self.dateCreated = Date()
        self.recording = recording
    }

    var formattedTimestamp: String {
        let minutes = Int(timestamp) / 60
        let seconds = Int(timestamp) % 60
        return String(format: "%d:%02d", minutes, seconds)
    }
}
