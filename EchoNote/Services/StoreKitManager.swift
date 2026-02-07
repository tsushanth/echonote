import Foundation
import StoreKit

/// Product identifiers for EchoNote in-app purchases
enum ProductID: String, CaseIterable {
    // Subscriptions
    case weekly = "com.yourcompany.echonote.subscription.weekly"
    case monthly = "com.yourcompany.echonote.subscription.monthly"
    case yearly = "com.yourcompany.echonote.subscription.yearly"
    case lifetime = "com.yourcompany.echonote.subscription.lifetime"

    // One-time purchases
    case removeAds = "com.yourcompany.echonote.remove_ads"

    static var subscriptionIDs: [String] {
        [weekly.rawValue, monthly.rawValue, yearly.rawValue, lifetime.rawValue]
    }

    static var nonConsumableIDs: [String] {
        [removeAds.rawValue]
    }

    static var allIDs: [String] {
        allCases.map(\.rawValue)
    }

    var isSubscription: Bool {
        switch self {
        case .weekly, .monthly, .yearly, .lifetime:
            return true
        case .removeAds:
            return false
        }
    }
}

/// Purchase state for a transaction
enum PurchaseState {
    case notPurchased
    case purchased
    case pending
    case failed(Error)
}

/// StoreKit 2 manager for handling in-app purchases and subscriptions
@MainActor
@Observable
final class StoreKitManager {

    // MARK: - Properties

    /// All available products fetched from App Store
    private(set) var products: [Product] = []

    /// Subscription products sorted by price
    private(set) var subscriptionProducts: [Product] = []

    /// Non-consumable products
    private(set) var nonConsumableProducts: [Product] = []

    /// Currently purchased product IDs
    private(set) var purchasedProductIDs: Set<String> = []

    /// Loading state
    private(set) var isLoading: Bool = false

    /// Error message for display
    var errorMessage: String?
    var showError: Bool = false

    /// Purchase in progress
    private(set) var isPurchasing: Bool = false

    /// Transaction listener task
    private nonisolated(unsafe) var transactionListenerTask: Task<Void, Never>?

    // MARK: - Initialization

    init() {
        startTransactionListener()
    }

    deinit {
        transactionListenerTask?.cancel()
    }

    // MARK: - Public Methods

    /// Fetch products from App Store Connect
    func fetchProducts() async {
        isLoading = true
        defer { isLoading = false }

        do {
            let storeProducts = try await Product.products(for: ProductID.allIDs)

            // Separate subscription and non-consumable products
            var subscriptions: [Product] = []
            var nonConsumables: [Product] = []

            for product in storeProducts {
                if ProductID.subscriptionIDs.contains(product.id) {
                    subscriptions.append(product)
                } else if ProductID.nonConsumableIDs.contains(product.id) {
                    nonConsumables.append(product)
                }
            }

            // Sort subscriptions by price (ascending)
            subscriptionProducts = subscriptions.sorted { $0.price < $1.price }
            nonConsumableProducts = nonConsumables
            products = storeProducts

            // Update purchased state
            await updatePurchasedProducts()

        } catch {
            handleError(error, context: "fetching products")
        }
    }

    /// Purchase a product
    func purchase(_ product: Product) async -> PurchaseState {
        isPurchasing = true
        defer { isPurchasing = false }

        do {
            let result = try await product.purchase()

            switch result {
            case .success(let verification):
                let transaction = try checkVerified(verification)
                await updatePurchasedProducts()
                await transaction.finish()
                return .purchased

            case .userCancelled:
                return .notPurchased

            case .pending:
                return .pending

            @unknown default:
                return .notPurchased
            }

        } catch {
            handleError(error, context: "purchasing \(product.displayName)")
            return .failed(error)
        }
    }

    /// Restore purchases
    func restorePurchases() async {
        isLoading = true
        defer { isLoading = false }

        do {
            try await AppStore.sync()
            await updatePurchasedProducts()
        } catch {
            handleError(error, context: "restoring purchases")
        }
    }

    /// Check if a specific product is purchased
    func isPurchased(_ productID: String) -> Bool {
        purchasedProductIDs.contains(productID)
    }

    /// Check if user has any active subscription
    func hasActiveSubscription() -> Bool {
        for subscriptionID in ProductID.subscriptionIDs {
            if purchasedProductIDs.contains(subscriptionID) {
                return true
            }
        }
        return false
    }

    /// Get the current subscription status
    func getSubscriptionStatus() async -> Product.SubscriptionInfo.Status? {
        for product in subscriptionProducts {
            if let subscription = product.subscription {
                do {
                    let statuses = try await subscription.status
                    for status in statuses {
                        if case .verified(let renewalInfo) = status.renewalInfo,
                           case .verified(_) = status.transaction {
                            if renewalInfo.willAutoRenew || status.state == .subscribed {
                                return status
                            }
                        }
                    }
                } catch {
                    continue
                }
            }
        }
        return nil
    }

    /// Get expiration date for current subscription
    func getSubscriptionExpirationDate() async -> Date? {
        guard let status = await getSubscriptionStatus() else { return nil }

        if case .verified(let transaction) = status.transaction {
            return transaction.expirationDate
        }
        return nil
    }

    // MARK: - Private Methods

    private func startTransactionListener() {
        transactionListenerTask = Task.detached { [weak self] in
            for await result in Transaction.updates {
                await self?.handleTransactionUpdate(result)
            }
        }
    }

    private func handleTransactionUpdate(_ result: VerificationResult<Transaction>) async {
        do {
            let transaction = try checkVerified(result)
            await updatePurchasedProducts()
            await transaction.finish()
        } catch {
            // Transaction verification failed
            print("Transaction verification failed: \(error)")
        }
    }

    private func updatePurchasedProducts() async {
        var purchased: Set<String> = []

        // Check for subscription entitlements
        for await result in Transaction.currentEntitlements {
            do {
                let transaction = try checkVerified(result)

                // Check if subscription is still valid
                if let expirationDate = transaction.expirationDate {
                    if expirationDate > Date() {
                        purchased.insert(transaction.productID)
                    }
                } else {
                    // Non-subscription (lifetime or non-consumable)
                    purchased.insert(transaction.productID)
                }
            } catch {
                // Skip invalid transactions
                continue
            }
        }

        purchasedProductIDs = purchased
    }

    private func checkVerified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .unverified(_, let error):
            throw error
        case .verified(let safe):
            return safe
        }
    }

    private func handleError(_ error: Error, context: String) {
        let storeKitError = error as? StoreKitError

        switch storeKitError {
        case .networkError:
            errorMessage = "Network connection unavailable. Please check your internet connection and try again."
        case .userCancelled:
            // Don't show error for user cancellation
            return
        case .notAvailableInStorefront:
            errorMessage = "This product is not available in your region."
        case .notEntitled:
            errorMessage = "You are not entitled to this product."
        default:
            if let localizedError = error as? LocalizedError {
                errorMessage = localizedError.localizedDescription
            } else {
                errorMessage = "An error occurred while \(context). Please try again."
            }
        }

        showError = true
    }
}

// MARK: - Product Extension

extension Product {
    /// Formatted price string with period for subscriptions
    var priceWithPeriod: String {
        if let subscription = subscription {
            let period = subscription.subscriptionPeriod
            let periodString: String

            switch period.unit {
            case .day:
                periodString = period.value == 7 ? "week" : "\(period.value) days"
            case .week:
                periodString = period.value == 1 ? "week" : "\(period.value) weeks"
            case .month:
                periodString = period.value == 1 ? "month" : "\(period.value) months"
            case .year:
                periodString = period.value == 1 ? "year" : "\(period.value) years"
            @unknown default:
                periodString = "period"
            }

            return "\(displayPrice)/\(periodString)"
        }

        return displayPrice
    }

    /// Short period description
    var periodDescription: String? {
        guard let subscription = subscription else { return nil }

        let period = subscription.subscriptionPeriod

        switch period.unit {
        case .day:
            return period.value == 7 ? "Weekly" : "\(period.value)-day"
        case .week:
            return period.value == 1 ? "Weekly" : "\(period.value)-week"
        case .month:
            return period.value == 1 ? "Monthly" : "\(period.value)-month"
        case .year:
            return period.value == 1 ? "Yearly" : "\(period.value)-year"
        @unknown default:
            return nil
        }
    }

    /// Check if this is the lifetime option
    var isLifetime: Bool {
        id == ProductID.lifetime.rawValue
    }
}
