import SwiftUI

struct WaveformView: View {
    let samples: [Float]
    var progress: Double = 0
    var activeColor: Color = AppConstants.Colors.waveformBlue
    var inactiveColor: Color = AppConstants.Colors.waveformGray
    var barWidth: CGFloat = AppConstants.UI.waveformBarWidth
    var spacing: CGFloat = AppConstants.UI.waveformBarSpacing
    var maxHeight: CGFloat = AppConstants.UI.maxWaveformHeight

    var body: some View {
        GeometryReader { geometry in
            let totalBarWidth = barWidth + spacing
            let visibleBars = Int(geometry.size.width / totalBarWidth)
            let displaySamples = resampleData(to: visibleBars)
            let progressIndex = Int(progress * Double(displaySamples.count))

            HStack(alignment: .center, spacing: spacing) {
                ForEach(0..<displaySamples.count, id: \.self) { index in
                    let height = max(2, CGFloat(displaySamples[index]) * geometry.size.height)
                    RoundedRectangle(cornerRadius: barWidth / 2)
                        .fill(index < progressIndex ? activeColor : inactiveColor)
                        .frame(width: barWidth, height: height)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(height: maxHeight)
    }

    private func resampleData(to count: Int) -> [Float] {
        guard !samples.isEmpty, count > 0 else { return [] }
        if samples.count == count { return samples }

        var resampled: [Float] = []
        let step = Double(samples.count) / Double(count)

        for i in 0..<count {
            let start = Int(Double(i) * step)
            let end = min(Int(Double(i + 1) * step), samples.count)
            let slice = samples[start..<end]
            let avg = slice.isEmpty ? 0 : slice.reduce(0, +) / Float(slice.count)
            resampled.append(avg)
        }

        return resampled
    }
}

struct LiveWaveformView: View {
    let levels: [Float]
    var color: Color = AppConstants.Colors.recordingRed
    var barWidth: CGFloat = AppConstants.UI.waveformBarWidth
    var spacing: CGFloat = AppConstants.UI.waveformBarSpacing

    var body: some View {
        GeometryReader { geometry in
            let totalBarWidth = barWidth + spacing
            let visibleBars = Int(geometry.size.width / totalBarWidth)
            let startIndex = max(0, levels.count - visibleBars)
            let visibleLevels = Array(levels.suffix(from: startIndex))

            HStack(alignment: .center, spacing: spacing) {
                ForEach(0..<visibleBars, id: \.self) { index in
                    let level: Float = index < visibleLevels.count ? visibleLevels[index] : 0
                    let height = max(2, CGFloat(level) * geometry.size.height)
                    RoundedRectangle(cornerRadius: barWidth / 2)
                        .fill(color)
                        .frame(width: barWidth, height: height)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(height: AppConstants.UI.maxWaveformHeight)
    }
}
