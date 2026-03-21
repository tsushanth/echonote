import SwiftUI
import PaywallKit

/// A special winback offer shown after the user has dismissed the paywall multiple times.
struct WinbackOfferView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var isPurchasing = false
    @State private var errorMessage: String?
    @State private var showError = false
    @ObservedObject private var store = StoreManager.shared

    private let valueProps: [(icon: String, text: String)] = [
        ("waveform", "Crystal-clear recordings"),
        ("waveform.badge.minus", "Noise cancellation"),
        ("infinity", "Unlimited recording time"),
        ("square.and.arrow.up", "Export in any format")
    ]

    private var yearlyProduct: PaywallProduct? {
        store.paywallProducts.first { $0.period == .yearly }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 28) {
                    // SPECIAL OFFER badge
                    Text("SPECIAL OFFER")
                        .font(.caption)
                        .fontWeight(.heavy)
                        .tracking(1.5)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 6)
                        .background(
                            LinearGradient(
                                colors: [.orange, .red],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .clipShape(Capsule())
                        .padding(.top, 24)

                    // Headline
                    VStack(spacing: 8) {
                        Text("We miss you!")
                            .font(.largeTitle)
                            .fontWeight(.bold)

                        Text("Come back and unlock the full ClearVoice experience")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                    }

                    // Value propositions
                    VStack(spacing: 16) {
                        ForEach(valueProps, id: \.text) { prop in
                            HStack(spacing: 14) {
                                Image(systemName: prop.icon)
                                    .font(.title3)
                                    .foregroundStyle(.blue)
                                    .frame(width: 32)

                                Text(prop.text)
                                    .font(.body)
                                    .fontWeight(.medium)

                                Spacer()

                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(.green)
                            }
                        }
                    }
                    .padding()
                    .background(Color(.systemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                    // CTA Button
                    Button {
                        Task { await purchaseYearly() }
                    } label: {
                        HStack {
                            if isPurchasing {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                VStack(spacing: 2) {
                                    Text("Start Free Trial")
                                        .fontWeight(.semibold)
                                    if let p = yearlyProduct {
                                        Text("then \(p.localizedPrice)/year")
                                            .font(.caption)
                                            .opacity(0.9)
                                    }
                                }
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(
                            LinearGradient(
                                colors: [.blue, .purple],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .foregroundStyle(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                    .disabled(isPurchasing || yearlyProduct == nil)

                    // No thanks
                    Button {
                        dismiss()
                    } label: {
                        Text("No thanks")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    // Legal
                    Text("Subscription automatically renews unless cancelled at least 24 hours before the end of the current period.")
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)

                    Spacer(minLength: 0)
                }
                .padding(.horizontal)
            }
            .background(Color(.systemGroupedBackground))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.secondary)
                            .font(.title2)
                    }
                }
            }
            .task {
                if store.paywallProducts.isEmpty {
                    await store.loadProducts()
                }
            }
            .alert("Error", isPresented: $showError) {
                Button("OK") { showError = false }
            } message: {
                Text(errorMessage ?? "An error occurred.")
            }
        }
    }

    private func purchaseYearly() async {
        guard let product = yearlyProduct else { return }
        isPurchasing = true
        defer { isPurchasing = false }

        let result = await store.purchase(productId: product.id)
        switch result {
        case .purchased:
            await PremiumManager.shared.validateSubscriptionState()
            dismiss()
        case .cancelled:
            break
        case .pending:
            break
        case .failed:
            errorMessage = "Purchase failed. Please try again."
            showError = true
        }
    }
}

#Preview {
    WinbackOfferView()
}
