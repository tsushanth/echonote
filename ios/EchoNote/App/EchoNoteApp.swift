import SwiftData
import SwiftUI
import FirebaseCore
import PaywallKit
import TikTokBusinessSDK

@main
struct EchoNoteApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @State private var hasRequestedTracking = false

    init() {
        // Configure Firebase Analytics
        FirebaseApp.configure()

        // Configure StoreKit 2 via PaywallKit (replaces RevenueCat)
        StoreManager.shared.configure(productIds: ProductID.allIDs)

        // Note: TikTok SDK is initialized after ATT consent via scenePhase below

        // Validate subscription state on app launch
        Task { @MainActor in
            await PremiumManager.shared.validateSubscriptionState()
            ReviewManager.shared.recordAppLaunch()
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
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
