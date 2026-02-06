import AVFoundation
import Foundation
import SwiftData
import SwiftUI

@Observable
final class RecordingViewModel {
    let recorderService = AudioRecorderService()
    let locationService = LocationService()

    var selectedFormat: AudioFormat = .compressed
    var selectedQuality: RecordingQuality = .high
    var isStereo: Bool = false
    var currentRecordingURL: URL?
    var recordingTitle: String = ""
    var showSaveSheet: Bool = false
    var errorMessage: String?
    var showError: Bool = false

    var isRecording: Bool { recorderService.isRecording }
    var isPaused: Bool { recorderService.isPaused }
    var currentTime: TimeInterval { recorderService.currentTime }
    var meterLevels: [Float] { recorderService.meterLevels }
    var averagePower: Float { recorderService.averagePower }

    func startRecording() {
        let fileName = UUID().uuidString
        currentRecordingURL = recorderService.startRecording(
            fileName: fileName,
            format: selectedFormat,
            quality: selectedQuality,
            isStereo: isStereo
        )

        if currentRecordingURL == nil {
            errorMessage = "Failed to start recording. Please check microphone permissions."
            showError = true
        }
    }

    func pauseRecording() {
        recorderService.pauseRecording()
    }

    func resumeRecording() {
        recorderService.resumeRecording()
    }

    func stopRecording() {
        guard let result = recorderService.stopRecording() else { return }
        currentRecordingURL = result.url
        showSaveSheet = true
    }

    func cancelRecording() {
        recorderService.cancelRecording()
        currentRecordingURL = nil
    }

    func saveRecording(modelContext: ModelContext) async {
        guard let url = currentRecordingURL else { return }

        var title = recordingTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        if title.isEmpty {
            let locationName = await locationService.getCurrentLocationName()
            title = locationName ?? "New Recording"
        }

        let fileSize = recorderService.getFileSize(url: url)
        let asset = AVURLAsset(url: url)
        let duration: TimeInterval
        do {
            let assetDuration = try await asset.load(.duration)
            duration = CMTimeGetSeconds(assetDuration)
        } catch {
            duration = 0
        }

        let relativePath = "\(AppConstants.recordingsDirectoryName)/\(url.lastPathComponent)"

        let recording = Recording(
            title: title,
            fileURL: relativePath,
            duration: duration,
            fileSize: fileSize,
            audioFormat: selectedFormat,
            quality: selectedQuality,
            isStereo: isStereo,
            locationName: locationService.currentLocationName
        )

        modelContext.insert(recording)
        try? modelContext.save()

        recordingTitle = ""
        currentRecordingURL = nil
        showSaveSheet = false
    }

    func discardRecording() {
        if let url = currentRecordingURL {
            try? FileManager.default.removeItem(at: url)
        }
        currentRecordingURL = nil
        recordingTitle = ""
        showSaveSheet = false
    }

    func requestPermissions() {
        recorderService.setupAudioSession()
        locationService.requestPermission()
    }
}
