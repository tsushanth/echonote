import SwiftUI
import PaywallKit

struct PaywallView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var store = StoreManager.shared
    @State private var selectedProductId: String?
    @State private var isPurchasing = false
    @State private var showRestoreAlert = false
    @State private var restoreMessage = ""

    private var premiumFeatures: [PremiumFeature] {
        var features: [PremiumFeature] = [
            .enhancedAudio,
            .transcription,
            .multilingualTranscription,
            .highQualityRecording,
            .stereoRecording,
            .wavFormat,
            .unlimitedRecordings,
            .customFolders,
            .bookmarks,
        ]
        if #available(iOS 26, *) {
            features.insert(.transcriptIntelligence, at: 3)
        }
        return features
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    headerSection
                    featuresSection
                    subscriptionSection
                    restoreSection
                    legalSection
                }
                .padding()
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Upgrade to Premium")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { dismiss() } label: {
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
                selectedProductId = store.paywallProducts.first { $0.period == .yearly }?.id
                    ?? store.paywallProducts.first?.id
            }
            .alert("Restore Purchases", isPresented: $showRestoreAlert) {
                Button("OK") { showRestoreAlert = false }
            } message: {
                Text(restoreMessage)
            }
        }
    }

    private var headerSection: some View {
        VStack(spacing: 16) {
            Image(systemName: "crown.fill")
                .font(.system(size: 60))
                .foregroundStyle(
                    LinearGradient(colors: [.yellow, .orange], startPoint: .top, endPoint: .bottom)
                )
                .shadow(color: .orange.opacity(0.3), radius: 10, y: 5)
            Text("Unlock Premium")
                .font(.largeTitle)
                .fontWeight(.bold)
            Text("Get access to all features and enhance your recording experience")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
        }
        .padding(.top)
    }

    private var featuresSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Premium Features")
                .font(.headline)
                .padding(.horizontal, 4)
            VStack(spacing: 12) {
                ForEach(premiumFeatures, id: \.rawValue) { feature in
                    FeatureRow(feature: feature)
                }
            }
            .padding()
            .background(Color(.systemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    private var subscriptionSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Choose Your Plan")
                .font(.headline)
                .padding(.horizontal, 4)

            if store.paywallProducts.isEmpty {
                HStack {
                    Spacer()
                    ProgressView().padding()
                    Spacer()
                }
                .frame(height: 150)
                .background(Color(.systemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            } else {
                VStack(spacing: 12) {
                    ForEach(store.paywallProducts, id: \.id) { product in
                        SubscriptionOptionView(
                            product: product,
                            isSelected: selectedProductId == product.id,
                            isBestValue: product.period == .yearly,
                            isMonthly: product.period == .monthly
                        ) {
                            withAnimation(.spring(response: 0.3)) {
                                selectedProductId = product.id
                            }
                        }
                    }
                }
                purchaseButton
            }
        }
    }

    private var purchaseButton: some View {
        Button {
            Task {
                guard let id = selectedProductId else { return }
                isPurchasing = true
                let result = await store.purchase(productId: id)
                isPurchasing = false
                if case .purchased = result {
                    await PremiumManager.shared.validateSubscriptionState()
                    dismiss()
                }
            }
        } label: {
            HStack {
                if isPurchasing {
                    ProgressView().tint(.white)
                } else {
                    Text("Subscribe Now").fontWeight(.semibold)
                }
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(LinearGradient(colors: [.purple, .blue], startPoint: .leading, endPoint: .trailing))
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .disabled(selectedProductId == nil || isPurchasing)
        .padding(.top, 8)
    }

    private var restoreSection: some View {
        Button {
            Task {
                await store.restore()
                await PremiumManager.shared.validateSubscriptionState()
                if PremiumManager.shared.isPremium {
                    restoreMessage = "Your purchases have been restored successfully!"
                    showRestoreAlert = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { dismiss() }
                } else {
                    restoreMessage = "No previous purchases found."
                    showRestoreAlert = true
                }
            }
        } label: {
            Text("Restore Purchases")
                .font(.subheadline)
                .foregroundStyle(.blue)
        }
    }

    private var legalSection: some View {
        VStack(spacing: 8) {
            Text("Subscription automatically renews unless cancelled at least 24 hours before the end of the current period. Manage subscriptions in Settings.")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            HStack(spacing: 16) {
                Link("Terms of Service", destination: URL(string: "https://kreativekoala.llc/terms")!)
                    .font(.caption)
                Text("•").foregroundStyle(.secondary)
                Link("Privacy Policy", destination: URL(string: "https://kreativekoala.llc/privacy")!)
                    .font(.caption)
            }
        }
        .padding(.top, 8)
    }
}

private struct FeatureRow: View {
    let feature: PremiumFeature
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: feature.iconName)
                .font(.title3)
                .foregroundStyle(.blue)
                .frame(width: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(feature.displayName).font(.subheadline).fontWeight(.medium)
                Text(feature.description).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
        }
    }
}

private struct SubscriptionOptionView: View {
    let product: PaywallProduct
    let isSelected: Bool
    let isBestValue: Bool
    let isMonthly: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(product.period.rawValue.capitalized)
                            .font(.headline)
                            .foregroundStyle(.primary)
                        if isBestValue {
                            Text("Best Value")
                                .font(.caption2).fontWeight(.bold).foregroundStyle(.white)
                                .padding(.horizontal, 6).padding(.vertical, 2)
                                .background(Color.green).clipShape(Capsule())
                        }
                        if isMonthly {
                            Text("Popular")
                                .font(.caption2).fontWeight(.bold).foregroundStyle(.white)
                                .padding(.horizontal, 6).padding(.vertical, 2)
                                .background(Color.blue).clipShape(Capsule())
                        }
                    }
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(product.localizedPrice)
                        .font(.headline).foregroundStyle(.primary)
                    Text("/\(product.period.rawValue)")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.title2)
                    .foregroundStyle(isSelected ? .blue : .secondary)
            }
            .padding()
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color(.systemBackground))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(isSelected ? Color.blue : Color.clear, lineWidth: 2)
                    )
            )
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    PaywallView()
}
