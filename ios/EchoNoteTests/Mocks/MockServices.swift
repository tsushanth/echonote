import Foundation
@testable import EchoNote

// MARK: - Mock Audio Player Service

/// A testable mock of AudioPlayerService for unit testing playback logic
@Observable
final class MockAudioPlayerService {
    var state: PlayerState = .idle
    var currentTime: TimeInterval = 0
    var duration: TimeInterval = 100 // Default duration for testing
    var playbackRate: Float = 1.0
    var isSkippingSilence: Bool = false
    var averagePower: Float = -20
    var currentFileURL: URL?

    var isPlaying: Bool { state == .playing }
    var isPaused: Bool { state == .paused }
    var progress: Double {
        guard duration > 0 else { return 0 }
        return currentTime / duration
    }

    // Test configuration
    var shouldFailLoad: Bool = false
    var waveformDataToReturn: [Float] = []

    // Call tracking
    var loadAudioCalled = false
    var playCalled = false
    var pauseCalled = false
    var stopCalled = false
    var seekCalled = false
    var lastSeekTime: TimeInterval = 0
    var lastSeekProgress: Double = 0

    func loadAudio(url: URL) -> Bool {
        loadAudioCalled = true
        currentFileURL = url

        if shouldFailLoad {
            return false
        }

        state = .paused
        currentTime = 0
        return true
    }

    func play() {
        playCalled = true
        state = .playing
    }

    func pause() {
        pauseCalled = true
        state = .paused
    }

    func stop() {
        stopCalled = true
        state = .idle
        currentTime = 0
    }

    func togglePlayPause() {
        if isPlaying {
            pause()
        } else {
            play()
        }
    }

    func seek(to time: TimeInterval) {
        seekCalled = true
        lastSeekTime = time
        let clampedTime = max(0, min(time, duration))
        currentTime = clampedTime
    }

    func seekToProgress(_ progress: Double) {
        lastSeekProgress = progress
        let time = progress * duration
        seek(to: time)
    }

    func skipForward() {
        let newTime = currentTime + AppConstants.Audio.skipForwardInterval
        seek(to: newTime)
    }

    func skipBackward() {
        let newTime = currentTime - AppConstants.Audio.skipBackwardInterval
        seek(to: newTime)
    }

    func setPlaybackRate(_ rate: Float) {
        let clampedRate = max(AppConstants.Audio.minPlaybackRate, min(rate, AppConstants.Audio.maxPlaybackRate))
        playbackRate = clampedRate
    }

    func increaseRate() {
        let newRate = playbackRate + AppConstants.Audio.playbackRateStep
        setPlaybackRate(newRate)
    }

    func decreaseRate() {
        let newRate = playbackRate - AppConstants.Audio.playbackRateStep
        setPlaybackRate(newRate)
    }

    func toggleSkipSilence() {
        isSkippingSilence.toggle()
    }

    func generateWaveformData(url: URL, samplesCount: Int = 200) -> [Float] {
        return waveformDataToReturn
    }

    func reset() {
        state = .idle
        currentTime = 0
        duration = 100
        playbackRate = 1.0
        isSkippingSilence = false
        loadAudioCalled = false
        playCalled = false
        pauseCalled = false
        stopCalled = false
        seekCalled = false
        shouldFailLoad = false
    }
}

// MARK: - Mock Audio Recorder Service

/// A testable mock of AudioRecorderService for unit testing recording logic
@Observable
final class MockAudioRecorderService {
    var state: RecorderState = .idle
    var currentTime: TimeInterval = 0
    var averagePower: Float = -30
    var peakPower: Float = -20
    var meterLevels: [Float] = []

    var isRecording: Bool { state == .recording }
    var isPaused: Bool { state == .paused }

    // Test configuration
    var shouldFailStart: Bool = false
    var simulatedFileURL: URL?
    var simulatedDuration: TimeInterval = 60
    var simulatedFileSize: Int64 = 1024 * 1024

    // Call tracking
    var startRecordingCalled = false
    var pauseRecordingCalled = false
    var resumeRecordingCalled = false
    var stopRecordingCalled = false
    var cancelRecordingCalled = false
    var setupAudioSessionCalled = false
    var lastRecordingFormat: AudioFormat?
    var lastRecordingQuality: RecordingQuality?
    var lastRecordingStereo: Bool?

    func setupAudioSession() {
        setupAudioSessionCalled = true
    }

    func startRecording(
        fileName: String,
        format: AudioFormat = .compressed,
        quality: RecordingQuality = .high,
        isStereo: Bool = false
    ) -> URL? {
        startRecordingCalled = true
        lastRecordingFormat = format
        lastRecordingQuality = quality
        lastRecordingStereo = isStereo

        if shouldFailStart {
            return nil
        }

        state = .recording
        meterLevels = []

        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        simulatedFileURL = documentsPath.appendingPathComponent("\(fileName).\(format.fileExtension)")

        return simulatedFileURL
    }

    func pauseRecording() {
        pauseRecordingCalled = true
        guard state == .recording else { return }
        state = .paused
    }

    func resumeRecording() {
        resumeRecordingCalled = true
        guard state == .paused else { return }
        state = .recording
    }

    func stopRecording() -> (url: URL, duration: TimeInterval)? {
        stopRecordingCalled = true
        guard state == .recording || state == .paused else { return nil }

        let result = simulatedFileURL.map { ($0, simulatedDuration) }
        state = .idle
        return result
    }

    func cancelRecording() {
        cancelRecordingCalled = true
        state = .idle
        simulatedFileURL = nil
    }

    func getFileSize(url: URL) -> Int64 {
        return simulatedFileSize
    }

    func reset() {
        state = .idle
        currentTime = 0
        meterLevels = []
        shouldFailStart = false
        startRecordingCalled = false
        pauseRecordingCalled = false
        resumeRecordingCalled = false
        stopRecordingCalled = false
        cancelRecordingCalled = false
        setupAudioSessionCalled = false
        simulatedFileURL = nil
    }
}

// MARK: - Mock Audio Editor Service

/// A testable mock of AudioEditorService for unit testing editing operations
@Observable
final class MockAudioEditorService {
    var isProcessing: Bool = false
    var progress: Double = 0

    // Test configuration
    var shouldFailTrim: Bool = false
    var shouldFailSaveAs: Bool = false
    var shouldFailEnhance: Bool = false
    var shouldFailMerge: Bool = false
    var errorToThrow: AudioEditorError = .exportFailed

    // Call tracking
    var trimCalled = false
    var saveAsCalled = false
    var enhanceCalled = false
    var mergeCalled = false
    var lastTrimStartTime: TimeInterval = 0
    var lastTrimEndTime: TimeInterval = 0
    var lastSaveAsName: String = ""
    var lastMergeURLs: [URL] = []

    func trimAudio(sourceURL: URL, startTime: TimeInterval, endTime: TimeInterval) async throws -> URL {
        trimCalled = true
        lastTrimStartTime = startTime
        lastTrimEndTime = endTime
        isProcessing = true
        defer { isProcessing = false }

        if shouldFailTrim {
            throw errorToThrow
        }

        progress = 1.0
        return sourceURL.deletingPathExtension().appendingPathExtension("trimmed.m4a")
    }

    func saveAs(sourceURL: URL, newName: String) throws -> URL {
        saveAsCalled = true
        lastSaveAsName = newName

        if shouldFailSaveAs {
            throw errorToThrow
        }

        let directory = sourceURL.deletingLastPathComponent()
        return directory.appendingPathComponent("\(newName).\(sourceURL.pathExtension)")
    }

    func enhanceRecording(sourceURL: URL) async throws -> URL {
        enhanceCalled = true
        isProcessing = true
        defer { isProcessing = false }

        if shouldFailEnhance {
            throw errorToThrow
        }

        progress = 1.0
        return sourceURL.deletingPathExtension().appendingPathExtension("enhanced.m4a")
    }

    func mergeAudioFiles(urls: [URL]) async throws -> URL {
        mergeCalled = true
        lastMergeURLs = urls
        isProcessing = true
        defer { isProcessing = false }

        if shouldFailMerge {
            throw errorToThrow
        }

        guard let firstURL = urls.first else {
            throw AudioEditorError.noAudioTrack
        }

        progress = 1.0
        return firstURL.deletingPathExtension().appendingPathExtension("merged.m4a")
    }

    func reset() {
        isProcessing = false
        progress = 0
        shouldFailTrim = false
        shouldFailSaveAs = false
        shouldFailEnhance = false
        shouldFailMerge = false
        trimCalled = false
        saveAsCalled = false
        enhanceCalled = false
        mergeCalled = false
    }
}

// MARK: - Mock Transcription Service

/// A testable mock of TranscriptionService for unit testing transcription logic
@Observable
final class MockTranscriptionService {
    var isTranscribing: Bool = false
    var progress: Double = 0
    var isAvailable: Bool = true

    // Test configuration
    var shouldFail: Bool = false
    var transcriptToReturn: String = "This is a mock transcript of the audio recording."
    var errorToThrow: TranscriptionError = .recognizerUnavailable

    // Call tracking
    var transcribeCalled = false
    var requestPermissionCalled = false
    var permissionGranted = true

    func requestPermission() async -> Bool {
        requestPermissionCalled = true
        return permissionGranted
    }

    func transcribe(audioURL: URL) async throws -> String {
        transcribeCalled = true
        isTranscribing = true

        defer {
            isTranscribing = false
            progress = 1.0
        }

        if shouldFail {
            throw errorToThrow
        }

        progress = 0.5
        return transcriptToReturn
    }

    func reset() {
        isTranscribing = false
        progress = 0
        shouldFail = false
        transcribeCalled = false
        requestPermissionCalled = false
    }
}

// MARK: - Mock Location Service

/// A testable mock of LocationService for unit testing location features
@Observable
final class MockLocationService {
    var currentLocationName: String?
    var authorizationStatus: Int = 0 // CLAuthorizationStatus.notDetermined equivalent

    // Test configuration
    var locationNameToReturn: String? = "Test Location"

    // Call tracking
    var requestPermissionCalled = false
    var getCurrentLocationNameCalled = false

    func requestPermission() {
        requestPermissionCalled = true
        authorizationStatus = 3 // authorizedWhenInUse
    }

    func getCurrentLocationName() async -> String? {
        getCurrentLocationNameCalled = true
        currentLocationName = locationNameToReturn
        return locationNameToReturn
    }

    func reset() {
        currentLocationName = nil
        authorizationStatus = 0
        requestPermissionCalled = false
        getCurrentLocationNameCalled = false
        locationNameToReturn = "Test Location"
    }
}

// MARK: - Transcription Error (for mock)

enum TranscriptionError: LocalizedError {
    case notAuthorized
    case recognizerUnavailable
    case noResult

    var errorDescription: String? {
        switch self {
        case .notAuthorized:
            return "Speech recognition not authorized"
        case .recognizerUnavailable:
            return "Speech recognizer unavailable"
        case .noResult:
            return "No transcription result"
        }
    }
}
