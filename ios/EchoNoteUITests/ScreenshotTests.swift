import XCTest

@MainActor
class ScreenshotTests: XCTestCase {
    let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
        setupSnapshot(app)
        app.launch()
    }

    func testScreenshots() {
        sleep(3)

        // 1. Recordings tab (default)
        snapshot("01_Recordings")

        // 2. Folders tab
        app.tabBars.buttons["Folders"].tap()
        sleep(1)
        snapshot("02_Folders")

        // 3. Favorites tab
        app.tabBars.buttons["Favorites"].tap()
        sleep(1)
        snapshot("03_Favorites")

        // 4. Settings tab
        app.tabBars.buttons["Settings"].tap()
        sleep(1)
        snapshot("04_Settings")

        // 5. Back to recordings
        app.tabBars.buttons["Recordings"].tap()
        sleep(1)
        snapshot("05_RecordingReady")
    }
}
