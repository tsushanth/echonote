import SwiftData
import SwiftUI

struct FavoritesView: View {
    @Bindable var listVM: RecordingsListViewModel
    @Bindable var playerVM: PlayerViewModel
    @Bindable var editorVM: EditorViewModel
    @Environment(\.modelContext) private var modelContext
    @Query(filter: #Predicate<Recording> { $0.isFavorite },
           sort: \Recording.dateCreated, order: .reverse) private var favorites: [Recording]

    var body: some View {
        NavigationStack {
            Group {
                if favorites.isEmpty {
                    emptyStateView
                } else {
                    favoritesList
                }
            }
            .navigationTitle("Favorites")
            .searchable(text: $listVM.searchText, prompt: "Search favorites")
        }
    }

    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Image(systemName: "star")
                .font(.system(size: 64))
                .foregroundStyle(.secondary)

            Text("No Favorites")
                .font(.title2)
                .fontWeight(.semibold)

            Text("Mark recordings as favorites to quickly access them here.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
        }
    }

    private var favoritesList: some View {
        List {
            ForEach(listVM.filteredRecordings(favorites)) { recording in
                RecordingRowView(recording: recording)
                    .onTapGesture {
                        playerVM.loadRecording(recording)
                    }
                    .swipeActions(edge: .trailing) {
                        Button {
                            listVM.toggleFavorite(recording, modelContext: modelContext)
                        } label: {
                            Label("Unfavorite", systemImage: "star.slash")
                        }
                        .tint(.yellow)
                    }
                    .contextMenu {
                        Button {
                            playerVM.loadRecording(recording)
                        } label: {
                            Label("Play", systemImage: "play.fill")
                        }

                        Button {
                            listVM.toggleFavorite(recording, modelContext: modelContext)
                        } label: {
                            Label("Remove from Favorites", systemImage: "star.slash")
                        }

                        ShareLink(item: recording.actualFileURL) {
                            Label("Share", systemImage: "square.and.arrow.up")
                        }
                    }
            }
        }
        .listStyle(.insetGrouped)
    }
}
