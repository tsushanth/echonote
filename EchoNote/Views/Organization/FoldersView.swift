import SwiftData
import SwiftUI

struct FoldersView: View {
    @Bindable var folderVM: FolderViewModel
    @Bindable var listVM: RecordingsListViewModel
    @Bindable var playerVM: PlayerViewModel
    @Bindable var editorVM: EditorViewModel
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \RecordingFolder.dateCreated, order: .reverse) private var folders: [RecordingFolder]
    @State private var showPaywall = false
    private var premiumManager = PremiumManager.shared

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
                        if premiumManager.hasReachedFolderLimit(currentCount: folders.count) {
                            showPaywall = true
                        } else {
                            folderVM.newFolderName = ""
                            folderVM.showCreateFolder = true
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "folder.badge.plus")
                            if !premiumManager.isPremium && folders.count >= PremiumManager.freeFolderLimit - 1 {
                                PremiumBadge(compact: true)
                            }
                        }
                    }
                    .accessibilityLabel("Create new folder")
                }
            }
            .sheet(isPresented: $showPaywall) {
                PaywallView()
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
                .accessibilityHidden(true)

            Text("No Folders")
                .font(.title2)
                .fontWeight(.semibold)

            Text("Create folders to organize your recordings.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            Button {
                if premiumManager.hasReachedFolderLimit(currentCount: folders.count) {
                    showPaywall = true
                } else {
                    folderVM.newFolderName = ""
                    folderVM.showCreateFolder = true
                }
            } label: {
                Label("Create Folder", systemImage: "folder.badge.plus")
            }
            .buttonStyle(.borderedProminent)
            .accessibilityLabel("Create your first folder")
        }
    }

    private var foldersList: some View {
        List {
            if !premiumManager.isPremium {
                Section {
                    HStack {
                        Image(systemName: "folder")
                            .foregroundStyle(.secondary)
                        Text("\(folders.count)/\(PremiumManager.freeFolderLimit) folders used")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Spacer()
                        if folders.count >= PremiumManager.freeFolderLimit {
                            Button("Upgrade") {
                                showPaywall = true
                            }
                            .font(.subheadline)
                        }
                    }
                }
            }

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
                .accessibilityLabel("\(folder.name), \(folder.recordingCount) recording\(folder.recordingCount == 1 ? "" : "s")")
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
                .accessibilityHidden(true)

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
        .accessibilityElement(children: .combine)
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
                        .accessibilityHidden(true)
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
                            .accessibilityHint("Double tap to play")
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
