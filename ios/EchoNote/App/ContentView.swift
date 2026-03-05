import SwiftData
import SwiftUI

struct ContentView: View {
    @State private var selectedTab: AppTab = .recordings
    @State private var recordingVM = RecordingViewModel()
    @State private var playerVM = PlayerViewModel()
    @State private var listVM = RecordingsListViewModel()
    @State private var folderVM = FolderViewModel()
    @State private var editorVM = EditorViewModel()

    var body: some View {
        TabView(selection: $selectedTab) {
            RecordingsListView(
                listVM: listVM,
                playerVM: playerVM,
                editorVM: editorVM,
                folderVM: folderVM,
                recordingVM: recordingVM
            )
            .tabItem {
                Label("Recordings", systemImage: "waveform")
            }
            .tag(AppTab.recordings)

            FoldersView(
                folderVM: folderVM,
                listVM: listVM,
                playerVM: playerVM,
                editorVM: editorVM
            )
            .tabItem {
                Label("Folders", systemImage: "folder.fill")
            }
            .tag(AppTab.folders)

            FavoritesView(
                listVM: listVM,
                playerVM: playerVM,
                editorVM: editorVM
            )
            .tabItem {
                Label("Favorites", systemImage: "star.fill")
            }
            .tag(AppTab.favorites)

            SettingsView()
            .tabItem {
                Label("Settings", systemImage: "gear")
            }
            .tag(AppTab.settings)
        }
        .tint(.accentColor)
        .sheet(isPresented: $playerVM.showPlaybackSheet) {
            PlaybackView(playerVM: playerVM, editorVM: editorVM)
                .presentationDetents([.large])
        }
        .onAppear {
            recordingVM.requestPermissions()
        }
    }
}

enum AppTab: Hashable {
    case recordings
    case folders
    case favorites
    case settings
}
