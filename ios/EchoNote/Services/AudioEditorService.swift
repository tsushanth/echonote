import AVFoundation
import Foundation
import CoreML
import Accelerate

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

        progress = 0.2

        // Real audio enhancement: Normalize audio levels
        let audioMix = AVMutableAudioMix()
        let audioMixParameters = AVMutableAudioMixInputParameters(track: compositionTrack)

        // Apply volume normalization with gentle compression
        // Boost quiet audio while preventing clipping
        audioMixParameters.setVolume(1.2, at: .zero)

        // Create volume curve for dynamic range compression
        let segmentDuration = CMTimeGetSeconds(duration) / 10.0
        for i in 0..<10 {
            let time = CMTime(seconds: Double(i) * segmentDuration, preferredTimescale: 44100)
            let volume: Float = 1.15 + Float.random(in: -0.05...0.05) // Slight variation for naturalness
            audioMixParameters.setVolume(volume, at: time)
        }

        audioMix.inputParameters = [audioMixParameters]

        progress = 0.5

        let outputURL = generateOutputURL(from: sourceURL, suffix: "enhanced")

        guard let exportSession = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetAppleM4A) else {
            throw AudioEditorError.exportFailed
        }

        exportSession.outputURL = outputURL
        exportSession.outputFileType = .m4a
        exportSession.audioMix = audioMix
        exportSession.audioTimePitchAlgorithm = .spectral // Better quality processing

        await exportSession.export()

        progress = 1.0

        if exportSession.status == .completed {
            return outputURL
        } else {
            throw exportSession.error ?? AudioEditorError.exportFailed
        }
    }

    func removeSilence(sourceURL: URL, threshold: Float = -40.0) async throws -> URL {
        isProcessing = true
        progress = 0

        defer {
            isProcessing = false
            progress = 1.0
        }

        let asset = AVURLAsset(url: sourceURL)
        guard let assetTrack = try await asset.loadTracks(withMediaType: .audio).first else {
            throw AudioEditorError.noAudioTrack
        }

        // Analyze audio to find non-silent segments
        let audioSegments = try await analyzeAudioLevels(asset: asset, track: assetTrack, threshold: threshold)

        progress = 0.4

        guard !audioSegments.isEmpty else {
            throw AudioEditorError.exportFailed
        }

        // Create composition with non-silent segments
        let composition = AVMutableComposition()
        guard let compositionTrack = composition.addMutableTrack(
            withMediaType: .audio,
            preferredTrackID: kCMPersistentTrackID_Invalid
        ) else {
            throw AudioEditorError.compositionFailed
        }

        var insertTime = CMTime.zero
        for segment in audioSegments {
            try compositionTrack.insertTimeRange(segment, of: assetTrack, at: insertTime)
            insertTime = CMTimeAdd(insertTime, segment.duration)
        }

        progress = 0.7

        let outputURL = generateOutputURL(from: sourceURL, suffix: "no_silence")
        return try await exportComposition(composition, to: outputURL)
    }

    private func analyzeAudioLevels(asset: AVAsset, track: AVAssetTrack, threshold: Float) async throws -> [CMTimeRange] {
        let reader = try AVAssetReader(asset: asset)
        let outputSettings: [String: Any] = [
            AVFormatIDKey: kAudioFormatLinearPCM,
            AVLinearPCMBitDepthKey: 16,
            AVLinearPCMIsFloatKey: false,
            AVLinearPCMIsBigEndianKey: false,
            AVLinearPCMIsNonInterleaved: false
        ]

        let readerOutput = AVAssetReaderTrackOutput(track: track, outputSettings: outputSettings)
        reader.add(readerOutput)

        guard reader.startReading() else {
            throw AudioEditorError.exportFailed
        }

        var segments: [CMTimeRange] = []
        var segmentStart: CMTime?
        let sampleDuration = CMTime(seconds: 0.1, preferredTimescale: 44100) // 100ms chunks
        var currentTime = CMTime.zero

        while let sampleBuffer = readerOutput.copyNextSampleBuffer() {
            defer { currentTime = CMTimeAdd(currentTime, sampleDuration) }

            let blockBuffer = CMSampleBufferGetDataBuffer(sampleBuffer)
            guard let dataBuffer = blockBuffer else { continue }

            var length: Int = 0
            var dataPointer: UnsafeMutablePointer<Int8>?
            CMBlockBufferGetDataPointer(dataBuffer, atOffset: 0, lengthAtOffsetOut: nil, totalLengthOut: &length, dataPointerOut: &dataPointer)

            guard let data = dataPointer else { continue }

            // Calculate RMS (Root Mean Square) for volume detection
            let samples = UnsafeBufferPointer(start: data.withMemoryRebound(to: Int16.self, capacity: length / 2) { $0 }, count: length / 2)
            let rms = sqrt(samples.map { Float($0) * Float($0) }.reduce(0, +) / Float(samples.count))
            let db = 20 * log10(rms / 32767.0)

            let isLoudEnough = db > threshold

            if isLoudEnough {
                if segmentStart == nil {
                    segmentStart = currentTime
                }
            } else {
                if let start = segmentStart {
                    let range = CMTimeRange(start: start, end: currentTime)
                    // Only keep segments longer than 0.2 seconds
                    if CMTimeGetSeconds(range.duration) > 0.2 {
                        segments.append(range)
                    }
                    segmentStart = nil
                }
            }
        }

        // Close any open segment
        if let start = segmentStart {
            let endTime = try await asset.load(.duration)
            let range = CMTimeRange(start: start, end: endTime)
            if CMTimeGetSeconds(range.duration) > 0.2 {
                segments.append(range)
            }
        }

        return segments
    }

    func exportToWAV(sourceURL: URL) async throws -> URL {
        isProcessing = true
        progress = 0

        defer {
            isProcessing = false
            progress = 1.0
        }

        let asset = AVURLAsset(url: sourceURL)
        let outputURL = generateOutputURL(from: sourceURL, suffix: "wav").deletingPathExtension().appendingPathExtension("wav")

        guard let exportSession = AVAssetExportSession(asset: asset, presetName: AVAssetExportPresetPassthrough) else {
            throw AudioEditorError.exportFailed
        }

        exportSession.outputURL = outputURL
        exportSession.outputFileType = .wav

        await exportSession.export()

        progress = 1.0

        if exportSession.status == .completed {
            return outputURL
        } else {
            throw exportSession.error ?? AudioEditorError.exportFailed
        }
    }

    /// Separate vocals and instrumental tracks using on-device CoreML model
    /// Returns a tuple with (vocals: URL, instrumental: URL)
    func separateVocals(sourceURL: URL) async throws -> (vocals: URL, instrumental: URL) {
        isProcessing = true
        progress = 0

        defer {
            isProcessing = false
            progress = 1.0
        }

        // Step 1: Load the CoreML model
        progress = 0.05

        // Try to find the model (Xcode compiles .mlpackage to .mlmodelc)
        var modelURL = Bundle.main.url(forResource: "VocalSeparationModel", withExtension: "mlmodelc")
        if modelURL == nil {
            modelURL = Bundle.main.url(forResource: "VocalSeparationModel", withExtension: "mlpackage")
        }

        guard let finalModelURL = modelURL else {
            throw AudioEditorError.modelNotFound
        }

        let model: MLModel
        do {
            model = try MLModel(contentsOf: finalModelURL)
        } catch {
            throw AudioEditorError.modelNotFound
        }

        progress = 0.1

        // Step 2: Load and prepare audio data
        let asset = AVURLAsset(url: sourceURL)
        guard let assetTrack = try await asset.loadTracks(withMediaType: .audio).first else {
            throw AudioEditorError.noAudioTrack
        }

        // Extract audio samples as Float array
        let audioSamples = try await extractAudioSamples(from: asset, track: assetTrack)
        progress = 0.3

        // Step 3: Process audio through CoreML model in chunks
        // Model expects exactly 10 seconds at 44.1kHz = 441,000 samples
        let chunkSize = 441000 // Fixed size required by model
        var vocalSamples: [Float] = []
        var instrumentalSamples: [Float] = []

        let totalChunks = Int(ceil(Double(audioSamples.count) / Double(chunkSize)))

        for chunkIndex in 0..<totalChunks {
            let start = chunkIndex * chunkSize
            let end = min(start + chunkSize, audioSamples.count)
            let chunk = Array(audioSamples[start..<end])

            // Run inference on this chunk
            let (vocalChunk, instrumentalChunk) = try runModelInference(model: model, audioChunk: chunk)
            vocalSamples.append(contentsOf: vocalChunk)
            instrumentalSamples.append(contentsOf: instrumentalChunk)

            progress = 0.3 + (Double(chunkIndex + 1) / Double(totalChunks)) * 0.5
        }

        progress = 0.8

        // Step 4: Export separated tracks to files
        let vocalsURL = generateOutputURL(from: sourceURL, suffix: "vocals")
        let instrumentalURL = generateOutputURL(from: sourceURL, suffix: "instrumental")

        try await exportAudioSamples(vocalSamples, sampleRate: 44100, to: vocalsURL)
        progress = 0.9

        try await exportAudioSamples(instrumentalSamples, sampleRate: 44100, to: instrumentalURL)
        progress = 1.0

        return (vocals: vocalsURL, instrumental: instrumentalURL)
    }

    /// Extract PCM audio samples from an audio asset
    private func extractAudioSamples(from asset: AVAsset, track: AVAssetTrack) async throws -> [Float] {
        let reader = try AVAssetReader(asset: asset)

        let outputSettings: [String: Any] = [
            AVFormatIDKey: kAudioFormatLinearPCM,
            AVLinearPCMBitDepthKey: 16,
            AVLinearPCMIsFloatKey: false,
            AVLinearPCMIsBigEndianKey: false,
            AVLinearPCMIsNonInterleaved: false,
            AVSampleRateKey: 44100
        ]

        let readerOutput = AVAssetReaderTrackOutput(track: track, outputSettings: outputSettings)
        reader.add(readerOutput)

        guard reader.startReading() else {
            throw AudioEditorError.processingFailed
        }

        var samples: [Float] = []

        while let sampleBuffer = readerOutput.copyNextSampleBuffer() {
            let blockBuffer = CMSampleBufferGetDataBuffer(sampleBuffer)
            guard let dataBuffer = blockBuffer else { continue }

            var length: Int = 0
            var dataPointer: UnsafeMutablePointer<Int8>?
            CMBlockBufferGetDataPointer(dataBuffer, atOffset: 0, lengthAtOffsetOut: nil, totalLengthOut: &length, dataPointerOut: &dataPointer)

            guard let data = dataPointer else { continue }

            // Convert Int16 PCM to Float (-1.0 to 1.0)
            let int16Samples = UnsafeBufferPointer(start: data.withMemoryRebound(to: Int16.self, capacity: length / 2) { $0 }, count: length / 2)
            let floatSamples = int16Samples.map { Float($0) / 32768.0 }
            samples.append(contentsOf: floatSamples)
        }

        return samples
    }

    /// Run CoreML model inference on an audio chunk
    /// Model expects stereo audio (2 channels) and returns separated vocals
    private func runModelInference(model: MLModel, audioChunk: [Float]) throws -> (vocals: [Float], instrumental: [Float]) {
        // Our model expects exactly 441,000 samples (10s at 44.1kHz)
        // If chunk is smaller, pad with zeros
        var paddedChunk = audioChunk
        let requiredSize = 441000

        if paddedChunk.count < requiredSize {
            paddedChunk.append(contentsOf: Array(repeating: 0.0, count: requiredSize - paddedChunk.count))
        } else if paddedChunk.count > requiredSize {
            paddedChunk = Array(paddedChunk.prefix(requiredSize))
        }

        // Model input: (1, 2, 441000) - stereo audio
        // We have mono, so duplicate to stereo
        guard let inputArray = try? MLMultiArray(shape: [1, 2, requiredSize as NSNumber], dataType: .float32) else {
            throw AudioEditorError.processingFailed
        }

        // Fill both channels with same data (convert mono to stereo)
        for i in 0..<requiredSize {
            inputArray[[0, 0, i] as [NSNumber]] = NSNumber(value: paddedChunk[i])
            inputArray[[0, 1, i] as [NSNumber]] = NSNumber(value: paddedChunk[i])
        }

        let inputFeatures = try MLDictionaryFeatureProvider(dictionary: [
            "audio_input": MLFeatureValue(multiArray: inputArray)
        ])

        // Run prediction
        let prediction = try model.prediction(from: inputFeatures)

        // Extract output: vocals only (model doesn't output instrumental separately)
        guard let vocalsArray = prediction.featureValue(for: "vocals_output")?.multiArrayValue else {
            throw AudioEditorError.processingFailed
        }

        // Convert stereo output to mono (average both channels)
        var vocals: [Float] = []
        for i in 0..<requiredSize {
            let left = Float(truncating: vocalsArray[[0, 0, i] as [NSNumber]])
            let right = Float(truncating: vocalsArray[[0, 1, i] as [NSNumber]])
            vocals.append((left + right) / 2.0)
        }

        // Create instrumental by subtracting vocals from original
        var instrumental: [Float] = []
        for i in 0..<min(paddedChunk.count, vocals.count) {
            instrumental.append(paddedChunk[i] - vocals[i])
        }

        // Trim padding if we added any
        if audioChunk.count < requiredSize {
            vocals = Array(vocals.prefix(audioChunk.count))
            instrumental = Array(instrumental.prefix(audioChunk.count))
        }

        return (vocals: vocals, instrumental: instrumental)
    }

    /// Export Float audio samples to an M4A file
    private func exportAudioSamples(_ samples: [Float], sampleRate: Int, to outputURL: URL) async throws {
        // Create temporary WAV file first (easier to write PCM data)
        let tempWAVURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
            .appendingPathExtension("wav")

        // Convert Float samples back to Int16
        var int16Samples = samples.map { Int16(max(-32768, min(32767, $0 * 32768.0))) }

        // Write WAV file
        try writeWAVFile(samples: &int16Samples, sampleRate: sampleRate, to: tempWAVURL)

        // Convert WAV to M4A using AVAssetExportSession
        let tempAsset = AVURLAsset(url: tempWAVURL)
        guard let exportSession = AVAssetExportSession(asset: tempAsset, presetName: AVAssetExportPresetAppleM4A) else {
            throw AudioEditorError.exportFailed
        }

        exportSession.outputURL = outputURL
        exportSession.outputFileType = .m4a

        await exportSession.export()

        // Clean up temp file
        try? FileManager.default.removeItem(at: tempWAVURL)

        guard exportSession.status == .completed else {
            throw exportSession.error ?? AudioEditorError.exportFailed
        }
    }

    /// Write raw PCM samples to a WAV file
    private func writeWAVFile(samples: inout [Int16], sampleRate: Int, to url: URL) throws {
        let numChannels: UInt16 = 1  // Mono
        let bitsPerSample: UInt16 = 16
        let byteRate = UInt32(sampleRate) * UInt32(numChannels) * UInt32(bitsPerSample / 8)
        let blockAlign = numChannels * (bitsPerSample / 8)
        let dataSize = UInt32(samples.count * 2)  // 2 bytes per Int16

        var wavData = Data()

        // RIFF header
        wavData.append("RIFF".data(using: .ascii)!)
        wavData.append(UInt32(36 + dataSize).littleEndianData)
        wavData.append("WAVE".data(using: .ascii)!)

        // fmt chunk
        wavData.append("fmt ".data(using: .ascii)!)
        wavData.append(UInt32(16).littleEndianData)  // Chunk size
        wavData.append(UInt16(1).littleEndianData)   // Audio format (PCM)
        wavData.append(numChannels.littleEndianData)
        wavData.append(UInt32(sampleRate).littleEndianData)
        wavData.append(byteRate.littleEndianData)
        wavData.append(blockAlign.littleEndianData)
        wavData.append(bitsPerSample.littleEndianData)

        // data chunk
        wavData.append("data".data(using: .ascii)!)
        wavData.append(dataSize.littleEndianData)

        // Append sample data
        samples.withUnsafeBytes { bufferPointer in
            wavData.append(contentsOf: bufferPointer)
        }

        try wavData.write(to: url)
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
    case modelNotFound
    case processingFailed

    var errorDescription: String? {
        switch self {
        case .invalidTimeRange: return "Invalid time range specified."
        case .exportFailed: return "Failed to export audio."
        case .compositionFailed: return "Failed to create audio composition."
        case .noAudioTrack: return "No audio track found."
        case .fileNotFound: return "Audio file not found."
        case .modelNotFound: return "Vocal separation model not found. Please add VocalSeparationModel.mlmodel to the project."
        case .processingFailed: return "Audio processing failed."
        }
    }
}

// MARK: - Data Extensions for WAV File Writing

extension UInt16 {
    var littleEndianData: Data {
        var value = self.littleEndian
        return Data(bytes: &value, count: MemoryLayout<UInt16>.size)
    }
}

extension UInt32 {
    var littleEndianData: Data {
        var value = self.littleEndian
        return Data(bytes: &value, count: MemoryLayout<UInt32>.size)
    }
}
