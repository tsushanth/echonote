import AVFoundation
import Combine
import Foundation

enum RecorderState {
    case idle
    case recording
    case paused
}

@Observable
final class AudioRecorderService: NSObject {
    private var audioRecorder: AVAudioRecorder?
    private var meterTimer: Timer?
    private var recordingStartTime: Date?
    private var accumulatedTime: TimeInterval = 0

    var state: RecorderState = .idle
    var currentTime: TimeInterval = 0
    var averagePower: Float = 0
    var peakPower: Float = 0
    var meterLevels: [Float] = []

    var isRecording: Bool { state == .recording }
    var isPaused: Bool { state == .paused }

    override init() {
        super.init()
        setupRecordingsDirectory()
    }

    private func setupRecordingsDirectory() {
        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let recordingsPath = documentsPath.appendingPathComponent(AppConstants.recordingsDirectoryName)
        if !FileManager.default.fileExists(atPath: recordingsPath.path) {
            try? FileManager.default.createDirectory(at: recordingsPath, withIntermediateDirectories: true)
        }
    }

    func setupAudioSession() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetooth])
            try session.setActive(true)
        } catch {
            print("Failed to set up audio session: \(error.localizedDescription)")
        }
    }

    func startRecording(
        fileName: String,
        format: AudioFormat = .compressed,
        quality: RecordingQuality = .high,
        isStereo: Bool = false
    ) -> URL? {
        setupAudioSession()

        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let recordingsPath = documentsPath.appendingPathComponent(AppConstants.recordingsDirectoryName)
        let fileURL = recordingsPath.appendingPathComponent("\(fileName).\(format.fileExtension)")

        let channels = isStereo ? AppConstants.Audio.stereoChannels : AppConstants.Audio.defaultChannels

        var settings: [String: Any] = [
            AVSampleRateKey: quality.sampleRate,
            AVNumberOfChannelsKey: channels
        ]

        switch format {
        case .uncompressed:
            settings[AVFormatIDKey] = kAudioFormatLinearPCM
            settings[AVLinearPCMBitDepthKey] = 16
            settings[AVLinearPCMIsFloatKey] = false
            settings[AVLinearPCMIsBigEndianKey] = false
        case .compressed:
            settings[AVFormatIDKey] = kAudioFormatMPEG4AAC
            settings[AVEncoderAudioQualityKey] = AVAudioQuality.high.rawValue
            settings[AVEncoderBitRateKey] = quality.bitRate
        }

        do {
            audioRecorder = try AVAudioRecorder(url: fileURL, settings: settings)
            audioRecorder?.delegate = self
            audioRecorder?.isMeteringEnabled = true
            audioRecorder?.prepareToRecord()
            audioRecorder?.record()

            state = .recording
            recordingStartTime = Date()
            accumulatedTime = 0
            meterLevels = []
            startMeterTimer()

            return fileURL
        } catch {
            print("Failed to start recording: \(error.localizedDescription)")
            return nil
        }
    }

    func pauseRecording() {
        guard state == .recording else { return }
        audioRecorder?.pause()
        accumulatedTime = audioRecorder?.currentTime ?? 0
        state = .paused
        stopMeterTimer()
    }

    func resumeRecording() {
        guard state == .paused else { return }
        audioRecorder?.record()
        state = .recording
        startMeterTimer()
    }

    func stopRecording() -> (url: URL, duration: TimeInterval)? {
        guard state == .recording || state == .paused else { return nil }

        let duration = audioRecorder?.currentTime ?? accumulatedTime
        let url = audioRecorder?.url

        audioRecorder?.stop()
        state = .idle
        stopMeterTimer()

        deactivateAudioSession()

        if let url = url {
            return (url, duration)
        }
        return nil
    }

    func cancelRecording() {
        audioRecorder?.stop()
        if let url = audioRecorder?.url {
            try? FileManager.default.removeItem(at: url)
        }
        state = .idle
        stopMeterTimer()
        deactivateAudioSession()
    }

    private func deactivateAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        } catch {
            print("Failed to deactivate audio session: \(error.localizedDescription)")
        }
    }

    private func startMeterTimer() {
        meterTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
            self?.updateMeters()
        }
    }

    private func stopMeterTimer() {
        meterTimer?.invalidate()
        meterTimer = nil
    }

    private func updateMeters() {
        guard let recorder = audioRecorder, recorder.isRecording else { return }
        recorder.updateMeters()

        currentTime = recorder.currentTime
        averagePower = recorder.averagePower(forChannel: 0)
        peakPower = recorder.peakPower(forChannel: 0)

        let normalizedPower = normalizeDecibels(averagePower)
        meterLevels.append(normalizedPower)
    }

    private func normalizeDecibels(_ decibels: Float) -> Float {
        let minDb: Float = -60.0
        let clampedValue = max(decibels, minDb)
        return (clampedValue - minDb) / abs(minDb)
    }

    func getFileSize(url: URL) -> Int64 {
        let attributes = try? FileManager.default.attributesOfItem(atPath: url.path)
        return attributes?[.size] as? Int64 ?? 0
    }
}

extension AudioRecorderService: AVAudioRecorderDelegate {
    func audioRecorderDidFinishRecording(_ recorder: AVAudioRecorder, successfully flag: Bool) {
        if !flag {
            print("Recording finished unsuccessfully")
        }
        state = .idle
    }

    func audioRecorderEncodeErrorDidOccur(_ recorder: AVAudioRecorder, error: Error?) {
        if let error = error {
            print("Recording encode error: \(error.localizedDescription)")
        }
        state = .idle
    }
}
