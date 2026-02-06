import SwiftData
import SwiftUI

struct TranscriptView: View {
    let recording: Recording
    @Bindable var editorVM: EditorViewModel
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                if editorVM.isTranscribing {
                    transcribingView
                } else if let transcript = recording.transcript {
                    transcriptContentView(transcript)
                } else {
                    emptyTranscriptView
                }
            }
            .navigationTitle("Transcript")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private var transcribingView: some View {
        VStack(spacing: 16) {
            Spacer()
            ProgressView(value: editorVM.transcriptionProgress)
                .padding(.horizontal, 40)
            Text("Transcribing audio...")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Spacer()
        }
    }

    private func transcriptContentView(_ transcript: String) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(recording.title)
                        .font(.headline)
                    Spacer()
                    Button {
                        UIPasteboard.general.string = transcript
                    } label: {
                        Label("Copy", systemImage: "doc.on.doc")
                            .font(.caption)
                    }
                    .buttonStyle(.bordered)
                }

                Divider()

                Text(transcript)
                    .font(.body)
                    .textSelection(.enabled)
                    .lineSpacing(4)
            }
            .padding()
        }
    }

    private var emptyTranscriptView: some View {
        VStack(spacing: 16) {
            Spacer()

            Image(systemName: "text.alignleft")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)

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

            Spacer()
        }
    }
}
