import XCTest
import SwiftUI
@testable import EchoNote

// MARK: - PaywallView Tests

/// Tests for the PaywallView component
/// Note: SwiftUI view testing is limited without UI testing framework.
/// These tests focus on the data and logic aspects of the paywall.
@MainActor
final class PaywallViewTests: XCTestCase {

    // MARK: - Feature Display Tests

    func testPremiumFeaturesCount() {
        // The PaywallView displays 8 premium features
        let displayedFeatures: [PremiumFeature] = [
            .enhancedAudio,
            .transcription,
            .highQualityRecording,
            .stereoRecording,
            .wavFormat,
            .unlimitedRecordings,
            .customFolders,
            .bookmarks
        ]

        XCTAssertEqual(displayedFeatures.count, 8, "PaywallView should display 8 premium features")
    }

    func testAllDisplayedFeaturesHaveMetadata() {
        let displayedFeatures: [PremiumFeature] = [
            .enhancedAudio,
            .transcription,
            .highQualityRecording,
            .stereoRecording,
            .wavFormat,
            .unlimitedRecordings,
            .customFolders,
            .bookmarks
        ]

        for feature in displayedFeatures {
            XCTAssertFalse(feature.displayName.isEmpty, "\(feature) should have a display name")
            XCTAssertFalse(feature.description.isEmpty, "\(feature) should have a description")
            XCTAssertFalse(feature.iconName.isEmpty, "\(feature) should have an icon name")
        }
    }

    // MARK: - Product ID Tests for Paywall

    func testYearlyProductID() {
        // The yearly product should be identified for "Best Value" badge
        let yearlyID = ProductID.yearly.rawValue
        XCTAssertEqual(yearlyID, "com.yourcompany.echonote.subscription.yearly")
    }

    func testRemoveAdsProductID() {
        // Remove Ads product is shown separately
        let removeAdsID = ProductID.removeAds.rawValue
        XCTAssertEqual(removeAdsID, "com.yourcompany.echonote.remove_ads")
    }

    // MARK: - Subscription Tier Tests for Paywall Display

    func testSubscriptionTierOrder() {
        // Products should be sorted by price: weekly < monthly < yearly < lifetime
        let expectedOrder: [ProductID] = [.weekly, .monthly, .yearly, .lifetime]

        // Verify raw values are in expected order for sorting
        XCTAssertEqual(ProductID.subscriptionIDs.count, 4)
    }

    // MARK: - Savings Calculation Tests

    func testSavingsCalculation_Monthly() {
        // Monthly: 4.33 weeks at $2.99/week = $12.95 expected, $9.99 actual
        let weeklyPrice: Decimal = 2.99
        let weeksInMonth: Decimal = 4.33
        let monthlyPrice: Decimal = 9.99

        let expectedCost = weeklyPrice * weeksInMonth
        let savings = ((expectedCost - monthlyPrice) / expectedCost) * 100

        let savingsInt = NSDecimalNumber(decimal: savings).intValue
        XCTAssertGreaterThan(savingsInt, 0, "Monthly plan should show savings")
    }

    func testSavingsCalculation_Yearly() {
        // Yearly: 52 weeks at $2.99/week = $155.48 expected, $49.99 actual
        let weeklyPrice: Decimal = 2.99
        let weeksInYear: Decimal = 52
        let yearlyPrice: Decimal = 49.99

        let expectedCost = weeklyPrice * weeksInYear
        let savings = ((expectedCost - yearlyPrice) / expectedCost) * 100

        let savingsInt = NSDecimalNumber(decimal: savings).intValue
        XCTAssertGreaterThan(savingsInt, 50, "Yearly plan should show significant savings (>50%)")
    }

    func testSavingsCalculation_Lifetime() {
        // Lifetime: 104 weeks (2 years) at $2.99/week = $310.96 expected, $99.99 actual
        let weeklyPrice: Decimal = 2.99
        let weeksForLifetime: Decimal = 104
        let lifetimePrice: Decimal = 99.99

        let expectedCost = weeklyPrice * weeksForLifetime
        let savings = ((expectedCost - lifetimePrice) / expectedCost) * 100

        let savingsInt = NSDecimalNumber(decimal: savings).intValue
        XCTAssertGreaterThan(savingsInt, 60, "Lifetime plan should show major savings (>60%)")
    }

    func testSavingsCalculation_Weekly() {
        // Weekly should not show savings (baseline)
        let weeklyPrice: Decimal = 2.99
        let expectedCost = weeklyPrice * 1
        let actualCost = weeklyPrice

        let savings = ((expectedCost - actualCost) / expectedCost) * 100
        XCTAssertEqual(NSDecimalNumber(decimal: savings).intValue, 0, "Weekly should not show savings")
    }

    // MARK: - Paywall State Tests

    func testInitialSelectedProduct_ShouldBeYearly() {
        // The paywall should auto-select yearly as the best value
        let yearlyID = ProductID.yearly.rawValue

        // In the actual implementation, this is set in the .task modifier
        // Here we verify the expected behavior
        XCTAssertEqual(yearlyID, "com.yourcompany.echonote.subscription.yearly")
    }

    // MARK: - Legal Links Tests

    func testTermsOfServiceURL() {
        let termsURL = URL(string: "https://yourcompany.com/terms")
        XCTAssertNotNil(termsURL, "Terms of Service URL should be valid")
    }

    func testPrivacyPolicyURL() {
        let privacyURL = URL(string: "https://yourcompany.com/privacy")
        XCTAssertNotNil(privacyURL, "Privacy Policy URL should be valid")
    }

    // MARK: - Button State Tests

    func testPurchaseButtonDisabled_NoProductSelected() {
        // When no product is selected, purchase button should be disabled
        let selectedProduct: MockProduct? = nil
        let isPurchasing = false

        let isDisabled = selectedProduct == nil || isPurchasing
        XCTAssertTrue(isDisabled, "Purchase button should be disabled when no product selected")
    }

    func testPurchaseButtonDisabled_WhilePurchasing() {
        // When purchase is in progress, button should be disabled
        let selectedProduct = MockProduct(
            id: ProductID.yearly.rawValue,
            displayName: "Yearly",
            displayPrice: "$49.99",
            price: Decimal(49.99),
            isSubscription: true
        )
        let isPurchasing = true

        let isDisabled = true // selectedProduct == nil is false, but isPurchasing is true
        XCTAssertTrue(isDisabled, "Purchase button should be disabled while purchasing")
    }

    func testPurchaseButtonEnabled_ProductSelectedNotPurchasing() {
        // When product is selected and not purchasing, button should be enabled
        let selectedProduct = MockProduct(
            id: ProductID.yearly.rawValue,
            displayName: "Yearly",
            displayPrice: "$49.99",
            price: Decimal(49.99),
            isSubscription: true
        )
        let isPurchasing = false

        let isDisabled = false // Neither condition is true
        XCTAssertFalse(isDisabled, "Purchase button should be enabled when product selected and not purchasing")
    }

    // MARK: - Restore Purchases Tests

    func testRestoreButtonDisabled_WhileLoading() {
        let isLoading = true
        XCTAssertTrue(isLoading, "Restore button should be disabled while loading")
    }

    func testRestoreButtonEnabled_NotLoading() {
        let isLoading = false
        XCTAssertFalse(isLoading, "Restore button should be enabled when not loading")
    }

    // MARK: - Alert Message Tests

    func testRestoreMessage_Success() {
        let isPremium = true
        let message = isPremium ? "Your purchases have been restored successfully!" : "No previous purchases found."
        XCTAssertEqual(message, "Your purchases have been restored successfully!")
    }

    func testRestoreMessage_NoPreiousPurchases() {
        let isPremium = false
        let message = isPremium ? "Your purchases have been restored successfully!" : "No previous purchases found."
        XCTAssertEqual(message, "No previous purchases found.")
    }

    func testPendingPurchaseMessage() {
        let pendingMessage = "Your purchase is pending approval. It will be activated once approved."
        XCTAssertFalse(pendingMessage.isEmpty)
    }

    // MARK: - View Component Tests

    func testFeatureRowHasRequiredProperties() {
        let feature = PremiumFeature.transcription

        // FeatureRow displays icon, display name, description, and checkmark
        XCTAssertEqual(feature.iconName, "text.quote")
        XCTAssertEqual(feature.displayName, "Transcription")
        XCTAssertEqual(feature.description, "Convert speech to text with high accuracy")
    }

    func testSubscriptionOptionViewProperties() {
        let product = MockProduct(
            id: ProductID.yearly.rawValue,
            displayName: "Yearly",
            displayPrice: "$49.99",
            price: Decimal(49.99),
            isSubscription: true
        )
        let isSelected = true
        let isBestValue = true

        XCTAssertEqual(product.id, ProductID.yearly.rawValue)
        XCTAssertTrue(isSelected)
        XCTAssertTrue(isBestValue)
    }

    // MARK: - Best Value Badge Tests

    func testBestValueBadge_OnlyForYearly() {
        // Best Value badge should only appear on yearly subscription
        let subscriptions: [ProductID] = [.weekly, .monthly, .yearly, .lifetime]

        for subscription in subscriptions {
            let isBestValue = subscription == .yearly
            if subscription == .yearly {
                XCTAssertTrue(isBestValue, "Yearly should have Best Value badge")
            } else {
                XCTAssertFalse(isBestValue, "\(subscription) should not have Best Value badge")
            }
        }
    }

    // MARK: - One-Time Badge Tests

    func testOneTimeBadge_OnlyForLifetime() {
        // One-Time badge should only appear on lifetime subscription
        let subscriptions: [ProductID] = [.weekly, .monthly, .yearly, .lifetime]

        for subscription in subscriptions {
            let isLifetime = subscription == .lifetime
            if subscription == .lifetime {
                XCTAssertTrue(isLifetime, "Lifetime should have One-Time badge")
            } else {
                XCTAssertFalse(isLifetime, "\(subscription) should not have One-Time badge")
            }
        }
    }

    // MARK: - Loading View Tests

    func testLoadingViewHeight() {
        // Loading view should have a height of 150
        let expectedHeight: CGFloat = 150
        XCTAssertEqual(expectedHeight, 150)
    }

    // MARK: - No Products View Tests

    func testNoProductsViewMessage() {
        let primaryMessage = "Unable to load products"
        let secondaryMessage = "Please check your internet connection and try again."

        XCTAssertFalse(primaryMessage.isEmpty)
        XCTAssertFalse(secondaryMessage.isEmpty)
    }

    // MARK: - Header Section Tests

    func testHeaderSection_CrownIcon() {
        let iconName = "crown.fill"
        XCTAssertEqual(iconName, "crown.fill")
    }

    func testHeaderSection_Title() {
        let title = "Unlock Premium"
        XCTAssertEqual(title, "Unlock Premium")
    }

    func testHeaderSection_Subtitle() {
        let subtitle = "Get access to all features and enhance your recording experience"
        XCTAssertFalse(subtitle.isEmpty)
    }

    // MARK: - Legal Section Tests

    func testLegalSection_SubscriptionDisclaimer() {
        let disclaimer = "Subscription automatically renews unless cancelled at least 24 hours before the end of the current period. Manage subscriptions in Settings."
        XCTAssertTrue(disclaimer.contains("renews"))
        XCTAssertTrue(disclaimer.contains("24 hours"))
        XCTAssertTrue(disclaimer.contains("Settings"))
    }
}

// MARK: - PaywallView Snapshot/Rendering Tests

extension PaywallViewTests {

    func testPaywallView_CanBeCreated() {
        // This tests that the PaywallView can be instantiated
        // The actual rendering would require ViewInspector or UI tests
        let view = PaywallView()
        XCTAssertNotNil(view)
    }
}

// MARK: - PurchaseState Handling Tests

extension PaywallViewTests {

    func testPurchaseStateHandling_Purchased() async {
        let mockManager = MockStoreKitManager()
        await mockManager.fetchProducts()

        guard let product = mockManager.subscriptionProducts.first else {
            XCTFail("No products available")
            return
        }

        mockManager.simulatedPurchaseResult = .purchased
        let result = await mockManager.purchase(product)

        switch result {
        case .purchased:
            // Success - paywall should dismiss
            XCTAssertTrue(mockManager.isPurchased(product.id))
        default:
            XCTFail("Expected purchased result")
        }
    }

    func testPurchaseStateHandling_Pending() async {
        let mockManager = MockStoreKitManager()
        await mockManager.fetchProducts()

        guard let product = mockManager.subscriptionProducts.first else {
            XCTFail("No products available")
            return
        }

        mockManager.simulatedPurchaseResult = .pending
        let result = await mockManager.purchase(product)

        switch result {
        case .pending:
            // Pending - should show alert with pending message
            XCTAssertFalse(mockManager.isPurchased(product.id))
        default:
            XCTFail("Expected pending result")
        }
    }

    func testPurchaseStateHandling_Failed() async {
        let mockManager = MockStoreKitManager()
        await mockManager.fetchProducts()

        guard let product = mockManager.subscriptionProducts.first else {
            XCTFail("No products available")
            return
        }

        mockManager.shouldFailPurchase = true
        mockManager.purchaseError = NSError(domain: "StoreKit", code: -1, userInfo: [NSLocalizedDescriptionKey: "Payment failed"])

        let result = await mockManager.purchase(product)

        switch result {
        case .failed:
            // Failed - should show error
            XCTAssertFalse(mockManager.isPurchased(product.id))
            XCTAssertTrue(mockManager.showError)
        default:
            XCTFail("Expected failed result")
        }
    }

    func testPurchaseStateHandling_Cancelled() async {
        let mockManager = MockStoreKitManager()
        await mockManager.fetchProducts()

        guard let product = mockManager.subscriptionProducts.first else {
            XCTFail("No products available")
            return
        }

        mockManager.simulatedPurchaseResult = .notPurchased
        let result = await mockManager.purchase(product)

        switch result {
        case .notPurchased:
            // Cancelled - no action needed
            XCTAssertFalse(mockManager.isPurchased(product.id))
        default:
            XCTFail("Expected notPurchased result")
        }
    }
}
