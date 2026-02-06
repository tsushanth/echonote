# EchoNote

A modern voice recording app for iOS, built with SwiftUI. EchoNote goes beyond basic voice memos with features like audio enhancement, transcription, folder organization, and a full-featured audio editor.

## Features

- **Recording** - Record audio in multiple formats (AAC, WAV, ALAC) with configurable quality and stereo support
- **Playback** - Variable-speed playback (0.5x-2.0x), skip silence, 15-second skip forward/backward, waveform visualization with tap-to-seek
- **Audio Editing** - Trim recordings with draggable handles, preview trimmed audio, save copies
- **Transcription** - On-device speech-to-text transcription powered by the Speech framework
- **Audio Enhancement** - Improve recording quality with one tap
- **Organization** - Create folders, favorite recordings, search and sort by date/name/duration/size
- **Batch Operations** - Multi-select recordings to share or delete in bulk
- **Location Naming** - Automatically name recordings based on your current location
- **Accessibility** - Full VoiceOver support with accessibility labels, hints, and values on all interactive elements
- **Dark Mode** - Fully adaptive UI with system-aware colors

## Requirements

- iOS 17.0+
- Xcode 15.0+
- Swift 5.0+

## Build Instructions

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/EchoNote.git
   cd EchoNote
   ```

2. Open the project in Xcode:
   ```bash
   open EchoNote.xcodeproj
   ```

3. Select your target device or simulator (iPhone recommended).

4. Build and run (Cmd+R).

No third-party dependencies are required. The project uses only Apple frameworks.

## Permissions

EchoNote requires the following permissions:

- **Microphone** - For recording audio
- **Speech Recognition** - For transcribing recordings
- **Location** - For automatic location-based recording names (optional)

## Project Structure

```
EchoNote/
├── App/
│   ├── EchoNoteApp.swift          # App entry point, SwiftData container setup
│   └── ContentView.swift          # Root tab navigation
├── Models/
│   ├── Recording.swift            # Recording data model
│   ├── RecordingFolder.swift      # Folder data model
│   └── Bookmark.swift             # Bookmark data model
├── ViewModels/
│   ├── RecordingViewModel.swift   # Recording logic and state
│   ├── PlayerViewModel.swift      # Playback controls and state
│   ├── RecordingsListViewModel.swift # List management, search, sort
│   ├── FolderViewModel.swift      # Folder CRUD operations
│   └── EditorViewModel.swift      # Audio editing and transcription
├── Views/
│   ├── Components/
│   │   ├── RecordingRowView.swift  # Reusable recording list row
│   │   └── WaveformView.swift      # Waveform and live waveform visualizations
│   ├── Recording/
│   │   └── RecordingView.swift     # Recording interface with format selection
│   ├── Playback/
│   │   └── PlaybackView.swift      # Full-featured audio player
│   ├── Editing/
│   │   ├── EditorView.swift        # Audio editor with trim, enhance, transcribe
│   │   └── TranscriptView.swift    # Transcript display and generation
│   ├── Organization/
│   │   ├── RecordingsListView.swift # Main recordings list with search/sort
│   │   ├── FoldersView.swift       # Folder management and navigation
│   │   ├── FavoritesView.swift     # Favorited recordings list
│   │   └── MoveToFolderView.swift  # Move recording to folder sheet
│   └── Settings/
│       └── SettingsView.swift      # App settings, storage info, acknowledgments
├── Services/
│   ├── AudioRecorderService.swift  # AVAudioRecorder wrapper
│   ├── AudioPlayerService.swift    # AVAudioPlayer wrapper with waveform generation
│   ├── AudioEditorService.swift    # Audio trimming and enhancement
│   ├── LocationService.swift       # CoreLocation integration
│   └── TranscriptionService.swift  # Speech framework transcription
├── Utilities/
│   └── Constants.swift             # App-wide constants (colors, dimensions, audio settings)
├── Extensions/
│   ├── Color+Hex.swift             # Hex color support
│   ├── Date+Formatting.swift       # Date formatting helpers
│   └── TimeInterval+Formatting.swift # Duration formatting helpers
└── Resources/
    ├── Info.plist                   # App configuration and permissions
    └── Assets.xcassets             # App icons and color assets
```

## Architecture

EchoNote follows the **MVVM** (Model-View-ViewModel) pattern:

- **Models** use SwiftData `@Model` for persistence
- **ViewModels** use `@Observable` for reactive state management
- **Views** are composed of small, reusable SwiftUI components
- **Services** encapsulate platform APIs (AVFoundation, Speech, CoreLocation)

## Tech Stack

| Framework | Purpose |
|-----------|---------|
| SwiftUI | Declarative UI |
| SwiftData | Data persistence |
| AVFoundation | Audio recording and playback |
| Speech | On-device transcription |
| CoreLocation | Location-based naming |
