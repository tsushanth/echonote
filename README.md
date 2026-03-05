# EchoNote

A modern voice recording app for **iOS** and **Android**. EchoNote goes beyond basic voice memos with features like audio enhancement, transcription, folder organization, and a full-featured audio editor.

## Features

- **Recording** - Record audio in multiple formats with configurable quality and stereo support
- **Playback** - Variable-speed playback (0.5x-2.0x), skip silence, 15-second skip forward/backward, waveform visualization with tap-to-seek
- **Audio Editing** - Trim recordings with draggable handles, preview trimmed audio, save copies
- **Transcription** - On-device speech-to-text transcription
- **Audio Enhancement** - Improve recording quality with one tap
- **Organization** - Create folders, favorite recordings, search and sort by date/name/duration/size
- **Batch Operations** - Multi-select recordings to share or delete in bulk
- **Location Naming** - Automatically name recordings based on your current location
- **Premium Subscriptions** - Weekly, monthly, yearly, and lifetime tiers
- **Accessibility** - Full VoiceOver/TalkBack support
- **Dark Mode** - Fully adaptive UI with system-aware colors

## Repository Structure

This is a monorepo containing both the iOS and Android apps:

```
EchoNote/
├── ios/                    # iOS app (SwiftUI + SwiftData)
│   ├── EchoNote.xcodeproj/
│   ├── EchoNote/           # Source code
│   └── EchoNoteTests/      # Unit tests
├── android/                # Android app (Kotlin + Jetpack Compose)
│   ├── app/                # App module
│   └── gradle/             # Gradle wrapper
├── shared/                 # Cross-platform shared resources
│   ├── docs/               # Feature parity tracking
│   └── assets/             # Shared design assets
└── model-conversion/       # ML model conversion scripts
```

## iOS

**Requirements:** iOS 17.0+, Xcode 15.0+, Swift 5.0+

```bash
open ios/EchoNote.xcodeproj
# Select target device/simulator, then Cmd+R
```

No third-party dependencies. Uses only Apple frameworks.

| Framework | Purpose |
|-----------|---------|
| SwiftUI | Declarative UI |
| SwiftData | Data persistence |
| AVFoundation | Audio recording and playback |
| Speech | On-device transcription |
| CoreLocation | Location-based naming |
| StoreKit 2 | Subscriptions and IAP |

## Android

**Requirements:** Android 8.0+ (API 26), Android Studio, Kotlin 2.0+

```bash
cd android
./gradlew assembleDebug
```

| Library | Purpose |
|---------|---------|
| Jetpack Compose | Declarative UI |
| Room | Data persistence |
| Media3 / ExoPlayer | Audio playback |
| Hilt | Dependency injection |
| Google Play Billing | Subscriptions and IAP |
| FusedLocationProvider | Location-based naming |

## Architecture

Both platforms follow the **MVVM** (Model-View-ViewModel) pattern:

- **Models** - Data layer (SwiftData on iOS, Room on Android)
- **ViewModels** - Reactive state management (`@Observable` on iOS, `StateFlow` on Android)
- **Views** - Declarative UI components (SwiftUI / Jetpack Compose)
- **Services** - Platform API wrappers (AVFoundation / MediaRecorder, etc.)

## Permissions

| Permission | Purpose |
|------------|---------|
| Microphone | Recording audio |
| Speech Recognition | Transcribing recordings |
| Location | Automatic location-based recording names (optional) |
| Billing / IAP | Premium subscriptions |
