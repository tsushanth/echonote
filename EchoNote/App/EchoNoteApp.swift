import SwiftData
import SwiftUI

@main
struct EchoNoteApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .modelContainer(for: [Recording.self, RecordingFolder.self, Bookmark.self])
    }
}
