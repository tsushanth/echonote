import SwiftUI
#if DEBUG
import PaywallKit
#endif

struct SettingsView: View {
    @AppStorage("defaultFormat") private var defaultFormat: AudioFormat = .compressed
    @AppStorage("defaultQuality") private var defaultQuality: RecordingQuality = .high
    @AppStorage("defaultStereo") private var defaultStereo: Bool = false
    @AppStorage("autoLocationNaming") private var autoLocationNaming: Bool = true
    @AppStorage("transcriptionEngine") private var transcriptionEngine: String = TranscriptionEngine.apple.rawValue
    @AppStorage("transcriptionLanguage") private var transcriptionLanguage: String = "auto"

    @State private var showPaywall = false
    private var premiumManager: PremiumManager { PremiumManager.shared }

    var body: some View {
        NavigationStack {
            Form {
                premiumSection
                recordingSection
                transcriptionSection
                storageSection
                #if DEBUG
                PaywallDebugView(
                    appId: "clearvoice",
                    appName: "ClearVoice Pro",
                    features: [
                        PaywallFeature(icon: "🎙️", title: "Unlimited Recordings", description: "No storage limits"),
                        PaywallFeature(icon: "✨", title: "AI Noise Reduction", description: "Crystal clear audio"),
                        PaywallFeature(icon: "📝", title: "Transcription", description: "Speech to text in 90+ languages"),
                        PaywallFeature(icon: "🎧", title: "High Quality Audio", description: "48kHz stereo recording"),
                        PaywallFeature(icon: "📁", title: "Unlimited Folders", description: "Organize your recordings"),
                    ],
                    theme: PaywallTheme(accent: Color(red: 0.0, green: 0.48, blue: 1.0), accent2: Color(red: 0.5, green: 0.3, blue: 0.9))
                )
                #endif
                aboutSection
            }
            .navigationTitle("Settings")
            .sheet(isPresented: $showPaywall) {
                RemotePaywallView()
            }
        }
    }

    private var premiumSection: some View {
        Section {
            if premiumManager.isPremium {
                HStack {
                    Image(systemName: "crown.fill")
                        .foregroundStyle(.yellow)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(premiumManager.isLifetime ? "Lifetime Premium" : "Premium Active")
                            .font(.headline)

                        if let days = premiumManager.remainingSubscriptionDays(), !premiumManager.isLifetime {
                            Text("\(days) days remaining")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }

                    Spacer()

                    PremiumBadge()
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel(premiumManager.isLifetime ? "Lifetime premium member" : "Premium subscription active")

                Button("Manage Subscription") {
                    if let url = URL(string: "https://apps.apple.com/account/subscriptions") {
                        UIApplication.shared.open(url)
                    }
                }
                .accessibilityLabel("Manage subscription in App Store")
            } else {
                Button {
                    showPaywall = true
                } label: {
                    HStack {
                        Image(systemName: "crown.fill")
                            .foregroundStyle(.yellow)

                        VStack(alignment: .leading, spacing: 2) {
                            Text("Upgrade to Premium")
                                .font(.headline)
                                .foregroundStyle(.primary)

                            Text("Unlock all features")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        Spacer()

                        Image(systemName: "chevron.right")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .accessibilityLabel("Upgrade to premium")
                .accessibilityHint("Opens subscription options")
            }
        } header: {
            Text("Premium")
        }
    }

    private var recordingSection: some View {
        Section("Recording Defaults") {
            HStack {
                Picker("Audio Format", selection: Binding(
                    get: { defaultFormat },
                    set: { newValue in
                        if newValue == .uncompressed && !premiumManager.hasAccess(to: .wavFormat) {
                            showPaywall = true
                        } else {
                            defaultFormat = newValue
                        }
                    }
                )) {
                    ForEach(AudioFormat.allCases, id: \.self) { format in
                        HStack {
                            Text(format.displayName)
                            if format == .uncompressed && !premiumManager.hasAccess(to: .wavFormat) {
                                PremiumBadge(compact: true)
                            }
                        }
                        .tag(format)
                    }
                }
                .accessibilityLabel("Default audio format")
                .accessibilityValue(defaultFormat.displayName)
            }

            HStack {
                Picker("Audio Quality", selection: Binding(
                    get: { defaultQuality },
                    set: { newValue in
                        let isPremiumQuality = newValue == .high || newValue == .maximum
                        if isPremiumQuality && !premiumManager.hasAccess(to: .highQualityRecording) {
                            showPaywall = true
                        } else {
                            defaultQuality = newValue
                        }
                    }
                )) {
                    ForEach(RecordingQuality.allCases, id: \.self) { quality in
                        HStack {
                            Text(quality.displayName)
                            if (quality == .high || quality == .maximum) && !premiumManager.hasAccess(to: .highQualityRecording) {
                                PremiumBadge(compact: true)
                            }
                        }
                        .tag(quality)
                    }
                }
                .accessibilityLabel("Default audio quality")
                .accessibilityValue(defaultQuality.displayName)
            }

            HStack {
                Toggle(isOn: Binding(
                    get: { defaultStereo },
                    set: { newValue in
                        if newValue && !premiumManager.hasAccess(to: .stereoRecording) {
                            showPaywall = true
                        } else {
                            defaultStereo = newValue
                        }
                    }
                )) {
                    HStack {
                        Text("Stereo Recording")
                        if !premiumManager.hasAccess(to: .stereoRecording) {
                            PremiumBadge(compact: true)
                        }
                    }
                }
                .accessibilityLabel("Default stereo recording")
                .accessibilityHint(premiumManager.hasAccess(to: .stereoRecording) ? "Enable to record in stereo by default" : "Premium feature")
            }

            Toggle("Auto Location Naming", isOn: $autoLocationNaming)
                .accessibilityLabel("Automatic location naming")
                .accessibilityHint("Automatically names recordings based on your location")
        }
    }

    private var transcriptionSection: some View {
        Section("Transcription") {
            Picker("Engine", selection: Binding(
                get: { TranscriptionEngine(rawValue: transcriptionEngine) ?? .apple },
                set: { newValue in
                    if newValue == .whisperKit && !premiumManager.hasAccess(to: .multilingualTranscription) {
                        showPaywall = true
                    } else {
                        transcriptionEngine = newValue.rawValue
                    }
                }
            )) {
                ForEach(TranscriptionEngine.allCases, id: \.self) { engine in
                    HStack {
                        Text(engine.displayName)
                        if engine == .whisperKit && !premiumManager.hasAccess(to: .multilingualTranscription) {
                            PremiumBadge(compact: true)
                        }
                    }
                    .tag(engine)
                }
            }
            .accessibilityLabel("Transcription engine")
            .accessibilityValue((TranscriptionEngine(rawValue: transcriptionEngine) ?? .apple).displayName)

            if TranscriptionEngine(rawValue: transcriptionEngine) == .whisperKit {
                Picker("Language", selection: $transcriptionLanguage) {
                    ForEach(TranscriptionLanguage.supported) { lang in
                        Text(lang.name).tag(lang.id)
                    }
                }
                .accessibilityLabel("Transcription language")
            }
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

            Link(destination: URL(string: "https://kreativekoala.llc/terms")!) {
                HStack {
                    Text("Terms of Use (EULA)")
                        .foregroundStyle(.primary)
                    Spacer()
                    Image(systemName: "arrow.up.right")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .accessibilityLabel("View terms of use")

            Link(destination: URL(string: "https://kreativekoala.llc/privacy")!) {
                HStack {
                    Text("Privacy Policy")
                        .foregroundStyle(.primary)
                    Spacer()
                    Image(systemName: "arrow.up.right")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .accessibilityLabel("View privacy policy")
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
