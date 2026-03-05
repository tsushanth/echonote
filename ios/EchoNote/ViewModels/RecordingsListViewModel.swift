import Foundation
import SwiftData
import SwiftUI

enum SortOption: String, CaseIterable {
    case dateNewest = "Date (Newest)"
    case dateOldest = "Date (Oldest)"
    case titleAZ = "Title (A-Z)"
    case titleZA = "Title (Z-A)"
    case durationLongest = "Duration (Longest)"
    case durationShortest = "Duration (Shortest)"
}

@Observable
final class RecordingsListViewModel {
    var searchText: String = ""
    var sortOption: SortOption = .dateNewest
    var selectedRecordings: Set<UUID> = []
    var isSelectionMode: Bool = false
    var showDeleteConfirmation: Bool = false
    var showMoveToFolderSheet: Bool = false
    var showShareSheet: Bool = false
    var recordingToDelete: Recording?

    func filteredRecordings(_ recordings: [Recording]) -> [Recording] {
        var result = recordings

        if !searchText.isEmpty {
            let query = searchText.lowercased()
            result = result.filter { recording in
                recording.title.lowercased().contains(query)
                    || (recording.locationName?.lowercased().contains(query) ?? false)
                    || (recording.transcript?.lowercased().contains(query) ?? false)
            }
        }

        result = sortRecordings(result)
        return result
    }

    private func sortRecordings(_ recordings: [Recording]) -> [Recording] {
        switch sortOption {
        case .dateNewest:
            return recordings.sorted { $0.dateCreated > $1.dateCreated }
        case .dateOldest:
            return recordings.sorted { $0.dateCreated < $1.dateCreated }
        case .titleAZ:
            return recordings.sorted { $0.title.localizedCompare($1.title) == .orderedAscending }
        case .titleZA:
            return recordings.sorted { $0.title.localizedCompare($1.title) == .orderedDescending }
        case .durationLongest:
            return recordings.sorted { $0.duration > $1.duration }
        case .durationShortest:
            return recordings.sorted { $0.duration < $1.duration }
        }
    }

    func toggleFavorite(_ recording: Recording, modelContext: ModelContext) {
        recording.isFavorite.toggle()
        recording.dateModified = Date()
        try? modelContext.save()
    }

    func deleteRecording(_ recording: Recording, modelContext: ModelContext) {
        let url = recording.actualFileURL
        try? FileManager.default.removeItem(at: url)
        modelContext.delete(recording)
        try? modelContext.save()
    }

    func deleteSelectedRecordings(from recordings: [Recording], modelContext: ModelContext) {
        for recording in recordings where selectedRecordings.contains(recording.id) {
            deleteRecording(recording, modelContext: modelContext)
        }
        selectedRecordings.removeAll()
        isSelectionMode = false
    }

    func renameRecording(_ recording: Recording, newName: String, modelContext: ModelContext) {
        recording.title = newName
        recording.dateModified = Date()
        try? modelContext.save()
    }

    func moveToFolder(_ recording: Recording, folder: RecordingFolder?, modelContext: ModelContext) {
        recording.folder = folder
        recording.dateModified = Date()
        try? modelContext.save()
    }

    func toggleSelection(_ recording: Recording) {
        if selectedRecordings.contains(recording.id) {
            selectedRecordings.remove(recording.id)
        } else {
            selectedRecordings.insert(recording.id)
        }
    }

    func selectAll(from recordings: [Recording]) {
        selectedRecordings = Set(recordings.map(\.id))
    }

    func deselectAll() {
        selectedRecordings.removeAll()
    }

    func shareURLs(from recordings: [Recording]) -> [URL] {
        recordings
            .filter { selectedRecordings.contains($0.id) }
            .map(\.actualFileURL)
    }
}
