import AVFoundation
import Foundation
import SwiftData

@MainActor
@Observable
final class EditorViewModel {
    let editorService = AudioEditorService()
    let transcriptionService = TranscriptionService()

    var recording: Recording?
    var trimStart: TimeInterval = 0
    var trimEnd: TimeInterval = 0
    var isProcessing: Bool = false
    var errorMessage: String?
    var showError: Bool = false
    var showSaveAsSheet: Bool = false
    var saveAsName: String = ""

    var isTranscribing: Bool { transcriptionService.isTranscribing }
    var transcriptionProgress: Double { transcriptionService.progress }

    func loadRecording(_ recording: Recording) {
        self.recording = recording
        self.trimStart = 0
        self.trimEnd = recording.duration
    }

    func trimRecording(modelContext: ModelContext) async {
        guard let recording = recording else { return }
        isProcessing = true

        do {
            let sourceURL = recording.actualFileURL
            let trimmedURL = try await editorService.trimAudio(
                sourceURL: sourceURL,
                startTime: trimStart,
                endTime: trimEnd
            )

            try FileManager.default.removeItem(at: sourceURL)
            let newFileName = sourceURL.lastPathComponent
            let destinationURL = sourceURL.deletingLastPathComponent().appendingPathComponent(newFileName)
            try FileManager.default.moveItem(at: trimmedURL, to: destinationURL)

            let asset = AVURLAsset(url: destinationURL)
            let duration = try await asset.load(.duration)
            recording.duration = CMTimeGetSeconds(duration)

            let attributes = try FileManager.default.attributesOfItem(atPath: destinationURL.path)
            recording.fileSize = attributes[.size] as? Int64 ?? 0
            recording.dateModified = Date()

            try? modelContext.save()
        } catch {
            errorMessage = error.localizedDescription
            showError = true
        }

        isProcessing = false
    }

    func saveAs(modelContext: ModelContext) async {
        guard let recording = recording else { return }
        let name = saveAsName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }

        isProcessing = true

        do {
            let sourceURL = recording.actualFileURL
            let newURL = try editorService.saveAs(sourceURL: sourceURL, newName: name)

            let asset = AVURLAsset(url: newURL)
            let duration = try await asset.load(.duration)
            let attributes = try FileManager.default.attributesOfItem(atPath: newURL.path)

            let newRecording = Recording(
                title: name,
                fileURL: "\(AppConstants.recordingsDirectoryName)/\(newURL.lastPathComponent)",
                duration: CMTimeGetSeconds(duration),
                fileSize: attributes[.size] as? Int64 ?? 0,
                audioFormat: recording.audioFormat,
                quality: recording.quality,
                isStereo: recording.isStereo
            )
            newRecording.folder = recording.folder

            modelContext.insert(newRecording)
            try? modelContext.save()

            showSaveAsSheet = false
            saveAsName = ""
        } catch {
            errorMessage = error.localizedDescription
            showError = true
        }

        isProcessing = false
    }

    func enhanceRecording(modelContext: ModelContext) async {
        guard let recording = recording else { return }
        isProcessing = true

        do {
            let sourceURL = recording.actualFileURL
            let enhancedURL = try await editorService.enhanceRecording(sourceURL: sourceURL)

            try FileManager.default.removeItem(at: sourceURL)
            try FileManager.default.moveItem(at: enhancedURL, to: sourceURL)

            recording.isEnhanced = true
            recording.dateModified = Date()

            let attributes = try FileManager.default.attributesOfItem(atPath: sourceURL.path)
            recording.fileSize = attributes[.size] as? Int64 ?? 0

            try? modelContext.save()
        } catch {
            errorMessage = error.localizedDescription
            showError = true
        }

        isProcessing = false
    }

    func transcribeRecording(modelContext: ModelContext) async {
        guard let recording = recording else { return }

        do {
            let sourceURL = recording.actualFileURL
            let transcript = try await transcriptionService.transcribe(audioURL: sourceURL)
            recording.transcript = transcript
            recording.dateModified = Date()
            try? modelContext.save()
        } catch {
            errorMessage = error.localizedDescription
            showError = true
        }
    }
}
