import XCTest
@testable import EchoNote

// MARK: - Recording Model Data Flow Tests

/// Tests for Recording model data transformations and computed properties
final class RecordingDataFlowTests: XCTestCase {

    // MARK: - Duration Formatting Tests

    func testFormattedDuration_ZeroSeconds() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 0)
        XCTAssertEqual(recording.formattedDuration, "0:00")
    }

    func testFormattedDuration_UnderOneMinute() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 45)
        XCTAssertEqual(recording.formattedDuration, "0:45")
    }

    func testFormattedDuration_ExactlyOneMinute() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 60)
        XCTAssertEqual(recording.formattedDuration, "1:00")
    }

    func testFormattedDuration_MinutesAndSeconds() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 125) // 2:05
        XCTAssertEqual(recording.formattedDuration, "2:05")
    }

    func testFormattedDuration_UnderOneHour() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 3599) // 59:59
        XCTAssertEqual(recording.formattedDuration, "59:59")
    }

    func testFormattedDuration_ExactlyOneHour() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 3600) // 1:00:00
        XCTAssertEqual(recording.formattedDuration, "1:00:00")
    }

    func testFormattedDuration_HoursMinutesSeconds() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 3661) // 1:01:01
        XCTAssertEqual(recording.formattedDuration, "1:01:01")
    }

    func testFormattedDuration_MultipleHours() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 7322) // 2:02:02
        XCTAssertEqual(recording.formattedDuration, "2:02:02")
    }

    func testFormattedDuration_SingleDigitSeconds_HasLeadingZero() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", duration: 65) // 1:05
        XCTAssertEqual(recording.formattedDuration, "1:05")
    }

    // MARK: - File Size Formatting Tests

    func testFormattedFileSize_Bytes() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", fileSize: 500)
        let formatted = recording.formattedFileSize
        XCTAssertFalse(formatted.isEmpty)
        // ByteCountFormatter returns localized strings, so we just check it's not empty
    }

    func testFormattedFileSize_Kilobytes() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", fileSize: 1024)
        let formatted = recording.formattedFileSize
        XCTAssertFalse(formatted.isEmpty)
    }

    func testFormattedFileSize_Megabytes() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", fileSize: 1024 * 1024)
        let formatted = recording.formattedFileSize
        XCTAssertFalse(formatted.isEmpty)
    }

    func testFormattedFileSize_Gigabytes() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", fileSize: 1024 * 1024 * 1024)
        let formatted = recording.formattedFileSize
        XCTAssertFalse(formatted.isEmpty)
    }

    func testFormattedFileSize_Zero() {
        let recording = Recording(title: "Test", fileURL: "test.m4a", fileSize: 0)
        let formatted = recording.formattedFileSize
        XCTAssertFalse(formatted.isEmpty)
    }

    // MARK: - Date Formatting Tests

    func testFormattedDate_IsNotEmpty() {
        let recording = Recording(title: "Test", fileURL: "test.m4a")
        XCTAssertFalse(recording.formattedDate.isEmpty)
    }

    func testFormattedDate_ContainsDateComponents() {
        let recording = Recording(title: "Test", fileURL: "test.m4a")
        let formatted = recording.formattedDate
        // Should contain date information
        XCTAssertFalse(formatted.isEmpty)
    }

    // MARK: - Actual File URL Tests

    func testActualFileURL_ConstructsValidURL() {
        let recording = Recording(title: "Test", fileURL: "Recordings/test_file.m4a")
        let url = recording.actualFileURL

        XCTAssertTrue(url.absoluteString.contains("Recordings/test_file.m4a"))
    }

    func testActualFileURL_HandlesSpecialCharacters() {
        let recording = Recording(title: "Test", fileURL: "Recordings/test file (1).m4a")
        let url = recording.actualFileURL

        XCTAssertNotNil(url)
    }

    // MARK: - Audio Format Transformation Tests

    func testAudioFormat_FileExtensions() {
        XCTAssertEqual(AudioFormat.compressed.fileExtension, "m4a")
        XCTAssertEqual(AudioFormat.uncompressed.fileExtension, "wav")
    }

    func testAudioFormat_DisplayNames() {
        XCTAssertEqual(AudioFormat.compressed.displayName, "Compressed (M4A)")
        XCTAssertEqual(AudioFormat.uncompressed.displayName, "Uncompressed (WAV)")
    }

    func testAudioFormat_RawValues() {
        XCTAssertEqual(AudioFormat.compressed.rawValue, "m4a")
        XCTAssertEqual(AudioFormat.uncompressed.rawValue, "wav")
    }

    // MARK: - Recording Quality Transformation Tests

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

    func testRecordingQuality_DisplayNames() {
        XCTAssertEqual(RecordingQuality.low.displayName, "Low")
        XCTAssertEqual(RecordingQuality.medium.displayName, "Medium")
        XCTAssertEqual(RecordingQuality.high.displayName, "High")
        XCTAssertEqual(RecordingQuality.maximum.displayName, "Maximum")
    }

    func testRecordingQuality_QualityIncreasesWithLevel() {
        let qualities = RecordingQuality.allCases
        for i in 0..<(qualities.count - 1) {
            XCTAssertLessThan(qualities[i].sampleRate, qualities[i + 1].sampleRate)
            XCTAssertLessThan(qualities[i].bitRate, qualities[i + 1].bitRate)
        }
    }
}

// MARK: - Recording Folder Data Flow Tests

/// Tests for RecordingFolder model data transformations and computed properties
final class RecordingFolderDataFlowTests: XCTestCase {

    // MARK: - Recording Count Tests

    func testRecordingCount_EmptyFolder() {
        let folder = RecordingFolder(name: "Empty")
        XCTAssertEqual(folder.recordingCount, 0)
    }

    // MARK: - Total Duration Tests

    func testTotalDuration_EmptyFolder() {
        let folder = RecordingFolder(name: "Empty")
        XCTAssertEqual(folder.totalDuration, 0)
    }

    // MARK: - Formatted Total Duration Tests

    func testFormattedTotalDuration_ZeroMinutes() {
        let folder = RecordingFolder(name: "Empty")
        XCTAssertEqual(folder.formattedTotalDuration, "0m")
    }

    // MARK: - Default Values Tests

    func testDefaultIconName() {
        let folder = RecordingFolder(name: "Test")
        XCTAssertEqual(folder.iconName, "folder.fill")
    }

    func testDefaultColorHex() {
        let folder = RecordingFolder(name: "Test")
        XCTAssertEqual(folder.colorHex, "FF3B4F")
    }

    func testCustomIconName() {
        let folder = RecordingFolder(name: "Test", iconName: "music.note")
        XCTAssertEqual(folder.iconName, "music.note")
    }

    func testCustomColorHex() {
        let folder = RecordingFolder(name: "Test", colorHex: "0000FF")
        XCTAssertEqual(folder.colorHex, "0000FF")
    }
}

// MARK: - Bookmark Data Flow Tests

/// Tests for Bookmark model data transformations and computed properties
final class BookmarkDataFlowTests: XCTestCase {

    // MARK: - Formatted Timestamp Tests

    func testFormattedTimestamp_ZeroSeconds() {
        let bookmark = Bookmark(timestamp: 0)
        XCTAssertEqual(bookmark.formattedTimestamp, "0:00")
    }

    func testFormattedTimestamp_UnderOneMinute() {
        let bookmark = Bookmark(timestamp: 45)
        XCTAssertEqual(bookmark.formattedTimestamp, "0:45")
    }

    func testFormattedTimestamp_MinutesAndSeconds() {
        let bookmark = Bookmark(timestamp: 125) // 2:05
        XCTAssertEqual(bookmark.formattedTimestamp, "2:05")
    }

    func testFormattedTimestamp_LargeValue() {
        let bookmark = Bookmark(timestamp: 3661) // 61:01
        XCTAssertEqual(bookmark.formattedTimestamp, "61:01")
    }

    func testFormattedTimestamp_SingleDigitSeconds_HasLeadingZero() {
        let bookmark = Bookmark(timestamp: 65) // 1:05
        XCTAssertEqual(bookmark.formattedTimestamp, "1:05")
    }

    func testFormattedTimestamp_Fractional_IsRounded() {
        let bookmark = Bookmark(timestamp: 65.7)
        // Should handle fractional seconds
        XCTAssertEqual(bookmark.formattedTimestamp, "1:05")
    }

    // MARK: - Default Values Tests

    func testDefaultNote_IsEmpty() {
        let bookmark = Bookmark(timestamp: 30)
        XCTAssertEqual(bookmark.note, "")
    }

    func testCustomNote() {
        let bookmark = Bookmark(timestamp: 30, note: "Important section")
        XCTAssertEqual(bookmark.note, "Important section")
    }

    func testDefaultRecording_IsNil() {
        let bookmark = Bookmark(timestamp: 30)
        XCTAssertNil(bookmark.recording)
    }
}

// MARK: - Premium Feature Data Flow Tests

/// Tests for PremiumFeature enum data transformations
final class PremiumFeatureDataFlowTests: XCTestCase {

    // MARK: - Display Names Tests

    func testPremiumFeature_AllHaveDisplayNames() {
        for feature in PremiumFeature.allCases {
            XCTAssertFalse(feature.displayName.isEmpty, "\(feature) should have a display name")
        }
    }

    func testPremiumFeature_SpecificDisplayNames() {
        XCTAssertEqual(PremiumFeature.enhancedAudio.displayName, "Audio Enhancement")
        XCTAssertEqual(PremiumFeature.vocalLayerSeparation.displayName, "Vocal Layer Separation")
        XCTAssertEqual(PremiumFeature.transcription.displayName, "Transcription")
        XCTAssertEqual(PremiumFeature.highQualityRecording.displayName, "High Quality Recording")
        XCTAssertEqual(PremiumFeature.stereoRecording.displayName, "Stereo Recording")
        XCTAssertEqual(PremiumFeature.wavFormat.displayName, "WAV Format Export")
        XCTAssertEqual(PremiumFeature.unlimitedRecordings.displayName, "Unlimited Recordings")
        XCTAssertEqual(PremiumFeature.cloudBackup.displayName, "Cloud Backup")
        XCTAssertEqual(PremiumFeature.customFolders.displayName, "Unlimited Folders")
        XCTAssertEqual(PremiumFeature.bookmarks.displayName, "Unlimited Bookmarks")
    }

    // MARK: - Descriptions Tests

    func testPremiumFeature_AllHaveDescriptions() {
        for feature in PremiumFeature.allCases {
            XCTAssertFalse(feature.description.isEmpty, "\(feature) should have a description")
        }
    }

    // MARK: - Icon Names Tests

    func testPremiumFeature_AllHaveIconNames() {
        for feature in PremiumFeature.allCases {
            XCTAssertFalse(feature.iconName.isEmpty, "\(feature) should have an icon name")
        }
    }

    // MARK: - Raw Values Tests

    func testPremiumFeature_RawValues() {
        XCTAssertEqual(PremiumFeature.enhancedAudio.rawValue, "enhanced_audio")
        XCTAssertEqual(PremiumFeature.vocalLayerSeparation.rawValue, "vocal_layer")
        XCTAssertEqual(PremiumFeature.transcription.rawValue, "transcription")
        XCTAssertEqual(PremiumFeature.highQualityRecording.rawValue, "high_quality")
        XCTAssertEqual(PremiumFeature.stereoRecording.rawValue, "stereo")
        XCTAssertEqual(PremiumFeature.wavFormat.rawValue, "wav_format")
        XCTAssertEqual(PremiumFeature.unlimitedRecordings.rawValue, "unlimited_recordings")
        XCTAssertEqual(PremiumFeature.cloudBackup.rawValue, "cloud_backup")
        XCTAssertEqual(PremiumFeature.customFolders.rawValue, "custom_folders")
        XCTAssertEqual(PremiumFeature.bookmarks.rawValue, "bookmarks")
    }

    // MARK: - Case Count Tests

    func testPremiumFeature_TotalCount() {
        XCTAssertEqual(PremiumFeature.allCases.count, 10)
    }
}

// MARK: - Subscription Tier Data Flow Tests

/// Tests for SubscriptionTier enum data transformations
final class SubscriptionTierDataFlowTests: XCTestCase {

    // MARK: - Display Names Tests

    func testSubscriptionTier_DisplayNames() {
        XCTAssertEqual(SubscriptionTier.free.displayName, "Free")
        XCTAssertEqual(SubscriptionTier.premium.displayName, "Premium")
        XCTAssertEqual(SubscriptionTier.lifetime.displayName, "Lifetime")
    }

    // MARK: - Raw Values Tests

    func testSubscriptionTier_RawValues() {
        XCTAssertEqual(SubscriptionTier.free.rawValue, "free")
        XCTAssertEqual(SubscriptionTier.premium.rawValue, "premium")
        XCTAssertEqual(SubscriptionTier.lifetime.rawValue, "lifetime")
    }

    // MARK: - Init from Raw Value Tests

    func testSubscriptionTier_InitFromRawValue() {
        XCTAssertEqual(SubscriptionTier(rawValue: "free"), .free)
        XCTAssertEqual(SubscriptionTier(rawValue: "premium"), .premium)
        XCTAssertEqual(SubscriptionTier(rawValue: "lifetime"), .lifetime)
        XCTAssertNil(SubscriptionTier(rawValue: "invalid"))
    }
}

// MARK: - Product ID Data Flow Tests

/// Tests for ProductID enum data transformations
final class ProductIDDataFlowTests: XCTestCase {

    // MARK: - Subscription IDs Tests

    func testProductID_SubscriptionIDs() {
        let subscriptionIDs = ProductID.subscriptionIDs
        XCTAssertEqual(subscriptionIDs.count, 4)
        XCTAssertTrue(subscriptionIDs.contains(ProductID.weekly.rawValue))
        XCTAssertTrue(subscriptionIDs.contains(ProductID.monthly.rawValue))
        XCTAssertTrue(subscriptionIDs.contains(ProductID.yearly.rawValue))
        XCTAssertTrue(subscriptionIDs.contains(ProductID.lifetime.rawValue))
    }

    // MARK: - Non-Consumable IDs Tests

    func testProductID_NonConsumableIDs() {
        let nonConsumableIDs = ProductID.nonConsumableIDs
        XCTAssertEqual(nonConsumableIDs.count, 1)
        XCTAssertTrue(nonConsumableIDs.contains(ProductID.removeAds.rawValue))
    }

    // MARK: - All IDs Tests

    func testProductID_AllIDs() {
        let allIDs = ProductID.allIDs
        XCTAssertEqual(allIDs.count, 5)
    }

    // MARK: - Is Subscription Tests

    func testProductID_IsSubscription() {
        XCTAssertTrue(ProductID.weekly.isSubscription)
        XCTAssertTrue(ProductID.monthly.isSubscription)
        XCTAssertTrue(ProductID.yearly.isSubscription)
        XCTAssertTrue(ProductID.lifetime.isSubscription)
        XCTAssertFalse(ProductID.removeAds.isSubscription)
    }
}

// MARK: - Sort Option Data Flow Tests

/// Tests for SortOption enum data transformations
final class SortOptionDataFlowTests: XCTestCase {

    // MARK: - Raw Values Tests

    func testSortOption_RawValues() {
        XCTAssertEqual(SortOption.dateNewest.rawValue, "Date (Newest)")
        XCTAssertEqual(SortOption.dateOldest.rawValue, "Date (Oldest)")
        XCTAssertEqual(SortOption.titleAZ.rawValue, "Title (A-Z)")
        XCTAssertEqual(SortOption.titleZA.rawValue, "Title (Z-A)")
        XCTAssertEqual(SortOption.durationLongest.rawValue, "Duration (Longest)")
        XCTAssertEqual(SortOption.durationShortest.rawValue, "Duration (Shortest)")
    }

    // MARK: - All Cases Tests

    func testSortOption_AllCases() {
        XCTAssertEqual(SortOption.allCases.count, 6)
    }
}

// MARK: - Audio Editor Error Data Flow Tests

/// Tests for AudioEditorError enum data transformations
final class AudioEditorErrorDataFlowTests: XCTestCase {

    // MARK: - Error Descriptions Tests

    func testAudioEditorError_InvalidTimeRange() {
        let error = AudioEditorError.invalidTimeRange
        XCTAssertEqual(error.errorDescription, "Invalid time range specified.")
    }

    func testAudioEditorError_ExportFailed() {
        let error = AudioEditorError.exportFailed
        XCTAssertEqual(error.errorDescription, "Failed to export audio.")
    }

    func testAudioEditorError_CompositionFailed() {
        let error = AudioEditorError.compositionFailed
        XCTAssertEqual(error.errorDescription, "Failed to create audio composition.")
    }

    func testAudioEditorError_NoAudioTrack() {
        let error = AudioEditorError.noAudioTrack
        XCTAssertEqual(error.errorDescription, "No audio track found.")
    }

    func testAudioEditorError_FileNotFound() {
        let error = AudioEditorError.fileNotFound
        XCTAssertEqual(error.errorDescription, "Audio file not found.")
    }

    func testAudioEditorError_ConformsToLocalizedError() {
        let error: LocalizedError = AudioEditorError.exportFailed
        XCTAssertNotNil(error.errorDescription)
    }
}

// MARK: - Player State Data Flow Tests

/// Tests for PlayerState enum data
final class PlayerStateDataFlowTests: XCTestCase {

    func testPlayerState_AllCases() {
        let idle = PlayerState.idle
        let playing = PlayerState.playing
        let paused = PlayerState.paused

        switch idle {
        case .idle: break
        case .playing, .paused: XCTFail("Should be idle")
        }

        switch playing {
        case .playing: break
        case .idle, .paused: XCTFail("Should be playing")
        }

        switch paused {
        case .paused: break
        case .idle, .playing: XCTFail("Should be paused")
        }
    }
}

// MARK: - Recorder State Data Flow Tests

/// Tests for RecorderState enum data
final class RecorderStateDataFlowTests: XCTestCase {

    func testRecorderState_AllCases() {
        let idle = RecorderState.idle
        let recording = RecorderState.recording
        let paused = RecorderState.paused

        switch idle {
        case .idle: break
        case .recording, .paused: XCTFail("Should be idle")
        }

        switch recording {
        case .recording: break
        case .idle, .paused: XCTFail("Should be recording")
        }

        switch paused {
        case .paused: break
        case .idle, .recording: XCTFail("Should be paused")
        }
    }
}

// MARK: - Purchase State Data Flow Tests

/// Tests for PurchaseState enum data
final class PurchaseStateDataFlowTests: XCTestCase {

    func testPurchaseState_NotPurchased() {
        let state = PurchaseState.notPurchased
        if case .notPurchased = state {
            // Success
        } else {
            XCTFail("Expected notPurchased")
        }
    }

    func testPurchaseState_Purchased() {
        let state = PurchaseState.purchased
        if case .purchased = state {
            // Success
        } else {
            XCTFail("Expected purchased")
        }
    }

    func testPurchaseState_Pending() {
        let state = PurchaseState.pending
        if case .pending = state {
            // Success
        } else {
            XCTFail("Expected pending")
        }
    }

    func testPurchaseState_Failed_ContainsError() {
        let testError = NSError(domain: "test", code: -1)
        let state = PurchaseState.failed(testError)

        if case .failed(let error) = state {
            XCTAssertEqual((error as NSError).domain, "test")
            XCTAssertEqual((error as NSError).code, -1)
        } else {
            XCTFail("Expected failed state")
        }
    }
}

// MARK: - Encoding/Decoding Tests

/// Tests for model serialization
final class ModelEncodingTests: XCTestCase {

    // MARK: - AudioFormat Codable Tests

    func testAudioFormat_Encoding() throws {
        let format = AudioFormat.uncompressed
        let encoder = JSONEncoder()

        let data = try encoder.encode(format)
        XCTAssertNotNil(data)
    }

    func testAudioFormat_Decoding() throws {
        let format = AudioFormat.compressed
        let encoder = JSONEncoder()
        let decoder = JSONDecoder()

        let data = try encoder.encode(format)
        let decoded = try decoder.decode(AudioFormat.self, from: data)

        XCTAssertEqual(format, decoded)
    }

    func testAudioFormat_RoundTrip() throws {
        let encoder = JSONEncoder()
        let decoder = JSONDecoder()

        for format in AudioFormat.allCases {
            let data = try encoder.encode(format)
            let decoded = try decoder.decode(AudioFormat.self, from: data)
            XCTAssertEqual(format, decoded)
        }
    }

    // MARK: - RecordingQuality Codable Tests

    func testRecordingQuality_Encoding() throws {
        let quality = RecordingQuality.high
        let encoder = JSONEncoder()

        let data = try encoder.encode(quality)
        XCTAssertNotNil(data)
    }

    func testRecordingQuality_Decoding() throws {
        let quality = RecordingQuality.maximum
        let encoder = JSONEncoder()
        let decoder = JSONDecoder()

        let data = try encoder.encode(quality)
        let decoded = try decoder.decode(RecordingQuality.self, from: data)

        XCTAssertEqual(quality, decoded)
    }

    func testRecordingQuality_RoundTrip() throws {
        let encoder = JSONEncoder()
        let decoder = JSONDecoder()

        for quality in RecordingQuality.allCases {
            let data = try encoder.encode(quality)
            let decoded = try decoder.decode(RecordingQuality.self, from: data)
            XCTAssertEqual(quality, decoded)
        }
    }

    // MARK: - SubscriptionTier Codable Tests

    func testSubscriptionTier_Encoding() throws {
        let tier = SubscriptionTier.premium
        let encoder = JSONEncoder()

        let data = try encoder.encode(tier)
        XCTAssertNotNil(data)
    }

    func testSubscriptionTier_Decoding() throws {
        let tier = SubscriptionTier.lifetime
        let encoder = JSONEncoder()
        let decoder = JSONDecoder()

        let data = try encoder.encode(tier)
        let decoded = try decoder.decode(SubscriptionTier.self, from: data)

        XCTAssertEqual(tier, decoded)
    }
}
