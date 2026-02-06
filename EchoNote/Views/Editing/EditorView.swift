import SwiftData
import SwiftUI

struct EditorView: View {
    @Bindable var editorVM: EditorViewModel
    let recording: Recording
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @State private var playerVM = PlayerViewModel()

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                recordingInfoSection

                trimSection

                waveformSection

                editorControls

                actionsList
            }
            .padding()
            .navigationTitle("Edit Recording")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .overlay {
                if editorVM.isProcessing {
                    processingOverlay
                }
            }
            .alert("Error", isPresented: $editorVM.showError) {
                Button("OK") { editorVM.showError = false }
            } message: {
                Text(editorVM.errorMessage ?? "An unknown error occurred.")
            }
            .sheet(isPresented: $editorVM.showSaveAsSheet) {
                saveAsSheet
            }
            .onAppear {
                editorVM.loadRecording(recording)
                _ = playerVM.playerService.loadAudio(url: recording.actualFileURL)
                playerVM.waveformSamples = playerVM.playerService.generateWaveformData(url: recording.actualFileURL)
            }
        }
    }

    private var recordingInfoSection: some View {
        VStack(spacing: 4) {
            Text(recording.title)
                .font(.headline)
            Text(recording.formattedDuration)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    private var trimSection: some View {
        VStack(spacing: 8) {
            Text("Trim Range")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            HStack {
                VStack(alignment: .leading) {
                    Text("Start")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(editorVM.trimStart.formattedTimeWithMilliseconds)
                        .font(.subheadline)
                        .monospacedDigit()
                }

                Spacer()

                VStack(alignment: .trailing) {
                    Text("End")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(editorVM.trimEnd.formattedTimeWithMilliseconds)
                        .font(.subheadline)
                        .monospacedDigit()
                }
            }
            .padding(.horizontal)
        }
    }

    private var waveformSection: some View {
        VStack(spacing: 4) {
            TrimWaveformView(
                samples: playerVM.waveformSamples,
                trimStart: $editorVM.trimStart,
                trimEnd: $editorVM.trimEnd,
                totalDuration: recording.duration
            )
            .frame(height: 150)
            .padding(.horizontal)
        }
    }

    private var editorControls: some View {
        HStack(spacing: 20) {
            Button {
                playerVM.seek(to: editorVM.trimStart)
                playerVM.play()
            } label: {
                Label("Preview", systemImage: "play.fill")
                    .font(.subheadline)
            }
            .buttonStyle(.bordered)

            Button {
                playerVM.stop()
            } label: {
                Label("Stop", systemImage: "stop.fill")
                    .font(.subheadline)
            }
            .buttonStyle(.bordered)
            .disabled(!playerVM.isPlaying)
        }
    }

    private var actionsList: some View {
        List {
            Section("Actions") {
                Button {
                    Task {
                        await editorVM.trimRecording(modelContext: modelContext)
                    }
                } label: {
                    Label("Trim Recording", systemImage: "scissors")
                }

                Button {
                    editorVM.saveAsName = recording.title + " (Copy)"
                    editorVM.showSaveAsSheet = true
                } label: {
                    Label("Save As...", systemImage: "doc.badge.plus")
                }

                Button {
                    Task {
                        await editorVM.enhanceRecording(modelContext: modelContext)
                    }
                } label: {
                    Label("Enhance Recording", systemImage: "wand.and.stars")
                }
                .disabled(recording.isEnhanced)

                Button {
                    Task {
                        await editorVM.transcribeRecording(modelContext: modelContext)
                    }
                } label: {
                    Label("Generate Transcript", systemImage: "text.alignleft")
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
    }

    private var processingOverlay: some View {
        ZStack {
            Color.black.opacity(0.4)
                .ignoresSafeArea()

            VStack(spacing: 16) {
                ProgressView()
                    .scaleEffect(1.5)
                Text("Processing...")
                    .font(.headline)
                    .foregroundStyle(.white)
            }
            .padding(32)
            .background(
                RoundedRectangle(cornerRadius: AppConstants.UI.cornerRadius)
                    .fill(.ultraThinMaterial)
            )
        }
    }

    private var saveAsSheet: some View {
        NavigationStack {
            Form {
                Section("New Name") {
                    TextField("Recording name", text: $editorVM.saveAsName)
                }

                Section {
                    Button("Save Copy") {
                        Task {
                            await editorVM.saveAs(modelContext: modelContext)
                        }
                    }
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .disabled(editorVM.saveAsName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .navigationTitle("Save As")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        editorVM.showSaveAsSheet = false
                    }
                }
            }
        }
    }
}

struct TrimWaveformView: View {
    let samples: [Float]
    @Binding var trimStart: TimeInterval
    @Binding var trimEnd: TimeInterval
    let totalDuration: TimeInterval

    @State private var leftHandleOffset: CGFloat = 0
    @State private var rightHandleOffset: CGFloat = 0

    var body: some View {
        GeometryReader { geometry in
            let width = geometry.size.width
            let height = geometry.size.height

            ZStack(alignment: .leading) {
                // Full waveform background
                WaveformView(
                    samples: samples,
                    progress: 0,
                    activeColor: .gray.opacity(0.3),
                    inactiveColor: .gray.opacity(0.3),
                    maxHeight: height
                )

                // Selected region overlay
                let startX = totalDuration > 0 ? CGFloat(trimStart / totalDuration) * width : 0
                let endX = totalDuration > 0 ? CGFloat(trimEnd / totalDuration) * width : width

                // Active region waveform
                WaveformView(
                    samples: samples,
                    progress: 1.0,
                    activeColor: .accentColor,
                    inactiveColor: .accentColor,
                    maxHeight: height
                )
                .mask(
                    Rectangle()
                        .frame(width: max(0, endX - startX))
                        .offset(x: startX)
                        .frame(maxWidth: .infinity, alignment: .leading)
                )

                // Left handle
                Rectangle()
                    .fill(Color.accentColor)
                    .frame(width: 4, height: height)
                    .offset(x: startX - 2)
                    .gesture(
                        DragGesture()
                            .onChanged { value in
                                let newX = max(0, min(value.location.x, endX - 20))
                                trimStart = (Double(newX) / Double(width)) * totalDuration
                            }
                    )

                // Right handle
                Rectangle()
                    .fill(Color.accentColor)
                    .frame(width: 4, height: height)
                    .offset(x: endX - 2)
                    .gesture(
                        DragGesture()
                            .onChanged { value in
                                let newX = max(startX + 20, min(value.location.x, width))
                                trimEnd = (Double(newX) / Double(width)) * totalDuration
                            }
                    )
            }
        }
    }
}
