import Foundation
import SwiftData

@Observable
final class FolderViewModel {
    var showCreateFolder: Bool = false
    var newFolderName: String = ""
    var editingFolder: RecordingFolder?
    var showRenameAlert: Bool = false
    var showDeleteConfirmation: Bool = false
    var folderToDelete: RecordingFolder?

    func createFolder(modelContext: ModelContext) {
        let trimmedName = newFolderName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else { return }

        let folder = RecordingFolder(name: trimmedName)
        modelContext.insert(folder)
        try? modelContext.save()

        newFolderName = ""
        showCreateFolder = false
    }

    func renameFolder(_ folder: RecordingFolder, newName: String, modelContext: ModelContext) {
        let trimmedName = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else { return }

        folder.name = trimmedName
        try? modelContext.save()
        editingFolder = nil
    }

    func deleteFolder(_ folder: RecordingFolder, modelContext: ModelContext) {
        for recording in folder.recordings {
            recording.folder = nil
        }
        modelContext.delete(folder)
        try? modelContext.save()
        folderToDelete = nil
    }
}
