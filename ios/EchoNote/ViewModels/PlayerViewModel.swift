import Foundation
import SwiftData

@Observable
final class PlayerViewModel {
    let playerService = AudioPlayerService()

    var currentRecording: Recording?
    var waveformSamples: [Float] = []
    var showPlaybackSheet: Bool = false

    var isPlaying: Bool { playerService.isPlaying }
    var isPaused: Bool { playerService.isPaused }
    var currentTime: TimeInterval { playerService.currentTime }
    var duration: TimeInterval { playerService.duration }
    var progress: Double { playerService.progress }
    var playbackRate: Float { playerService.playbackRate }
    var isSkippingSilence: Bool { playerService.isSkippingSilence }

    func loadRecording(_ recording: Recording) {
        currentRecording = recording
        let url = recording.actualFileURL

        guard playerService.loadAudio(url: url) else {
            return
        }

        waveformSamples = playerService.generateWaveformData(url: url)
        showPlaybackSheet = true
    }

    func play() {
        playerService.play()
    }

    func pause() {
        playerService.pause()
    }

    func togglePlayPause() {
        playerService.togglePlayPause()
    }

    func stop() {
        playerService.stop()
        showPlaybackSheet = false
        currentRecording = nil
    }

    func seek(to time: TimeInterval) {
        playerService.seek(to: time)
    }

    func seekToProgress(_ progress: Double) {
        playerService.seekToProgress(progress)
    }

    func skipForward() {
        playerService.skipForward()
    }

    func skipBackward() {
        playerService.skipBackward()
    }

    func setPlaybackRate(_ rate: Float) {
        playerService.setPlaybackRate(rate)
    }

    func increaseRate() {
        playerService.increaseRate()
    }

    func decreaseRate() {
        playerService.decreaseRate()
    }

    func toggleSkipSilence() {
        playerService.toggleSkipSilence()
    }
}
