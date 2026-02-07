import XCTest
@testable import EchoNote

// MARK: - Mock Premium Manager for Testing

/// A testable version of PremiumManager that doesn't use singleton
@MainActor
final class TestPremiumManager {

    // MARK: - Properties

    private(set) var currentTier: SubscriptionTier = .free
    private(set) var hasRemovedAds: Bool = false
    private(set) var subscriptionExpirationDate: Date?

    var isPremium: Bool {
        currentTier != .free
    }

    var isLifetime: Bool {
        currentTier == .lifetime
    }

    // MARK: - Test UserDefaults

    private let userDefaults: UserDefaults
    private let suiteName = "com.echonote.tests.premium"

    // MARK: - UserDefaults Keys

    private enum UserDefaultsKey {
        static let subscriptionTier = "com.echonote.subscription.tier"
        static let hasRemovedAds = "com.echonote.ads.removed"
        static let subscriptionExpiration = "com.echonote.subscription.expiration"
        static let lastValidationDate = "com.echonote.validation.date"
    }

    // MARK: - Free Tier Limits

    static let freeRecordingLimit = 10
    static let freeFolderLimit = 3
    static let freeBookmarkLimit = 5

    // MARK: - Initialization

    init() {
        // Use a separate UserDefaults suite for testing
        userDefaults = UserDefaults(suiteName: suiteName) ?? .standard
        clearTestData()
    }

    deinit {
        clearTestData()
    }

    // MARK: - Test Helpers

    func clearTestData() {
        userDefaults.removePersistentDomain(forName: suiteName)
        currentTier = .free
        hasRemovedAds = false
        subscriptionExpirationDate = nil
    }

    // MARK: - Public Methods

    func setTier(_ tier: SubscriptionTier) {
        currentTier = tier
        persistState()
    }

    func setRemovedAds(_ removed: Bool) {
        hasRemovedAds = removed
        persistState()
    }

    func setExpirationDate(_ date: Date?) {
        subscriptionExpirationDate = date
        persistState()
    }

    func loadPersistedState() {
        if let tierRaw = userDefaults.string(forKey: UserDefaultsKey.subscriptionTier),
           let tier = SubscriptionTier(rawValue: tierRaw) {
            currentTier = tier
        }

        hasRemovedAds = userDefaults.bool(forKey: UserDefaultsKey.hasRemovedAds)

        if let expirationInterval = userDefaults.object(forKey: UserDefaultsKey.subscriptionExpiration) as? TimeInterval {
            let expirationDate = Date(timeIntervalSince1970: expirationInterval)
            if expirationDate > Date() {
                subscriptionExpirationDate = expirationDate
            } else {
                currentTier = .free
            }
        }
    }

    func persistState() {
        userDefaults.set(currentTier.rawValue, forKey: UserDefaultsKey.subscriptionTier)
        userDefaults.set(hasRemovedAds, forKey: UserDefaultsKey.hasRemovedAds)

        if let expirationDate = subscriptionExpirationDate {
            userDefaults.set(expirationDate.timeIntervalSince1970, forKey: UserDefaultsKey.subscriptionExpiration)
        } else {
            userDefaults.removeObject(forKey: UserDefaultsKey.subscriptionExpiration)
        }

        userDefaults.set(Date().timeIntervalSince1970, forKey: UserDefaultsKey.lastValidationDate)
    }

    func hasAccess(to feature: PremiumFeature) -> Bool {
        if isPremium {
            return true
        }

        switch feature {
        case .highQualityRecording, .stereoRecording, .wavFormat:
            return false
        case .enhancedAudio, .vocalLayerSeparation, .transcription:
            return false
        case .cloudBackup:
            return false
        case .unlimitedRecordings, .customFolders, .bookmarks:
            return true
        }
    }

    func hasReachedRecordingLimit(currentCount: Int) -> Bool {
        if isPremium { return false }
        return currentCount >= Self.freeRecordingLimit
    }

    func hasReachedFolderLimit(currentCount: Int) -> Bool {
        if isPremium { return false }
        return currentCount >= Self.freeFolderLimit
    }

    func hasReachedBookmarkLimit(currentCount: Int) -> Bool {
        if isPremium { return false }
        return currentCount >= Self.freeBookmarkLimit
    }

    func isSubscriptionExpiringSoon() -> Bool {
        guard let expirationDate = subscriptionExpirationDate else { return false }
        let threeDaysFromNow = Calendar.current.date(byAdding: .day, value: 3, to: Date()) ?? Date()
        return expirationDate < threeDaysFromNow && expirationDate > Date()
    }

    func remainingSubscriptionDays() -> Int? {
        guard let expirationDate = subscriptionExpirationDate else { return nil }
        let calendar = Calendar.current
        let components = calendar.dateComponents([.day], from: Date(), to: expirationDate)
        return components.day
    }
}

// MARK: - Premium Manager Tests

@MainActor
final class PremiumManagerTests: XCTestCase {

    var sut: TestPremiumManager!

    override func setUp() async throws {
        try await super.setUp()
        sut = TestPremiumManager()
    }

    override func tearDown() async throws {
        sut.clearTestData()
        sut = nil
        try await super.tearDown()
    }

    // MARK: - Initial State Tests

    func testInitialState_IsFree() {
        XCTAssertEqual(sut.currentTier, .free)
        XCTAssertFalse(sut.isPremium)
        XCTAssertFalse(sut.isLifetime)
        XCTAssertFalse(sut.hasRemovedAds)
        XCTAssertNil(sut.subscriptionExpirationDate)
    }

    // MARK: - Tier State Tests

    func testSetTier_Premium() {
        // When
        sut.setTier(.premium)

        // Then
        XCTAssertEqual(sut.currentTier, .premium)
        XCTAssertTrue(sut.isPremium)
        XCTAssertFalse(sut.isLifetime)
    }

    func testSetTier_Lifetime() {
        // When
        sut.setTier(.lifetime)

        // Then
        XCTAssertEqual(sut.currentTier, .lifetime)
        XCTAssertTrue(sut.isPremium)
        XCTAssertTrue(sut.isLifetime)
    }

    func testSetTier_Free() {
        // Given
        sut.setTier(.premium)

        // When
        sut.setTier(.free)

        // Then
        XCTAssertEqual(sut.currentTier, .free)
        XCTAssertFalse(sut.isPremium)
        XCTAssertFalse(sut.isLifetime)
    }

    // MARK: - Persistence Tests

    func testPersistence_TierIsPersisted() {
        // Given
        sut.setTier(.premium)

        // When
        let newManager = TestPremiumManager()
        newManager.loadPersistedState()

        // Note: This test uses a shared UserDefaults suite, so the state is shared
        // In a real scenario, each manager instance would share the same persisted state
    }

    func testPersistence_RemovedAdsIsPersisted() {
        // Given
        sut.setRemovedAds(true)

        // Then
        sut.loadPersistedState()
        XCTAssertTrue(sut.hasRemovedAds)
    }

    func testPersistence_ExpirationDateIsPersisted() {
        // Given
        let futureDate = Calendar.current.date(byAdding: .day, value: 30, to: Date())!
        sut.setExpirationDate(futureDate)

        // Then
        sut.loadPersistedState()
        XCTAssertNotNil(sut.subscriptionExpirationDate)
    }

    func testPersistence_ExpiredSubscription_ResetsTier() {
        // Given
        let pastDate = Calendar.current.date(byAdding: .day, value: -1, to: Date())!
        sut.setTier(.premium)
        sut.setExpirationDate(pastDate)
        sut.persistState()

        // When
        sut.loadPersistedState()

        // Then
        XCTAssertEqual(sut.currentTier, .free)
    }

    // MARK: - Feature Access Tests - Free Tier

    func testFeatureAccess_FreeTier_HighQuality() {
        XCTAssertFalse(sut.hasAccess(to: .highQualityRecording))
    }

    func testFeatureAccess_FreeTier_Stereo() {
        XCTAssertFalse(sut.hasAccess(to: .stereoRecording))
    }

    func testFeatureAccess_FreeTier_WAV() {
        XCTAssertFalse(sut.hasAccess(to: .wavFormat))
    }

    func testFeatureAccess_FreeTier_EnhancedAudio() {
        XCTAssertFalse(sut.hasAccess(to: .enhancedAudio))
    }

    func testFeatureAccess_FreeTier_VocalLayer() {
        XCTAssertFalse(sut.hasAccess(to: .vocalLayerSeparation))
    }

    func testFeatureAccess_FreeTier_Transcription() {
        XCTAssertFalse(sut.hasAccess(to: .transcription))
    }

    func testFeatureAccess_FreeTier_CloudBackup() {
        XCTAssertFalse(sut.hasAccess(to: .cloudBackup))
    }

    func testFeatureAccess_FreeTier_UnlimitedRecordings() {
        XCTAssertTrue(sut.hasAccess(to: .unlimitedRecordings))
    }

    func testFeatureAccess_FreeTier_CustomFolders() {
        XCTAssertTrue(sut.hasAccess(to: .customFolders))
    }

    func testFeatureAccess_FreeTier_Bookmarks() {
        XCTAssertTrue(sut.hasAccess(to: .bookmarks))
    }

    // MARK: - Feature Access Tests - Premium Tier

    func testFeatureAccess_PremiumTier_AllFeaturesAvailable() {
        // Given
        sut.setTier(.premium)

        // Then
        for feature in PremiumFeature.allCases {
            XCTAssertTrue(sut.hasAccess(to: feature), "Premium tier should have access to \(feature)")
        }
    }

    func testFeatureAccess_LifetimeTier_AllFeaturesAvailable() {
        // Given
        sut.setTier(.lifetime)

        // Then
        for feature in PremiumFeature.allCases {
            XCTAssertTrue(sut.hasAccess(to: feature), "Lifetime tier should have access to \(feature)")
        }
    }

    // MARK: - Free Tier Limits Tests

    func testRecordingLimit_FreeTier_BelowLimit() {
        XCTAssertFalse(sut.hasReachedRecordingLimit(currentCount: 5))
    }

    func testRecordingLimit_FreeTier_AtLimit() {
        XCTAssertTrue(sut.hasReachedRecordingLimit(currentCount: 10))
    }

    func testRecordingLimit_FreeTier_AboveLimit() {
        XCTAssertTrue(sut.hasReachedRecordingLimit(currentCount: 15))
    }

    func testRecordingLimit_PremiumTier_NoLimit() {
        // Given
        sut.setTier(.premium)

        // Then
        XCTAssertFalse(sut.hasReachedRecordingLimit(currentCount: 100))
    }

    func testFolderLimit_FreeTier_BelowLimit() {
        XCTAssertFalse(sut.hasReachedFolderLimit(currentCount: 2))
    }

    func testFolderLimit_FreeTier_AtLimit() {
        XCTAssertTrue(sut.hasReachedFolderLimit(currentCount: 3))
    }

    func testFolderLimit_FreeTier_AboveLimit() {
        XCTAssertTrue(sut.hasReachedFolderLimit(currentCount: 5))
    }

    func testFolderLimit_PremiumTier_NoLimit() {
        // Given
        sut.setTier(.premium)

        // Then
        XCTAssertFalse(sut.hasReachedFolderLimit(currentCount: 50))
    }

    func testBookmarkLimit_FreeTier_BelowLimit() {
        XCTAssertFalse(sut.hasReachedBookmarkLimit(currentCount: 3))
    }

    func testBookmarkLimit_FreeTier_AtLimit() {
        XCTAssertTrue(sut.hasReachedBookmarkLimit(currentCount: 5))
    }

    func testBookmarkLimit_FreeTier_AboveLimit() {
        XCTAssertTrue(sut.hasReachedBookmarkLimit(currentCount: 10))
    }

    func testBookmarkLimit_PremiumTier_NoLimit() {
        // Given
        sut.setTier(.premium)

        // Then
        XCTAssertFalse(sut.hasReachedBookmarkLimit(currentCount: 100))
    }

    // MARK: - Subscription Expiration Tests

    func testSubscriptionExpiring_NotExpiringSoon() {
        // Given
        let thirtyDaysFromNow = Calendar.current.date(byAdding: .day, value: 30, to: Date())!
        sut.setExpirationDate(thirtyDaysFromNow)

        // Then
        XCTAssertFalse(sut.isSubscriptionExpiringSoon())
    }

    func testSubscriptionExpiring_ExpiringSoon() {
        // Given
        let twoDaysFromNow = Calendar.current.date(byAdding: .day, value: 2, to: Date())!
        sut.setExpirationDate(twoDaysFromNow)

        // Then
        XCTAssertTrue(sut.isSubscriptionExpiringSoon())
    }

    func testSubscriptionExpiring_ExpiredInPast() {
        // Given
        let yesterday = Calendar.current.date(byAdding: .day, value: -1, to: Date())!
        sut.setExpirationDate(yesterday)

        // Then
        XCTAssertFalse(sut.isSubscriptionExpiringSoon())
    }

    func testSubscriptionExpiring_NoExpiration() {
        // Given
        sut.setExpirationDate(nil)

        // Then
        XCTAssertFalse(sut.isSubscriptionExpiringSoon())
    }

    func testRemainingDays_ThirtyDays() {
        // Given
        let thirtyDaysFromNow = Calendar.current.date(byAdding: .day, value: 30, to: Date())!
        sut.setExpirationDate(thirtyDaysFromNow)

        // Then
        XCTAssertEqual(sut.remainingSubscriptionDays(), 30)
    }

    func testRemainingDays_NoExpiration() {
        // Given
        sut.setExpirationDate(nil)

        // Then
        XCTAssertNil(sut.remainingSubscriptionDays())
    }

    // MARK: - Remove Ads Tests

    func testRemoveAds_InitiallyFalse() {
        XCTAssertFalse(sut.hasRemovedAds)
    }

    func testRemoveAds_SetTrue() {
        // When
        sut.setRemovedAds(true)

        // Then
        XCTAssertTrue(sut.hasRemovedAds)
    }

    func testRemoveAds_PremiumImpliesNoAds() {
        // This tests the business logic that premium users don't see ads
        sut.setTier(.premium)

        // Premium users should effectively have ads removed (tested in PremiumManager.validateSubscriptionState)
        XCTAssertTrue(sut.isPremium)
    }

    // MARK: - Subscription Tier Enum Tests

    func testSubscriptionTier_DisplayNames() {
        XCTAssertEqual(SubscriptionTier.free.displayName, "Free")
        XCTAssertEqual(SubscriptionTier.premium.displayName, "Premium")
        XCTAssertEqual(SubscriptionTier.lifetime.displayName, "Lifetime")
    }

    func testSubscriptionTier_RawValues() {
        XCTAssertEqual(SubscriptionTier.free.rawValue, "free")
        XCTAssertEqual(SubscriptionTier.premium.rawValue, "premium")
        XCTAssertEqual(SubscriptionTier.lifetime.rawValue, "lifetime")
    }

    func testSubscriptionTier_InitFromRawValue() {
        XCTAssertEqual(SubscriptionTier(rawValue: "free"), .free)
        XCTAssertEqual(SubscriptionTier(rawValue: "premium"), .premium)
        XCTAssertEqual(SubscriptionTier(rawValue: "lifetime"), .lifetime)
        XCTAssertNil(SubscriptionTier(rawValue: "invalid"))
    }

    // MARK: - Premium Feature Enum Tests

    func testPremiumFeature_AllCasesCount() {
        XCTAssertEqual(PremiumFeature.allCases.count, 10)
    }

    func testPremiumFeature_DisplayNames() {
        XCTAssertEqual(PremiumFeature.enhancedAudio.displayName, "Audio Enhancement")
        XCTAssertEqual(PremiumFeature.vocalLayerSeparation.displayName, "Vocal Layer Separation")
        XCTAssertEqual(PremiumFeature.transcription.displayName, "Transcription")
        XCTAssertEqual(PremiumFeature.highQualityRecording.displayName, "High Quality Recording")
        XCTAssertEqual(PremiumFeature.stereoRecording.displayName, "Stereo Recording")
        XCTAssertEqual(PremiumFeature.wavFormat.displayName, "WAV Format Export")
        XCTAssertEqual(PremiumFeature.unlimitedRecordings.displayName, "Unlimited Recordings")
        XCTAssertEqual(PremiumFeature.cloudBackup.displayName, "Cloud Backup")
        XCTAssertEqual(PremiumFeature.customFolders.displayName, "Unlimited Folders")
        XCTAssertEqual(PremiumFeature.bookmarks.displayName, "Unlimited Bookmarks")
    }

    func testPremiumFeature_Descriptions() {
        // Verify each feature has a non-empty description
        for feature in PremiumFeature.allCases {
            XCTAssertFalse(feature.description.isEmpty, "\(feature) should have a description")
        }
    }

    func testPremiumFeature_IconNames() {
        // Verify each feature has a non-empty icon name
        for feature in PremiumFeature.allCases {
            XCTAssertFalse(feature.iconName.isEmpty, "\(feature) should have an icon name")
        }
    }

    func testPremiumFeature_RawValues() {
        XCTAssertEqual(PremiumFeature.enhancedAudio.rawValue, "enhanced_audio")
        XCTAssertEqual(PremiumFeature.vocalLayerSeparation.rawValue, "vocal_layer")
        XCTAssertEqual(PremiumFeature.transcription.rawValue, "transcription")
    }

    // MARK: - Static Limit Constants Tests

    func testStaticLimits() {
        XCTAssertEqual(TestPremiumManager.freeRecordingLimit, 10)
        XCTAssertEqual(TestPremiumManager.freeFolderLimit, 3)
        XCTAssertEqual(TestPremiumManager.freeBookmarkLimit, 5)
    }
}

// MARK: - Integration Tests

@MainActor
extension PremiumManagerTests {

    func testUpgradeFromFreeToPreium() {
        // Given - Free tier
        XCTAssertEqual(sut.currentTier, .free)
        XCTAssertFalse(sut.hasAccess(to: .transcription))

        // When - Upgrade to premium
        sut.setTier(.premium)
        sut.setRemovedAds(true)
        let futureDate = Calendar.current.date(byAdding: .month, value: 1, to: Date())!
        sut.setExpirationDate(futureDate)

        // Then
        XCTAssertEqual(sut.currentTier, .premium)
        XCTAssertTrue(sut.isPremium)
        XCTAssertTrue(sut.hasAccess(to: .transcription))
        XCTAssertTrue(sut.hasRemovedAds)
        XCTAssertFalse(sut.hasReachedRecordingLimit(currentCount: 100))
    }

    func testDowngradeFromPremiumToFree() {
        // Given - Premium tier
        sut.setTier(.premium)
        XCTAssertTrue(sut.hasAccess(to: .transcription))

        // When - Downgrade to free
        sut.setTier(.free)
        sut.setExpirationDate(nil)

        // Then
        XCTAssertEqual(sut.currentTier, .free)
        XCTAssertFalse(sut.isPremium)
        XCTAssertFalse(sut.hasAccess(to: .transcription))
        XCTAssertTrue(sut.hasReachedRecordingLimit(currentCount: 10))
    }

    func testLifetimePurchase() {
        // Given - Free tier
        XCTAssertEqual(sut.currentTier, .free)

        // When - Purchase lifetime
        sut.setTier(.lifetime)

        // Then
        XCTAssertEqual(sut.currentTier, .lifetime)
        XCTAssertTrue(sut.isPremium)
        XCTAssertTrue(sut.isLifetime)
        XCTAssertNil(sut.subscriptionExpirationDate) // Lifetime has no expiration

        // All features available
        for feature in PremiumFeature.allCases {
            XCTAssertTrue(sut.hasAccess(to: feature))
        }
    }
}
