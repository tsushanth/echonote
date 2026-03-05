import AVFoundation
import Foundation
import SwiftData

@MainActor
@Observable
final class EditorViewModel {
    let editorService = AudioEditorService()
    let transcriptionService = TranscriptionService()
    let intelligenceService = TranscriptIntelligenceService()

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
    var isModelDownloading: Bool { transcriptionService.isModelDownloading }
    var isIntelligenceAvailable: Bool { intelligenceService.isAvailable }
    var isIntelligenceProcessing: Bool { intelligenceService.isProcessing }

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

    // MARK: - Transcription

    func transcribeRecording(modelContext: ModelContext) async {
        guard let recording = recording else { return }

        let engineRaw = UserDefaults.standard.string(forKey: "transcriptionEngine") ?? TranscriptionEngine.apple.rawValue
        let engine = TranscriptionEngine(rawValue: engineRaw) ?? .apple
        let languageCode = UserDefaults.standard.string(forKey: "transcriptionLanguage") ?? "auto"
        let language: String? = (languageCode == "auto") ? nil : languageCode

        do {
            let sourceURL = recording.actualFileURL
            let transcript = try await transcriptionService.transcribe(
                audioURL: sourceURL,
                engine: engine,
                language: language
            )
            recording.transcript = transcript
            recording.transcriptionEngine = engine.rawValue
            recording.transcriptionLanguage = languageCode
            // Clear any previous AI-processed content since transcript changed
            recording.cleanedTranscript = nil
            recording.summary = nil
            recording.actionItems = nil
            recording.dateModified = Date()
            try? modelContext.save()
        } catch {
            errorMessage = error.localizedDescription
            showError = true
        }
    }

    // MARK: - AI Post-Processing

    func cleanTranscript(modelContext: ModelContext) async {
        guard let recording = recording, let transcript = recording.transcript else { return }

        do {
            let cleaned = try await intelligenceService.cleanTranscript(transcript)
            recording.cleanedTranscript = cleaned
            recording.dateModified = Date()
            try? modelContext.save()
        } catch {
            errorMessage = error.localizedDescription
            showError = true
        }
    }

    func summarizeTranscript(modelContext: ModelContext) async {
        guard let recording = recording, let transcript = recording.transcript else { return }

        do {
            let summary = try await intelligenceService.summarize(transcript)
            recording.summary = summary
            recording.dateModified = Date()
            try? modelContext.save()
        } catch {
            errorMessage = error.localizedDescription
            showError = true
        }
    }

    func extractActionItems(modelContext: ModelContext) async {
        guard let recording = recording, let transcript = recording.transcript else { return }

        do {
            let items = try await intelligenceService.extractActionItems(transcript)
            recording.actionItems = items
            recording.dateModified = Date()
            try? modelContext.save()
        } catch {
            errorMessage = error.localizedDescription
            showError = true
        }
    }

    // MARK: - Audio Editing

    func removeSilence(modelContext: ModelContext) async {
        guard let recording = recording else { return }
        isProcessing = true

        do {
            let sourceURL = recording.actualFileURL
            let processedURL = try await editorService.removeSilence(sourceURL: sourceURL)

            try FileManager.default.removeItem(at: sourceURL)
            try FileManager.default.moveItem(at: processedURL, to: sourceURL)

            let asset = AVURLAsset(url: sourceURL)
            let duration = try await asset.load(.duration)
            recording.duration = CMTimeGetSeconds(duration)

            let attributes = try FileManager.default.attributesOfItem(atPath: sourceURL.path)
            recording.fileSize = attributes[.size] as? Int64 ?? 0
            recording.dateModified = Date()

            try? modelContext.save()
        } catch {
            errorMessage = error.localizedDescription
            showError = true
        }

        isProcessing = false
    }

    func exportToWAV(modelContext: ModelContext) async -> URL? {
        guard let recording = recording else { return nil }
        isProcessing = true

        var resultURL: URL?
        do {
            let sourceURL = recording.actualFileURL
            let wavURL = try await editorService.exportToWAV(sourceURL: sourceURL)
            resultURL = wavURL
        } catch {
            errorMessage = error.localizedDescription
            showError = true
        }

        isProcessing = false
        return resultURL
    }

    func separateVocals(modelContext: ModelContext) async -> (vocals: URL, instrumental: URL)? {
        guard let recording = recording else { return nil }
        isProcessing = true

        var result: (vocals: URL, instrumental: URL)?
        do {
            let sourceURL = recording.actualFileURL
            let separated = try await editorService.separateVocals(sourceURL: sourceURL)
            result = separated

            // Create new recordings for the separated tracks
            let vocalsAsset = AVURLAsset(url: separated.vocals)
            let vocalsDuration = try await vocalsAsset.load(.duration)
            let vocalsAttributes = try FileManager.default.attributesOfItem(atPath: separated.vocals.path)

            let vocalsRecording = Recording(
                title: "\(recording.title) - Vocals",
                fileURL: "\(AppConstants.recordingsDirectoryName)/\(separated.vocals.lastPathComponent)",
                duration: CMTimeGetSeconds(vocalsDuration),
                fileSize: vocalsAttributes[.size] as? Int64 ?? 0,
                audioFormat: recording.audioFormat,
                quality: recording.quality,
                isStereo: recording.isStereo
            )
            vocalsRecording.folder = recording.folder
            modelContext.insert(vocalsRecording)

            let instrumentalAsset = AVURLAsset(url: separated.instrumental)
            let instrumentalDuration = try await instrumentalAsset.load(.duration)
            let instrumentalAttributes = try FileManager.default.attributesOfItem(atPath: separated.instrumental.path)

            let instrumentalRecording = Recording(
                title: "\(recording.title) - Instrumental",
                fileURL: "\(AppConstants.recordingsDirectoryName)/\(separated.instrumental.lastPathComponent)",
                duration: CMTimeGetSeconds(instrumentalDuration),
                fileSize: instrumentalAttributes[.size] as? Int64 ?? 0,
                audioFormat: recording.audioFormat,
                quality: recording.quality,
                isStereo: recording.isStereo
            )
            instrumentalRecording.folder = recording.folder
            modelContext.insert(instrumentalRecording)

            try? modelContext.save()
        } catch AudioEditorError.modelNotFound {
            errorMessage = "Vocal separation model not found. Please add VocalSeparationModel.mlmodel to the project."
            showError = true
        } catch {
            errorMessage = error.localizedDescription
            showError = true
        }

        isProcessing = false
        return result
    }
}
