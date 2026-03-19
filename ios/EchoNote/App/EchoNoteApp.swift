import SwiftData
import SwiftUI
import FirebaseCore
import RevenueCat
import TikTokBusinessSDK

@main
struct EchoNoteApp: App {
    @State private var storeKitManager = StoreKitManager()
    @Environment(\.scenePhase) private var scenePhase
    @State private var hasRequestedTracking = false

    init() {
        // Configure Firebase Analytics
        FirebaseApp.configure()

        // Configure RevenueCat
        Purchases.logLevel = .warn
        Purchases.configure(withAPIKey: "appl_diGoASyegBRlFeGrsuFxIPMldWa")

        // Note: TikTok SDK is initialized after ATT consent via scenePhase below

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
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active && !hasRequestedTracking {
                hasRequestedTracking = true
                TikTokHelper.shared.requestTrackingPermission { granted in
                    // Initialize TikTok SDK only after ATT consent is determined
                    if granted {
                        TikTokHelper.shared.initialize()
                    }
                }
            }
        }
        .modelContainer(for: [Recording.self, RecordingFolder.self, Bookmark.self])
    }
}
