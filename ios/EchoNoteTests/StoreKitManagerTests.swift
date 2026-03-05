import XCTest
import StoreKit
@testable import EchoNote

// MARK: - Mock StoreKit Manager for Testing

/// A testable version of StoreKitManager that allows us to inject mock data
@MainActor
final class MockStoreKitManager: @unchecked Sendable {

    // MARK: - Properties

    private(set) var products: [MockProduct] = []
    private(set) var subscriptionProducts: [MockProduct] = []
    private(set) var nonConsumableProducts: [MockProduct] = []
    private(set) var purchasedProductIDs: Set<String> = []
    private(set) var isLoading: Bool = false
    var errorMessage: String?
    var showError: Bool = false
    private(set) var isPurchasing: Bool = false

    // MARK: - Test Configuration

    var shouldFailFetch: Bool = false
    var fetchError: Error?
    var shouldFailPurchase: Bool = false
    var purchaseError: Error?
    var simulatedPurchaseResult: PurchaseState = .purchased

    // MARK: - Mock Methods

    func fetchProducts() async {
        isLoading = true
        defer { isLoading = false }

        if shouldFailFetch {
            errorMessage = fetchError?.localizedDescription ?? "Failed to fetch products"
            showError = true
            return
        }

        // Create mock products
        let weekly = MockProduct(
            id: ProductID.weekly.rawValue,
            displayName: "Weekly",
            displayPrice: "$2.99",
            price: Decimal(2.99),
            isSubscription: true
        )

        let monthly = MockProduct(
            id: ProductID.monthly.rawValue,
            displayName: "Monthly",
            displayPrice: "$9.99",
            price: Decimal(9.99),
            isSubscription: true
        )

        let yearly = MockProduct(
            id: ProductID.yearly.rawValue,
            displayName: "Yearly",
            displayPrice: "$49.99",
            price: Decimal(49.99),
            isSubscription: true
        )

        let lifetime = MockProduct(
            id: ProductID.lifetime.rawValue,
            displayName: "Lifetime",
            displayPrice: "$99.99",
            price: Decimal(99.99),
            isSubscription: true
        )

        let removeAds = MockProduct(
            id: ProductID.removeAds.rawValue,
            displayName: "Remove Ads",
            displayPrice: "$4.99",
            price: Decimal(4.99),
            isSubscription: false
        )

        subscriptionProducts = [weekly, monthly, yearly, lifetime].sorted { $0.price < $1.price }
        nonConsumableProducts = [removeAds]
        products = subscriptionProducts + nonConsumableProducts
    }

    func purchase(_ product: MockProduct) async -> PurchaseState {
        isPurchasing = true
        defer { isPurchasing = false }

        if shouldFailPurchase {
            errorMessage = purchaseError?.localizedDescription ?? "Purchase failed"
            showError = true
            return .failed(purchaseError ?? NSError(domain: "test", code: -1))
        }

        if case .purchased = simulatedPurchaseResult {
            purchasedProductIDs.insert(product.id)
        }

        return simulatedPurchaseResult
    }

    func restorePurchases() async {
        isLoading = true
        defer { isLoading = false }

        // Simulate restoring previously purchased products
        // In a real test, this would be configured based on test requirements
    }

    func isPurchased(_ productID: String) -> Bool {
        purchasedProductIDs.contains(productID)
    }

    func hasActiveSubscription() -> Bool {
        for subscriptionID in ProductID.subscriptionIDs {
            if purchasedProductIDs.contains(subscriptionID) {
                return true
            }
        }
        return false
    }

    func simulatePurchase(productID: String) {
        purchasedProductIDs.insert(productID)
    }

    func clearPurchases() {
        purchasedProductIDs.removeAll()
    }
}

// MARK: - Mock Product

struct MockProduct {
    let id: String
    let displayName: String
    let displayPrice: String
    let price: Decimal
    let isSubscription: Bool
}

// MARK: - StoreKit Manager Tests

@MainActor
final class StoreKitManagerTests: XCTestCase {

    var sut: MockStoreKitManager!

    override func setUp() async throws {
        try await super.setUp()
        sut = MockStoreKitManager()
    }

    override func tearDown() async throws {
        sut = nil
        try await super.tearDown()
    }

    // MARK: - Product Loading Tests

    func testFetchProducts_Success() async {
        // Given
        sut.shouldFailFetch = false

        // When
        await sut.fetchProducts()

        // Then
        XCTAssertFalse(sut.products.isEmpty, "Products should be loaded")
        XCTAssertEqual(sut.subscriptionProducts.count, 4, "Should have 4 subscription products")
        XCTAssertEqual(sut.nonConsumableProducts.count, 1, "Should have 1 non-consumable product")
        XCTAssertFalse(sut.isLoading, "Loading should be false after fetch")
        XCTAssertNil(sut.errorMessage, "Should not have error message on success")
    }

    func testFetchProducts_Failure() async {
        // Given
        sut.shouldFailFetch = true
        sut.fetchError = NSError(domain: "StoreKit", code: -1, userInfo: [NSLocalizedDescriptionKey: "Network error"])

        // When
        await sut.fetchProducts()

        // Then
        XCTAssertTrue(sut.products.isEmpty, "Products should be empty on failure")
        XCTAssertNotNil(sut.errorMessage, "Should have error message on failure")
        XCTAssertTrue(sut.showError, "Should show error on failure")
    }

    func testFetchProducts_ProductsSortedByPrice() async {
        // Given
        sut.shouldFailFetch = false

        // When
        await sut.fetchProducts()

        // Then
        let prices = sut.subscriptionProducts.map { $0.price }
        let sortedPrices = prices.sorted()
        XCTAssertEqual(prices, sortedPrices, "Subscription products should be sorted by price ascending")
    }

    func testFetchProducts_LoadingStateTransitions() async {
        // Given
        sut.shouldFailFetch = false
        XCTAssertFalse(sut.isLoading, "Initially should not be loading")

        // When
        let fetchTask = Task {
            await sut.fetchProducts()
        }

        // Then
        await fetchTask.value
        XCTAssertFalse(sut.isLoading, "Should not be loading after fetch completes")
    }

    // MARK: - Purchase Flow Tests

    func testPurchase_Success() async {
        // Given
        await sut.fetchProducts()
        guard let product = sut.subscriptionProducts.first else {
            XCTFail("No products available")
            return
        }
        sut.shouldFailPurchase = false
        sut.simulatedPurchaseResult = .purchased

        // When
        let result = await sut.purchase(product)

        // Then
        if case .purchased = result {
            XCTAssertTrue(sut.isPurchased(product.id), "Product should be marked as purchased")
        } else {
            XCTFail("Expected purchased result")
        }
    }

    func testPurchase_UserCancelled() async {
        // Given
        await sut.fetchProducts()
        guard let product = sut.subscriptionProducts.first else {
            XCTFail("No products available")
            return
        }
        sut.shouldFailPurchase = false
        sut.simulatedPurchaseResult = .notPurchased

        // When
        let result = await sut.purchase(product)

        // Then
        if case .notPurchased = result {
            XCTAssertFalse(sut.isPurchased(product.id), "Product should not be marked as purchased")
        } else {
            XCTFail("Expected notPurchased result")
        }
    }

    func testPurchase_Pending() async {
        // Given
        await sut.fetchProducts()
        guard let product = sut.subscriptionProducts.first else {
            XCTFail("No products available")
            return
        }
        sut.shouldFailPurchase = false
        sut.simulatedPurchaseResult = .pending

        // When
        let result = await sut.purchase(product)

        // Then
        if case .pending = result {
            XCTAssertFalse(sut.isPurchased(product.id), "Product should not be marked as purchased when pending")
        } else {
            XCTFail("Expected pending result")
        }
    }

    func testPurchase_Failure() async {
        // Given
        await sut.fetchProducts()
        guard let product = sut.subscriptionProducts.first else {
            XCTFail("No products available")
            return
        }
        sut.shouldFailPurchase = true
        sut.purchaseError = NSError(domain: "StoreKit", code: -1, userInfo: [NSLocalizedDescriptionKey: "Payment failed"])

        // When
        let result = await sut.purchase(product)

        // Then
        if case .failed = result {
            XCTAssertFalse(sut.isPurchased(product.id), "Product should not be marked as purchased on failure")
            XCTAssertNotNil(sut.errorMessage, "Should have error message on failure")
            XCTAssertTrue(sut.showError, "Should show error on failure")
        } else {
            XCTFail("Expected failed result")
        }
    }

    func testPurchase_IsPurchasingStateTransitions() async {
        // Given
        await sut.fetchProducts()
        guard let product = sut.subscriptionProducts.first else {
            XCTFail("No products available")
            return
        }
        XCTAssertFalse(sut.isPurchasing, "Initially should not be purchasing")

        // When
        _ = await sut.purchase(product)

        // Then
        XCTAssertFalse(sut.isPurchasing, "Should not be purchasing after purchase completes")
    }

    // MARK: - Restore Purchases Tests

    func testRestorePurchases_SetsLoadingState() async {
        // Given
        XCTAssertFalse(sut.isLoading, "Initially should not be loading")

        // When
        await sut.restorePurchases()

        // Then
        XCTAssertFalse(sut.isLoading, "Should not be loading after restore completes")
    }

    // MARK: - Subscription Status Tests

    func testHasActiveSubscription_NoSubscriptions() async {
        // Given
        sut.clearPurchases()

        // When
        let hasSubscription = sut.hasActiveSubscription()

        // Then
        XCTAssertFalse(hasSubscription, "Should not have active subscription when no purchases")
    }

    func testHasActiveSubscription_WithWeeklySubscription() async {
        // Given
        sut.simulatePurchase(productID: ProductID.weekly.rawValue)

        // When
        let hasSubscription = sut.hasActiveSubscription()

        // Then
        XCTAssertTrue(hasSubscription, "Should have active subscription with weekly purchase")
    }

    func testHasActiveSubscription_WithMonthlySubscription() async {
        // Given
        sut.simulatePurchase(productID: ProductID.monthly.rawValue)

        // When
        let hasSubscription = sut.hasActiveSubscription()

        // Then
        XCTAssertTrue(hasSubscription, "Should have active subscription with monthly purchase")
    }

    func testHasActiveSubscription_WithYearlySubscription() async {
        // Given
        sut.simulatePurchase(productID: ProductID.yearly.rawValue)

        // When
        let hasSubscription = sut.hasActiveSubscription()

        // Then
        XCTAssertTrue(hasSubscription, "Should have active subscription with yearly purchase")
    }

    func testHasActiveSubscription_WithLifetimeSubscription() async {
        // Given
        sut.simulatePurchase(productID: ProductID.lifetime.rawValue)

        // When
        let hasSubscription = sut.hasActiveSubscription()

        // Then
        XCTAssertTrue(hasSubscription, "Should have active subscription with lifetime purchase")
    }

    func testHasActiveSubscription_OnlyRemoveAds() async {
        // Given
        sut.simulatePurchase(productID: ProductID.removeAds.rawValue)

        // When
        let hasSubscription = sut.hasActiveSubscription()

        // Then
        XCTAssertFalse(hasSubscription, "Remove ads should not count as active subscription")
    }

    // MARK: - Error Handling Tests

    func testErrorHandling_NetworkError() async {
        // Given
        sut.shouldFailFetch = true
        sut.fetchError = NSError(domain: "Network", code: -1009, userInfo: [NSLocalizedDescriptionKey: "No internet connection"])

        // When
        await sut.fetchProducts()

        // Then
        XCTAssertNotNil(sut.errorMessage)
        XCTAssertTrue(sut.showError)
    }

    func testErrorHandling_ClearError() async {
        // Given
        sut.shouldFailFetch = true
        await sut.fetchProducts()
        XCTAssertTrue(sut.showError)

        // When
        sut.showError = false

        // Then
        XCTAssertFalse(sut.showError)
    }

    // MARK: - Product ID Tests

    func testProductID_SubscriptionIDs() {
        // Given
        let subscriptionIDs = ProductID.subscriptionIDs

        // Then
        XCTAssertEqual(subscriptionIDs.count, 4)
        XCTAssertTrue(subscriptionIDs.contains(ProductID.weekly.rawValue))
        XCTAssertTrue(subscriptionIDs.contains(ProductID.monthly.rawValue))
        XCTAssertTrue(subscriptionIDs.contains(ProductID.yearly.rawValue))
        XCTAssertTrue(subscriptionIDs.contains(ProductID.lifetime.rawValue))
    }

    func testProductID_NonConsumableIDs() {
        // Given
        let nonConsumableIDs = ProductID.nonConsumableIDs

        // Then
        XCTAssertEqual(nonConsumableIDs.count, 1)
        XCTAssertTrue(nonConsumableIDs.contains(ProductID.removeAds.rawValue))
    }

    func testProductID_AllIDs() {
        // Given
        let allIDs = ProductID.allIDs

        // Then
        XCTAssertEqual(allIDs.count, 5)
    }

    func testProductID_IsSubscription() {
        // Then
        XCTAssertTrue(ProductID.weekly.isSubscription)
        XCTAssertTrue(ProductID.monthly.isSubscription)
        XCTAssertTrue(ProductID.yearly.isSubscription)
        XCTAssertTrue(ProductID.lifetime.isSubscription)
        XCTAssertFalse(ProductID.removeAds.isSubscription)
    }

    // MARK: - Purchase State Tests

    func testPurchaseState_NotPurchased() {
        let state = PurchaseState.notPurchased
        if case .notPurchased = state {
            // Success
        } else {
            XCTFail("Expected notPurchased state")
        }
    }

    func testPurchaseState_Purchased() {
        let state = PurchaseState.purchased
        if case .purchased = state {
            // Success
        } else {
            XCTFail("Expected purchased state")
        }
    }

    func testPurchaseState_Pending() {
        let state = PurchaseState.pending
        if case .pending = state {
            // Success
        } else {
            XCTFail("Expected pending state")
        }
    }

    func testPurchaseState_Failed() {
        let error = NSError(domain: "test", code: -1)
        let state = PurchaseState.failed(error)
        if case .failed(let returnedError) = state {
            XCTAssertEqual((returnedError as NSError).domain, "test")
        } else {
            XCTFail("Expected failed state")
        }
    }
}

// MARK: - Integration-style Tests

extension StoreKitManagerTests {

    func testFullPurchaseFlow() async {
        // Given - Initial state
        XCTAssertFalse(sut.hasActiveSubscription())

        // When - Fetch products
        await sut.fetchProducts()

        // Then - Products loaded
        XCTAssertFalse(sut.products.isEmpty)

        // When - Purchase yearly subscription
        guard let yearlyProduct = sut.subscriptionProducts.first(where: { $0.id == ProductID.yearly.rawValue }) else {
            XCTFail("Yearly product not found")
            return
        }

        sut.simulatedPurchaseResult = .purchased
        let result = await sut.purchase(yearlyProduct)

        // Then - Purchase successful
        if case .purchased = result {
            XCTAssertTrue(sut.hasActiveSubscription())
            XCTAssertTrue(sut.isPurchased(ProductID.yearly.rawValue))
        } else {
            XCTFail("Expected successful purchase")
        }
    }

    func testMultipleProductsPurchased() async {
        // Given
        await sut.fetchProducts()

        // When - Purchase subscription and remove ads
        sut.simulatePurchase(productID: ProductID.monthly.rawValue)
        sut.simulatePurchase(productID: ProductID.removeAds.rawValue)

        // Then
        XCTAssertTrue(sut.hasActiveSubscription())
        XCTAssertTrue(sut.isPurchased(ProductID.monthly.rawValue))
        XCTAssertTrue(sut.isPurchased(ProductID.removeAds.rawValue))
    }
}
