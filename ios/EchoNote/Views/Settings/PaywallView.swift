import SwiftUI
import RevenueCat

struct PaywallView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var storeKitManager = StoreKitManager()
    @State private var selectedPackage: RevenueCat.Package?
    @State private var purchaseState: PurchaseState = .notPurchased
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
        // Only show AI intelligence on devices that support it
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
                await storeKitManager.fetchProducts()
                // Auto-select the best value (yearly)
                selectedPackage = storeKitManager.packages.first(where: { $0.packageType == .annual })
                    ?? storeKitManager.packages.first
            }
            .alert("Error", isPresented: $storeKitManager.showError) {
                Button("OK") { storeKitManager.showError = false }
            } message: {
                Text(storeKitManager.errorMessage ?? "An unknown error occurred.")
            }
            .alert("Restore Purchases", isPresented: $showRestoreAlert) {
                Button("OK") { showRestoreAlert = false }
            } message: {
                Text(restoreMessage)
            }
        }
    }

    // MARK: - Header Section

    private var headerSection: some View {
        VStack(spacing: 16) {
            Image(systemName: "crown.fill")
                .font(.system(size: 60))
                .foregroundStyle(
                    LinearGradient(
                        colors: [.yellow, .orange],
                        startPoint: .top,
                        endPoint: .bottom
                    )
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

    // MARK: - Features Section

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

    // MARK: - Subscription Section

    private var subscriptionSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Choose Your Plan")
                .font(.headline)
                .padding(.horizontal, 4)

            if storeKitManager.isLoading {
                loadingView
            } else if storeKitManager.packages.isEmpty {
                noProductsView
            } else {
                VStack(spacing: 12) {
                    ForEach(storeKitManager.packages, id: \.identifier) { package in
                        SubscriptionOptionView(
                            package: package,
                            isSelected: selectedPackage?.identifier == package.identifier,
                            isBestValue: package.packageType == .annual,
                            isMonthly: package.packageType == .monthly
                        ) {
                            withAnimation(.spring(response: 0.3)) {
                                selectedPackage = package
                            }
                        }
                    }
                }

                purchaseButton
            }
        }
    }

    private var loadingView: some View {
        HStack {
            Spacer()
            ProgressView()
                .padding()
            Spacer()
        }
        .frame(height: 150)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var noProductsView: some View {
        VStack(spacing: 12) {
            Image(systemName: "wifi.slash")
                .font(.largeTitle)
                .foregroundStyle(.secondary)

            Text("Unable to load products")
                .font(.headline)

            Text("Please check your internet connection and try again.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            Button("Retry") {
                Task {
                    await storeKitManager.fetchProducts()
                }
            }
            .buttonStyle(.bordered)
        }
        .padding()
        .frame(maxWidth: .infinity)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var purchaseButton: some View {
        Button {
            Task {
                guard let package = selectedPackage else { return }
                let result = await storeKitManager.purchase(package)
                purchaseState = result

                switch result {
                case .purchased:
                    await PremiumManager.shared.validateSubscriptionState()
                    dismiss()
                case .pending:
                    restoreMessage = "Your purchase is pending approval. It will be activated once approved."
                    showRestoreAlert = true
                default:
                    break
                }
            }
        } label: {
            HStack {
                if storeKitManager.isPurchasing {
                    ProgressView()
                        .tint(.white)
                } else {
                    Text("Subscribe Now")
                        .fontWeight(.semibold)
                }
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(
                LinearGradient(
                    colors: [.purple, .blue],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .disabled(selectedPackage == nil || storeKitManager.isPurchasing)
        .padding(.top, 8)
    }

    // MARK: - Restore Section

    private var restoreSection: some View {
        Button {
            Task {
                await storeKitManager.restorePurchases()
                await PremiumManager.shared.validateSubscriptionState()

                if PremiumManager.shared.isPremium {
                    restoreMessage = "Your purchases have been restored successfully!"
                    showRestoreAlert = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                        dismiss()
                    }
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
        .disabled(storeKitManager.isLoading)
    }

    // MARK: - Legal Section

    private var legalSection: some View {
        VStack(spacing: 8) {
            Text("Subscription automatically renews unless cancelled at least 24 hours before the end of the current period. Manage subscriptions in Settings.")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            HStack(spacing: 16) {
                Link("Terms of Service", destination: URL(string: "https://kreativekoala.llc/terms")!)
                    .font(.caption)

                Text("•")
                    .foregroundStyle(.secondary)

                Link("Privacy Policy", destination: URL(string: "https://kreativekoala.llc/privacy")!)
                    .font(.caption)
            }
        }
        .padding(.top, 8)
    }
}

// MARK: - Feature Row

private struct FeatureRow: View {
    let feature: PremiumFeature

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: feature.iconName)
                .font(.title3)
                .foregroundStyle(.blue)
                .frame(width: 32)

            VStack(alignment: .leading, spacing: 2) {
                Text(feature.displayName)
                    .font(.subheadline)
                    .fontWeight(.medium)

                Text(feature.description)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(.green)
        }
    }
}

// MARK: - Subscription Option View

private struct SubscriptionOptionView: View {
    let package: RevenueCat.Package
    let isSelected: Bool
    let isBestValue: Bool
    let isMonthly: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(package.periodDescription ?? package.storeProduct.localizedTitle)
                            .font(.headline)
                            .foregroundStyle(.primary)

                        if isBestValue {
                            Text("Best Value")
                                .font(.caption2)
                                .fontWeight(.bold)
                                .foregroundStyle(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.green)
                                .clipShape(Capsule())
                        }

                        if isMonthly {
                            Text("Popular")
                                .font(.caption2)
                                .fontWeight(.bold)
                                .foregroundStyle(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.blue)
                                .clipShape(Capsule())
                        }
                    }

                    if let savings = calculateSavings(for: package) {
                        Text("Save \(savings)%")
                            .font(.caption)
                            .foregroundStyle(.green)
                    }
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 2) {
                    Text(package.localizedPriceString)
                        .font(.headline)
                        .foregroundStyle(.primary)

                    Text(package.priceWithPeriod)
                        .font(.caption)
                        .foregroundStyle(.secondary)
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

    private func calculateSavings(for package: RevenueCat.Package) -> Int? {
        // Calculate savings compared to weekly
        guard package.packageType != .weekly else { return nil }

        // Approximate weekly price for comparison: $2.99/week
        let weeklyPrice: Decimal = 2.99
        var weeksInPeriod: Decimal = 1

        if package.packageType == .monthly {
            weeksInPeriod = 4.33 // ~4.33 weeks per month
        } else if package.packageType == .annual {
            weeksInPeriod = 52
        }

        let expectedCost = weeklyPrice * weeksInPeriod
        let actualCost = package.storeProduct.price
        let savings = ((expectedCost - actualCost) / expectedCost) * 100

        let savingsInt = NSDecimalNumber(decimal: savings).intValue
        return savingsInt > 0 ? savingsInt : nil
    }
}

#Preview {
    PaywallView()
}
