import Foundation

extension Date {
    var relativeFormatted: String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: self, relativeTo: Date())
    }

    var shortFormatted: String {
        let formatter = DateFormatter()
        let calendar = Calendar.current
        if calendar.isDateInToday(self) {
            formatter.dateFormat = "h:mm a"
        } else if calendar.isDateInYesterday(self) {
            return "Yesterday"
        } else if calendar.isDate(self, equalTo: Date(), toGranularity: .weekOfYear) {
            formatter.dateFormat = "EEEE"
        } else if calendar.isDate(self, equalTo: Date(), toGranularity: .year) {
            formatter.dateFormat = "MMM d"
        } else {
            formatter.dateFormat = "MMM d, yyyy"
        }
        return formatter.string(from: self)
    }
}
