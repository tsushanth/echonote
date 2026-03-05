import SwiftData
import SwiftUI
import FirebaseCore
import RevenueCat
import TikTokBusinessSDK

@main
struct EchoNoteApp: App {
    @State private var storeKitManager = StoreKitManager()

    init() {
        // Configure Firebase Analytics
        FirebaseApp.configure()

        // Configure RevenueCat
        Purchases.logLevel = .warn
        Purchases.configure(withAPIKey: "appl_diGoASyegBRlFeGrsuFxIPMldWa")

        // Configure TikTok Events SDK
        TikTokHelper.shared.initialize()

        // Validate subscription state on app launch (queries RevenueCat directly)
        Task { @MainActor in
            await PremiumManager.shared.validateSubscriptionState()
            ReviewManager.shared.recordAppLaunch()
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(storeKitManager)
                .task {
                    TikTokHelper.shared.requestTrackingPermission()
                }
        }
        .modelContainer(for: [Recording.self, RecordingFolder.self, Bookmark.self])
    }
}
