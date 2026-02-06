import AVFoundation
import Foundation

@Observable
final class AudioEditorService {
    var isProcessing: Bool = false
    var progress: Double = 0

    func trimAudio(sourceURL: URL, startTime: TimeInterval, endTime: TimeInterval) async throws -> URL {
        isProcessing = true
        progress = 0

        defer {
            isProcessing = false
            progress = 1.0
        }

        let asset = AVURLAsset(url: sourceURL)
        let duration = try await asset.load(.duration)
        let totalDuration = CMTimeGetSeconds(duration)

        let clampedStart = max(0, startTime)
        let clampedEnd = min(endTime, totalDuration)

        guard clampedStart < clampedEnd else {
            throw AudioEditorError.invalidTimeRange
        }

        let startCMTime = CMTime(seconds: clampedStart, preferredTimescale: 44100)
        let endCMTime = CMTime(seconds: clampedEnd, preferredTimescale: 44100)
        let timeRange = CMTimeRange(start: startCMTime, end: endCMTime)

        let outputURL = generateOutputURL(from: sourceURL, suffix: "trimmed")

        guard let exportSession = AVAssetExportSession(asset: asset, presetName: AVAssetExportPresetAppleM4A) else {
            throw AudioEditorError.exportFailed
        }

        exportSession.outputURL = outputURL
        exportSession.outputFileType = .m4a
        exportSession.timeRange = timeRange

        await exportSession.export()

        if exportSession.status == .completed {
            progress = 1.0
            return outputURL
        } else {
            throw exportSession.error ?? AudioEditorError.exportFailed
        }
    }

    func replaceSegment(
        sourceURL: URL,
        replacementURL: URL,
        startTime: TimeInterval,
        endTime: TimeInterval
    ) async throws -> URL {
        isProcessing = true
        progress = 0

        defer {
            isProcessing = false
            progress = 1.0
        }

        let sourceAsset = AVURLAsset(url: sourceURL)
        let replacementAsset = AVURLAsset(url: replacementURL)

        let composition = AVMutableComposition()

        guard let compositionTrack = composition.addMutableTrack(
            withMediaType: .audio,
            preferredTrackID: kCMPersistentTrackID_Invalid
        ) else {
            throw AudioEditorError.compositionFailed
        }

        let sourceTracks = try await sourceAsset.loadTracks(withMediaType: .audio)
        let replacementTracks = try await replacementAsset.loadTracks(withMediaType: .audio)

        guard let sourceTrack = sourceTracks.first,
              let replacementTrack = replacementTracks.first else {
            throw AudioEditorError.noAudioTrack
        }

        let startCMTime = CMTime(seconds: startTime, preferredTimescale: 44100)
        let endCMTime = CMTime(seconds: endTime, preferredTimescale: 44100)

        // Insert audio before the replacement point
        if startTime > 0 {
            let beforeRange = CMTimeRange(start: .zero, end: startCMTime)
            try compositionTrack.insertTimeRange(beforeRange, of: sourceTrack, at: .zero)
        }

        // Insert replacement audio
        let replacementDuration = try await replacementAsset.load(.duration)
        let replacementRange = CMTimeRange(start: .zero, end: replacementDuration)
        try compositionTrack.insertTimeRange(replacementRange, of: replacementTrack, at: startCMTime)

        // Insert audio after the replacement point
        let sourceDuration = try await sourceAsset.load(.duration)
        let afterStart = endCMTime
        let afterEnd = sourceDuration
        if CMTimeCompare(afterStart, afterEnd) < 0 {
            let afterRange = CMTimeRange(start: afterStart, end: afterEnd)
            let insertionPoint = CMTimeAdd(startCMTime, replacementDuration)
            try compositionTrack.insertTimeRange(afterRange, of: sourceTrack, at: insertionPoint)
        }

        progress = 0.5

        let outputURL = generateOutputURL(from: sourceURL, suffix: "replaced")
        return try await exportComposition(composition, to: outputURL)
    }

    func saveAs(sourceURL: URL, newName: String) throws -> URL {
        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let recordingsPath = documentsPath.appendingPathComponent(AppConstants.recordingsDirectoryName)
        let fileExtension = sourceURL.pathExtension
        let outputURL = recordingsPath.appendingPathComponent("\(newName).\(fileExtension)")

        try FileManager.default.copyItem(at: sourceURL, to: outputURL)
        return outputURL
    }

    func enhanceRecording(sourceURL: URL) async throws -> URL {
        isProcessing = true
        progress = 0

        defer {
            isProcessing = false
            progress = 1.0
        }

        let asset = AVURLAsset(url: sourceURL)

        let composition = AVMutableComposition()
        guard let compositionTrack = composition.addMutableTrack(
            withMediaType: .audio,
            preferredTrackID: kCMPersistentTrackID_Invalid
        ) else {
            throw AudioEditorError.compositionFailed
        }

        let tracks = try await asset.loadTracks(withMediaType: .audio)
        guard let sourceTrack = tracks.first else {
            throw AudioEditorError.noAudioTrack
        }

        let duration = try await asset.load(.duration)
        let timeRange = CMTimeRange(start: .zero, duration: duration)
        try compositionTrack.insertTimeRange(timeRange, of: sourceTrack, at: .zero)

        let audioMix = AVMutableAudioMix()
        let audioMixParameters = AVMutableAudioMixInputParameters(track: compositionTrack)
        audioMixParameters.setVolume(1.0, at: .zero)
        audioMix.inputParameters = [audioMixParameters]

        progress = 0.3

        let outputURL = generateOutputURL(from: sourceURL, suffix: "enhanced")

        guard let exportSession = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetAppleM4A) else {
            throw AudioEditorError.exportFailed
        }

        exportSession.outputURL = outputURL
        exportSession.outputFileType = .m4a
        exportSession.audioMix = audioMix

        await exportSession.export()

        progress = 1.0

        if exportSession.status == .completed {
            return outputURL
        } else {
            throw exportSession.error ?? AudioEditorError.exportFailed
        }
    }

    func mergeAudioFiles(urls: [URL]) async throws -> URL {
        isProcessing = true
        progress = 0

        defer {
            isProcessing = false
            progress = 1.0
        }

        guard !urls.isEmpty else {
            throw AudioEditorError.noAudioTrack
        }

        let composition = AVMutableComposition()
        guard let compositionTrack = composition.addMutableTrack(
            withMediaType: .audio,
            preferredTrackID: kCMPersistentTrackID_Invalid
        ) else {
            throw AudioEditorError.compositionFailed
        }

        var insertionPoint = CMTime.zero

        for (index, url) in urls.enumerated() {
            let asset = AVURLAsset(url: url)
            let tracks = try await asset.loadTracks(withMediaType: .audio)
            guard let track = tracks.first else { continue }

            let duration = try await asset.load(.duration)
            let timeRange = CMTimeRange(start: .zero, duration: duration)
            try compositionTrack.insertTimeRange(timeRange, of: track, at: insertionPoint)
            insertionPoint = CMTimeAdd(insertionPoint, duration)

            progress = Double(index + 1) / Double(urls.count) * 0.8
        }

        let outputURL = generateOutputURL(from: urls[0], suffix: "merged")
        return try await exportComposition(composition, to: outputURL)
    }

    private func exportComposition(_ composition: AVMutableComposition, to outputURL: URL) async throws -> URL {
        guard let exportSession = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetAppleM4A) else {
            throw AudioEditorError.exportFailed
        }

        exportSession.outputURL = outputURL
        exportSession.outputFileType = .m4a

        await exportSession.export()

        if exportSession.status == .completed {
            progress = 1.0
            return outputURL
        } else {
            throw exportSession.error ?? AudioEditorError.exportFailed
        }
    }

    private func generateOutputURL(from sourceURL: URL, suffix: String) -> URL {
        let directory = sourceURL.deletingLastPathComponent()
        let baseName = sourceURL.deletingPathExtension().lastPathComponent
        let ext = sourceURL.pathExtension.isEmpty ? "m4a" : sourceURL.pathExtension
        let timestamp = Int(Date().timeIntervalSince1970)
        return directory.appendingPathComponent("\(baseName)_\(suffix)_\(timestamp).\(ext)")
    }
}

enum AudioEditorError: LocalizedError {
    case invalidTimeRange
    case exportFailed
    case compositionFailed
    case noAudioTrack
    case fileNotFound

    var errorDescription: String? {
        switch self {
        case .invalidTimeRange: return "Invalid time range specified."
        case .exportFailed: return "Failed to export audio."
        case .compositionFailed: return "Failed to create audio composition."
        case .noAudioTrack: return "No audio track found."
        case .fileNotFound: return "Audio file not found."
        }
    }
}
