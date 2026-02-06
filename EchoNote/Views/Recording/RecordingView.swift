import SwiftData
import SwiftUI

struct RecordingView: View {
    @Bindable var recordingVM: RecordingViewModel
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer()

                timerDisplay

                liveWaveform

                recordingControls

                formatSelector

                Spacer()
            }
            .padding()
            .navigationTitle("Record")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    if recordingVM.isRecording || recordingVM.isPaused {
                        Button("Cancel") {
                            recordingVM.cancelRecording()
                            dismiss()
                        }
                        .accessibilityLabel("Cancel recording")
                    } else {
                        Button("Close") {
                            dismiss()
                        }
                        .accessibilityLabel("Close recording screen")
                    }
                }
            }
            .sheet(isPresented: $recordingVM.showSaveSheet) {
                SaveRecordingSheet(recordingVM: recordingVM)
            }
            .alert("Error", isPresented: $recordingVM.showError) {
                Button("OK") { recordingVM.showError = false }
            } message: {
                Text(recordingVM.errorMessage ?? "An unknown error occurred.")
            }
        }
    }

    private var timerDisplay: some View {
        Text(recordingVM.currentTime.formattedTimeWithMilliseconds)
            .font(.system(size: 64, weight: .thin, design: .monospaced))
            .foregroundStyle(recordingVM.isRecording ? AppConstants.Colors.recordingRed : .primary)
            .contentTransition(.numericText())
            .animation(.linear(duration: 0.1), value: recordingVM.currentTime)
            .accessibilityLabel("Recording time: \(recordingVM.currentTime.formattedTimeWithMilliseconds)")
    }

    private var liveWaveform: some View {
        LiveWaveformView(
            levels: recordingVM.meterLevels,
            color: AppConstants.Colors.recordingRed
        )
        .padding(.horizontal)
        .accessibilityLabel("Audio level meter")
        .accessibilityHidden(!recordingVM.isRecording)
    }

    private var recordingControls: some View {
        HStack(spacing: 40) {
            if recordingVM.isRecording || recordingVM.isPaused {
                Button {
                    recordingVM.stopRecording()
                } label: {
                    Image(systemName: "stop.fill")
                        .font(.title)
                        .foregroundStyle(.primary)
                        .frame(width: 56, height: 56)
                        .background(
                            Circle()
                                .fill(Color(.tertiarySystemBackground))
                        )
                }
                .accessibilityLabel("Stop recording")
                .sensoryFeedback(.impact(weight: .medium), trigger: recordingVM.isPaused)

                Button {
                    if recordingVM.isRecording {
                        recordingVM.pauseRecording()
                    } else {
                        recordingVM.resumeRecording()
                    }
                } label: {
                    Image(systemName: recordingVM.isRecording ? "pause.fill" : "play.fill")
                        .font(.title)
                        .foregroundStyle(.white)
                        .frame(width: AppConstants.UI.recordButtonSize, height: AppConstants.UI.recordButtonSize)
                        .background(
                            Circle()
                                .fill(AppConstants.Colors.recordingRed)
                        )
                }
                .accessibilityLabel(recordingVM.isRecording ? "Pause recording" : "Resume recording")
            } else {
                Button {
                    recordingVM.startRecording()
                } label: {
                    ZStack {
                        Circle()
                            .fill(AppConstants.Colors.recordingRed)
                            .frame(width: AppConstants.UI.recordButtonSize, height: AppConstants.UI.recordButtonSize)

                        Circle()
                            .stroke(.white, lineWidth: 4)
                            .frame(width: AppConstants.UI.recordButtonSize + 8, height: AppConstants.UI.recordButtonSize + 8)
                    }
                }
                .accessibilityLabel("Start recording")
                .accessibilityHint("Double tap to begin a new recording")
            }
        }
    }

    private var formatSelector: some View {
        VStack(spacing: 12) {
            if !recordingVM.isRecording && !recordingVM.isPaused {
                HStack {
                    Text("Format")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Picker("Format", selection: $recordingVM.selectedFormat) {
                        ForEach(AudioFormat.allCases, id: \.self) { format in
                            Text(format.displayName).tag(format)
                        }
                    }
                    .pickerStyle(.menu)
                    .accessibilityLabel("Audio format")
                    .accessibilityValue(recordingVM.selectedFormat.displayName)
                }

                HStack {
                    Text("Quality")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Picker("Quality", selection: $recordingVM.selectedQuality) {
                        ForEach(RecordingQuality.allCases, id: \.self) { quality in
                            Text(quality.displayName).tag(quality)
                        }
                    }
                    .pickerStyle(.menu)
                    .accessibilityLabel("Recording quality")
                    .accessibilityValue(recordingVM.selectedQuality.displayName)
                }

                Toggle("Stereo Recording", isOn: $recordingVM.isStereo)
                    .font(.subheadline)
                    .tint(.accentColor)
                    .accessibilityLabel("Stereo recording")
                    .accessibilityHint("Enable to record in stereo")
            }
        }
        .padding(.horizontal)
    }
}

struct SaveRecordingSheet: View {
    @Bindable var recordingVM: RecordingViewModel
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @FocusState private var isNameFocused: Bool

    var body: some View {
        NavigationStack {
            Form {
                Section("Recording Name") {
                    TextField("Enter name (or use location)", text: $recordingVM.recordingTitle)
                        .textInputAutocapitalization(.words)
                        .focused($isNameFocused)
                        .submitLabel(.done)
                        .onSubmit { isNameFocused = false }
                        .accessibilityLabel("Recording name")
                }

                Section {
                    if let url = recordingVM.currentRecordingURL {
                        let fileSize = recordingVM.recorderService.getFileSize(url: url)
                        LabeledContent("Format", value: recordingVM.selectedFormat.displayName)
                        LabeledContent("Quality", value: recordingVM.selectedQuality.displayName)
                        LabeledContent("Size", value: ByteCountFormatter.string(fromByteCount: fileSize, countStyle: .file))
                    }
                }

                Section {
                    Button("Save Recording") {
                        Task {
                            await recordingVM.saveRecording(modelContext: modelContext)
                            dismiss()
                        }
                    }
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel("Save recording")
                    .accessibilityHint("Saves the recording with the entered name")

                    Button("Discard", role: .destructive) {
                        recordingVM.discardRecording()
                        dismiss()
                    }
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel("Discard recording")
                    .accessibilityHint("Permanently deletes this recording")
                }
            }
            .navigationTitle("Save Recording")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { isNameFocused = true }
        }
    }
}
