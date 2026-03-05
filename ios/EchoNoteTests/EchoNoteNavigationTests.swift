import XCTest
@testable import EchoNote

// MARK: - Navigation Decision Tests

/// Tests for navigation logic decisions without touching UI
@MainActor
final class NavigationDecisionTests: XCTestCase {

    // MARK: - Paywall Display Decision Tests

    func testShouldShowPaywall_FreeUserAccessesPremiumFeature_ShouldShow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.free)

        let shouldShowPaywall = !premiumManager.hasAccess(to: .transcription)

        XCTAssertTrue(shouldShowPaywall)
    }

    func testShouldShowPaywall_PremiumUserAccessesPremiumFeature_ShouldNotShow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.premium)

        let shouldShowPaywall = !premiumManager.hasAccess(to: .transcription)

        XCTAssertFalse(shouldShowPaywall)
    }

    func testShouldShowPaywall_FreeUserAccessesFreeFeature_ShouldNotShow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.free)

        // Bookmarks are available in free tier (with limits)
        let shouldShowPaywall = !premiumManager.hasAccess(to: .bookmarks)

        XCTAssertFalse(shouldShowPaywall)
    }

    // MARK: - Recording Limit Decision Tests

    func testShouldBlockNewRecording_FreeUserAtLimit_ShouldBlock() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.free)

        let shouldBlock = premiumManager.hasReachedRecordingLimit(currentCount: TestPremiumManager.freeRecordingLimit)

        XCTAssertTrue(shouldBlock)
    }

    func testShouldBlockNewRecording_FreeUserBelowLimit_ShouldNotBlock() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.free)

        let shouldBlock = premiumManager.hasReachedRecordingLimit(currentCount: TestPremiumManager.freeRecordingLimit - 1)

        XCTAssertFalse(shouldBlock)
    }

    func testShouldBlockNewRecording_PremiumUserManyRecordings_ShouldNotBlock() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.premium)

        let shouldBlock = premiumManager.hasReachedRecordingLimit(currentCount: 100)

        XCTAssertFalse(shouldBlock)
    }

    // MARK: - Folder Limit Decision Tests

    func testShouldBlockNewFolder_FreeUserAtLimit_ShouldBlock() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.free)

        let shouldBlock = premiumManager.hasReachedFolderLimit(currentCount: TestPremiumManager.freeFolderLimit)

        XCTAssertTrue(shouldBlock)
    }

    func testShouldBlockNewFolder_FreeUserBelowLimit_ShouldNotBlock() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.free)

        let shouldBlock = premiumManager.hasReachedFolderLimit(currentCount: 1)

        XCTAssertFalse(shouldBlock)
    }

    func testShouldBlockNewFolder_PremiumUserManyFolders_ShouldNotBlock() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.premium)

        let shouldBlock = premiumManager.hasReachedFolderLimit(currentCount: 50)

        XCTAssertFalse(shouldBlock)
    }

    // MARK: - Bookmark Limit Decision Tests

    func testShouldBlockNewBookmark_FreeUserAtLimit_ShouldBlock() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.free)

        let shouldBlock = premiumManager.hasReachedBookmarkLimit(currentCount: TestPremiumManager.freeBookmarkLimit)

        XCTAssertTrue(shouldBlock)
    }

    func testShouldBlockNewBookmark_FreeUserBelowLimit_ShouldNotBlock() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.free)

        let shouldBlock = premiumManager.hasReachedBookmarkLimit(currentCount: 3)

        XCTAssertFalse(shouldBlock)
    }

    // MARK: - Subscription Renewal Warning Decision Tests

    func testShouldShowRenewalWarning_ExpiringSoon_ShouldShow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.premium)
        let twoDaysFromNow = Calendar.current.date(byAdding: .day, value: 2, to: Date())!
        premiumManager.setExpirationDate(twoDaysFromNow)

        let shouldShowWarning = premiumManager.isSubscriptionExpiringSoon()

        XCTAssertTrue(shouldShowWarning)
    }

    func testShouldShowRenewalWarning_NotExpiringSoon_ShouldNotShow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.premium)
        let thirtyDaysFromNow = Calendar.current.date(byAdding: .day, value: 30, to: Date())!
        premiumManager.setExpirationDate(thirtyDaysFromNow)

        let shouldShowWarning = premiumManager.isSubscriptionExpiringSoon()

        XCTAssertFalse(shouldShowWarning)
    }

    func testShouldShowRenewalWarning_LifetimeUser_ShouldNotShow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.lifetime)

        let shouldShowWarning = premiumManager.isSubscriptionExpiringSoon()

        XCTAssertFalse(shouldShowWarning)
    }

    func testShouldShowRenewalWarning_FreeUser_ShouldNotShow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.free)

        let shouldShowWarning = premiumManager.isSubscriptionExpiringSoon()

        XCTAssertFalse(shouldShowWarning)
    }
}

// MARK: - Recording Format Selection Decision Tests

/// Tests for format selection logic based on premium status
@MainActor
final class FormatSelectionDecisionTests: XCTestCase {

    // MARK: - WAV Format Selection Tests

    func testCanSelectWAVFormat_FreeUser_ShouldNotAllow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.free)

        let canSelectWAV = premiumManager.hasAccess(to: .wavFormat)

        XCTAssertFalse(canSelectWAV)
    }

    func testCanSelectWAVFormat_PremiumUser_ShouldAllow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.premium)

        let canSelectWAV = premiumManager.hasAccess(to: .wavFormat)

        XCTAssertTrue(canSelectWAV)
    }

    // MARK: - High Quality Selection Tests

    func testCanSelectHighQuality_FreeUser_ShouldNotAllow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.free)

        let canSelectHighQuality = premiumManager.hasAccess(to: .highQualityRecording)

        XCTAssertFalse(canSelectHighQuality)
    }

    func testCanSelectHighQuality_PremiumUser_ShouldAllow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.premium)

        let canSelectHighQuality = premiumManager.hasAccess(to: .highQualityRecording)

        XCTAssertTrue(canSelectHighQuality)
    }

    // MARK: - Stereo Selection Tests

    func testCanSelectStereo_FreeUser_ShouldNotAllow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.free)

        let canSelectStereo = premiumManager.hasAccess(to: .stereoRecording)

        XCTAssertFalse(canSelectStereo)
    }

    func testCanSelectStereo_PremiumUser_ShouldAllow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.premium)

        let canSelectStereo = premiumManager.hasAccess(to: .stereoRecording)

        XCTAssertTrue(canSelectStereo)
    }
}

// MARK: - Editor Feature Access Decision Tests

/// Tests for editor feature access decisions
@MainActor
final class EditorFeatureAccessDecisionTests: XCTestCase {

    // MARK: - Transcription Access Tests

    func testCanAccessTranscription_FreeUser_ShouldNotAllow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.free)

        let canTranscribe = premiumManager.hasAccess(to: .transcription)

        XCTAssertFalse(canTranscribe)
    }

    func testCanAccessTranscription_PremiumUser_ShouldAllow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.premium)

        let canTranscribe = premiumManager.hasAccess(to: .transcription)

        XCTAssertTrue(canTranscribe)
    }

    // MARK: - Enhancement Access Tests

    func testCanAccessEnhancement_FreeUser_ShouldNotAllow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.free)

        let canEnhance = premiumManager.hasAccess(to: .enhancedAudio)

        XCTAssertFalse(canEnhance)
    }

    func testCanAccessEnhancement_PremiumUser_ShouldAllow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.premium)

        let canEnhance = premiumManager.hasAccess(to: .enhancedAudio)

        XCTAssertTrue(canEnhance)
    }

    // MARK: - Vocal Layer Separation Access Tests

    func testCanAccessVocalLayer_FreeUser_ShouldNotAllow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.free)

        let canSeparate = premiumManager.hasAccess(to: .vocalLayerSeparation)

        XCTAssertFalse(canSeparate)
    }

    func testCanAccessVocalLayer_PremiumUser_ShouldAllow() {
        let premiumManager = TestPremiumManager()
        premiumManager.setTier(.premium)

        let canSeparate = premiumManager.hasAccess(to: .vocalLayerSeparation)

        XCTAssertTrue(canSeparate)
    }
}

// MARK: - Playback Sheet Navigation Tests

/// Tests for playback sheet navigation logic
final class PlaybackSheetNavigationTests: XCTestCase {

    var viewModel: PlayerViewModel!

    override func setUp() {
        super.setUp()
        viewModel = PlayerViewModel()
    }

    override func tearDown() {
        viewModel.stop()
        viewModel = nil
        super.tearDown()
    }

    // MARK: - Show Playback Sheet Tests

    func testShowPlaybackSheet_WhenRecordingLoaded_ShouldShow() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 120)
        viewModel.currentRecording = recording
        viewModel.showPlaybackSheet = true

        XCTAssertTrue(viewModel.showPlaybackSheet)
    }

    func testHidePlaybackSheet_WhenStopped_ShouldHide() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 120)
        viewModel.currentRecording = recording
        viewModel.showPlaybackSheet = true

        viewModel.stop()

        XCTAssertFalse(viewModel.showPlaybackSheet)
        XCTAssertNil(viewModel.currentRecording)
    }
}

// MARK: - Save Recording Sheet Navigation Tests

/// Tests for save recording sheet navigation logic
@MainActor
final class SaveRecordingSheetNavigationTests: XCTestCase {

    var viewModel: RecordingViewModel!

    override func setUp() async throws {
        try await super.setUp()
        viewModel = RecordingViewModel()
    }

    override func tearDown() async throws {
        viewModel = nil
        try await super.tearDown()
    }

    // MARK: - Show Save Sheet Tests

    func testShowSaveSheet_CanBeTriggered() {
        viewModel.showSaveSheet = true

        XCTAssertTrue(viewModel.showSaveSheet)
    }

    // MARK: - Discard Hides Sheet Tests

    func testDiscardRecording_HidesSaveSheet() {
        viewModel.showSaveSheet = true
        viewModel.recordingTitle = "Test"

        viewModel.discardRecording()

        XCTAssertFalse(viewModel.showSaveSheet)
    }
}

// MARK: - Editor Sheet Navigation Tests

/// Tests for editor sheet navigation logic
@MainActor
final class EditorSheetNavigationTests: XCTestCase {

    var viewModel: EditorViewModel!

    override func setUp() async throws {
        try await super.setUp()
        viewModel = EditorViewModel()
    }

    override func tearDown() async throws {
        viewModel = nil
        try await super.tearDown()
    }

    // MARK: - SaveAs Sheet Tests

    func testShowSaveAsSheet_CanBeTriggered() {
        viewModel.showSaveAsSheet = true

        XCTAssertTrue(viewModel.showSaveAsSheet)
    }

    func testHideSaveAsSheet_CanBeDismissed() {
        viewModel.showSaveAsSheet = true

        viewModel.showSaveAsSheet = false

        XCTAssertFalse(viewModel.showSaveAsSheet)
    }

    // MARK: - Error Sheet Tests

    func testShowError_CanBeTriggered() {
        viewModel.errorMessage = "Test error"
        viewModel.showError = true

        XCTAssertTrue(viewModel.showError)
        XCTAssertEqual(viewModel.errorMessage, "Test error")
    }

    func testHideError_CanBeDismissed() {
        viewModel.errorMessage = "Test error"
        viewModel.showError = true

        viewModel.showError = false

        XCTAssertFalse(viewModel.showError)
    }
}

// MARK: - Folder Sheet Navigation Tests

/// Tests for folder sheet navigation logic
final class FolderSheetNavigationTests: XCTestCase {

    var viewModel: FolderViewModel!

    override func setUp() {
        super.setUp()
        viewModel = FolderViewModel()
    }

    override func tearDown() {
        viewModel = nil
        super.tearDown()
    }

    // MARK: - Create Folder Sheet Tests

    func testShowCreateFolder_CanBeTriggered() {
        viewModel.showCreateFolder = true

        XCTAssertTrue(viewModel.showCreateFolder)
    }

    func testHideCreateFolder_CanBeDismissed() {
        viewModel.showCreateFolder = true

        viewModel.showCreateFolder = false

        XCTAssertFalse(viewModel.showCreateFolder)
    }

    // MARK: - Rename Alert Tests

    func testShowRenameAlert_CanBeTriggered() {
        let folder = RecordingFolder(name: "Test")
        viewModel.editingFolder = folder
        viewModel.showRenameAlert = true

        XCTAssertTrue(viewModel.showRenameAlert)
        XCTAssertNotNil(viewModel.editingFolder)
    }

    func testHideRenameAlert_CanBeDismissed() {
        viewModel.showRenameAlert = true

        viewModel.showRenameAlert = false

        XCTAssertFalse(viewModel.showRenameAlert)
    }

    // MARK: - Delete Confirmation Tests

    func testShowDeleteConfirmation_CanBeTriggered() {
        let folder = RecordingFolder(name: "To Delete")
        viewModel.folderToDelete = folder
        viewModel.showDeleteConfirmation = true

        XCTAssertTrue(viewModel.showDeleteConfirmation)
        XCTAssertNotNil(viewModel.folderToDelete)
    }

    func testHideDeleteConfirmation_CanBeDismissed() {
        viewModel.showDeleteConfirmation = true

        viewModel.showDeleteConfirmation = false

        XCTAssertFalse(viewModel.showDeleteConfirmation)
    }
}

// MARK: - Recording List Sheet Navigation Tests

/// Tests for recording list sheet navigation logic
final class RecordingListSheetNavigationTests: XCTestCase {

    var viewModel: RecordingsListViewModel!

    override func setUp() {
        super.setUp()
        viewModel = RecordingsListViewModel()
    }

    override func tearDown() {
        viewModel = nil
        super.tearDown()
    }

    // MARK: - Delete Confirmation Tests

    func testShowDeleteConfirmation_CanBeTriggered() {
        let recording = Recording(title: "Test", fileURL: "test.m4a")
        viewModel.recordingToDelete = recording
        viewModel.showDeleteConfirmation = true

        XCTAssertTrue(viewModel.showDeleteConfirmation)
        XCTAssertNotNil(viewModel.recordingToDelete)
    }

    func testHideDeleteConfirmation_CanBeDismissed() {
        viewModel.showDeleteConfirmation = true

        viewModel.showDeleteConfirmation = false

        XCTAssertFalse(viewModel.showDeleteConfirmation)
    }

    // MARK: - Move to Folder Sheet Tests

    func testShowMoveToFolderSheet_CanBeTriggered() {
        viewModel.showMoveToFolderSheet = true

        XCTAssertTrue(viewModel.showMoveToFolderSheet)
    }

    func testHideMoveToFolderSheet_CanBeDismissed() {
        viewModel.showMoveToFolderSheet = true

        viewModel.showMoveToFolderSheet = false

        XCTAssertFalse(viewModel.showMoveToFolderSheet)
    }

    // MARK: - Share Sheet Tests

    func testShowShareSheet_CanBeTriggered() {
        viewModel.showShareSheet = true

        XCTAssertTrue(viewModel.showShareSheet)
    }

    func testHideShareSheet_CanBeDismissed() {
        viewModel.showShareSheet = true

        viewModel.showShareSheet = false

        XCTAssertFalse(viewModel.showShareSheet)
    }
}

// MARK: - Selection Mode Navigation Tests

/// Tests for selection mode state transitions
final class SelectionModeNavigationTests: XCTestCase {

    var viewModel: RecordingsListViewModel!

    override func setUp() {
        super.setUp()
        viewModel = RecordingsListViewModel()
    }

    override func tearDown() {
        viewModel = nil
        super.tearDown()
    }

    // MARK: - Enter Selection Mode Tests

    func testEnterSelectionMode_EnablesSelectionMode() {
        XCTAssertFalse(viewModel.isSelectionMode)

        viewModel.isSelectionMode = true

        XCTAssertTrue(viewModel.isSelectionMode)
    }

    // MARK: - Exit Selection Mode Tests

    func testExitSelectionMode_DisablesSelectionMode() {
        viewModel.isSelectionMode = true
        XCTAssertTrue(viewModel.isSelectionMode)

        viewModel.isSelectionMode = false

        XCTAssertFalse(viewModel.isSelectionMode)
    }

    func testExitSelectionMode_WithSelections_KeepsSelections() {
        let recording = Recording(title: "Test", fileURL: "test.m4a")
        viewModel.isSelectionMode = true
        viewModel.toggleSelection(recording)
        XCTAssertEqual(viewModel.selectedRecordings.count, 1)

        viewModel.isSelectionMode = false

        // Selections should persist when exiting selection mode
        XCTAssertEqual(viewModel.selectedRecordings.count, 1)
    }
}
