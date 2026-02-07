import Foundation
import SwiftUI

enum AppConstants {
    static let appName = "EchoNote"
    static let recordingsDirectoryName = "Recordings"

    enum StoreKit {
        static let bundleIdentifier = "com.yourcompany.echonote"

        enum Subscriptions {
            static let weekly = "com.yourcompany.echonote.subscription.weekly"
            static let monthly = "com.yourcompany.echonote.subscription.monthly"
            static let yearly = "com.yourcompany.echonote.subscription.yearly"
            static let lifetime = "com.yourcompany.echonote.subscription.lifetime"
        }

        enum InAppPurchases {
            static let removeAds = "com.yourcompany.echonote.remove_ads"
        }

        static var allProductIDs: [String] {
            [
                Subscriptions.weekly,
                Subscriptions.monthly,
                Subscriptions.yearly,
                Subscriptions.lifetime,
                InAppPurchases.removeAds
            ]
        }
    }

    enum Premium {
        static let freeRecordingLimit = 10
        static let freeFolderLimit = 3
        static let freeBookmarkLimit = 5
        static let termsOfServiceURL = "https://yourcompany.com/terms"
        static let privacyPolicyURL = "https://yourcompany.com/privacy"
    }

    enum Audio {
        static let defaultSampleRate: Double = 44100
        static let defaultChannels: Int = 1
        static let stereoChannels: Int = 2
        static let skipForwardInterval: TimeInterval = 15
        static let skipBackwardInterval: TimeInterval = 15
        static let minPlaybackRate: Float = 0.5
        static let maxPlaybackRate: Float = 2.0
        static let playbackRateStep: Float = 0.25
        static let waveformSamplesPerSecond: Int = 50
        static let silenceThreshold: Float = 0.01
        static let silenceMinDuration: TimeInterval = 0.5
    }

    enum UI {
        static let cornerRadius: CGFloat = 12
        static let smallCornerRadius: CGFloat = 8
        static let waveformBarWidth: CGFloat = 3
        static let waveformBarSpacing: CGFloat = 2
        static let recordButtonSize: CGFloat = 72
        static let animationDuration: Double = 0.3
        static let maxWaveformHeight: CGFloat = 120
    }

    enum Colors {
        static let recordingRed = Color(red: 1.0, green: 0.23, blue: 0.19)
        static let waveformBlue = Color(red: 0.0, green: 0.48, blue: 1.0)
        static let waveformGray = Color(.systemGray3)
        static let playheadColor = Color(.label)
        static let backgroundPrimary = Color(.systemBackground)
        static let backgroundSecondary = Color(.secondarySystemBackground)
        static let backgroundTertiary = Color(.tertiarySystemBackground)
    }
}
