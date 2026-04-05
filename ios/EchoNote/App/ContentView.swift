import SwiftData
import SwiftUI

struct ContentView: View {
    @State private var selectedTab: AppTab = .recordings
    @State private var recordingVM = RecordingViewModel()
    @State private var playerVM = PlayerViewModel()
    @State private var listVM = RecordingsListViewModel()
    @State private var folderVM = FolderViewModel()
    @State private var editorVM = EditorViewModel()
    @StateObject private var paywallCoordinator = PaywallCoordinator.shared
    @Environment(\.scenePhase) private var scenePhase
    @State private var showAppOpenPaywall = false
    @AppStorage("has_seen_first_paywall") private var hasSeenFirstPaywall = false
    @State private var showFirstLaunchPaywall = false

    // Show paywall on 1st, 2nd, 4th app open (then every 2nd after)
    private static let paywallTriggerOpens: Set<Int> = [1, 2, 4]
    private static let paywallRecurringInterval = 2

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
        .sheet(isPresented: $paywallCoordinator.showWinbackOffer) {
            WinbackOfferView()
        }
        .fullScreenCover(isPresented: $showFirstLaunchPaywall, onDismiss: {
            hasSeenFirstPaywall = true
        }) {
            RemotePaywallView(triggerSource: "first_launch")
        }
        .fullScreenCover(isPresented: $showAppOpenPaywall) {
            RemotePaywallView(triggerSource: "app_open")
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                paywallCoordinator.checkWinbackEligibility()
            }
        }
        .onAppear {
            recordingVM.requestPermissions()
            if !hasSeenFirstPaywall && !PremiumManager.shared.isPremium {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                    showFirstLaunchPaywall = true
                }
            } else {
                checkAppOpenPaywall()
            }
        }
    }

    private func checkAppOpenPaywall() {
        // Don't show to premium users
        guard !PremiumManager.shared.isPremium else { return }

        let key = "com.clearvoice.appOpenCount"
        let count = UserDefaults.standard.integer(forKey: key) + 1
        UserDefaults.standard.set(count, forKey: key)

        // Trigger on specific opens, then recurring
        let shouldShow = Self.paywallTriggerOpens.contains(count)
            || (count > 4 && (count - 4) % Self.paywallRecurringInterval == 0)

        if shouldShow {
            // Small delay so the app loads first
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                showAppOpenPaywall = true
            }
        }
    }
}

enum AppTab: Hashable {
    case recordings
    case folders
    case favorites
    case settings
}
