import Foundation

#if canImport(FoundationModels)
import FoundationModels
#endif

// MARK: - Transcript Intelligence Service

@MainActor
@Observable
final class TranscriptIntelligenceService {
    var isProcessing: Bool = false

    var isAvailable: Bool {
        if #available(iOS 26, *) {
            #if canImport(FoundationModels)
            return checkModelAvailability()
            #else
            return false
            #endif
        }
        return false
    }

    // MARK: - Public Methods

    func cleanTranscript(_ rawTranscript: String) async throws -> String {
        guard #available(iOS 26, *) else {
            throw IntelligenceError.unavailable
        }
        #if canImport(FoundationModels)
        return try await cleanTranscriptiOS26(rawTranscript)
        #else
        throw IntelligenceError.unavailable
        #endif
    }

    func summarize(_ transcript: String) async throws -> String {
        guard #available(iOS 26, *) else {
            throw IntelligenceError.unavailable
        }
        #if canImport(FoundationModels)
        return try await summarizeiOS26(transcript)
        #else
        throw IntelligenceError.unavailable
        #endif
    }

    func extractActionItems(_ transcript: String) async throws -> String {
        guard #available(iOS 26, *) else {
            throw IntelligenceError.unavailable
        }
        #if canImport(FoundationModels)
        return try await extractActionItemsiOS26(transcript)
        #else
        throw IntelligenceError.unavailable
        #endif
    }

    // MARK: - iOS 26+ Implementations

    #if canImport(FoundationModels)

    @available(iOS 26, *)
    private func checkModelAvailability() -> Bool {
        let availability = LanguageModelSession.availability
        switch availability {
        case .available:
            return true
        default:
            return false
        }
    }

    @available(iOS 26, *)
    private func cleanTranscriptiOS26(_ rawTranscript: String) async throws -> String {
        isProcessing = true
        defer { isProcessing = false }

        let session = LanguageModelSession(instructions: """
            You are a transcript editor. Clean up the following transcript by:
            - Removing filler words (um, uh, like, you know, basically, so, etc.)
            - Fixing grammar and punctuation
            - Adding proper paragraph breaks at topic changes
            - Preserving the original meaning exactly
            Return only the cleaned transcript, nothing else.
            """)

        let response = try await session.respond(to: rawTranscript)
        return response.content
    }

    @available(iOS 26, *)
    private func summarizeiOS26(_ transcript: String) async throws -> String {
        isProcessing = true
        defer { isProcessing = false }

        let session = LanguageModelSession(instructions: """
            You are a summarizer. Create a concise summary of the following transcript.
            Keep the summary to 2-4 sentences capturing the key points.
            Return only the summary, nothing else.
            """)

        let response = try await session.respond(to: transcript)
        return response.content
    }

    @available(iOS 26, *)
    private func extractActionItemsiOS26(_ transcript: String) async throws -> String {
        isProcessing = true
        defer { isProcessing = false }

        let session = LanguageModelSession(instructions: """
            You are an assistant that extracts action items from transcripts.
            List each action item on a new line, prefixed with "- ".
            If there are no action items, respond with "No action items found."
            Return only the action items list, nothing else.
            """)

        let response = try await session.respond(to: transcript)
        return response.content
    }

    #else

    private func checkModelAvailability() -> Bool {
        return false
    }

    #endif
}

// MARK: - Errors

enum IntelligenceError: LocalizedError {
    case unavailable
    case processingFailed

    var errorDescription: String? {
        switch self {
        case .unavailable:
            return "AI features require iOS 26 or later with Apple Intelligence enabled."
        case .processingFailed:
            return "Failed to process transcript."
        }
    }
}
