import Foundation
import Speech

@Observable
final class TranscriptionService {
    var isTranscribing: Bool = false
    var progress: Double = 0
    var isAvailable: Bool = false

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

    func transcribe(audioURL: URL) async throws -> String {
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
}

enum TranscriptionError: LocalizedError {
    case notAuthorized
    case recognizerUnavailable
    case noResult

    var errorDescription: String? {
        switch self {
        case .notAuthorized: return "Speech recognition permission not granted."
        case .recognizerUnavailable: return "Speech recognizer is not available."
        case .noResult: return "No transcription result available."
        }
    }
}
