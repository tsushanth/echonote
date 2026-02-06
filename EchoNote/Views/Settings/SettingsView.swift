import SwiftUI

struct SettingsView: View {
    @AppStorage("defaultFormat") private var defaultFormat: AudioFormat = .compressed
    @AppStorage("defaultQuality") private var defaultQuality: RecordingQuality = .high
    @AppStorage("defaultStereo") private var defaultStereo: Bool = false
    @AppStorage("autoLocationNaming") private var autoLocationNaming: Bool = true

    var body: some View {
        NavigationStack {
            Form {
                recordingSection
                storageSection
                aboutSection
            }
            .navigationTitle("Settings")
        }
    }

    private var recordingSection: some View {
        Section("Recording Defaults") {
            Picker("Audio Format", selection: $defaultFormat) {
                ForEach(AudioFormat.allCases, id: \.self) { format in
                    Text(format.displayName).tag(format)
                }
            }
            .accessibilityLabel("Default audio format")
            .accessibilityValue(defaultFormat.displayName)

            Picker("Audio Quality", selection: $defaultQuality) {
                ForEach(RecordingQuality.allCases, id: \.self) { quality in
                    Text(quality.displayName).tag(quality)
                }
            }
            .accessibilityLabel("Default audio quality")
            .accessibilityValue(defaultQuality.displayName)

            Toggle("Stereo Recording", isOn: $defaultStereo)
                .accessibilityLabel("Default stereo recording")
                .accessibilityHint("Enable to record in stereo by default")

            Toggle("Auto Location Naming", isOn: $autoLocationNaming)
                .accessibilityLabel("Automatic location naming")
                .accessibilityHint("Automatically names recordings based on your location")
        }
    }

    private var storageSection: some View {
        Section("Storage") {
            StorageInfoRow()
        }
    }

    private var aboutSection: some View {
        Section("About") {
            LabeledContent("Version", value: appVersion)
            LabeledContent("Build", value: buildNumber)

            NavigationLink("Acknowledgments") {
                AcknowledgmentsView()
            }
            .accessibilityLabel("View acknowledgments")
        }
    }

    private var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
    }

    private var buildNumber: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
    }
}

struct StorageInfoRow: View {
    @State private var usedStorage: String = "Calculating..."

    var body: some View {
        LabeledContent("Recordings Storage", value: usedStorage)
            .task {
                usedStorage = calculateStorageUsed()
            }
            .accessibilityLabel("Recordings storage usage")
            .accessibilityValue(usedStorage)
    }

    private func calculateStorageUsed() -> String {
        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let recordingsPath = documentsPath.appendingPathComponent(AppConstants.recordingsDirectoryName)

        guard let contents = try? FileManager.default.contentsOfDirectory(
            at: recordingsPath,
            includingPropertiesForKeys: [.fileSizeKey]
        ) else {
            return "0 KB"
        }

        var totalSize: Int64 = 0
        for fileURL in contents {
            let attributes = try? FileManager.default.attributesOfItem(atPath: fileURL.path)
            totalSize += attributes?[.size] as? Int64 ?? 0
        }

        return ByteCountFormatter.string(fromByteCount: totalSize, countStyle: .file)
    }
}

struct AcknowledgmentsView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("EchoNote")
                    .font(.title)
                    .fontWeight(.bold)

                Text("Built with SwiftUI, AVFoundation, and Speech framework.")
                    .font(.body)

                Divider()

                Text("Frameworks Used")
                    .font(.headline)

                VStack(alignment: .leading, spacing: 8) {
                    frameworkRow("SwiftUI", description: "Modern declarative UI framework")
                    frameworkRow("SwiftData", description: "Data persistence framework")
                    frameworkRow("AVFoundation", description: "Audio recording and playback")
                    frameworkRow("Speech", description: "Speech recognition and transcription")
                    frameworkRow("CoreLocation", description: "Location-based naming")
                }
            }
            .padding()
        }
        .navigationTitle("Acknowledgments")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func frameworkRow(_ name: String, description: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(name)
                .font(.subheadline)
                .fontWeight(.medium)
            Text(description)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }
}
