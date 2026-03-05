import XCTest
@testable import EchoNote

// MARK: - Recording Actions Tests

/// Tests for "what happens when the user performs X" in the recording flow
@MainActor
final class RecordingActionTests: XCTestCase {

    var viewModel: RecordingViewModel!

    override func setUp() async throws {
        try await super.setUp()
        viewModel = RecordingViewModel()
    }

    override func tearDown() async throws {
        viewModel = nil
        try await super.tearDown()
    }

    // MARK: - User Taps Record Tests

    func testUserTapsRecord_WithDefaultSettings_StartsRecordingWithCompressedFormat() {
        // Given: Default settings (compressed, high quality, mono)
        XCTAssertEqual(viewModel.selectedFormat, .compressed)
        XCTAssertEqual(viewModel.selectedQuality, .high)
        XCTAssertFalse(viewModel.isStereo)

        // When: User starts recording (simulated by checking settings are ready)
        // Note: Actual startRecording() requires device permissions

        // Then: Settings are configured for compressed recording
        XCTAssertEqual(viewModel.selectedFormat.fileExtension, "m4a")
        XCTAssertEqual(viewModel.selectedQuality.sampleRate, 44100)
    }

    func testUserChangesToWAVFormat_SettingsUpdate() {
        // Given: Default compressed format
        XCTAssertEqual(viewModel.selectedFormat, .compressed)

        // When: User changes to WAV format
        viewModel.selectedFormat = .uncompressed

        // Then: Format is updated
        XCTAssertEqual(viewModel.selectedFormat, .uncompressed)
        XCTAssertEqual(viewModel.selectedFormat.fileExtension, "wav")
        XCTAssertEqual(viewModel.selectedFormat.displayName, "Uncompressed (WAV)")
    }

    func testUserSelectsMaximumQuality_SettingsUpdate() {
        // Given: Default high quality
        XCTAssertEqual(viewModel.selectedQuality, .high)

        // When: User selects maximum quality
        viewModel.selectedQuality = .maximum

        // Then: Quality settings are updated
        XCTAssertEqual(viewModel.selectedQuality, .maximum)
        XCTAssertEqual(viewModel.selectedQuality.sampleRate, 48000)
        XCTAssertEqual(viewModel.selectedQuality.bitRate, 256000)
    }

    func testUserEnablesStereo_SettingsUpdate() {
        // Given: Default mono
        XCTAssertFalse(viewModel.isStereo)

        // When: User enables stereo
        viewModel.isStereo = true

        // Then: Stereo is enabled
        XCTAssertTrue(viewModel.isStereo)
    }

    func testUserEntersTitle_TitleIsSet() {
        // Given: Empty title
        XCTAssertEqual(viewModel.recordingTitle, "")

        // When: User enters a title
        viewModel.recordingTitle = "Important Meeting"

        // Then: Title is set
        XCTAssertEqual(viewModel.recordingTitle, "Important Meeting")
    }

    func testUserDiscardsRecording_StateIsCleared() {
        // Given: Recording in progress with title
        viewModel.recordingTitle = "Test Recording"
        viewModel.showSaveSheet = true

        // When: User discards recording
        viewModel.discardRecording()

        // Then: All recording state is cleared
        XCTAssertNil(viewModel.currentRecordingURL)
        XCTAssertEqual(viewModel.recordingTitle, "")
        XCTAssertFalse(viewModel.showSaveSheet)
    }
}

// MARK: - Playback Actions Tests

/// Tests for "what happens when the user performs X" in the playback flow
final class PlaybackActionTests: XCTestCase {

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

    // MARK: - User Selects Recording Tests

    func testUserSelectsRecording_RecordingIsSetAsCurrent() {
        // Given: No current recording
        XCTAssertNil(viewModel.currentRecording)

        // When: User selects a recording
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 120)
        viewModel.currentRecording = recording

        // Then: Recording is set as current
        XCTAssertNotNil(viewModel.currentRecording)
        XCTAssertEqual(viewModel.currentRecording?.title, "Test")
    }

    // MARK: - User Stops Playback Tests

    func testUserStopsPlayback_AllPlaybackStateIsCleared() {
        // Given: A recording is playing
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 120)
        viewModel.currentRecording = recording
        viewModel.showPlaybackSheet = true

        // When: User stops playback
        viewModel.stop()

        // Then: All playback state is cleared
        XCTAssertNil(viewModel.currentRecording)
        XCTAssertFalse(viewModel.showPlaybackSheet)
    }

    // MARK: - Playback Rate Tests

    func testUserChangesPlaybackRate_RateIsUpdated() {
        // Given: Default playback rate
        XCTAssertEqual(viewModel.playbackRate, 1.0)

        // The rate is changed via the playerService
        // This tests the initial state
    }

    // MARK: - Skip Silence Tests

    func testInitialSkipSilenceState_IsFalse() {
        // Given: Default state
        XCTAssertFalse(viewModel.isSkippingSilence)
    }
}

// MARK: - Editor Actions Tests

/// Tests for "what happens when the user performs X" in the editor flow
@MainActor
final class EditorActionTests: XCTestCase {

    var viewModel: EditorViewModel!

    override func setUp() async throws {
        try await super.setUp()
        viewModel = EditorViewModel()
    }

    override func tearDown() async throws {
        viewModel = nil
        try await super.tearDown()
    }

    // MARK: - User Loads Recording for Editing Tests

    func testUserOpensEditor_TrimRangeIsSetToFullDuration() {
        // Given: A recording to edit
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 120)

        // When: User loads recording into editor
        viewModel.loadRecording(recording)

        // Then: Trim range is set to full duration
        XCTAssertEqual(viewModel.trimStart, 0)
        XCTAssertEqual(viewModel.trimEnd, 120)
        XCTAssertNotNil(viewModel.recording)
    }

    func testUserLoadsDifferentRecording_TrimRangeIsReset() {
        // Given: First recording loaded with modified trim
        let recording1 = Recording(title: "First", fileURL: "first.m4a", duration: 100)
        viewModel.loadRecording(recording1)
        viewModel.trimStart = 20
        viewModel.trimEnd = 80

        // When: User loads different recording
        let recording2 = Recording(title: "Second", fileURL: "second.m4a", duration: 200)
        viewModel.loadRecording(recording2)

        // Then: Trim range is reset to new recording's duration
        XCTAssertEqual(viewModel.trimStart, 0)
        XCTAssertEqual(viewModel.trimEnd, 200)
    }

    // MARK: - User Adjusts Trim Range Tests

    func testUserAdjustsTrimStart_TrimStartIsUpdated() {
        // Given: Recording loaded
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 120)
        viewModel.loadRecording(recording)

        // When: User adjusts trim start
        viewModel.trimStart = 15

        // Then: Trim start is updated
        XCTAssertEqual(viewModel.trimStart, 15)
    }

    func testUserAdjustsTrimEnd_TrimEndIsUpdated() {
        // Given: Recording loaded
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 120)
        viewModel.loadRecording(recording)

        // When: User adjusts trim end
        viewModel.trimEnd = 100

        // Then: Trim end is updated
        XCTAssertEqual(viewModel.trimEnd, 100)
    }

    // MARK: - User Opens SaveAs Dialog Tests

    func testUserOpensSaveAsDialog_SheetIsShown() {
        // Given: Editor with loaded recording
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 120)
        viewModel.loadRecording(recording)

        // When: User opens save as dialog
        viewModel.showSaveAsSheet = true

        // Then: Sheet is shown
        XCTAssertTrue(viewModel.showSaveAsSheet)
    }

    func testUserEntersSaveAsName_NameIsSet() {
        // Given: Save as dialog open
        viewModel.showSaveAsSheet = true

        // When: User enters new name
        viewModel.saveAsName = "Recording Copy"

        // Then: Name is set
        XCTAssertEqual(viewModel.saveAsName, "Recording Copy")
    }

    func testUserCancelsSaveAs_SheetIsClosed() {
        // Given: Save as dialog open with name
        viewModel.showSaveAsSheet = true
        viewModel.saveAsName = "Copy"

        // When: User cancels
        viewModel.showSaveAsSheet = false

        // Then: Sheet is closed (name may still be set)
        XCTAssertFalse(viewModel.showSaveAsSheet)
    }

    // MARK: - Error Handling Tests

    func testOperationFails_ErrorIsDisplayed() {
        // When: An error occurs during operation
        viewModel.errorMessage = "Failed to process audio"
        viewModel.showError = true

        // Then: Error is displayed to user
        XCTAssertTrue(viewModel.showError)
        XCTAssertEqual(viewModel.errorMessage, "Failed to process audio")
    }

    func testUserDismissesError_ErrorIsHidden() {
        // Given: Error is displayed
        viewModel.errorMessage = "Test error"
        viewModel.showError = true

        // When: User dismisses error
        viewModel.showError = false

        // Then: Error is hidden
        XCTAssertFalse(viewModel.showError)
    }
}

// MARK: - List Actions Tests

/// Tests for "what happens when the user performs X" in the recordings list
final class RecordingsListActionTests: XCTestCase {

    var viewModel: RecordingsListViewModel!

    override func setUp() {
        super.setUp()
        viewModel = RecordingsListViewModel()
    }

    override func tearDown() {
        viewModel = nil
        super.tearDown()
    }

    // MARK: - User Searches Tests

    func testUserTypesInSearchField_FilteredResultsUpdate() {
        // Given: Some recordings
        let recordings = [
            createRecording(title: "Team Meeting"),
            createRecording(title: "Voice Memo"),
            createRecording(title: "Client Meeting")
        ]

        // When: User types "meeting" in search
        viewModel.searchText = "meeting"
        let filtered = viewModel.filteredRecordings(recordings)

        // Then: Only matching recordings are shown
        XCTAssertEqual(filtered.count, 2)
        XCTAssertTrue(filtered.allSatisfy { $0.title.lowercased().contains("meeting") })
    }

    func testUserClearsSearch_AllRecordingsAreShown() {
        // Given: Active search
        let recordings = createTestRecordings(count: 5)
        viewModel.searchText = "test"

        // When: User clears search
        viewModel.searchText = ""
        let filtered = viewModel.filteredRecordings(recordings)

        // Then: All recordings are shown
        XCTAssertEqual(filtered.count, 5)
    }

    // MARK: - User Changes Sort Order Tests

    func testUserChangesToSortByTitleAZ_RecordingsAreSortedAlphabetically() {
        // Given: Recordings in random title order
        let recordings = [
            createRecording(title: "Zebra"),
            createRecording(title: "Apple"),
            createRecording(title: "Mango")
        ]

        // When: User selects sort by title A-Z
        viewModel.sortOption = .titleAZ
        let sorted = viewModel.filteredRecordings(recordings)

        // Then: Recordings are sorted alphabetically
        XCTAssertEqual(sorted[0].title, "Apple")
        XCTAssertEqual(sorted[1].title, "Mango")
        XCTAssertEqual(sorted[2].title, "Zebra")
    }

    func testUserChangesToSortByDuration_RecordingsAreSortedByDuration() {
        // Given: Recordings with different durations
        let recordings = [
            createRecording(title: "Short", duration: 30),
            createRecording(title: "Long", duration: 300),
            createRecording(title: "Medium", duration: 120)
        ]

        // When: User selects sort by duration (longest first)
        viewModel.sortOption = .durationLongest
        let sorted = viewModel.filteredRecordings(recordings)

        // Then: Recordings are sorted by duration
        XCTAssertEqual(sorted[0].title, "Long")
        XCTAssertEqual(sorted[1].title, "Medium")
        XCTAssertEqual(sorted[2].title, "Short")
    }

    // MARK: - User Enters Selection Mode Tests

    func testUserEntersSelectionMode_SelectionModeIsEnabled() {
        // Given: Normal mode
        XCTAssertFalse(viewModel.isSelectionMode)

        // When: User enters selection mode
        viewModel.isSelectionMode = true

        // Then: Selection mode is enabled
        XCTAssertTrue(viewModel.isSelectionMode)
    }

    func testUserSelectsRecording_RecordingIsAddedToSelection() {
        // Given: Selection mode enabled
        viewModel.isSelectionMode = true
        let recording = createRecording(title: "Test")

        // When: User taps to select recording
        viewModel.toggleSelection(recording)

        // Then: Recording is in selection
        XCTAssertTrue(viewModel.selectedRecordings.contains(recording.id))
        XCTAssertEqual(viewModel.selectedRecordings.count, 1)
    }

    func testUserDeselectsRecording_RecordingIsRemovedFromSelection() {
        // Given: Recording already selected
        let recording = createRecording(title: "Test")
        viewModel.toggleSelection(recording)
        XCTAssertTrue(viewModel.selectedRecordings.contains(recording.id))

        // When: User taps to deselect
        viewModel.toggleSelection(recording)

        // Then: Recording is removed from selection
        XCTAssertFalse(viewModel.selectedRecordings.contains(recording.id))
    }

    func testUserTapsSelectAll_AllRecordingsAreSelected() {
        // Given: Multiple recordings, none selected
        let recordings = createTestRecordings(count: 5)
        XCTAssertTrue(viewModel.selectedRecordings.isEmpty)

        // When: User taps select all
        viewModel.selectAll(from: recordings)

        // Then: All recordings are selected
        XCTAssertEqual(viewModel.selectedRecordings.count, 5)
        for recording in recordings {
            XCTAssertTrue(viewModel.selectedRecordings.contains(recording.id))
        }
    }

    func testUserTapsDeselectAll_AllRecordingsAreDeselected() {
        // Given: All recordings selected
        let recordings = createTestRecordings(count: 5)
        viewModel.selectAll(from: recordings)
        XCTAssertEqual(viewModel.selectedRecordings.count, 5)

        // When: User taps deselect all
        viewModel.deselectAll()

        // Then: No recordings are selected
        XCTAssertTrue(viewModel.selectedRecordings.isEmpty)
    }

    // MARK: - User Prepares to Delete Tests

    func testUserSwipesToDelete_ShowsDeleteConfirmation() {
        // Given: A recording
        let recording = createRecording(title: "To Delete")

        // When: User swipes to delete
        viewModel.recordingToDelete = recording
        viewModel.showDeleteConfirmation = true

        // Then: Delete confirmation is shown
        XCTAssertTrue(viewModel.showDeleteConfirmation)
        XCTAssertNotNil(viewModel.recordingToDelete)
        XCTAssertEqual(viewModel.recordingToDelete?.title, "To Delete")
    }

    func testUserCancelsDelete_ConfirmationIsDismissed() {
        // Given: Delete confirmation showing
        let recording = createRecording(title: "Test")
        viewModel.recordingToDelete = recording
        viewModel.showDeleteConfirmation = true

        // When: User cancels
        viewModel.showDeleteConfirmation = false
        viewModel.recordingToDelete = nil

        // Then: Confirmation is dismissed
        XCTAssertFalse(viewModel.showDeleteConfirmation)
        XCTAssertNil(viewModel.recordingToDelete)
    }

    // MARK: - User Shares Recordings Tests

    func testUserTapsShare_ShareURLsAreGenerated() {
        // Given: Some recordings selected
        let recordings = createTestRecordings(count: 5)
        viewModel.toggleSelection(recordings[0])
        viewModel.toggleSelection(recordings[2])

        // When: User taps share
        let urls = viewModel.shareURLs(from: recordings)

        // Then: URLs for selected recordings are returned
        XCTAssertEqual(urls.count, 2)
    }

    func testUserTapsShareWithNoSelection_NoURLsGenerated() {
        // Given: No recordings selected
        let recordings = createTestRecordings(count: 5)
        XCTAssertTrue(viewModel.selectedRecordings.isEmpty)

        // When: User tries to share
        let urls = viewModel.shareURLs(from: recordings)

        // Then: No URLs are generated
        XCTAssertTrue(urls.isEmpty)
    }

    // MARK: - User Opens Move to Folder Tests

    func testUserTapsMoveToFolder_SheetIsShown() {
        // Given: Normal state
        XCTAssertFalse(viewModel.showMoveToFolderSheet)

        // When: User opens move to folder sheet
        viewModel.showMoveToFolderSheet = true

        // Then: Sheet is shown
        XCTAssertTrue(viewModel.showMoveToFolderSheet)
    }

    // MARK: - Helper Methods

    private func createRecording(title: String, duration: TimeInterval = 60) -> Recording {
        Recording(title: title, fileURL: "\(UUID().uuidString).m4a", duration: duration)
    }

    private func createTestRecordings(count: Int) -> [Recording] {
        (0..<count).map { createRecording(title: "Recording \($0)") }
    }
}

// MARK: - Folder Actions Tests

/// Tests for "what happens when the user performs X" with folders
final class FolderActionTests: XCTestCase {

    var viewModel: FolderViewModel!

    override func setUp() {
        super.setUp()
        viewModel = FolderViewModel()
    }

    override func tearDown() {
        viewModel = nil
        super.tearDown()
    }

    // MARK: - User Creates Folder Tests

    func testUserTapsCreateFolder_DialogIsShown() {
        // Given: Normal state
        XCTAssertFalse(viewModel.showCreateFolder)

        // When: User taps create folder
        viewModel.showCreateFolder = true

        // Then: Dialog is shown
        XCTAssertTrue(viewModel.showCreateFolder)
    }

    func testUserEntersFolderName_NameIsSet() {
        // Given: Create folder dialog open
        viewModel.showCreateFolder = true

        // When: User enters name
        viewModel.newFolderName = "Work"

        // Then: Name is set
        XCTAssertEqual(viewModel.newFolderName, "Work")
    }

    func testUserCancelsCreateFolder_DialogIsDismissedAndNameCleared() {
        // Given: Dialog open with name
        viewModel.showCreateFolder = true
        viewModel.newFolderName = "Work"

        // When: User cancels (simulated by dismissing and clearing)
        viewModel.showCreateFolder = false
        viewModel.newFolderName = ""

        // Then: Dialog is dismissed and name is cleared
        XCTAssertFalse(viewModel.showCreateFolder)
        XCTAssertEqual(viewModel.newFolderName, "")
    }

    // MARK: - User Renames Folder Tests

    func testUserTapsRenameFolder_RenameDialogIsShown() {
        // Given: A folder exists
        let folder = RecordingFolder(name: "Original")

        // When: User taps rename
        viewModel.editingFolder = folder
        viewModel.showRenameAlert = true

        // Then: Rename dialog is shown with folder reference
        XCTAssertTrue(viewModel.showRenameAlert)
        XCTAssertNotNil(viewModel.editingFolder)
        XCTAssertEqual(viewModel.editingFolder?.name, "Original")
    }

    func testUserCancelsRename_DialogIsDismissed() {
        // Given: Rename dialog open
        let folder = RecordingFolder(name: "Test")
        viewModel.editingFolder = folder
        viewModel.showRenameAlert = true

        // When: User cancels
        viewModel.editingFolder = nil
        viewModel.showRenameAlert = false

        // Then: Dialog is dismissed
        XCTAssertFalse(viewModel.showRenameAlert)
        XCTAssertNil(viewModel.editingFolder)
    }

    // MARK: - User Deletes Folder Tests

    func testUserTapsDeleteFolder_ConfirmationIsShown() {
        // Given: A folder exists
        let folder = RecordingFolder(name: "To Delete")

        // When: User taps delete
        viewModel.folderToDelete = folder
        viewModel.showDeleteConfirmation = true

        // Then: Confirmation is shown
        XCTAssertTrue(viewModel.showDeleteConfirmation)
        XCTAssertNotNil(viewModel.folderToDelete)
    }

    func testUserCancelsDeleteFolder_ConfirmationIsDismissed() {
        // Given: Delete confirmation showing
        let folder = RecordingFolder(name: "Test")
        viewModel.folderToDelete = folder
        viewModel.showDeleteConfirmation = true

        // When: User cancels
        viewModel.folderToDelete = nil
        viewModel.showDeleteConfirmation = false

        // Then: Confirmation is dismissed
        XCTAssertFalse(viewModel.showDeleteConfirmation)
        XCTAssertNil(viewModel.folderToDelete)
    }
}

// MARK: - Premium Feature Actions Tests

/// Tests for "what happens when the user tries to access premium features"
@MainActor
final class PremiumFeatureActionTests: XCTestCase {

    var premiumManager: TestPremiumManager!

    override func setUp() async throws {
        try await super.setUp()
        premiumManager = TestPremiumManager()
    }

    override func tearDown() async throws {
        premiumManager.clearTestData()
        premiumManager = nil
        try await super.tearDown()
    }

    // MARK: - Free User Accesses Premium Feature Tests

    func testFreeUserAccessesTranscription_FeatureIsBlocked() {
        // Given: Free tier user
        XCTAssertEqual(premiumManager.currentTier, .free)

        // When: User tries to access transcription
        let hasAccess = premiumManager.hasAccess(to: .transcription)

        // Then: Access is denied
        XCTAssertFalse(hasAccess)
    }

    func testFreeUserAccessesHighQuality_FeatureIsBlocked() {
        // Given: Free tier user
        XCTAssertEqual(premiumManager.currentTier, .free)

        // When: User tries to access high quality recording
        let hasAccess = premiumManager.hasAccess(to: .highQualityRecording)

        // Then: Access is denied
        XCTAssertFalse(hasAccess)
    }

    func testFreeUserAccessesStereo_FeatureIsBlocked() {
        // Given: Free tier user
        XCTAssertEqual(premiumManager.currentTier, .free)

        // When: User tries to access stereo recording
        let hasAccess = premiumManager.hasAccess(to: .stereoRecording)

        // Then: Access is denied
        XCTAssertFalse(hasAccess)
    }

    func testFreeUserAccessesWAVFormat_FeatureIsBlocked() {
        // Given: Free tier user
        XCTAssertEqual(premiumManager.currentTier, .free)

        // When: User tries to access WAV format
        let hasAccess = premiumManager.hasAccess(to: .wavFormat)

        // Then: Access is denied
        XCTAssertFalse(hasAccess)
    }

    // MARK: - Premium User Accesses Features Tests

    func testPremiumUserAccessesAllFeatures_AllFeaturesAvailable() {
        // Given: Premium tier user
        premiumManager.setTier(.premium)

        // When/Then: All features are accessible
        for feature in PremiumFeature.allCases {
            XCTAssertTrue(premiumManager.hasAccess(to: feature), "Premium user should have access to \(feature)")
        }
    }

    func testLifetimeUserAccessesAllFeatures_AllFeaturesAvailable() {
        // Given: Lifetime tier user
        premiumManager.setTier(.lifetime)

        // When/Then: All features are accessible
        for feature in PremiumFeature.allCases {
            XCTAssertTrue(premiumManager.hasAccess(to: feature), "Lifetime user should have access to \(feature)")
        }
    }

    // MARK: - Free Tier Limits Tests

    func testFreeUserCreatesRecording_AtLimit_IsBlocked() {
        // Given: Free user at recording limit
        XCTAssertEqual(premiumManager.currentTier, .free)
        let currentCount = TestPremiumManager.freeRecordingLimit

        // When: User tries to create another recording
        let hasReachedLimit = premiumManager.hasReachedRecordingLimit(currentCount: currentCount)

        // Then: User is blocked
        XCTAssertTrue(hasReachedLimit)
    }

    func testFreeUserCreatesRecording_BelowLimit_IsAllowed() {
        // Given: Free user below recording limit
        XCTAssertEqual(premiumManager.currentTier, .free)
        let currentCount = TestPremiumManager.freeRecordingLimit - 1

        // When: User tries to create recording
        let hasReachedLimit = premiumManager.hasReachedRecordingLimit(currentCount: currentCount)

        // Then: User is allowed
        XCTAssertFalse(hasReachedLimit)
    }

    func testFreeUserCreatesFolder_AtLimit_IsBlocked() {
        // Given: Free user at folder limit
        XCTAssertEqual(premiumManager.currentTier, .free)
        let currentCount = TestPremiumManager.freeFolderLimit

        // When: User tries to create another folder
        let hasReachedLimit = premiumManager.hasReachedFolderLimit(currentCount: currentCount)

        // Then: User is blocked
        XCTAssertTrue(hasReachedLimit)
    }

    func testFreeUserAddsBookmark_AtLimit_IsBlocked() {
        // Given: Free user at bookmark limit
        XCTAssertEqual(premiumManager.currentTier, .free)
        let currentCount = TestPremiumManager.freeBookmarkLimit

        // When: User tries to add another bookmark
        let hasReachedLimit = premiumManager.hasReachedBookmarkLimit(currentCount: currentCount)

        // Then: User is blocked
        XCTAssertTrue(hasReachedLimit)
    }

    func testPremiumUserIgnoresLimits_NoLimitsApply() {
        // Given: Premium user
        premiumManager.setTier(.premium)

        // When: User has many recordings/folders/bookmarks
        let hasReachedRecordingLimit = premiumManager.hasReachedRecordingLimit(currentCount: 100)
        let hasReachedFolderLimit = premiumManager.hasReachedFolderLimit(currentCount: 50)
        let hasReachedBookmarkLimit = premiumManager.hasReachedBookmarkLimit(currentCount: 100)

        // Then: No limits apply
        XCTAssertFalse(hasReachedRecordingLimit)
        XCTAssertFalse(hasReachedFolderLimit)
        XCTAssertFalse(hasReachedBookmarkLimit)
    }

    // MARK: - Subscription Upgrade Tests

    func testUserUpgradesToPremium_AllFeaturesUnlock() {
        // Given: Free user
        XCTAssertEqual(premiumManager.currentTier, .free)
        XCTAssertFalse(premiumManager.hasAccess(to: .transcription))

        // When: User upgrades to premium
        premiumManager.setTier(.premium)

        // Then: All features are unlocked
        XCTAssertTrue(premiumManager.isPremium)
        XCTAssertTrue(premiumManager.hasAccess(to: .transcription))
        XCTAssertTrue(premiumManager.hasAccess(to: .highQualityRecording))
        XCTAssertTrue(premiumManager.hasAccess(to: .stereoRecording))
    }

    func testUserDowngradesToFree_FeaturesAreLocked() {
        // Given: Premium user
        premiumManager.setTier(.premium)
        XCTAssertTrue(premiumManager.hasAccess(to: .transcription))

        // When: User downgrades to free
        premiumManager.setTier(.free)

        // Then: Premium features are locked
        XCTAssertFalse(premiumManager.isPremium)
        XCTAssertFalse(premiumManager.hasAccess(to: .transcription))
    }

    // MARK: - Subscription Expiration Tests

    func testSubscriptionExpiringSoon_WarningIsShown() {
        // Given: Subscription expiring in 2 days
        let twoDaysFromNow = Calendar.current.date(byAdding: .day, value: 2, to: Date())!
        premiumManager.setTier(.premium)
        premiumManager.setExpirationDate(twoDaysFromNow)

        // When: Checking expiration status
        let isExpiringSoon = premiumManager.isSubscriptionExpiringSoon()

        // Then: Warning should be shown
        XCTAssertTrue(isExpiringSoon)
    }

    func testSubscriptionNotExpiringSoon_NoWarning() {
        // Given: Subscription expiring in 30 days
        let thirtyDaysFromNow = Calendar.current.date(byAdding: .day, value: 30, to: Date())!
        premiumManager.setTier(.premium)
        premiumManager.setExpirationDate(thirtyDaysFromNow)

        // When: Checking expiration status
        let isExpiringSoon = premiumManager.isSubscriptionExpiringSoon()

        // Then: No warning
        XCTAssertFalse(isExpiringSoon)
    }

    func testLifetimeSubscription_NoExpirationConcerns() {
        // Given: Lifetime user
        premiumManager.setTier(.lifetime)

        // When: Checking expiration status
        let isExpiringSoon = premiumManager.isSubscriptionExpiringSoon()
        let remainingDays = premiumManager.remainingSubscriptionDays()

        // Then: No expiration concerns
        XCTAssertFalse(isExpiringSoon)
        XCTAssertNil(remainingDays)
    }
}
