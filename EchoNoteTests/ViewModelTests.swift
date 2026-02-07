import XCTest
@testable import EchoNote

// MARK: - RecordingsListViewModel Tests

final class RecordingsListViewModelTests: XCTestCase {

    var sut: RecordingsListViewModel!

    override func setUp() {
        super.setUp()
        sut = RecordingsListViewModel()
    }

    override func tearDown() {
        sut = nil
        super.tearDown()
    }

    // MARK: - Initial State Tests

    func testInitialState() {
        XCTAssertEqual(sut.searchText, "")
        XCTAssertEqual(sut.sortOption, .dateNewest)
        XCTAssertTrue(sut.selectedRecordings.isEmpty)
        XCTAssertFalse(sut.isSelectionMode)
        XCTAssertFalse(sut.showDeleteConfirmation)
        XCTAssertFalse(sut.showMoveToFolderSheet)
        XCTAssertFalse(sut.showShareSheet)
    }

    // MARK: - Search/Filter Tests

    func testFilteredRecordings_EmptySearch() {
        let recordings = createTestRecordings()
        sut.searchText = ""

        let filtered = sut.filteredRecordings(recordings)

        XCTAssertEqual(filtered.count, recordings.count)
    }

    func testFilteredRecordings_ByTitle() {
        let recordings = createTestRecordings()
        sut.searchText = "Meeting"

        let filtered = sut.filteredRecordings(recordings)

        XCTAssertTrue(filtered.allSatisfy { $0.title.lowercased().contains("meeting") })
    }

    func testFilteredRecordings_ByLocation() {
        let recordings = createTestRecordings()
        sut.searchText = "Office"

        let filtered = sut.filteredRecordings(recordings)

        XCTAssertTrue(filtered.allSatisfy {
            $0.locationName?.lowercased().contains("office") ?? false
        })
    }

    func testFilteredRecordings_CaseInsensitive() {
        let recordings = createTestRecordings()

        sut.searchText = "MEETING"
        let filtered1 = sut.filteredRecordings(recordings)

        sut.searchText = "meeting"
        let filtered2 = sut.filteredRecordings(recordings)

        XCTAssertEqual(filtered1.count, filtered2.count)
    }

    func testFilteredRecordings_NoMatches() {
        let recordings = createTestRecordings()
        sut.searchText = "xyz_nonexistent"

        let filtered = sut.filteredRecordings(recordings)

        XCTAssertTrue(filtered.isEmpty)
    }

    // MARK: - Sorting Tests

    func testSortOption_DateNewest() {
        var recordings = createTestRecordings()
        sut.sortOption = .dateNewest

        let sorted = sut.filteredRecordings(recordings)

        // Verify sorted by date descending
        for i in 0..<(sorted.count - 1) {
            XCTAssertGreaterThanOrEqual(sorted[i].dateCreated, sorted[i + 1].dateCreated)
        }
    }

    func testSortOption_DateOldest() {
        let recordings = createTestRecordings()
        sut.sortOption = .dateOldest

        let sorted = sut.filteredRecordings(recordings)

        // Verify sorted by date ascending
        for i in 0..<(sorted.count - 1) {
            XCTAssertLessThanOrEqual(sorted[i].dateCreated, sorted[i + 1].dateCreated)
        }
    }

    func testSortOption_TitleAZ() {
        let recordings = createTestRecordings()
        sut.sortOption = .titleAZ

        let sorted = sut.filteredRecordings(recordings)

        // Verify sorted alphabetically
        for i in 0..<(sorted.count - 1) {
            XCTAssertLessThanOrEqual(
                sorted[i].title.localizedCompare(sorted[i + 1].title),
                .orderedSame
            )
        }
    }

    func testSortOption_TitleZA() {
        let recordings = createTestRecordings()
        sut.sortOption = .titleZA

        let sorted = sut.filteredRecordings(recordings)

        // Verify sorted reverse alphabetically
        for i in 0..<(sorted.count - 1) {
            XCTAssertGreaterThanOrEqual(
                sorted[i].title.localizedCompare(sorted[i + 1].title),
                .orderedSame
            )
        }
    }

    func testSortOption_DurationLongest() {
        let recordings = createTestRecordings()
        sut.sortOption = .durationLongest

        let sorted = sut.filteredRecordings(recordings)

        // Verify sorted by duration descending
        for i in 0..<(sorted.count - 1) {
            XCTAssertGreaterThanOrEqual(sorted[i].duration, sorted[i + 1].duration)
        }
    }

    func testSortOption_DurationShortest() {
        let recordings = createTestRecordings()
        sut.sortOption = .durationShortest

        let sorted = sut.filteredRecordings(recordings)

        // Verify sorted by duration ascending
        for i in 0..<(sorted.count - 1) {
            XCTAssertLessThanOrEqual(sorted[i].duration, sorted[i + 1].duration)
        }
    }

    // MARK: - Selection Tests

    func testToggleSelection_AddToSelection() {
        let recording = Recording(title: "Test", fileURL: "test.m4a")

        sut.toggleSelection(recording)

        XCTAssertTrue(sut.selectedRecordings.contains(recording.id))
    }

    func testToggleSelection_RemoveFromSelection() {
        let recording = Recording(title: "Test", fileURL: "test.m4a")

        sut.toggleSelection(recording)
        XCTAssertTrue(sut.selectedRecordings.contains(recording.id))

        sut.toggleSelection(recording)
        XCTAssertFalse(sut.selectedRecordings.contains(recording.id))
    }

    func testSelectAll() {
        let recordings = createTestRecordings()

        sut.selectAll(from: recordings)

        XCTAssertEqual(sut.selectedRecordings.count, recordings.count)
        for recording in recordings {
            XCTAssertTrue(sut.selectedRecordings.contains(recording.id))
        }
    }

    func testDeselectAll() {
        let recordings = createTestRecordings()
        sut.selectAll(from: recordings)
        XCTAssertFalse(sut.selectedRecordings.isEmpty)

        sut.deselectAll()

        XCTAssertTrue(sut.selectedRecordings.isEmpty)
    }

    // MARK: - Share URLs Tests

    func testShareURLs_ReturnsSelectedOnly() {
        let recordings = createTestRecordings()
        sut.toggleSelection(recordings[0])
        sut.toggleSelection(recordings[1])

        let urls = sut.shareURLs(from: recordings)

        XCTAssertEqual(urls.count, 2)
    }

    func testShareURLs_EmptyWhenNoneSelected() {
        let recordings = createTestRecordings()

        let urls = sut.shareURLs(from: recordings)

        XCTAssertTrue(urls.isEmpty)
    }

    // MARK: - Helper Methods

    private func createTestRecordings() -> [Recording] {
        let recording1 = Recording(
            title: "Meeting Notes",
            fileURL: "meeting.m4a",
            duration: 120
        )
        recording1.locationName = "Office"

        let recording2 = Recording(
            title: "Voice Memo",
            fileURL: "memo.m4a",
            duration: 60
        )

        let recording3 = Recording(
            title: "Team Meeting",
            fileURL: "team.m4a",
            duration: 180
        )
        recording3.locationName = "Conference Room"

        return [recording1, recording2, recording3]
    }
}

// MARK: - SortOption Enum Tests

final class SortOptionTests: XCTestCase {

    func testSortOption_RawValues() {
        XCTAssertEqual(SortOption.dateNewest.rawValue, "Date (Newest)")
        XCTAssertEqual(SortOption.dateOldest.rawValue, "Date (Oldest)")
        XCTAssertEqual(SortOption.titleAZ.rawValue, "Title (A-Z)")
        XCTAssertEqual(SortOption.titleZA.rawValue, "Title (Z-A)")
        XCTAssertEqual(SortOption.durationLongest.rawValue, "Duration (Longest)")
        XCTAssertEqual(SortOption.durationShortest.rawValue, "Duration (Shortest)")
    }

    func testSortOption_AllCases() {
        XCTAssertEqual(SortOption.allCases.count, 6)
    }
}

// MARK: - FolderViewModel Tests

final class FolderViewModelTests: XCTestCase {

    var sut: FolderViewModel!

    override func setUp() {
        super.setUp()
        sut = FolderViewModel()
    }

    override func tearDown() {
        sut = nil
        super.tearDown()
    }

    // MARK: - Initial State Tests

    func testInitialState() {
        XCTAssertFalse(sut.showCreateFolder)
        XCTAssertEqual(sut.newFolderName, "")
        XCTAssertNil(sut.editingFolder)
        XCTAssertFalse(sut.showRenameAlert)
        XCTAssertFalse(sut.showDeleteConfirmation)
        XCTAssertNil(sut.folderToDelete)
    }

    // MARK: - Create Folder State Tests

    func testShowCreateFolder() {
        sut.showCreateFolder = true
        XCTAssertTrue(sut.showCreateFolder)

        sut.showCreateFolder = false
        XCTAssertFalse(sut.showCreateFolder)
    }

    func testNewFolderName() {
        sut.newFolderName = "Test Folder"
        XCTAssertEqual(sut.newFolderName, "Test Folder")
    }

    // MARK: - Edit Folder State Tests

    func testEditingFolder() {
        let folder = RecordingFolder(name: "Test")
        sut.editingFolder = folder

        XCTAssertNotNil(sut.editingFolder)
        XCTAssertEqual(sut.editingFolder?.name, "Test")
    }

    func testShowRenameAlert() {
        sut.showRenameAlert = true
        XCTAssertTrue(sut.showRenameAlert)
    }

    // MARK: - Delete Folder State Tests

    func testFolderToDelete() {
        let folder = RecordingFolder(name: "To Delete")
        sut.folderToDelete = folder

        XCTAssertNotNil(sut.folderToDelete)
        XCTAssertEqual(sut.folderToDelete?.name, "To Delete")
    }

    func testShowDeleteConfirmation() {
        sut.showDeleteConfirmation = true
        XCTAssertTrue(sut.showDeleteConfirmation)
    }
}

// MARK: - PlayerViewModel Tests

final class PlayerViewModelTests: XCTestCase {

    var sut: PlayerViewModel!

    override func setUp() {
        super.setUp()
        sut = PlayerViewModel()
    }

    override func tearDown() {
        sut = nil
        super.tearDown()
    }

    // MARK: - Initial State Tests

    func testInitialState() {
        XCTAssertNil(sut.currentRecording)
        XCTAssertTrue(sut.waveformSamples.isEmpty)
        XCTAssertFalse(sut.showPlaybackSheet)
    }

    // MARK: - Computed Properties Tests

    func testIsPlaying_InitiallyFalse() {
        XCTAssertFalse(sut.isPlaying)
    }

    func testIsPaused_InitiallyFalse() {
        XCTAssertFalse(sut.isPaused)
    }

    func testCurrentTime_InitiallyZero() {
        XCTAssertEqual(sut.currentTime, 0)
    }

    func testDuration_InitiallyZero() {
        XCTAssertEqual(sut.duration, 0)
    }

    func testProgress_InitiallyZero() {
        XCTAssertEqual(sut.progress, 0)
    }

    func testPlaybackRate_DefaultValue() {
        // Default playback rate should be 1.0
        XCTAssertEqual(sut.playbackRate, 1.0)
    }

    func testIsSkippingSilence_InitiallyFalse() {
        XCTAssertFalse(sut.isSkippingSilence)
    }

    // MARK: - Stop Tests

    func testStop_ClearsState() {
        let recording = Recording(title: "Test", fileURL: "test.m4a")
        sut.currentRecording = recording
        sut.showPlaybackSheet = true

        sut.stop()

        XCTAssertNil(sut.currentRecording)
        XCTAssertFalse(sut.showPlaybackSheet)
    }
}

// MARK: - EditorViewModel Tests

@MainActor
final class EditorViewModelTests: XCTestCase {

    var sut: EditorViewModel!

    override func setUp() async throws {
        try await super.setUp()
        sut = EditorViewModel()
    }

    override func tearDown() async throws {
        sut = nil
        try await super.tearDown()
    }

    // MARK: - Initial State Tests

    func testInitialState() {
        XCTAssertNil(sut.recording)
        XCTAssertEqual(sut.trimStart, 0)
        XCTAssertEqual(sut.trimEnd, 0)
        XCTAssertFalse(sut.isProcessing)
        XCTAssertNil(sut.errorMessage)
        XCTAssertFalse(sut.showError)
        XCTAssertFalse(sut.showSaveAsSheet)
        XCTAssertEqual(sut.saveAsName, "")
    }

    // MARK: - Load Recording Tests

    func testLoadRecording() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 120)

        sut.loadRecording(recording)

        XCTAssertNotNil(sut.recording)
        XCTAssertEqual(sut.recording?.title, "Test")
        XCTAssertEqual(sut.trimStart, 0)
        XCTAssertEqual(sut.trimEnd, 120)
    }

    func testLoadRecording_ResetsTrimRange() {
        // First load
        let recording1 = Recording(title: "Test1", fileURL: "test1.m4a", duration: 100)
        sut.loadRecording(recording1)
        sut.trimStart = 10
        sut.trimEnd = 90

        // Load new recording
        let recording2 = Recording(title: "Test2", fileURL: "test2.m4a", duration: 200)
        sut.loadRecording(recording2)

        XCTAssertEqual(sut.trimStart, 0)
        XCTAssertEqual(sut.trimEnd, 200)
    }

    // MARK: - Trim Range Tests

    func testTrimRange_Modification() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 120)
        sut.loadRecording(recording)

        sut.trimStart = 10
        sut.trimEnd = 100

        XCTAssertEqual(sut.trimStart, 10)
        XCTAssertEqual(sut.trimEnd, 100)
    }

    // MARK: - SaveAs Sheet Tests

    func testShowSaveAsSheet() {
        sut.showSaveAsSheet = true
        XCTAssertTrue(sut.showSaveAsSheet)
    }

    func testSaveAsName() {
        sut.saveAsName = "New Recording Name"
        XCTAssertEqual(sut.saveAsName, "New Recording Name")
    }

    // MARK: - Error State Tests

    func testErrorState() {
        sut.errorMessage = "Test error"
        sut.showError = true

        XCTAssertEqual(sut.errorMessage, "Test error")
        XCTAssertTrue(sut.showError)
    }

    func testClearError() {
        sut.errorMessage = "Test error"
        sut.showError = true

        sut.showError = false

        XCTAssertFalse(sut.showError)
    }

    // MARK: - Transcription State Tests

    func testIsTranscribing_InitiallyFalse() {
        XCTAssertFalse(sut.isTranscribing)
    }

    func testTranscriptionProgress_InitiallyZero() {
        XCTAssertEqual(sut.transcriptionProgress, 0)
    }
}

// MARK: - RecordingViewModel Tests

@MainActor
final class RecordingViewModelTests: XCTestCase {

    var sut: RecordingViewModel!

    override func setUp() async throws {
        try await super.setUp()
        sut = RecordingViewModel()
    }

    override func tearDown() async throws {
        sut = nil
        try await super.tearDown()
    }

    // MARK: - Initial State Tests

    func testInitialState() {
        XCTAssertNil(sut.currentRecordingURL)
        XCTAssertEqual(sut.recordingTitle, "")
        XCTAssertFalse(sut.showSaveSheet)
        XCTAssertNil(sut.errorMessage)
        XCTAssertFalse(sut.showError)
    }

    func testDefaultFormat() {
        // Default format should be compressed (m4a)
        XCTAssertEqual(sut.selectedFormat, .compressed)
    }

    func testDefaultQuality() {
        // Default quality should be high
        XCTAssertEqual(sut.selectedQuality, .high)
    }

    // MARK: - Recording State Tests

    func testIsRecording_InitiallyFalse() {
        XCTAssertFalse(sut.isRecording)
    }

    func testIsPaused_InitiallyFalse() {
        XCTAssertFalse(sut.isPaused)
    }

    func testCurrentTime_InitiallyZero() {
        XCTAssertEqual(sut.currentTime, 0)
    }

    // MARK: - Discard Recording Tests

    func testDiscardRecording_ClearsState() {
        sut.recordingTitle = "Test Recording"
        sut.showSaveSheet = true

        sut.discardRecording()

        XCTAssertNil(sut.currentRecordingURL)
        XCTAssertEqual(sut.recordingTitle, "")
        XCTAssertFalse(sut.showSaveSheet)
    }

    // MARK: - Format and Quality Selection Tests

    func testFormatSelection() {
        sut.selectedFormat = .uncompressed
        XCTAssertEqual(sut.selectedFormat, .uncompressed)

        sut.selectedFormat = .compressed
        XCTAssertEqual(sut.selectedFormat, .compressed)
    }

    func testQualitySelection() {
        sut.selectedQuality = .low
        XCTAssertEqual(sut.selectedQuality, .low)

        sut.selectedQuality = .maximum
        XCTAssertEqual(sut.selectedQuality, .maximum)
    }

    func testStereoToggle() {
        XCTAssertFalse(sut.isStereo)

        sut.isStereo = true
        XCTAssertTrue(sut.isStereo)

        sut.isStereo = false
        XCTAssertFalse(sut.isStereo)
    }

    // MARK: - Error State Tests

    func testErrorState() {
        sut.errorMessage = "Microphone access denied"
        sut.showError = true

        XCTAssertEqual(sut.errorMessage, "Microphone access denied")
        XCTAssertTrue(sut.showError)
    }

    // MARK: - Meter Levels Tests

    func testMeterLevels_InitiallyEmpty() {
        XCTAssertTrue(sut.meterLevels.isEmpty)
    }

    func testAveragePower_InitialValue() {
        // Average power should have an initial value (typically -160 for silence)
        XCTAssertLessThanOrEqual(sut.averagePower, 0)
    }
}

// MARK: - Async Operation Tests

extension RecordingsListViewModelTests {

    func testFilteredRecordings_WithTranscript() {
        let recordings = createTestRecordings()
        recordings[0].transcript = "This is a test transcript about coding"
        sut.searchText = "coding"

        let filtered = sut.filteredRecordings(recordings)

        XCTAssertEqual(filtered.count, 1)
        XCTAssertTrue(filtered.first?.transcript?.contains("coding") ?? false)
    }
}
