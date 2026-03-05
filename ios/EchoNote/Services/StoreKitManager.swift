import Foundation
import RevenueCat
import FirebaseAnalytics

/// Product identifiers for ClearVoice in-app purchases
enum ProductID: String, CaseIterable {
    // Subscriptions
    case weekly = "com.kreativekoala.clearvoice.subscription.weekly"
    case monthly = "com.kreativekoala.clearvoice.subscription.monthly"
    case yearly = "com.kreativekoala.clearvoice.subscription.yearly"

    static var subscriptionIDs: [String] {
        [weekly.rawValue, monthly.rawValue, yearly.rawValue]
    }

    static var allIDs: [String] {
        allCases.map(\.rawValue)
    }
}

/// Purchase state for a transaction
enum PurchaseState {
    case notPurchased
    case purchased
    case pending
    case failed(Error)
}

/// RevenueCat-backed manager for handling in-app purchases and subscriptions
@MainActor
@Observable
final class StoreKitManager {

    // MARK: - Properties

    /// Current RevenueCat offering
    private(set) var currentOffering: Offering?

    /// Available packages from the current offering
    private(set) var packages: [RevenueCat.Package] = []

    /// Currently purchased product IDs
    private(set) var purchasedProductIDs: Set<String> = []

    /// Loading state
    private(set) var isLoading: Bool = false

    /// Error message for display
    var errorMessage: String?
    var showError: Bool = false

    /// Purchase in progress
    private(set) var isPurchasing: Bool = false

    private static let entitlementID = "premium"

    // MARK: - Initialization

    init() {
        // RevenueCat is configured in EchoNoteApp.init()
    }

    // MARK: - Public Methods

    /// Fetch products from RevenueCat
    func fetchProducts() async {
        isLoading = true
        defer { isLoading = false }

        do {
            let offerings = try await Purchases.shared.offerings()
            if let current = offerings.current {
                currentOffering = current
                packages = current.availablePackages
            }
            // Update purchased state
            await updatePurchasedProducts()
        } catch {
            handleError(error, context: "fetching products")
        }
    }

    /// Purchase a package
    func purchase(_ package: RevenueCat.Package) async -> PurchaseState {
        isPurchasing = true
        defer { isPurchasing = false }

        do {
            let result = try await Purchases.shared.purchase(package: package)

            if result.customerInfo.entitlements[Self.entitlementID]?.isActive == true {
                purchasedProductIDs.insert(package.storeProduct.productIdentifier)
                await updatePurchasedProducts()

                // Track purchase events for attribution
                let productId = package.storeProduct.productIdentifier
                let price = package.storeProduct.price
                let currency = package.storeProduct.currencyCode ?? "USD"
                let params: [String: Any] = ["product_id": productId, "price": Double(truncating: price as NSNumber), "currency": currency]
                Analytics.logEvent(AnalyticsEventPurchase, parameters: [
                    AnalyticsParameterCurrency: currency,
                    AnalyticsParameterValue: Double(truncating: price as NSNumber),
                    AnalyticsParameterItems: [[AnalyticsParameterItemID: productId]]
                ])
                Analytics.logEvent("purchase_success", parameters: params)
                TikTokHelper.shared.trackEvent("purchase_success", properties: params)

                return .purchased
            }

            if !result.userCancelled {
                return .pending
            }
            return .notPurchased
        } catch {
            if error.isCancelledError {
                return .notPurchased
            }
            handleError(error, context: "purchasing")
            return .failed(error)
        }
    }

    /// Restore purchases
    func restorePurchases() async {
        isLoading = true
        defer { isLoading = false }

        do {
            let customerInfo = try await Purchases.shared.restorePurchases()
            updateFromCustomerInfo(customerInfo)
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
        !purchasedProductIDs.isEmpty
    }

    /// Get expiration date for current subscription
    func getSubscriptionExpirationDate() async -> Date? {
        do {
            let customerInfo = try await Purchases.shared.customerInfo()
            return customerInfo.entitlements[Self.entitlementID]?.expirationDate
        } catch {
            return nil
        }
    }

    // MARK: - Private Methods

    private func updatePurchasedProducts() async {
        do {
            let customerInfo = try await Purchases.shared.customerInfo()
            updateFromCustomerInfo(customerInfo)
        } catch {
            #if DEBUG
            print("Failed to get customer info: \(error.localizedDescription)")
            #endif
        }
    }

    private func updateFromCustomerInfo(_ customerInfo: CustomerInfo) {
        if customerInfo.entitlements[Self.entitlementID]?.isActive == true {
            // Add all active subscription product IDs
            var purchased: Set<String> = []
            for (_, entitlement) in customerInfo.entitlements.all where entitlement.isActive {
                purchased.insert(entitlement.productIdentifier)
            }
            purchasedProductIDs = purchased
        } else {
            purchasedProductIDs = []
        }
    }

    private func handleError(_ error: Error, context: String) {
        if error.isCancelledError {
            return
        }

        let nsError = error as NSError
        if nsError.domain == RevenueCat.ErrorCode.errorDomain {
            switch RevenueCat.ErrorCode(rawValue: nsError.code) {
            case .networkError:
                errorMessage = "Network connection unavailable. Please check your internet connection and try again."
            case .storeProblemError:
                errorMessage = "There was a problem with the App Store. Please try again later."
            default:
                errorMessage = "An error occurred while \(context). Please try again."
            }
        } else {
            errorMessage = "An error occurred while \(context). Please try again."
        }

        showError = true
    }
}

// MARK: - Error Helpers

private extension Error {
    /// Check if this error represents a user-cancelled purchase
    var isCancelledError: Bool {
        let nsError = self as NSError
        // RevenueCat wraps cancellation errors with code 1
        if nsError.domain == RevenueCat.ErrorCode.errorDomain,
           nsError.code == RevenueCat.ErrorCode.purchaseCancelledError.rawValue {
            return true
        }
        return false
    }
}

// MARK: - Package Extension for PaywallView compatibility

extension RevenueCat.Package {
    /// Formatted price string with period for subscriptions
    var priceWithPeriod: String {
        let price = localizedPriceString

        switch packageType {
        case .weekly:
            return "\(price)/week"
        case .monthly:
            return "\(price)/month"
        case .annual:
            return "\(price)/year"
        default:
            if let period = storeProduct.subscriptionPeriod {
                switch period.unit {
                case .day:
                    return period.value == 7 ? "\(price)/week" : "\(price)/\(period.value) days"
                case .week:
                    return period.value == 1 ? "\(price)/week" : "\(price)/\(period.value) weeks"
                case .month:
                    return period.value == 1 ? "\(price)/month" : "\(price)/\(period.value) months"
                case .year:
                    return period.value == 1 ? "\(price)/year" : "\(price)/\(period.value) years"
                @unknown default:
                    return price
                }
            }
            return price
        }
    }

    /// Short period description
    var periodDescription: String? {
        switch packageType {
        case .weekly: return "Weekly"
        case .monthly: return "Monthly"
        case .annual: return "Yearly"
        default:
            if let period = storeProduct.subscriptionPeriod {
                switch period.unit {
                case .day: return period.value == 7 ? "Weekly" : "\(period.value)-day"
                case .week: return period.value == 1 ? "Weekly" : "\(period.value)-week"
                case .month: return period.value == 1 ? "Monthly" : "\(period.value)-month"
                case .year: return period.value == 1 ? "Yearly" : "\(period.value)-year"
                @unknown default: return nil
                }
            }
            return nil
        }
    }
}
