import SwiftData
import SwiftUI

struct TranscriptView: View {
    let recording: Recording
    @Bindable var editorVM: EditorViewModel
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @State private var didCopy = false
    @State private var selectedTab: TranscriptTab = .raw
    @State private var showPaywall = false
    private var premiumManager: PremiumManager { PremiumManager.shared }

    enum TranscriptTab: String, CaseIterable {
        case raw = "Transcript"
        case cleaned = "Cleaned"
        case summary = "Summary"
        case actions = "Actions"
    }

    var availableTabs: [TranscriptTab] {
        if editorVM.isIntelligenceAvailable && premiumManager.hasAccess(to: .transcriptIntelligence) {
            return TranscriptTab.allCases
        }
        return [.raw]
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if editorVM.isTranscribing {
                    transcribingView
                } else if recording.transcript != nil {
                    if availableTabs.count > 1 {
                        Picker("View", selection: $selectedTab) {
                            ForEach(availableTabs, id: \.self) { tab in
                                Text(tab.rawValue).tag(tab)
                            }
                        }
                        .pickerStyle(.segmented)
                        .padding(.horizontal)
                        .padding(.top, 8)
                    }

                    tabContent
                } else {
                    emptyTranscriptView
                }
            }
            .navigationTitle("Transcript")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                        .accessibilityLabel("Close transcript")
                }
            }
            .sheet(isPresented: $showPaywall) {
                PaywallView()
            }
        }
    }

    // MARK: - Tab Content

    @ViewBuilder
    private var tabContent: some View {
        switch selectedTab {
        case .raw:
            transcriptContentView(recording.transcript ?? "")
        case .cleaned:
            intelligenceContentView(
                content: recording.cleanedTranscript,
                icon: "text.badge.checkmark",
                emptyTitle: "Clean Transcript",
                emptyDescription: "Remove filler words and fix grammar.",
                action: { await editorVM.cleanTranscript(modelContext: modelContext) }
            )
        case .summary:
            intelligenceContentView(
                content: recording.summary,
                icon: "doc.text.magnifyingglass",
                emptyTitle: "Generate Summary",
                emptyDescription: "Create a concise summary of this recording.",
                action: { await editorVM.summarizeTranscript(modelContext: modelContext) }
            )
        case .actions:
            intelligenceContentView(
                content: recording.actionItems,
                icon: "checklist",
                emptyTitle: "Extract Action Items",
                emptyDescription: "Find action items mentioned in this recording.",
                action: { await editorVM.extractActionItems(modelContext: modelContext) }
            )
        }
    }

    // MARK: - Transcribing State

    private var transcribingView: some View {
        VStack(spacing: 16) {
            Spacer()

            if editorVM.isModelDownloading {
                ProgressView()
                    .scaleEffect(1.2)
                Text("Downloading transcription model...")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                ProgressView(value: editorVM.transcriptionProgress)
                    .padding(.horizontal, 40)
                    .accessibilityLabel("Transcription progress")
                    .accessibilityValue("\(Int(editorVM.transcriptionProgress * 100)) percent")
                Text("Transcribing audio...")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()
        }
    }

    // MARK: - Transcript Content (Raw)

    private func transcriptContentView(_ transcript: String) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(recording.title)
                        .font(.headline)
                    Spacer()
                    Button {
                        UIPasteboard.general.string = transcript
                        didCopy.toggle()
                    } label: {
                        Label("Copy", systemImage: "doc.on.doc")
                            .font(.caption)
                    }
                    .buttonStyle(.bordered)
                    .accessibilityLabel("Copy transcript")
                    .accessibilityHint("Copies the full transcript to the clipboard")
                    .sensoryFeedback(.success, trigger: didCopy)
                }

                Divider()

                Text(transcript)
                    .font(.body)
                    .textSelection(.enabled)
                    .lineSpacing(4)
                    .accessibilityLabel("Transcript text")
            }
            .padding()
        }
    }

    // MARK: - Intelligence Content (Cleaned / Summary / Actions)

    private func intelligenceContentView(
        content: String?,
        icon: String,
        emptyTitle: String,
        emptyDescription: String,
        action: @escaping () async -> Void
    ) -> some View {
        Group {
            if editorVM.isIntelligenceProcessing {
                VStack(spacing: 16) {
                    Spacer()
                    ProgressView()
                        .scaleEffect(1.2)
                    Text("Processing with AI...")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Spacer()
                }
            } else if let content = content, !content.isEmpty {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text(emptyTitle)
                                .font(.headline)
                            Spacer()
                            Button {
                                UIPasteboard.general.string = content
                                didCopy.toggle()
                            } label: {
                                Label("Copy", systemImage: "doc.on.doc")
                                    .font(.caption)
                            }
                            .buttonStyle(.bordered)
                            .sensoryFeedback(.success, trigger: didCopy)
                        }

                        Divider()

                        Text(content)
                            .font(.body)
                            .textSelection(.enabled)
                            .lineSpacing(4)
                    }
                    .padding()
                }
            } else {
                VStack(spacing: 16) {
                    Spacer()

                    Image(systemName: icon)
                        .font(.system(size: 48))
                        .foregroundStyle(.secondary)
                        .accessibilityHidden(true)

                    Text(emptyTitle)
                        .font(.headline)

                    Text(emptyDescription)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)

                    Button {
                        Task { await action() }
                    } label: {
                        Label(emptyTitle, systemImage: "sparkles")
                            .font(.headline)
                    }
                    .buttonStyle(.borderedProminent)

                    Spacer()
                }
            }
        }
    }

    // MARK: - Empty Transcript

    private var emptyTranscriptView: some View {
        VStack(spacing: 16) {
            Spacer()

            Image(systemName: "text.alignleft")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)

            Text("No Transcript Available")
                .font(.headline)

            Text("Generate a transcript from your recording.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)

            Button {
                editorVM.loadRecording(recording)
                Task {
                    await editorVM.transcribeRecording(modelContext: modelContext)
                }
            } label: {
                Label("Generate Transcript", systemImage: "wand.and.stars")
                    .font(.headline)
            }
            .buttonStyle(.borderedProminent)
            .accessibilityLabel("Generate transcript")
            .accessibilityHint("Transcribes the audio recording to text")

            Spacer()
        }
    }
}
