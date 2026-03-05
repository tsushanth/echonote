import XCTest
import SwiftData
@testable import EchoNote

// MARK: - Recording Model Tests

final class RecordingTests: XCTestCase {

    // MARK: - Initialization Tests

    func testRecording_DefaultInitialization() {
        // Given
        let title = "Test Recording"
        let fileURL = "Recordings/test.m4a"

        // When
        let recording = Recording(title: title, fileURL: fileURL)

        // Then
        XCTAssertNotNil(recording.id)
        XCTAssertEqual(recording.title, title)
        XCTAssertEqual(recording.fileURL, fileURL)
        XCTAssertEqual(recording.duration, 0)
        XCTAssertEqual(recording.fileSize, 0)
        XCTAssertEqual(recording.audioFormat, .compressed)
        XCTAssertEqual(recording.quality, .high)
        XCTAssertFalse(recording.isFavorite)
        XCTAssertFalse(recording.isStereo)
        XCTAssertFalse(recording.isEnhanced)
        XCTAssertFalse(recording.hasVocalLayer)
        XCTAssertNil(recording.locationName)
        XCTAssertNil(recording.transcript)
        XCTAssertNil(recording.waveformData)
        XCTAssertNil(recording.folder)
        XCTAssertTrue(recording.bookmarks.isEmpty)
    }

    func testRecording_FullInitialization() {
        // Given
        let title = "Full Test Recording"
        let fileURL = "Recordings/full_test.wav"
        let duration: TimeInterval = 120.5
        let fileSize: Int64 = 1024000
        let audioFormat = AudioFormat.uncompressed
        let quality = RecordingQuality.maximum
        let isStereo = true
        let locationName = "New York"

        // When
        let recording = Recording(
            title: title,
            fileURL: fileURL,
            duration: duration,
            fileSize: fileSize,
            audioFormat: audioFormat,
            quality: quality,
            isStereo: isStereo,
            locationName: locationName
        )

        // Then
        XCTAssertEqual(recording.title, title)
        XCTAssertEqual(recording.fileURL, fileURL)
        XCTAssertEqual(recording.duration, duration)
        XCTAssertEqual(recording.fileSize, fileSize)
        XCTAssertEqual(recording.audioFormat, audioFormat)
        XCTAssertEqual(recording.quality, quality)
        XCTAssertTrue(recording.isStereo)
        XCTAssertEqual(recording.locationName, locationName)
    }

    func testRecording_UniqueIDs() {
        // Given
        let recording1 = Recording(title: "Recording 1", fileURL: "file1.m4a")
        let recording2 = Recording(title: "Recording 2", fileURL: "file2.m4a")

        // Then
        XCTAssertNotEqual(recording1.id, recording2.id)
    }

    func testRecording_DatesAreSet() {
        // Given
        let beforeCreation = Date()

        // When
        let recording = Recording(title: "Test", fileURL: "test.m4a")

        // Then
        XCTAssertGreaterThanOrEqual(recording.dateCreated, beforeCreation)
        XCTAssertGreaterThanOrEqual(recording.dateModified, beforeCreation)
        XCTAssertEqual(recording.dateCreated, recording.dateModified)
    }

    // MARK: - Computed Properties Tests

    func testFormattedDuration_Seconds() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 45)
        XCTAssertEqual(recording.formattedDuration, "0:45")
    }

    func testFormattedDuration_Minutes() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 125) // 2:05
        XCTAssertEqual(recording.formattedDuration, "2:05")
    }

    func testFormattedDuration_Hours() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 3661) // 1:01:01
        XCTAssertEqual(recording.formattedDuration, "1:01:01")
    }

    func testFormattedDuration_Zero() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 0)
        XCTAssertEqual(recording.formattedDuration, "0:00")
    }

    func testFormattedFileSize_Bytes() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", fileSize: 500)
        // ByteCountFormatter will return something like "500 bytes"
        XCTAssertFalse(recording.formattedFileSize.isEmpty)
    }

    func testFormattedFileSize_Kilobytes() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", fileSize: 1024)
        XCTAssertFalse(recording.formattedFileSize.isEmpty)
    }

    func testFormattedFileSize_Megabytes() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", fileSize: 1024 * 1024)
        XCTAssertFalse(recording.formattedFileSize.isEmpty)
    }

    func testFormattedDate() {
        let recording = Recording(title: "Test", fileURL: "test.m4a")
        XCTAssertFalse(recording.formattedDate.isEmpty)
    }

    func testActualFileURL() {
        let recording = Recording(title: "Test", fileURL: "Recordings/test.m4a")
        let url = recording.actualFileURL

        XCTAssertTrue(url.absoluteString.contains("Recordings/test.m4a"))
    }

    // MARK: - Property Modification Tests

    func testRecording_ToggleFavorite() {
        let recording = Recording(title: "Test", fileURL: "test.m4a")
        XCTAssertFalse(recording.isFavorite)

        recording.isFavorite = true
        XCTAssertTrue(recording.isFavorite)

        recording.isFavorite = false
        XCTAssertFalse(recording.isFavorite)
    }

    func testRecording_SetTranscript() {
        let recording = Recording(title: "Test", fileURL: "test.m4a")
        XCTAssertNil(recording.transcript)

        recording.transcript = "This is a test transcript."
        XCTAssertEqual(recording.transcript, "This is a test transcript.")
    }

    func testRecording_SetEnhanced() {
        let recording = Recording(title: "Test", fileURL: "test.m4a")
        XCTAssertFalse(recording.isEnhanced)

        recording.isEnhanced = true
        XCTAssertTrue(recording.isEnhanced)
    }
}

// MARK: - AudioFormat Enum Tests

final class AudioFormatTests: XCTestCase {

    func testAudioFormat_RawValues() {
        XCTAssertEqual(AudioFormat.uncompressed.rawValue, "wav")
        XCTAssertEqual(AudioFormat.compressed.rawValue, "m4a")
    }

    func testAudioFormat_DisplayNames() {
        XCTAssertEqual(AudioFormat.uncompressed.displayName, "Uncompressed (WAV)")
        XCTAssertEqual(AudioFormat.compressed.displayName, "Compressed (M4A)")
    }

    func testAudioFormat_FileExtensions() {
        XCTAssertEqual(AudioFormat.uncompressed.fileExtension, "wav")
        XCTAssertEqual(AudioFormat.compressed.fileExtension, "m4a")
    }

    func testAudioFormat_AllCases() {
        XCTAssertEqual(AudioFormat.allCases.count, 2)
        XCTAssertTrue(AudioFormat.allCases.contains(.uncompressed))
        XCTAssertTrue(AudioFormat.allCases.contains(.compressed))
    }

    func testAudioFormat_Codable() throws {
        let format = AudioFormat.uncompressed
        let encoder = JSONEncoder()
        let decoder = JSONDecoder()

        let data = try encoder.encode(format)
        let decoded = try decoder.decode(AudioFormat.self, from: data)

        XCTAssertEqual(format, decoded)
    }
}

// MARK: - RecordingQuality Enum Tests

final class RecordingQualityTests: XCTestCase {

    func testRecordingQuality_RawValues() {
        XCTAssertEqual(RecordingQuality.low.rawValue, "low")
        XCTAssertEqual(RecordingQuality.medium.rawValue, "medium")
        XCTAssertEqual(RecordingQuality.high.rawValue, "high")
        XCTAssertEqual(RecordingQuality.maximum.rawValue, "maximum")
    }

    func testRecordingQuality_DisplayNames() {
        XCTAssertEqual(RecordingQuality.low.displayName, "Low")
        XCTAssertEqual(RecordingQuality.medium.displayName, "Medium")
        XCTAssertEqual(RecordingQuality.high.displayName, "High")
        XCTAssertEqual(RecordingQuality.maximum.displayName, "Maximum")
    }

    func testRecordingQuality_SampleRates() {
        XCTAssertEqual(RecordingQuality.low.sampleRate, 12000)
        XCTAssertEqual(RecordingQuality.medium.sampleRate, 24000)
        XCTAssertEqual(RecordingQuality.high.sampleRate, 44100)
        XCTAssertEqual(RecordingQuality.maximum.sampleRate, 48000)
    }

    func testRecordingQuality_BitRates() {
        XCTAssertEqual(RecordingQuality.low.bitRate, 32000)
        XCTAssertEqual(RecordingQuality.medium.bitRate, 64000)
        XCTAssertEqual(RecordingQuality.high.bitRate, 128000)
        XCTAssertEqual(RecordingQuality.maximum.bitRate, 256000)
    }

    func testRecordingQuality_AllCases() {
        XCTAssertEqual(RecordingQuality.allCases.count, 4)
    }

    func testRecordingQuality_SampleRateIncreases() {
        // Verify sample rates increase with quality
        let rates = RecordingQuality.allCases.map { $0.sampleRate }
        for i in 0..<(rates.count - 1) {
            XCTAssertLessThan(rates[i], rates[i + 1])
        }
    }

    func testRecordingQuality_BitRateIncreases() {
        // Verify bit rates increase with quality
        let rates = RecordingQuality.allCases.map { $0.bitRate }
        for i in 0..<(rates.count - 1) {
            XCTAssertLessThan(rates[i], rates[i + 1])
        }
    }

    func testRecordingQuality_Codable() throws {
        let quality = RecordingQuality.high
        let encoder = JSONEncoder()
        let decoder = JSONDecoder()

        let data = try encoder.encode(quality)
        let decoded = try decoder.decode(RecordingQuality.self, from: data)

        XCTAssertEqual(quality, decoded)
    }
}

// MARK: - RecordingFolder Model Tests

final class RecordingFolderTests: XCTestCase {

    func testRecordingFolder_DefaultInitialization() {
        // When
        let folder = RecordingFolder(name: "Test Folder")

        // Then
        XCTAssertNotNil(folder.id)
        XCTAssertEqual(folder.name, "Test Folder")
        XCTAssertEqual(folder.iconName, "folder.fill")
        XCTAssertEqual(folder.colorHex, "FF3B4F")
        XCTAssertTrue(folder.recordings.isEmpty)
    }

    func testRecordingFolder_CustomInitialization() {
        // When
        let folder = RecordingFolder(
            name: "Custom Folder",
            iconName: "star.fill",
            colorHex: "0000FF"
        )

        // Then
        XCTAssertEqual(folder.name, "Custom Folder")
        XCTAssertEqual(folder.iconName, "star.fill")
        XCTAssertEqual(folder.colorHex, "0000FF")
    }

    func testRecordingFolder_UniqueIDs() {
        let folder1 = RecordingFolder(name: "Folder 1")
        let folder2 = RecordingFolder(name: "Folder 2")

        XCTAssertNotEqual(folder1.id, folder2.id)
    }

    func testRecordingFolder_DateCreatedIsSet() {
        let beforeCreation = Date()
        let folder = RecordingFolder(name: "Test")

        XCTAssertGreaterThanOrEqual(folder.dateCreated, beforeCreation)
    }

    // MARK: - Computed Properties Tests

    func testRecordingFolder_RecordingCountEmpty() {
        let folder = RecordingFolder(name: "Empty Folder")
        XCTAssertEqual(folder.recordingCount, 0)
    }

    func testRecordingFolder_TotalDurationEmpty() {
        let folder = RecordingFolder(name: "Empty Folder")
        XCTAssertEqual(folder.totalDuration, 0)
    }

    func testRecordingFolder_FormattedTotalDurationMinutes() {
        let folder = RecordingFolder(name: "Test")
        // Without recordings, should show 0m
        XCTAssertEqual(folder.formattedTotalDuration, "0m")
    }

    // MARK: - Folder Name Tests

    func testRecordingFolder_NameModification() {
        let folder = RecordingFolder(name: "Original")
        XCTAssertEqual(folder.name, "Original")

        folder.name = "Modified"
        XCTAssertEqual(folder.name, "Modified")
    }

    func testRecordingFolder_EmptyName() {
        let folder = RecordingFolder(name: "")
        XCTAssertEqual(folder.name, "")
    }

    func testRecordingFolder_LongName() {
        let longName = String(repeating: "A", count: 1000)
        let folder = RecordingFolder(name: longName)
        XCTAssertEqual(folder.name, longName)
    }

    // MARK: - Icon and Color Tests

    func testRecordingFolder_IconModification() {
        let folder = RecordingFolder(name: "Test")
        folder.iconName = "music.note"
        XCTAssertEqual(folder.iconName, "music.note")
    }

    func testRecordingFolder_ColorModification() {
        let folder = RecordingFolder(name: "Test")
        folder.colorHex = "00FF00"
        XCTAssertEqual(folder.colorHex, "00FF00")
    }
}

// MARK: - Bookmark Model Tests

final class BookmarkTests: XCTestCase {

    func testBookmark_DefaultInitialization() {
        // Given
        let timestamp: TimeInterval = 30.5

        // When
        let bookmark = Bookmark(timestamp: timestamp)

        // Then
        XCTAssertNotNil(bookmark.id)
        XCTAssertEqual(bookmark.timestamp, timestamp)
        XCTAssertEqual(bookmark.note, "")
        XCTAssertNil(bookmark.recording)
    }

    func testBookmark_FullInitialization() {
        // Given
        let timestamp: TimeInterval = 60.0
        let note = "Important part"
        let recording = Recording(title: "Test", fileURL: "test.m4a")

        // When
        let bookmark = Bookmark(timestamp: timestamp, note: note, recording: recording)

        // Then
        XCTAssertEqual(bookmark.timestamp, timestamp)
        XCTAssertEqual(bookmark.note, note)
        XCTAssertNotNil(bookmark.recording)
    }

    func testBookmark_UniqueIDs() {
        let bookmark1 = Bookmark(timestamp: 10)
        let bookmark2 = Bookmark(timestamp: 20)

        XCTAssertNotEqual(bookmark1.id, bookmark2.id)
    }

    func testBookmark_DateCreatedIsSet() {
        let beforeCreation = Date()
        let bookmark = Bookmark(timestamp: 0)

        XCTAssertGreaterThanOrEqual(bookmark.dateCreated, beforeCreation)
    }

    // MARK: - Formatted Timestamp Tests

    func testFormattedTimestamp_Seconds() {
        let bookmark = Bookmark(timestamp: 45)
        XCTAssertEqual(bookmark.formattedTimestamp, "0:45")
    }

    func testFormattedTimestamp_Minutes() {
        let bookmark = Bookmark(timestamp: 125) // 2:05
        XCTAssertEqual(bookmark.formattedTimestamp, "2:05")
    }

    func testFormattedTimestamp_Zero() {
        let bookmark = Bookmark(timestamp: 0)
        XCTAssertEqual(bookmark.formattedTimestamp, "0:00")
    }

    func testFormattedTimestamp_LargeValue() {
        let bookmark = Bookmark(timestamp: 3661) // 61:01
        XCTAssertEqual(bookmark.formattedTimestamp, "61:01")
    }

    // MARK: - Note Tests

    func testBookmark_NoteModification() {
        let bookmark = Bookmark(timestamp: 10)
        XCTAssertEqual(bookmark.note, "")

        bookmark.note = "Updated note"
        XCTAssertEqual(bookmark.note, "Updated note")
    }

    func testBookmark_LongNote() {
        let longNote = String(repeating: "A", count: 1000)
        let bookmark = Bookmark(timestamp: 10, note: longNote)
        XCTAssertEqual(bookmark.note, longNote)
    }

    // MARK: - Timestamp Tests

    func testBookmark_TimestampModification() {
        let bookmark = Bookmark(timestamp: 10)
        XCTAssertEqual(bookmark.timestamp, 10)

        bookmark.timestamp = 20
        XCTAssertEqual(bookmark.timestamp, 20)
    }

    func testBookmark_NegativeTimestamp() {
        // TimeInterval can be negative, though it may not make sense semantically
        let bookmark = Bookmark(timestamp: -10)
        XCTAssertEqual(bookmark.timestamp, -10)
    }

    func testBookmark_FractionalTimestamp() {
        let bookmark = Bookmark(timestamp: 10.567)
        XCTAssertEqual(bookmark.timestamp, 10.567, accuracy: 0.001)
    }
}

// MARK: - Model Relationships Tests

final class ModelRelationshipTests: XCTestCase {

    func testRecording_CanBeAddedToFolder() {
        let folder = RecordingFolder(name: "Test Folder")
        let recording = Recording(title: "Test", fileURL: "test.m4a")

        recording.folder = folder

        XCTAssertNotNil(recording.folder)
        XCTAssertEqual(recording.folder?.name, "Test Folder")
    }

    func testRecording_CanBeRemovedFromFolder() {
        let folder = RecordingFolder(name: "Test Folder")
        let recording = Recording(title: "Test", fileURL: "test.m4a")

        recording.folder = folder
        XCTAssertNotNil(recording.folder)

        recording.folder = nil
        XCTAssertNil(recording.folder)
    }

    func testBookmark_CanBeAssociatedWithRecording() {
        let recording = Recording(title: "Test", fileURL: "test.m4a")
        let bookmark = Bookmark(timestamp: 30, recording: recording)

        XCTAssertNotNil(bookmark.recording)
        XCTAssertEqual(bookmark.recording?.title, "Test")
    }

    func testRecording_BookmarksArray() {
        let recording = Recording(title: "Test", fileURL: "test.m4a")
        XCTAssertTrue(recording.bookmarks.isEmpty)

        let bookmark = Bookmark(timestamp: 30, recording: recording)
        recording.bookmarks.append(bookmark)

        XCTAssertEqual(recording.bookmarks.count, 1)
        XCTAssertEqual(recording.bookmarks.first?.timestamp, 30)
    }

    func testRecording_MultipleBookmarks() {
        let recording = Recording(title: "Test", fileURL: "test.m4a")

        let bookmark1 = Bookmark(timestamp: 10, note: "First", recording: recording)
        let bookmark2 = Bookmark(timestamp: 20, note: "Second", recording: recording)
        let bookmark3 = Bookmark(timestamp: 30, note: "Third", recording: recording)

        recording.bookmarks = [bookmark1, bookmark2, bookmark3]

        XCTAssertEqual(recording.bookmarks.count, 3)
    }
}

// MARK: - Data Validation Tests

final class DataValidationTests: XCTestCase {

    func testRecording_TitleCanBeEmpty() {
        let recording = Recording(title: "", fileURL: "test.m4a")
        XCTAssertEqual(recording.title, "")
    }

    func testRecording_TitleWithSpecialCharacters() {
        let specialTitle = "Test 🎵 Recording (1) - Draft"
        let recording = Recording(title: specialTitle, fileURL: "test.m4a")
        XCTAssertEqual(recording.title, specialTitle)
    }

    func testRecording_FileURLWithSpaces() {
        let fileURL = "Recordings/my test recording.m4a"
        let recording = Recording(title: "Test", fileURL: fileURL)
        XCTAssertEqual(recording.fileURL, fileURL)
    }

    func testRecordingFolder_NameWithUnicode() {
        let unicodeName = "会议录音 📁"
        let folder = RecordingFolder(name: unicodeName)
        XCTAssertEqual(folder.name, unicodeName)
    }

    func testBookmark_NoteWithNewlines() {
        let noteWithNewlines = "Line 1\nLine 2\nLine 3"
        let bookmark = Bookmark(timestamp: 10, note: noteWithNewlines)
        XCTAssertEqual(bookmark.note, noteWithNewlines)
    }
}
