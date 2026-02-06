import SwiftData
import SwiftUI

struct RecordingsListView: View {
    @Bindable var listVM: RecordingsListViewModel
    @Bindable var playerVM: PlayerViewModel
    @Bindable var editorVM: EditorViewModel
    @Bindable var folderVM: FolderViewModel
    @Bindable var recordingVM: RecordingViewModel
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \Recording.dateCreated, order: .reverse) private var recordings: [Recording]
    @State private var showRecordingSheet = false
    @State private var renamingRecording: Recording?
    @State private var newName: String = ""

    var body: some View {
        NavigationStack {
            Group {
                if recordings.isEmpty {
                    emptyStateView
                } else {
                    recordingsList
                }
            }
            .navigationTitle("Recordings")
            .searchable(text: $listVM.searchText, prompt: "Search recordings")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        ForEach(SortOption.allCases, id: \.self) { option in
                            Button {
                                listVM.sortOption = option
                            } label: {
                                HStack {
                                    Text(option.rawValue)
                                    if listVM.sortOption == option {
                                        Image(systemName: "checkmark")
                                    }
                                }
                            }
                        }
                    } label: {
                        Image(systemName: "arrow.up.arrow.down")
                    }
                }

                ToolbarItem(placement: .primaryAction) {
                    if listVM.isSelectionMode {
                        Button("Done") {
                            listVM.isSelectionMode = false
                            listVM.deselectAll()
                        }
                    } else {
                        Button("Select") {
                            listVM.isSelectionMode = true
                        }
                        .disabled(recordings.isEmpty)
                    }
                }
            }
            .safeAreaInset(edge: .bottom) {
                bottomBar
            }
            .sheet(isPresented: $showRecordingSheet) {
                RecordingView(recordingVM: recordingVM)
            }
            .alert("Rename Recording", isPresented: Binding(
                get: { renamingRecording != nil },
                set: { if !$0 { renamingRecording = nil } }
            )) {
                TextField("Name", text: $newName)
                Button("Cancel", role: .cancel) { renamingRecording = nil }
                Button("Save") {
                    if let recording = renamingRecording {
                        listVM.renameRecording(recording, newName: newName, modelContext: modelContext)
                    }
                    renamingRecording = nil
                }
            }
            .confirmationDialog(
                "Delete Recording?",
                isPresented: $listVM.showDeleteConfirmation,
                titleVisibility: .visible
            ) {
                Button("Delete", role: .destructive) {
                    if let recording = listVM.recordingToDelete {
                        listVM.deleteRecording(recording, modelContext: modelContext)
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This action cannot be undone.")
            }
        }
    }

    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Image(systemName: "waveform")
                .font(.system(size: 64))
                .foregroundStyle(.secondary)

            Text("No Recordings")
                .font(.title2)
                .fontWeight(.semibold)

            Text("Tap the record button to create your first recording.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
        }
    }

    private var recordingsList: some View {
        List {
            ForEach(listVM.filteredRecordings(recordings)) { recording in
                RecordingRowView(
                    recording: recording,
                    isSelected: listVM.selectedRecordings.contains(recording.id),
                    isSelectionMode: listVM.isSelectionMode
                )
                .onTapGesture {
                    if listVM.isSelectionMode {
                        listVM.toggleSelection(recording)
                    } else {
                        playerVM.loadRecording(recording)
                    }
                }
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    Button(role: .destructive) {
                        listVM.recordingToDelete = recording
                        listVM.showDeleteConfirmation = true
                    } label: {
                        Label("Delete", systemImage: "trash")
                    }

                    Button {
                        listVM.toggleFavorite(recording, modelContext: modelContext)
                    } label: {
                        Label(
                            recording.isFavorite ? "Unfavorite" : "Favorite",
                            systemImage: recording.isFavorite ? "star.slash" : "star.fill"
                        )
                    }
                    .tint(.yellow)
                }
                .swipeActions(edge: .leading) {
                    Button {
                        newName = recording.title
                        renamingRecording = recording
                    } label: {
                        Label("Rename", systemImage: "pencil")
                    }
                    .tint(.blue)
                }
                .contextMenu {
                    Button {
                        playerVM.loadRecording(recording)
                    } label: {
                        Label("Play", systemImage: "play.fill")
                    }

                    Button {
                        newName = recording.title
                        renamingRecording = recording
                    } label: {
                        Label("Rename", systemImage: "pencil")
                    }

                    Button {
                        listVM.toggleFavorite(recording, modelContext: modelContext)
                    } label: {
                        Label(
                            recording.isFavorite ? "Remove from Favorites" : "Add to Favorites",
                            systemImage: recording.isFavorite ? "star.slash" : "star.fill"
                        )
                    }

                    Button {
                        editorVM.loadRecording(recording)
                    } label: {
                        Label("Edit", systemImage: "waveform.and.magnifyingglass")
                    }

                    ShareLink(item: recording.actualFileURL) {
                        Label("Share", systemImage: "square.and.arrow.up")
                    }

                    Divider()

                    Button(role: .destructive) {
                        listVM.recordingToDelete = recording
                        listVM.showDeleteConfirmation = true
                    } label: {
                        Label("Delete", systemImage: "trash")
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    private var bottomBar: some View {
        VStack(spacing: 0) {
            Divider()
            HStack {
                if listVM.isSelectionMode {
                    selectionControls
                } else {
                    Text("\(recordings.count) Recording\(recordings.count == 1 ? "" : "s")")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    Spacer()

                    Button {
                        showRecordingSheet = true
                    } label: {
                        Image(systemName: "circle.fill")
                            .font(.system(size: 36))
                            .foregroundStyle(AppConstants.Colors.recordingRed)
                    }
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
        }
        .background(.bar)
    }

    private var selectionControls: some View {
        HStack {
            Button {
                if listVM.selectedRecordings.count == recordings.count {
                    listVM.deselectAll()
                } else {
                    listVM.selectAll(from: recordings)
                }
            } label: {
                Text(listVM.selectedRecordings.count == recordings.count ? "Deselect All" : "Select All")
                    .font(.subheadline)
            }

            Spacer()

            Text("\(listVM.selectedRecordings.count) selected")
                .font(.caption)
                .foregroundStyle(.secondary)

            Spacer()

            if !listVM.selectedRecordings.isEmpty {
                ShareLink(
                    items: listVM.shareURLs(from: recordings)
                ) {
                    Image(systemName: "square.and.arrow.up")
                }

                Button(role: .destructive) {
                    listVM.deleteSelectedRecordings(from: recordings, modelContext: modelContext)
                } label: {
                    Image(systemName: "trash")
                }
            }
        }
    }
}
