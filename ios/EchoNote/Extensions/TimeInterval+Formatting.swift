import Foundation

extension TimeInterval {
    var formattedTime: String {
        let totalSeconds = Int(self)
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        let seconds = totalSeconds % 60
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, seconds)
        }
        return String(format: "%d:%02d", minutes, seconds)
    }

    var formattedTimeWithMilliseconds: String {
        let totalSeconds = Int(self)
        let minutes = (totalSeconds % 3600) / 60
        let seconds = totalSeconds % 60
        let milliseconds = Int((self - Double(totalSeconds)) * 100)
        return String(format: "%d:%02d.%02d", minutes, seconds, milliseconds)
    }
}
