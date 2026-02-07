import Foundation
import StoreKit

/// Premium feature categories in EchoNote
enum PremiumFeature: String, CaseIterable {
    case enhancedAudio = "enhanced_audio"
    case vocalLayerSeparation = "vocal_layer"
    case transcription = "transcription"
    case highQualityRecording = "high_quality"
    case stereoRecording = "stereo"
    case wavFormat = "wav_format"
    case unlimitedRecordings = "unlimited_recordings"
    case cloudBackup = "cloud_backup"
    case customFolders = "custom_folders"
    case bookmarks = "bookmarks"

    var displayName: String {
        switch self {
        case .enhancedAudio:
            return "Audio Enhancement"
        case .vocalLayerSeparation:
            return "Vocal Layer Separation"
        case .transcription:
            return "Transcription"
        case .highQualityRecording:
            return "High Quality Recording"
        case .stereoRecording:
            return "Stereo Recording"
        case .wavFormat:
            return "WAV Format Export"
        case .unlimitedRecordings:
            return "Unlimited Recordings"
        case .cloudBackup:
            return "Cloud Backup"
        case .customFolders:
            return "Unlimited Folders"
        case .bookmarks:
            return "Unlimited Bookmarks"
        }
    }

    var description: String {
        switch self {
        case .enhancedAudio:
            return "AI-powered noise reduction and audio clarity"
        case .vocalLayerSeparation:
            return "Separate vocals from background audio"
        case .transcription:
            return "Convert speech to text with high accuracy"
        case .highQualityRecording:
            return "Record in maximum quality up to 48kHz"
        case .stereoRecording:
            return "Capture immersive stereo audio"
        case .wavFormat:
            return "Export in uncompressed WAV format"
        case .unlimitedRecordings:
            return "No limits on recording storage"
        case .cloudBackup:
            return "Automatic backup to iCloud"
        case .customFolders:
            return "Organize with unlimited folders"
        case .bookmarks:
            return "Add unlimited bookmarks to recordings"
        }
    }

    var iconName: String {
        switch self {
        case .enhancedAudio:
            return "waveform.badge.magnifyingglass"
        case .vocalLayerSeparation:
            return "person.wave.2"
        case .transcription:
            return "text.quote"
        case .highQualityRecording:
            return "dial.high"
        case .stereoRecording:
            return "speaker.wave.2"
        case .wavFormat:
            return "doc.badge.arrow.up"
        case .unlimitedRecordings:
            return "infinity"
        case .cloudBackup:
            return "icloud.and.arrow.up"
        case .customFolders:
            return "folder.badge.plus"
        case .bookmarks:
            return "bookmark.fill"
        }
    }
}

/// User subscription tier
enum SubscriptionTier: String, Codable {
    case free = "free"
    case premium = "premium"
    case lifetime = "lifetime"

    var displayName: String {
        switch self {
        case .free:
            return "Free"
        case .premium:
            return "Premium"
        case .lifetime:
            return "Lifetime"
        }
    }
}

/// Manager for premium feature access and subscription state
@MainActor
@Observable
final class PremiumManager {

    // MARK: - Singleton

    static let shared = PremiumManager()

    // MARK: - Properties

    /// Current subscription tier
    private(set) var currentTier: SubscriptionTier = .free

    /// Whether user has removed ads
    private(set) var hasRemovedAds: Bool = false

    /// Subscription expiration date (nil for lifetime or free)
    private(set) var subscriptionExpirationDate: Date?

    /// Whether user has any premium access
    var isPremium: Bool {
        currentTier != .free
    }

    /// Whether user has lifetime access
    var isLifetime: Bool {
        currentTier == .lifetime
    }

    /// StoreKit manager reference
    private var storeKitManager: StoreKitManager?

    // MARK: - UserDefaults Keys

    private enum UserDefaultsKey {
        static let subscriptionTier = "com.echonote.subscription.tier"
        static let hasRemovedAds = "com.echonote.ads.removed"
        static let subscriptionExpiration = "com.echonote.subscription.expiration"
        static let lastValidationDate = "com.echonote.validation.date"
    }

    // MARK: - Free Tier Limits

    /// Maximum recordings for free tier
    static let freeRecordingLimit = 10

    /// Maximum folders for free tier
    static let freeFolderLimit = 3

    /// Maximum bookmarks per recording for free tier
    static let freeBookmarkLimit = 5

    // MARK: - Initialization

    private init() {
        loadPersistedState()
    }

    // MARK: - Public Methods

    /// Configure with StoreKit manager
    func configure(with storeKitManager: StoreKitManager) {
        self.storeKitManager = storeKitManager
    }

    /// Validate and update subscription state from StoreKit
    func validateSubscriptionState() async {
        guard let storeKitManager = storeKitManager else { return }

        // Check for active subscriptions
        let hasActiveSubscription = storeKitManager.hasActiveSubscription()
        let hasLifetime = storeKitManager.isPurchased(ProductID.lifetime.rawValue)
        let hasRemovedAds = storeKitManager.isPurchased(ProductID.removeAds.rawValue)

        // Update state
        if hasLifetime {
            currentTier = .lifetime
            subscriptionExpirationDate = nil
        } else if hasActiveSubscription {
            currentTier = .premium
            subscriptionExpirationDate = await storeKitManager.getSubscriptionExpirationDate()
        } else {
            currentTier = .free
            subscriptionExpirationDate = nil
        }

        self.hasRemovedAds = hasRemovedAds || isPremium

        // Persist state
        persistState()
    }

    /// Check if a specific feature is available
    func hasAccess(to feature: PremiumFeature) -> Bool {
        // Premium users have access to all features
        if isPremium {
            return true
        }

        // Free tier features
        switch feature {
        case .highQualityRecording, .stereoRecording, .wavFormat:
            return false
        case .enhancedAudio, .vocalLayerSeparation, .transcription:
            return false
        case .cloudBackup:
            return false
        case .unlimitedRecordings, .customFolders, .bookmarks:
            // Limited access in free tier
            return true
        }
    }

    /// Check if recording limit has been reached (free tier)
    func hasReachedRecordingLimit(currentCount: Int) -> Bool {
        if isPremium { return false }
        return currentCount >= Self.freeRecordingLimit
    }

    /// Check if folder limit has been reached (free tier)
    func hasReachedFolderLimit(currentCount: Int) -> Bool {
        if isPremium { return false }
        return currentCount >= Self.freeFolderLimit
    }

    /// Check if bookmark limit has been reached (free tier)
    func hasReachedBookmarkLimit(currentCount: Int) -> Bool {
        if isPremium { return false }
        return currentCount >= Self.freeBookmarkLimit
    }

    /// Handle successful purchase
    func handlePurchase(productID: String) async {
        await validateSubscriptionState()
    }

    /// Check if subscription is expiring soon (within 3 days)
    func isSubscriptionExpiringSoon() -> Bool {
        guard let expirationDate = subscriptionExpirationDate else { return false }
        let threeDaysFromNow = Calendar.current.date(byAdding: .day, value: 3, to: Date()) ?? Date()
        return expirationDate < threeDaysFromNow && expirationDate > Date()
    }

    /// Get remaining days of subscription
    func remainingSubscriptionDays() -> Int? {
        guard let expirationDate = subscriptionExpirationDate else { return nil }
        let calendar = Calendar.current
        let components = calendar.dateComponents([.day], from: Date(), to: expirationDate)
        return components.day
    }

    // MARK: - Private Methods

    private func loadPersistedState() {
        let defaults = UserDefaults.standard

        if let tierRaw = defaults.string(forKey: UserDefaultsKey.subscriptionTier),
           let tier = SubscriptionTier(rawValue: tierRaw) {
            currentTier = tier
        }

        hasRemovedAds = defaults.bool(forKey: UserDefaultsKey.hasRemovedAds)

        if let expirationInterval = defaults.object(forKey: UserDefaultsKey.subscriptionExpiration) as? TimeInterval {
            let expirationDate = Date(timeIntervalSince1970: expirationInterval)
            // Only set if still valid
            if expirationDate > Date() {
                subscriptionExpirationDate = expirationDate
            } else {
                // Subscription expired
                currentTier = .free
            }
        }
    }

    private func persistState() {
        let defaults = UserDefaults.standard

        defaults.set(currentTier.rawValue, forKey: UserDefaultsKey.subscriptionTier)
        defaults.set(hasRemovedAds, forKey: UserDefaultsKey.hasRemovedAds)

        if let expirationDate = subscriptionExpirationDate {
            defaults.set(expirationDate.timeIntervalSince1970, forKey: UserDefaultsKey.subscriptionExpiration)
        } else {
            defaults.removeObject(forKey: UserDefaultsKey.subscriptionExpiration)
        }

        defaults.set(Date().timeIntervalSince1970, forKey: UserDefaultsKey.lastValidationDate)
    }
}

// MARK: - Premium Badge View

import SwiftUI

/// A badge indicating premium feature
struct PremiumBadge: View {
    var compact: Bool = false

    var body: some View {
        Text("PRO")
            .font(compact ? .caption2 : .caption)
            .fontWeight(.bold)
            .foregroundStyle(.white)
            .padding(.horizontal, compact ? 4 : 6)
            .padding(.vertical, compact ? 2 : 3)
            .background(
                LinearGradient(
                    colors: [.purple, .blue],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .clipShape(RoundedRectangle(cornerRadius: compact ? 4 : 6))
    }
}

/// View modifier to add premium badge to any view
struct PremiumFeatureModifier: ViewModifier {
    let feature: PremiumFeature
    @State private var showPaywall = false

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .topTrailing) {
                if !PremiumManager.shared.hasAccess(to: feature) {
                    PremiumBadge(compact: true)
                        .padding(4)
                }
            }
            .sheet(isPresented: $showPaywall) {
                PaywallView()
            }
    }
}

extension View {
    /// Add premium badge and paywall trigger for a feature
    func premiumFeature(_ feature: PremiumFeature) -> some View {
        modifier(PremiumFeatureModifier(feature: feature))
    }
}
