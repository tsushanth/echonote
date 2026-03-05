import XCTest
@testable import EchoNote

// MARK: - Audio Player Service Tests

/// Tests for AudioPlayerService business logic with mock dependencies
final class AudioPlayerServiceTests: XCTestCase {

    var sut: MockAudioPlayerService!

    override func setUp() {
        super.setUp()
        sut = MockAudioPlayerService()
    }

    override func tearDown() {
        sut.reset()
        sut = nil
        super.tearDown()
    }

    // MARK: - Initial State Tests

    func testInitialState_IsIdle() {
        XCTAssertEqual(sut.state, .idle)
        XCTAssertFalse(sut.isPlaying)
        XCTAssertFalse(sut.isPaused)
        XCTAssertEqual(sut.currentTime, 0)
        XCTAssertEqual(sut.playbackRate, 1.0)
        XCTAssertFalse(sut.isSkippingSilence)
    }

    // MARK: - Load Audio Tests

    func testLoadAudio_Success_TransitionsToPaused() {
        let url = URL(fileURLWithPath: "/test/audio.m4a")

        let result = sut.loadAudio(url: url)

        XCTAssertTrue(result)
        XCTAssertTrue(sut.loadAudioCalled)
        XCTAssertEqual(sut.state, .paused)
        XCTAssertEqual(sut.currentTime, 0)
        XCTAssertEqual(sut.currentFileURL, url)
    }

    func testLoadAudio_Failure_ReturnsfalseAndStaysIdle() {
        sut.shouldFailLoad = true
        let url = URL(fileURLWithPath: "/test/audio.m4a")

        let result = sut.loadAudio(url: url)

        XCTAssertFalse(result)
        XCTAssertEqual(sut.state, .idle)
    }

    // MARK: - Play/Pause Tests

    func testPlay_TransitionsToPlaying() {
        let url = URL(fileURLWithPath: "/test/audio.m4a")
        _ = sut.loadAudio(url: url)

        sut.play()

        XCTAssertTrue(sut.playCalled)
        XCTAssertEqual(sut.state, .playing)
        XCTAssertTrue(sut.isPlaying)
        XCTAssertFalse(sut.isPaused)
    }

    func testPause_TransitionsToPaused() {
        let url = URL(fileURLWithPath: "/test/audio.m4a")
        _ = sut.loadAudio(url: url)
        sut.play()

        sut.pause()

        XCTAssertTrue(sut.pauseCalled)
        XCTAssertEqual(sut.state, .paused)
        XCTAssertFalse(sut.isPlaying)
        XCTAssertTrue(sut.isPaused)
    }

    func testTogglePlayPause_FromPaused_PlaysAudio() {
        let url = URL(fileURLWithPath: "/test/audio.m4a")
        _ = sut.loadAudio(url: url)
        XCTAssertTrue(sut.isPaused)

        sut.togglePlayPause()

        XCTAssertTrue(sut.isPlaying)
    }

    func testTogglePlayPause_FromPlaying_PausesAudio() {
        let url = URL(fileURLWithPath: "/test/audio.m4a")
        _ = sut.loadAudio(url: url)
        sut.play()
        XCTAssertTrue(sut.isPlaying)

        sut.togglePlayPause()

        XCTAssertTrue(sut.isPaused)
    }

    // MARK: - Stop Tests

    func testStop_ResetsToIdle() {
        let url = URL(fileURLWithPath: "/test/audio.m4a")
        _ = sut.loadAudio(url: url)
        sut.play()
        sut.currentTime = 50

        sut.stop()

        XCTAssertTrue(sut.stopCalled)
        XCTAssertEqual(sut.state, .idle)
        XCTAssertEqual(sut.currentTime, 0)
    }

    // MARK: - Seek Tests

    func testSeek_UpdatesCurrentTime() {
        let url = URL(fileURLWithPath: "/test/audio.m4a")
        _ = sut.loadAudio(url: url)
        sut.duration = 100

        sut.seek(to: 50)

        XCTAssertTrue(sut.seekCalled)
        XCTAssertEqual(sut.currentTime, 50)
        XCTAssertEqual(sut.lastSeekTime, 50)
    }

    func testSeek_ClampsToValidRange() {
        let url = URL(fileURLWithPath: "/test/audio.m4a")
        _ = sut.loadAudio(url: url)
        sut.duration = 100

        sut.seek(to: 150) // Beyond duration
        XCTAssertEqual(sut.currentTime, 100)

        sut.seek(to: -10) // Negative
        XCTAssertEqual(sut.currentTime, 0)
    }

    func testSeekToProgress_ConvertsProgressToTime() {
        let url = URL(fileURLWithPath: "/test/audio.m4a")
        _ = sut.loadAudio(url: url)
        sut.duration = 100

        sut.seekToProgress(0.5)

        XCTAssertEqual(sut.currentTime, 50)
        XCTAssertEqual(sut.lastSeekProgress, 0.5)
    }

    // MARK: - Skip Forward/Backward Tests

    func testSkipForward_AdvancesBySkipInterval() {
        let url = URL(fileURLWithPath: "/test/audio.m4a")
        _ = sut.loadAudio(url: url)
        sut.duration = 100
        sut.currentTime = 20

        sut.skipForward()

        XCTAssertEqual(sut.currentTime, 20 + AppConstants.Audio.skipForwardInterval)
    }

    func testSkipForward_ClampsToEnd() {
        let url = URL(fileURLWithPath: "/test/audio.m4a")
        _ = sut.loadAudio(url: url)
        sut.duration = 100
        sut.currentTime = 95

        sut.skipForward()

        XCTAssertEqual(sut.currentTime, 100) // Clamped to duration
    }

    func testSkipBackward_GoesBackBySkipInterval() {
        let url = URL(fileURLWithPath: "/test/audio.m4a")
        _ = sut.loadAudio(url: url)
        sut.duration = 100
        sut.currentTime = 50

        sut.skipBackward()

        XCTAssertEqual(sut.currentTime, 50 - AppConstants.Audio.skipBackwardInterval)
    }

    func testSkipBackward_ClampsToStart() {
        let url = URL(fileURLWithPath: "/test/audio.m4a")
        _ = sut.loadAudio(url: url)
        sut.duration = 100
        sut.currentTime = 5

        sut.skipBackward()

        XCTAssertEqual(sut.currentTime, 0) // Clamped to 0
    }

    // MARK: - Playback Rate Tests

    func testSetPlaybackRate_UpdatesRate() {
        sut.setPlaybackRate(1.5)

        XCTAssertEqual(sut.playbackRate, 1.5)
    }

    func testSetPlaybackRate_ClampsToValidRange() {
        sut.setPlaybackRate(3.0) // Above max
        XCTAssertEqual(sut.playbackRate, AppConstants.Audio.maxPlaybackRate)

        sut.setPlaybackRate(0.1) // Below min
        XCTAssertEqual(sut.playbackRate, AppConstants.Audio.minPlaybackRate)
    }

    func testIncreaseRate_IncrementsByStep() {
        sut.playbackRate = 1.0

        sut.increaseRate()

        XCTAssertEqual(sut.playbackRate, 1.0 + AppConstants.Audio.playbackRateStep)
    }

    func testDecreaseRate_DecrementsByStep() {
        sut.playbackRate = 1.5

        sut.decreaseRate()

        XCTAssertEqual(sut.playbackRate, 1.5 - AppConstants.Audio.playbackRateStep)
    }

    // MARK: - Skip Silence Tests

    func testToggleSkipSilence_TogglesState() {
        XCTAssertFalse(sut.isSkippingSilence)

        sut.toggleSkipSilence()
        XCTAssertTrue(sut.isSkippingSilence)

        sut.toggleSkipSilence()
        XCTAssertFalse(sut.isSkippingSilence)
    }

    // MARK: - Progress Calculation Tests

    func testProgress_CalculatesCorrectly() {
        sut.duration = 100
        sut.currentTime = 25

        XCTAssertEqual(sut.progress, 0.25, accuracy: 0.001)
    }

    func testProgress_ZeroDuration_ReturnsZero() {
        sut.duration = 0
        sut.currentTime = 25

        XCTAssertEqual(sut.progress, 0)
    }

    // MARK: - Waveform Generation Tests

    func testGenerateWaveformData_ReturnsConfiguredData() {
        sut.waveformDataToReturn = [0.1, 0.5, 0.8, 0.3]
        let url = URL(fileURLWithPath: "/test/audio.m4a")

        let waveform = sut.generateWaveformData(url: url)

        XCTAssertEqual(waveform, [0.1, 0.5, 0.8, 0.3])
    }
}

// MARK: - Audio Recorder Service Tests

/// Tests for AudioRecorderService business logic with mock dependencies
final class AudioRecorderServiceTests: XCTestCase {

    var sut: MockAudioRecorderService!

    override func setUp() {
        super.setUp()
        sut = MockAudioRecorderService()
    }

    override func tearDown() {
        sut.reset()
        sut = nil
        super.tearDown()
    }

    // MARK: - Initial State Tests

    func testInitialState_IsIdle() {
        XCTAssertEqual(sut.state, .idle)
        XCTAssertFalse(sut.isRecording)
        XCTAssertFalse(sut.isPaused)
        XCTAssertEqual(sut.currentTime, 0)
        XCTAssertTrue(sut.meterLevels.isEmpty)
    }

    // MARK: - Start Recording Tests

    func testStartRecording_Success_TransitionsToRecording() {
        let url = sut.startRecording(fileName: "test", format: .compressed, quality: .high, isStereo: false)

        XCTAssertNotNil(url)
        XCTAssertTrue(sut.startRecordingCalled)
        XCTAssertEqual(sut.state, .recording)
        XCTAssertTrue(sut.isRecording)
        XCTAssertEqual(sut.lastRecordingFormat, .compressed)
        XCTAssertEqual(sut.lastRecordingQuality, .high)
        XCTAssertEqual(sut.lastRecordingStereo, false)
    }

    func testStartRecording_Failure_ReturnsNil() {
        sut.shouldFailStart = true

        let url = sut.startRecording(fileName: "test")

        XCTAssertNil(url)
        XCTAssertEqual(sut.state, .idle)
    }

    func testStartRecording_WithWAVFormat_UsesCorrectExtension() {
        let url = sut.startRecording(fileName: "test", format: .uncompressed)

        XCTAssertNotNil(url)
        XCTAssertEqual(sut.lastRecordingFormat, .uncompressed)
        XCTAssertTrue(url?.pathExtension == "wav")
    }

    func testStartRecording_WithStereo_SetsCorrectly() {
        _ = sut.startRecording(fileName: "test", isStereo: true)

        XCTAssertEqual(sut.lastRecordingStereo, true)
    }

    // MARK: - Pause/Resume Recording Tests

    func testPauseRecording_TransitionsToPaused() {
        _ = sut.startRecording(fileName: "test")
        XCTAssertTrue(sut.isRecording)

        sut.pauseRecording()

        XCTAssertTrue(sut.pauseRecordingCalled)
        XCTAssertEqual(sut.state, .paused)
        XCTAssertTrue(sut.isPaused)
    }

    func testPauseRecording_WhenNotRecording_DoesNothing() {
        XCTAssertEqual(sut.state, .idle)

        sut.pauseRecording()

        XCTAssertEqual(sut.state, .idle) // Unchanged
    }

    func testResumeRecording_TransitionsToRecording() {
        _ = sut.startRecording(fileName: "test")
        sut.pauseRecording()
        XCTAssertTrue(sut.isPaused)

        sut.resumeRecording()

        XCTAssertTrue(sut.resumeRecordingCalled)
        XCTAssertEqual(sut.state, .recording)
        XCTAssertTrue(sut.isRecording)
    }

    func testResumeRecording_WhenNotPaused_DoesNothing() {
        _ = sut.startRecording(fileName: "test")
        XCTAssertTrue(sut.isRecording)

        sut.resumeRecording()

        XCTAssertEqual(sut.state, .recording) // Unchanged
    }

    // MARK: - Stop Recording Tests

    func testStopRecording_ReturnsURLAndDuration() {
        _ = sut.startRecording(fileName: "test")
        sut.simulatedDuration = 120

        let result = sut.stopRecording()

        XCTAssertTrue(sut.stopRecordingCalled)
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.duration, 120)
        XCTAssertEqual(sut.state, .idle)
    }

    func testStopRecording_WhenIdle_ReturnsNil() {
        XCTAssertEqual(sut.state, .idle)

        let result = sut.stopRecording()

        XCTAssertNil(result)
    }

    func testStopRecording_WhenPaused_StillWorks() {
        _ = sut.startRecording(fileName: "test")
        sut.pauseRecording()
        sut.simulatedDuration = 60

        let result = sut.stopRecording()

        XCTAssertNotNil(result)
        XCTAssertEqual(result?.duration, 60)
    }

    // MARK: - Cancel Recording Tests

    func testCancelRecording_ResetsToIdle() {
        _ = sut.startRecording(fileName: "test")
        XCTAssertTrue(sut.isRecording)

        sut.cancelRecording()

        XCTAssertTrue(sut.cancelRecordingCalled)
        XCTAssertEqual(sut.state, .idle)
        XCTAssertNil(sut.simulatedFileURL)
    }

    // MARK: - File Size Tests

    func testGetFileSize_ReturnsConfiguredSize() {
        sut.simulatedFileSize = 2048000
        let url = URL(fileURLWithPath: "/test/audio.m4a")

        let size = sut.getFileSize(url: url)

        XCTAssertEqual(size, 2048000)
    }

    // MARK: - Audio Session Tests

    func testSetupAudioSession_IsCalled() {
        sut.setupAudioSession()

        XCTAssertTrue(sut.setupAudioSessionCalled)
    }
}

// MARK: - Audio Editor Service Tests

/// Tests for AudioEditorService business logic with mock dependencies
final class AudioEditorServiceTests: XCTestCase {

    var sut: MockAudioEditorService!

    override func setUp() {
        super.setUp()
        sut = MockAudioEditorService()
    }

    override func tearDown() {
        sut.reset()
        sut = nil
        super.tearDown()
    }

    // MARK: - Initial State Tests

    func testInitialState_NotProcessing() {
        XCTAssertFalse(sut.isProcessing)
        XCTAssertEqual(sut.progress, 0)
    }

    // MARK: - Trim Audio Tests

    func testTrimAudio_Success_ReturnsOutputURL() async throws {
        let sourceURL = URL(fileURLWithPath: "/test/audio.m4a")

        let outputURL = try await sut.trimAudio(sourceURL: sourceURL, startTime: 10, endTime: 60)

        XCTAssertTrue(sut.trimCalled)
        XCTAssertEqual(sut.lastTrimStartTime, 10)
        XCTAssertEqual(sut.lastTrimEndTime, 60)
        XCTAssertNotNil(outputURL)
        XCTAssertEqual(sut.progress, 1.0)
    }

    func testTrimAudio_Failure_ThrowsError() async {
        sut.shouldFailTrim = true
        sut.errorToThrow = .invalidTimeRange
        let sourceURL = URL(fileURLWithPath: "/test/audio.m4a")

        do {
            _ = try await sut.trimAudio(sourceURL: sourceURL, startTime: 10, endTime: 60)
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertTrue(error is AudioEditorError)
        }
    }

    // MARK: - SaveAs Tests

    func testSaveAs_Success_ReturnsCopiedURL() throws {
        let sourceURL = URL(fileURLWithPath: "/test/audio.m4a")

        let outputURL = try sut.saveAs(sourceURL: sourceURL, newName: "Copy")

        XCTAssertTrue(sut.saveAsCalled)
        XCTAssertEqual(sut.lastSaveAsName, "Copy")
        XCTAssertTrue(outputURL.lastPathComponent.contains("Copy"))
    }

    func testSaveAs_Failure_ThrowsError() {
        sut.shouldFailSaveAs = true
        let sourceURL = URL(fileURLWithPath: "/test/audio.m4a")

        XCTAssertThrowsError(try sut.saveAs(sourceURL: sourceURL, newName: "Copy"))
    }

    // MARK: - Enhance Recording Tests

    func testEnhanceRecording_Success_ReturnsEnhancedURL() async throws {
        let sourceURL = URL(fileURLWithPath: "/test/audio.m4a")

        let outputURL = try await sut.enhanceRecording(sourceURL: sourceURL)

        XCTAssertTrue(sut.enhanceCalled)
        XCTAssertNotNil(outputURL)
        XCTAssertTrue(outputURL.absoluteString.contains("enhanced"))
    }

    func testEnhanceRecording_Failure_ThrowsError() async {
        sut.shouldFailEnhance = true
        let sourceURL = URL(fileURLWithPath: "/test/audio.m4a")

        do {
            _ = try await sut.enhanceRecording(sourceURL: sourceURL)
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertTrue(error is AudioEditorError)
        }
    }

    // MARK: - Merge Audio Tests

    func testMergeAudioFiles_Success_ReturnsMergedURL() async throws {
        let urls = [
            URL(fileURLWithPath: "/test/audio1.m4a"),
            URL(fileURLWithPath: "/test/audio2.m4a")
        ]

        let outputURL = try await sut.mergeAudioFiles(urls: urls)

        XCTAssertTrue(sut.mergeCalled)
        XCTAssertEqual(sut.lastMergeURLs.count, 2)
        XCTAssertNotNil(outputURL)
    }

    func testMergeAudioFiles_EmptyList_ThrowsError() async {
        do {
            _ = try await sut.mergeAudioFiles(urls: [])
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertTrue(error is AudioEditorError)
        }
    }

    // MARK: - Processing State Tests

    func testProcessing_SetsProcessingFlag() async throws {
        let sourceURL = URL(fileURLWithPath: "/test/audio.m4a")

        // After completion, isProcessing should be false
        _ = try await sut.trimAudio(sourceURL: sourceURL, startTime: 0, endTime: 60)

        XCTAssertFalse(sut.isProcessing)
    }
}

// MARK: - Transcription Service Tests

/// Tests for TranscriptionService business logic with mock dependencies
final class TranscriptionServiceTests: XCTestCase {

    var sut: MockTranscriptionService!

    override func setUp() {
        super.setUp()
        sut = MockTranscriptionService()
    }

    override func tearDown() {
        sut.reset()
        sut = nil
        super.tearDown()
    }

    // MARK: - Initial State Tests

    func testInitialState_NotTranscribing() {
        XCTAssertFalse(sut.isTranscribing)
        XCTAssertEqual(sut.progress, 0)
        XCTAssertTrue(sut.isAvailable)
    }

    // MARK: - Request Permission Tests

    func testRequestPermission_Granted_ReturnsTrue() async {
        sut.permissionGranted = true

        let result = await sut.requestPermission()

        XCTAssertTrue(sut.requestPermissionCalled)
        XCTAssertTrue(result)
    }

    func testRequestPermission_Denied_ReturnsFalse() async {
        sut.permissionGranted = false

        let result = await sut.requestPermission()

        XCTAssertFalse(result)
    }

    // MARK: - Transcribe Tests

    func testTranscribe_Success_ReturnsTranscript() async throws {
        sut.transcriptToReturn = "Hello, this is a test transcript."
        let audioURL = URL(fileURLWithPath: "/test/audio.m4a")

        let transcript = try await sut.transcribe(audioURL: audioURL)

        XCTAssertTrue(sut.transcribeCalled)
        XCTAssertEqual(transcript, "Hello, this is a test transcript.")
        XCTAssertFalse(sut.isTranscribing) // Should be false after completion
        XCTAssertEqual(sut.progress, 1.0)
    }

    func testTranscribe_Failure_ThrowsError() async {
        sut.shouldFail = true
        sut.errorToThrow = .recognizerUnavailable
        let audioURL = URL(fileURLWithPath: "/test/audio.m4a")

        do {
            _ = try await sut.transcribe(audioURL: audioURL)
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertTrue(error is TranscriptionError)
        }
    }

    func testTranscribe_NotAuthorized_ThrowsError() async {
        sut.shouldFail = true
        sut.errorToThrow = .notAuthorized
        let audioURL = URL(fileURLWithPath: "/test/audio.m4a")

        do {
            _ = try await sut.transcribe(audioURL: audioURL)
            XCTFail("Expected error to be thrown")
        } catch let error as TranscriptionError {
            XCTAssertEqual(error, .notAuthorized)
        } catch {
            XCTFail("Wrong error type")
        }
    }
}

// MARK: - Location Service Tests

/// Tests for LocationService business logic with mock dependencies
final class LocationServiceTests: XCTestCase {

    var sut: MockLocationService!

    override func setUp() {
        super.setUp()
        sut = MockLocationService()
    }

    override func tearDown() {
        sut.reset()
        sut = nil
        super.tearDown()
    }

    // MARK: - Initial State Tests

    func testInitialState_NoLocationName() {
        XCTAssertNil(sut.currentLocationName)
        XCTAssertEqual(sut.authorizationStatus, 0) // notDetermined
    }

    // MARK: - Request Permission Tests

    func testRequestPermission_UpdatesAuthorizationStatus() {
        sut.requestPermission()

        XCTAssertTrue(sut.requestPermissionCalled)
        XCTAssertEqual(sut.authorizationStatus, 3) // authorizedWhenInUse
    }

    // MARK: - Get Current Location Name Tests

    func testGetCurrentLocationName_Success_ReturnsLocationName() async {
        sut.locationNameToReturn = "San Francisco"

        let name = await sut.getCurrentLocationName()

        XCTAssertTrue(sut.getCurrentLocationNameCalled)
        XCTAssertEqual(name, "San Francisco")
        XCTAssertEqual(sut.currentLocationName, "San Francisco")
    }

    func testGetCurrentLocationName_NoLocation_ReturnsNil() async {
        sut.locationNameToReturn = nil

        let name = await sut.getCurrentLocationName()

        XCTAssertNil(name)
    }
}

// MARK: - StoreKit Manager Integration Tests

/// Tests for StoreKitManager with mock configuration
@MainActor
final class StoreKitManagerIntegrationTests: XCTestCase {

    var sut: MockStoreKitManager!

    override func setUp() async throws {
        try await super.setUp()
        sut = MockStoreKitManager()
    }

    override func tearDown() async throws {
        sut = nil
        try await super.tearDown()
    }

    // MARK: - Product Fetching Tests

    func testFetchProducts_LoadsAllProducts() async {
        await sut.fetchProducts()

        XCTAssertFalse(sut.products.isEmpty)
        XCTAssertEqual(sut.subscriptionProducts.count, 4)
        XCTAssertEqual(sut.nonConsumableProducts.count, 1)
    }

    func testFetchProducts_SortsByPrice() async {
        await sut.fetchProducts()

        let prices = sut.subscriptionProducts.map { $0.price }
        let sortedPrices = prices.sorted()
        XCTAssertEqual(prices, sortedPrices)
    }

    func testFetchProducts_Failure_SetsError() async {
        sut.shouldFailFetch = true
        sut.fetchError = NSError(domain: "Test", code: -1)

        await sut.fetchProducts()

        XCTAssertTrue(sut.showError)
        XCTAssertNotNil(sut.errorMessage)
        XCTAssertTrue(sut.products.isEmpty)
    }

    // MARK: - Purchase Flow Tests

    func testPurchase_Success_AddsToPurchasedProducts() async {
        await sut.fetchProducts()
        guard let product = sut.subscriptionProducts.first else {
            XCTFail("No products available")
            return
        }
        sut.simulatedPurchaseResult = .purchased

        let result = await sut.purchase(product)

        if case .purchased = result {
            XCTAssertTrue(sut.isPurchased(product.id))
        } else {
            XCTFail("Expected purchased result")
        }
    }

    func testPurchase_Cancelled_DoesNotAddToPurchased() async {
        await sut.fetchProducts()
        guard let product = sut.subscriptionProducts.first else {
            XCTFail("No products available")
            return
        }
        sut.simulatedPurchaseResult = .notPurchased

        let result = await sut.purchase(product)

        if case .notPurchased = result {
            XCTAssertFalse(sut.isPurchased(product.id))
        } else {
            XCTFail("Expected notPurchased result")
        }
    }

    func testPurchase_Pending_DoesNotAddToPurchased() async {
        await sut.fetchProducts()
        guard let product = sut.subscriptionProducts.first else {
            XCTFail("No products available")
            return
        }
        sut.simulatedPurchaseResult = .pending

        let result = await sut.purchase(product)

        if case .pending = result {
            XCTAssertFalse(sut.isPurchased(product.id))
        } else {
            XCTFail("Expected pending result")
        }
    }

    func testPurchase_Failure_SetsError() async {
        await sut.fetchProducts()
        guard let product = sut.subscriptionProducts.first else {
            XCTFail("No products available")
            return
        }
        sut.shouldFailPurchase = true

        let result = await sut.purchase(product)

        if case .failed = result {
            XCTAssertTrue(sut.showError)
            XCTAssertNotNil(sut.errorMessage)
        } else {
            XCTFail("Expected failed result")
        }
    }

    // MARK: - Subscription Status Tests

    func testHasActiveSubscription_NoSubscription_ReturnsFalse() {
        XCTAssertFalse(sut.hasActiveSubscription())
    }

    func testHasActiveSubscription_WithSubscription_ReturnsTrue() {
        sut.simulatePurchase(productID: ProductID.monthly.rawValue)

        XCTAssertTrue(sut.hasActiveSubscription())
    }

    func testHasActiveSubscription_OnlyRemoveAds_ReturnsFalse() {
        sut.simulatePurchase(productID: ProductID.removeAds.rawValue)

        XCTAssertFalse(sut.hasActiveSubscription())
    }

    func testHasActiveSubscription_AnySubscriptionType_ReturnsTrue() {
        // Test each subscription type
        for productID in ProductID.subscriptionIDs {
            sut.clearPurchases()
            sut.simulatePurchase(productID: productID)
            XCTAssertTrue(sut.hasActiveSubscription(), "Subscription \(productID) should be active")
        }
    }

    // MARK: - IsPurchased Tests

    func testIsPurchased_NotPurchased_ReturnsFalse() {
        XCTAssertFalse(sut.isPurchased(ProductID.monthly.rawValue))
    }

    func testIsPurchased_Purchased_ReturnsTrue() {
        sut.simulatePurchase(productID: ProductID.monthly.rawValue)

        XCTAssertTrue(sut.isPurchased(ProductID.monthly.rawValue))
    }
}

// MARK: - Premium Manager Integration Tests

/// Tests for PremiumManager with full subscription lifecycle
@MainActor
final class PremiumManagerIntegrationTests: XCTestCase {

    var sut: TestPremiumManager!

    override func setUp() async throws {
        try await super.setUp()
        sut = TestPremiumManager()
    }

    override func tearDown() async throws {
        sut.clearTestData()
        sut = nil
        try await super.tearDown()
    }

    // MARK: - Subscription Lifecycle Tests

    func testFullSubscriptionLifecycle() {
        // Start free
        XCTAssertEqual(sut.currentTier, .free)
        XCTAssertFalse(sut.isPremium)

        // Upgrade to premium
        let expirationDate = Calendar.current.date(byAdding: .month, value: 1, to: Date())!
        sut.setTier(.premium)
        sut.setExpirationDate(expirationDate)

        XCTAssertTrue(sut.isPremium)
        XCTAssertFalse(sut.isLifetime)
        XCTAssertNotNil(sut.subscriptionExpirationDate)

        // Check feature access
        XCTAssertTrue(sut.hasAccess(to: .transcription))
        XCTAssertTrue(sut.hasAccess(to: .highQualityRecording))

        // Downgrade to free
        sut.setTier(.free)
        sut.setExpirationDate(nil)

        XCTAssertFalse(sut.isPremium)
        XCTAssertFalse(sut.hasAccess(to: .transcription))
    }

    func testLifetimePurchaseFlow() {
        // Start free
        XCTAssertEqual(sut.currentTier, .free)

        // Purchase lifetime
        sut.setTier(.lifetime)

        XCTAssertTrue(sut.isPremium)
        XCTAssertTrue(sut.isLifetime)
        XCTAssertNil(sut.subscriptionExpirationDate) // No expiration for lifetime

        // All features accessible
        for feature in PremiumFeature.allCases {
            XCTAssertTrue(sut.hasAccess(to: feature))
        }

        // No limits apply
        XCTAssertFalse(sut.hasReachedRecordingLimit(currentCount: 100))
        XCTAssertFalse(sut.hasReachedFolderLimit(currentCount: 50))
        XCTAssertFalse(sut.hasReachedBookmarkLimit(currentCount: 100))
    }

    func testSubscriptionRenewalReminder() {
        // Set up subscription expiring in 2 days
        let twoDaysFromNow = Calendar.current.date(byAdding: .day, value: 2, to: Date())!
        sut.setTier(.premium)
        sut.setExpirationDate(twoDaysFromNow)

        XCTAssertTrue(sut.isSubscriptionExpiringSoon())
        // Allow for +/- 1 day variance due to time of day differences
        let remaining = sut.remainingSubscriptionDays() ?? 0
        XCTAssertTrue(remaining >= 1 && remaining <= 2, "Expected around 2 days, got \(remaining)")
    }

    func testSubscriptionNotExpiringSoon() {
        // Set up subscription expiring in 30 days
        let thirtyDaysFromNow = Calendar.current.date(byAdding: .day, value: 30, to: Date())!
        sut.setTier(.premium)
        sut.setExpirationDate(thirtyDaysFromNow)

        XCTAssertFalse(sut.isSubscriptionExpiringSoon())
        // Allow for +/- 1 day variance due to time of day differences
        let remaining = sut.remainingSubscriptionDays() ?? 0
        XCTAssertTrue(remaining >= 29 && remaining <= 30, "Expected around 30 days, got \(remaining)")
    }

    // MARK: - Persistence Tests

    func testStatePersistence() {
        // Set up state
        sut.setTier(.premium)
        sut.setRemovedAds(true)

        // Load persisted state
        sut.loadPersistedState()

        // Verify persisted values
        XCTAssertTrue(sut.hasRemovedAds)
    }

    func testExpiredSubscriptionResetsTier() {
        // Set up expired subscription
        let yesterday = Calendar.current.date(byAdding: .day, value: -1, to: Date())!
        sut.setTier(.premium)
        sut.setExpirationDate(yesterday)
        sut.persistState()

        // Reload state
        sut.loadPersistedState()

        // Should be reset to free
        XCTAssertEqual(sut.currentTier, .free)
    }
}
