import SwiftUI
import PaywallKit

/// PaywallKit-powered paywall with StoreKit 2 purchases.
struct RemotePaywallView: View {
    @Environment(\.dismiss) private var dismiss
    var triggerSource: String = "unknown"
    @State private var didPurchaseOrRestore = false
    @ObservedObject private var store = StoreManager.shared

    var body: some View {
        PaywallKit.PaywallView(
            appId: "clearvoice",
            appName: "ClearVoice Pro",
            features: [
                PaywallFeature(icon: "🎙️", title: "Unlimited Recordings", description: "No storage limits"),
                PaywallFeature(icon: "✨", title: "AI Noise Reduction", description: "Crystal clear audio"),
                PaywallFeature(icon: "📝", title: "Transcription", description: "Speech to text in 90+ languages"),
                PaywallFeature(icon: "🎧", title: "High Quality Audio", description: "48kHz stereo recording"),
                PaywallFeature(icon: "📁", title: "Unlimited Folders", description: "Organize your recordings"),
            ],
            products: store.paywallProducts,
            theme: PaywallTheme(accent: Color(red: 0.0, green: 0.48, blue: 1.0), accent2: Color(red: 0.5, green: 0.3, blue: 0.9)),
            showWinback: true,
            onPurchase: { productId in
                let result = await store.purchase(productId: productId)
                if case .purchased = result {
                    didPurchaseOrRestore = true
                    await PremiumManager.shared.validateSubscriptionState()
                    await MainActor.run { dismiss() }
                    return true
                }
                return false
            },
            onRestore: {
                await store.restore()
                await PremiumManager.shared.validateSubscriptionState()
                if PremiumManager.shared.isPremium {
                    didPurchaseOrRestore = true
                    await MainActor.run { dismiss() }
                }
            },
            onDismiss: {
                if !didPurchaseOrRestore {
                    PaywallCoordinator.shared.trackDismiss()
                }
                dismiss()
            }
        )
        .task {
            if store.paywallProducts.isEmpty {
                await store.loadProducts()
            }
        }
    }
}
