import Foundation

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
