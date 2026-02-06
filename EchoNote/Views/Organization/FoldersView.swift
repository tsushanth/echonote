import SwiftData
import SwiftUI

struct FoldersView: View {
    @Bindable var folderVM: FolderViewModel
    @Bindable var listVM: RecordingsListViewModel
    @Bindable var playerVM: PlayerViewModel
    @Bindable var editorVM: EditorViewModel
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \RecordingFolder.dateCreated, order: .reverse) private var folders: [RecordingFolder]

    var body: some View {
        NavigationStack {
            Group {
                if folders.isEmpty {
                    emptyStateView
                } else {
                    foldersList
                }
            }
            .navigationTitle("Folders")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        folderVM.newFolderName = ""
                        folderVM.showCreateFolder = true
                    } label: {
                        Image(systemName: "folder.badge.plus")
                    }
                }
            }
            .alert("New Folder", isPresented: $folderVM.showCreateFolder) {
                TextField("Folder name", text: $folderVM.newFolderName)
                Button("Cancel", role: .cancel) {}
                Button("Create") {
                    folderVM.createFolder(modelContext: modelContext)
                }
            }
            .alert("Rename Folder", isPresented: $folderVM.showRenameAlert) {
                TextField("Folder name", text: $folderVM.newFolderName)
                Button("Cancel", role: .cancel) { folderVM.editingFolder = nil }
                Button("Save") {
                    if let folder = folderVM.editingFolder {
                        folderVM.renameFolder(folder, newName: folderVM.newFolderName, modelContext: modelContext)
                    }
                }
            }
            .confirmationDialog(
                "Delete Folder?",
                isPresented: $folderVM.showDeleteConfirmation,
                titleVisibility: .visible
            ) {
                Button("Delete", role: .destructive) {
                    if let folder = folderVM.folderToDelete {
                        folderVM.deleteFolder(folder, modelContext: modelContext)
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Recordings in this folder will not be deleted.")
            }
        }
    }

    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Image(systemName: "folder")
                .font(.system(size: 64))
                .foregroundStyle(.secondary)

            Text("No Folders")
                .font(.title2)
                .fontWeight(.semibold)

            Text("Create folders to organize your recordings.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            Button {
                folderVM.newFolderName = ""
                folderVM.showCreateFolder = true
            } label: {
                Label("Create Folder", systemImage: "folder.badge.plus")
            }
            .buttonStyle(.borderedProminent)
        }
    }

    private var foldersList: some View {
        List {
            ForEach(folders) { folder in
                NavigationLink {
                    FolderDetailView(
                        folder: folder,
                        listVM: listVM,
                        playerVM: playerVM,
                        editorVM: editorVM
                    )
                } label: {
                    FolderRowView(folder: folder)
                }
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    Button(role: .destructive) {
                        folderVM.folderToDelete = folder
                        folderVM.showDeleteConfirmation = true
                    } label: {
                        Label("Delete", systemImage: "trash")
                    }

                    Button {
                        folderVM.editingFolder = folder
                        folderVM.newFolderName = folder.name
                        folderVM.showRenameAlert = true
                    } label: {
                        Label("Rename", systemImage: "pencil")
                    }
                    .tint(.blue)
                }
            }
        }
        .listStyle(.insetGrouped)
    }
}

struct FolderRowView: View {
    let folder: RecordingFolder

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: folder.iconName)
                .font(.title2)
                .foregroundStyle(Color(hex: folder.colorHex))
                .frame(width: 36, height: 36)

            VStack(alignment: .leading, spacing: 2) {
                Text(folder.name)
                    .font(.headline)

                HStack(spacing: 8) {
                    Text("\(folder.recordingCount) recording\(folder.recordingCount == 1 ? "" : "s")")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    if folder.recordingCount > 0 {
                        Text(folder.formattedTotalDuration)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            Spacer()
        }
        .padding(.vertical, 4)
    }
}

struct FolderDetailView: View {
    let folder: RecordingFolder
    @Bindable var listVM: RecordingsListViewModel
    @Bindable var playerVM: PlayerViewModel
    @Bindable var editorVM: EditorViewModel
    @Environment(\.modelContext) private var modelContext

    var body: some View {
        Group {
            if folder.recordings.isEmpty {
                VStack(spacing: 16) {
                    Image(systemName: "folder")
                        .font(.system(size: 48))
                        .foregroundStyle(.secondary)
                    Text("Empty Folder")
                        .font(.headline)
                    Text("Move recordings here to organize them.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            } else {
                List {
                    ForEach(listVM.filteredRecordings(folder.recordings)) { recording in
                        RecordingRowView(recording: recording)
                            .onTapGesture {
                                playerVM.loadRecording(recording)
                            }
                            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                Button(role: .destructive) {
                                    listVM.deleteRecording(recording, modelContext: modelContext)
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }

                                Button {
                                    listVM.moveToFolder(recording, folder: nil, modelContext: modelContext)
                                } label: {
                                    Label("Remove", systemImage: "folder.badge.minus")
                                }
                                .tint(.orange)
                            }
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
        .navigationTitle(folder.name)
        .searchable(text: $listVM.searchText, prompt: "Search in folder")
    }
}
