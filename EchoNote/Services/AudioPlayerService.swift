import AVFoundation
import Combine
import Foundation

enum PlayerState {
    case idle
    case playing
    case paused
}

@Observable
final class AudioPlayerService: NSObject {
    private var audioPlayer: AVAudioPlayer?
    private var playerTimer: Timer?

    var state: PlayerState = .idle
    var currentTime: TimeInterval = 0
    var duration: TimeInterval = 0
    var playbackRate: Float = 1.0
    var isSkippingSilence: Bool = false
    var averagePower: Float = 0
    var currentFileURL: URL?

    var isPlaying: Bool { state == .playing }
    var isPaused: Bool { state == .paused }
    var progress: Double {
        guard duration > 0 else { return 0 }
        return currentTime / duration
    }

    func loadAudio(url: URL) -> Bool {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default)
            try session.setActive(true)

            audioPlayer = try AVAudioPlayer(contentsOf: url)
            audioPlayer?.delegate = self
            audioPlayer?.enableRate = true
            audioPlayer?.isMeteringEnabled = true
            audioPlayer?.prepareToPlay()
            duration = audioPlayer?.duration ?? 0
            currentTime = 0
            currentFileURL = url
            state = .paused
            return true
        } catch {
            print("Failed to load audio: \(error.localizedDescription)")
            return false
        }
    }

    func play() {
        guard let player = audioPlayer else { return }
        player.rate = playbackRate
        player.play()
        state = .playing
        startPlayerTimer()
    }

    func pause() {
        audioPlayer?.pause()
        state = .paused
        stopPlayerTimer()
    }

    func stop() {
        audioPlayer?.stop()
        audioPlayer?.currentTime = 0
        currentTime = 0
        state = .idle
        stopPlayerTimer()

        do {
            try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        } catch {
            print("Failed to deactivate audio session: \(error.localizedDescription)")
        }
    }

    func togglePlayPause() {
        if isPlaying {
            pause()
        } else {
            play()
        }
    }

    func seek(to time: TimeInterval) {
        let clampedTime = max(0, min(time, duration))
        audioPlayer?.currentTime = clampedTime
        currentTime = clampedTime
    }

    func seekToProgress(_ progress: Double) {
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
        if isPlaying {
            audioPlayer?.rate = clampedRate
        }
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

    private func startPlayerTimer() {
        playerTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
            self?.updatePlayback()
        }
    }

    private func stopPlayerTimer() {
        playerTimer?.invalidate()
        playerTimer = nil
    }

    private func updatePlayback() {
        guard let player = audioPlayer, player.isPlaying else { return }
        player.updateMeters()
        currentTime = player.currentTime
        averagePower = player.averagePower(forChannel: 0)

        if isSkippingSilence {
            handleSilenceSkipping(player: player)
        }
    }

    private func handleSilenceSkipping(player: AVAudioPlayer) {
        let normalizedPower = normalizeDecibels(player.averagePower(forChannel: 0))
        if normalizedPower < AppConstants.Audio.silenceThreshold {
            let scanAhead = min(player.currentTime + AppConstants.Audio.silenceMinDuration, player.duration)
            player.currentTime = scanAhead
            currentTime = scanAhead
        }
    }

    private func normalizeDecibels(_ decibels: Float) -> Float {
        let minDb: Float = -60.0
        let clampedValue = max(decibels, minDb)
        return (clampedValue - minDb) / abs(minDb)
    }

    func generateWaveformData(url: URL, samplesCount: Int = 200) -> [Float] {
        guard let audioFile = try? AVAudioFile(forReading: url) else { return [] }

        let totalFrames = AVAudioFrameCount(audioFile.length)
        let framesPerSample = max(1, totalFrames / AVAudioFrameCount(samplesCount))

        guard let format = AVAudioFormat(standardFormatWithSampleRate: audioFile.fileFormat.sampleRate, channels: 1) else {
            return []
        }

        var samples: [Float] = []

        for i in 0..<samplesCount {
            let startFrame = AVAudioFramePosition(Int(framesPerSample) * i)
            guard startFrame < audioFile.length else { break }

            audioFile.framePosition = startFrame
            let framesToRead = min(framesPerSample, AVAudioFrameCount(audioFile.length - startFrame))

            guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: framesToRead) else { continue }

            do {
                try audioFile.read(into: buffer, frameCount: framesToRead)
            } catch {
                samples.append(0)
                continue
            }

            guard let channelData = buffer.floatChannelData?[0] else {
                samples.append(0)
                continue
            }

            var sum: Float = 0
            for frame in 0..<Int(buffer.frameLength) {
                sum += abs(channelData[frame])
            }
            let avg = sum / Float(buffer.frameLength)
            samples.append(avg)
        }

        let maxSample = samples.max() ?? 1.0
        if maxSample > 0 {
            samples = samples.map { $0 / maxSample }
        }

        return samples
    }
}

extension AudioPlayerService: AVAudioPlayerDelegate {
    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        state = .paused
        currentTime = 0
        stopPlayerTimer()
    }

    func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        if let error = error {
            print("Player decode error: \(error.localizedDescription)")
        }
        state = .idle
        stopPlayerTimer()
    }
}
