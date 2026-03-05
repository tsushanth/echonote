import SwiftUI

struct RecordingRowView: View {
    let recording: Recording
    var isSelected: Bool = false
    var isSelectionMode: Bool = false

    var body: some View {
        HStack(spacing: 12) {
            if isSelectionMode {
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(isSelected ? .accent : .secondary)
                    .font(.title3)
                    .accessibilityLabel(isSelected ? "Selected" : "Not selected")
            }

            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(recording.title)
                        .font(.headline)
                        .lineLimit(1)

                    if recording.isFavorite {
                        Image(systemName: "star.fill")
                            .font(.caption)
                            .foregroundStyle(.yellow)
                            .accessibilityLabel("Favorite")
                    }

                    if recording.isEnhanced {
                        Image(systemName: "wand.and.stars")
                            .font(.caption)
                            .foregroundStyle(.purple)
                            .accessibilityLabel("Enhanced")
                    }
                }

                HStack(spacing: 8) {
                    Text(recording.dateCreated.shortFormatted)
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    Text(recording.formattedDuration)
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    if let location = recording.locationName {
                        HStack(spacing: 2) {
                            Image(systemName: "location.fill")
                                .font(.system(size: 8))
                            Text(location)
                                .lineLimit(1)
                        }
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    }
                }

                HStack(spacing: 8) {
                    Label(recording.formattedFileSize, systemImage: "doc.fill")
                        .font(.caption2)
                        .foregroundStyle(.tertiary)

                    if recording.isStereo {
                        Label("Stereo", systemImage: "speaker.wave.2.fill")
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                    }

                    if recording.hasVocalLayer {
                        Label("Layered", systemImage: "square.stack.fill")
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                    }

                    if recording.transcript != nil {
                        Label("Transcript", systemImage: "text.alignleft")
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                    }
                }
            }

            Spacer()

            if !isSelectionMode {
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
                    .accessibilityHidden(true)
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityLabel(recordingAccessibilityLabel)
    }

    private var recordingAccessibilityLabel: String {
        var parts = [recording.title]
        parts.append(recording.formattedDuration)
        parts.append(recording.dateCreated.shortFormatted)
        if recording.isFavorite { parts.append("Favorite") }
        if recording.isEnhanced { parts.append("Enhanced") }
        if let location = recording.locationName { parts.append(location) }
        return parts.joined(separator: ", ")
    }
}
