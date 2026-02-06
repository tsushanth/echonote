import SwiftData
import SwiftUI

struct MoveToFolderView: View {
    let recording: Recording
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @Query(sort: \RecordingFolder.dateCreated, order: .reverse) private var folders: [RecordingFolder]
    @State private var showCreateFolder = false
    @State private var newFolderName = ""

    var body: some View {
        NavigationStack {
            List {
                noFolderSection
                foldersSection
                createFolderSection
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Move to Folder")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .accessibilityLabel("Cancel moving recording")
                }
            }
            .alert("New Folder", isPresented: $showCreateFolder) {
                TextField("Folder name", text: $newFolderName)
                Button("Cancel", role: .cancel) {}
                Button("Create") {
                    createAndAssignFolder()
                }
            }
        }
    }

    private var noFolderSection: some View {
        Section {
            Button {
                recording.folder = nil
                try? modelContext.save()
                dismiss()
            } label: {
                noFolderRow
            }
            .foregroundStyle(.primary)
            .accessibilityLabel("No folder")
            .accessibilityValue(recording.folder == nil ? "Currently selected" : "")
        }
    }

    private var noFolderRow: some View {
        HStack {
            Image(systemName: "tray")
                .foregroundStyle(.secondary)
            Text("No Folder")
            Spacer()
            if recording.folder == nil {
                Image(systemName: "checkmark")
                    .foregroundStyle(Color.accentColor)
            }
        }
    }

    private var foldersSection: some View {
        Section("Folders") {
            ForEach(folders) { folder in
                Button {
                    recording.folder = folder
                    try? modelContext.save()
                    dismiss()
                } label: {
                    folderRow(folder)
                }
                .foregroundStyle(.primary)
                .accessibilityLabel("Move to \(folder.name)")
                .accessibilityValue(recording.folder?.id == folder.id ? "Currently selected" : "")
            }
        }
    }

    private func folderRow(_ folder: RecordingFolder) -> some View {
        HStack {
            Image(systemName: folder.iconName)
                .foregroundStyle(Color(hex: folder.colorHex))
            Text(folder.name)
            Spacer()
            if recording.folder?.id == folder.id {
                Image(systemName: "checkmark")
                    .foregroundStyle(Color.accentColor)
            }
        }
    }

    private var createFolderSection: some View {
        Section {
            Button {
                showCreateFolder = true
            } label: {
                Label("Create New Folder", systemImage: "folder.badge.plus")
            }
            .accessibilityLabel("Create new folder")
            .accessibilityHint("Creates a new folder and moves the recording into it")
        }
    }

    private func createAndAssignFolder() {
        let name = newFolderName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }
        let folder = RecordingFolder(name: name)
        modelContext.insert(folder)
        recording.folder = folder
        try? modelContext.save()
        newFolderName = ""
        dismiss()
    }
}
