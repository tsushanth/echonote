import Foundation
import Speech
#if canImport(WhisperKit)
import WhisperKit
#endif

// MARK: - Transcription Engine

enum TranscriptionEngine: String, CaseIterable {
    case apple = "apple"
    case whisperKit = "whisper"

    var displayName: String {
        switch self {
        case .apple: return "Apple Speech"
        case .whisperKit: return "WhisperKit (Multilingual)"
        }
    }
}

// MARK: - Transcription Language

struct TranscriptionLanguage: Identifiable, Hashable {
    let id: String
    let name: String

    static let autoDetect = TranscriptionLanguage(id: "auto", name: "Auto-Detect")

    static let supported: [TranscriptionLanguage] = [
        autoDetect,
        TranscriptionLanguage(id: "en", name: "English"),
        TranscriptionLanguage(id: "es", name: "Spanish"),
        TranscriptionLanguage(id: "fr", name: "French"),
        TranscriptionLanguage(id: "de", name: "German"),
        TranscriptionLanguage(id: "it", name: "Italian"),
        TranscriptionLanguage(id: "pt", name: "Portuguese"),
        TranscriptionLanguage(id: "ja", name: "Japanese"),
        TranscriptionLanguage(id: "ko", name: "Korean"),
        TranscriptionLanguage(id: "zh", name: "Chinese"),
        TranscriptionLanguage(id: "ar", name: "Arabic"),
        TranscriptionLanguage(id: "hi", name: "Hindi"),
        TranscriptionLanguage(id: "ru", name: "Russian"),
        TranscriptionLanguage(id: "nl", name: "Dutch"),
        TranscriptionLanguage(id: "tr", name: "Turkish"),
        TranscriptionLanguage(id: "pl", name: "Polish"),
        TranscriptionLanguage(id: "sv", name: "Swedish"),
    ]
}

// MARK: - Transcription Service

@Observable
final class TranscriptionService {
    var isTranscribing: Bool = false
    var progress: Double = 0
    var isAvailable: Bool = false

    // WhisperKit state
    var isModelDownloading: Bool = false
    var modelDownloadProgress: Double = 0
    var isModelReady: Bool = false

    #if canImport(WhisperKit)
    private var whisperKit: WhisperKit?
    #endif
    private static let defaultWhisperModel = "openai_whisper-base"

    init() {
        checkAvailability()
    }

    private func checkAvailability() {
        isAvailable = SFSpeechRecognizer.authorizationStatus() == .authorized
            || SFSpeechRecognizer.authorizationStatus() == .notDetermined
    }

    func requestPermission() async -> Bool {
        return await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { status in
                self.isAvailable = status == .authorized
                continuation.resume(returning: status == .authorized)
            }
        }
    }

    // MARK: - Public Transcription Entry Point

    func transcribe(audioURL: URL, engine: TranscriptionEngine, language: String?) async throws -> String {
        switch engine {
        case .apple:
            return try await transcribeWithApple(audioURL: audioURL)
        case .whisperKit:
            #if canImport(WhisperKit)
            return try await transcribeWithWhisperKit(audioURL: audioURL, language: language)
            #else
            throw TranscriptionError.engineNotReady
            #endif
        }
    }

    // MARK: - Apple Speech (Existing)

    private func transcribeWithApple(audioURL: URL) async throws -> String {
        if !isAvailable {
            let granted = await requestPermission()
            guard granted else {
                throw TranscriptionError.notAuthorized
            }
        }

        guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US")),
              recognizer.isAvailable else {
            throw TranscriptionError.recognizerUnavailable
        }

        isTranscribing = true
        progress = 0

        defer {
            isTranscribing = false
            progress = 1.0
        }

        let request = SFSpeechURLRecognitionRequest(url: audioURL)
        request.shouldReportPartialResults = false
        request.addsPunctuation = true

        return try await withCheckedThrowingContinuation { continuation in
            var hasResumed = false
            recognizer.recognitionTask(with: request) { [weak self] result, error in
                guard !hasResumed else { return }

                if let error = error {
                    hasResumed = true
                    continuation.resume(throwing: error)
                    return
                }

                guard let result = result else {
                    hasResumed = true
                    continuation.resume(throwing: TranscriptionError.noResult)
                    return
                }

                if result.isFinal {
                    hasResumed = true
                    self?.progress = 1.0
                    continuation.resume(returning: result.bestTranscription.formattedString)
                } else {
                    let segmentCount = Double(result.bestTranscription.segments.count)
                    self?.progress = min(segmentCount / 100.0, 0.9)
                }
            }
        }
    }

    // MARK: - WhisperKit

    #if canImport(WhisperKit)
    func prepareWhisperKit(model: String = defaultWhisperModel) async throws {
        guard whisperKit == nil else { return }

        isModelDownloading = true
        modelDownloadProgress = 0
        defer { isModelDownloading = false }

        do {
            whisperKit = try await WhisperKit(
                WhisperKitConfig(model: model, verbose: false, logLevel: .error)
            )
            isModelReady = true
        } catch {
            isModelReady = false
            throw TranscriptionError.modelDownloadFailed
        }
    }

    private func transcribeWithWhisperKit(audioURL: URL, language: String?) async throws -> String {
        if whisperKit == nil {
            try await prepareWhisperKit()
        }

        guard let whisperKit = whisperKit else {
            throw TranscriptionError.engineNotReady
        }

        isTranscribing = true
        progress = 0

        defer {
            isTranscribing = false
            progress = 1.0
        }

        let options = DecodingOptions(
            language: (language == "auto" || language == nil) ? nil : language
        )

        let results = try await whisperKit.transcribe(
            audioPath: audioURL.path,
            decodeOptions: options
        )

        guard let result = results.first else {
            throw TranscriptionError.noResult
        }

        progress = 1.0
        return result.text.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    #endif
}

// MARK: - Errors

enum TranscriptionError: LocalizedError {
    case notAuthorized
    case recognizerUnavailable
    case noResult
    case modelDownloadFailed
    case insufficientStorage
    case engineNotReady

    var errorDescription: String? {
        switch self {
        case .notAuthorized:
            return "Speech recognition permission not granted."
        case .recognizerUnavailable:
            return "Speech recognizer is not available."
        case .noResult:
            return "No transcription result available."
        case .modelDownloadFailed:
            return "Failed to download the transcription model. Please check your internet connection and try again."
        case .insufficientStorage:
            return "Not enough storage to download the transcription model. Please free up space and try again."
        case .engineNotReady:
            return "The transcription engine is still loading. Please wait and try again."
        }
    }
}
