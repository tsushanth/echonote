import SwiftData
import SwiftUI

struct PlaybackView: View {
    @Bindable var playerVM: PlayerViewModel
    @Bindable var editorVM: EditorViewModel
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @State private var showEditorSheet = false
    @State private var showTranscript = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    if let recording = playerVM.currentRecording {
                        recordingInfo(recording)

                        waveformSection

                        timeDisplay

                        progressSlider

                        playbackControls

                        rateAndSkipControls

                        actionButtons(recording)
                    }
                }
                .padding()
            }
            .navigationTitle("Now Playing")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        playerVM.stop()
                        dismiss()
                    }
                    .accessibilityLabel("Close player")
                }
            }
            .sheet(isPresented: $showEditorSheet) {
                if let recording = playerVM.currentRecording {
                    EditorView(editorVM: editorVM, recording: recording)
                }
            }
            .sheet(isPresented: $showTranscript) {
                if let recording = playerVM.currentRecording {
                    TranscriptView(recording: recording, editorVM: editorVM)
                }
            }
        }
    }

    private func recordingInfo(_ recording: Recording) -> some View {
        VStack(spacing: 8) {
            Text(recording.title)
                .font(.title3)
                .fontWeight(.semibold)
                .lineLimit(2)
                .multilineTextAlignment(.center)

            HStack(spacing: 8) {
                if let location = recording.locationName {
                    Label(location, systemImage: "location.fill")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Text(recording.formattedDate)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.top, 8)
        .accessibilityElement(children: .combine)
    }

    private var waveformSection: some View {
        GeometryReader { geometry in
            WaveformView(
                samples: playerVM.waveformSamples,
                progress: playerVM.progress,
                activeColor: .accentColor,
                inactiveColor: AppConstants.Colors.waveformGray,
                maxHeight: geometry.size.height
            )
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onEnded { value in
                        let tapProgress = max(0, min(value.location.x / geometry.size.width, 1.0))
                        playerVM.seekToProgress(tapProgress)
                    }
            )
        }
        .frame(height: AppConstants.UI.maxWaveformHeight)
        .padding(.horizontal)
        .accessibilityLabel("Audio waveform")
        .accessibilityHint("Tap to seek to a position")
        .accessibilityValue("\(Int(playerVM.progress * 100)) percent played")
    }

    private var timeDisplay: some View {
        HStack {
            Text(playerVM.currentTime.formattedTime)
                .font(.caption)
                .foregroundStyle(.secondary)
                .monospacedDigit()
            Spacer()
            Text(playerVM.duration.formattedTime)
                .font(.caption)
                .foregroundStyle(.secondary)
                .monospacedDigit()
        }
        .padding(.horizontal)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Time: \(playerVM.currentTime.formattedTime) of \(playerVM.duration.formattedTime)")
    }

    private var progressSlider: some View {
        Slider(
            value: Binding(
                get: { playerVM.progress },
                set: { playerVM.seekToProgress($0) }
            ),
            in: 0...1
        )
        .tint(.accentColor)
        .padding(.horizontal)
        .accessibilityLabel("Playback position")
        .accessibilityValue("\(Int(playerVM.progress * 100)) percent")
    }

    private var playbackControls: some View {
        HStack(spacing: 40) {
            Button {
                playerVM.skipBackward()
            } label: {
                Image(systemName: "gobackward.15")
                    .font(.title2)
                    .foregroundStyle(.primary)
            }
            .accessibilityLabel("Skip back 15 seconds")

            Button {
                playerVM.togglePlayPause()
            } label: {
                Image(systemName: playerVM.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(Color.accentColor)
            }
            .accessibilityLabel(playerVM.isPlaying ? "Pause" : "Play")
            .sensoryFeedback(.selection, trigger: playerVM.isPlaying)

            Button {
                playerVM.skipForward()
            } label: {
                Image(systemName: "goforward.15")
                    .font(.title2)
                    .foregroundStyle(.primary)
            }
            .accessibilityLabel("Skip forward 15 seconds")
        }
    }

    private var rateAndSkipControls: some View {
        HStack(spacing: 20) {
            Button {
                playerVM.decreaseRate()
            } label: {
                Image(systemName: "minus")
                    .font(.caption)
            }
            .accessibilityLabel("Decrease playback speed")
            .accessibilityHint("Current speed: \(playerVM.playbackRate, specifier: "%.2f")x")

            Text("\(playerVM.playbackRate, specifier: "%.2f")x")
                .font(.subheadline)
                .monospacedDigit()
                .frame(width: 50)
                .accessibilityLabel("Playback speed: \(playerVM.playbackRate, specifier: "%.2f")x")

            Button {
                playerVM.increaseRate()
            } label: {
                Image(systemName: "plus")
                    .font(.caption)
            }
            .accessibilityLabel("Increase playback speed")
            .accessibilityHint("Current speed: \(playerVM.playbackRate, specifier: "%.2f")x")

            Divider()
                .frame(height: 20)

            Button {
                playerVM.toggleSkipSilence()
            } label: {
                HStack(spacing: 4) {
                    Image(systemName: "forward.fill")
                        .font(.caption)
                    Text("Skip Silence")
                        .font(.caption)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    Capsule()
                        .fill(playerVM.isSkippingSilence ? Color.accentColor.opacity(0.2) : Color(.tertiarySystemBackground))
                )
            }
            .foregroundStyle(playerVM.isSkippingSilence ? Color.accentColor : Color.secondary)
            .accessibilityLabel("Skip silence")
            .accessibilityValue(playerVM.isSkippingSilence ? "On" : "Off")
            .accessibilityHint("Double tap to toggle skipping silent sections")
        }
    }

    private func actionButtons(_ recording: Recording) -> some View {
        HStack(spacing: 16) {
            Button {
                editorVM.loadRecording(recording)
                showEditorSheet = true
            } label: {
                Label("Edit", systemImage: "waveform.and.magnifyingglass")
                    .font(.subheadline)
            }
            .buttonStyle(.bordered)
            .accessibilityLabel("Edit recording")
            .accessibilityHint("Opens the audio editor")

            Button {
                showTranscript = true
            } label: {
                Label("Transcript", systemImage: "text.alignleft")
                    .font(.subheadline)
            }
            .buttonStyle(.bordered)
            .accessibilityLabel("View transcript")
            .accessibilityHint("Opens the transcript view")

            ShareLink(item: recording.actualFileURL) {
                Label("Share", systemImage: "square.and.arrow.up")
                    .font(.subheadline)
            }
            .buttonStyle(.bordered)
            .accessibilityLabel("Share recording")
        }
    }
}
