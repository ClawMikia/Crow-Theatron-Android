# 🐦 Crow Théatron

**Crow Théatron** is a high-performance, futuristic Android video player and media management suite. Designed for power users and video enthusiasts, it blends a striking cyberpunk aesthetic with advanced technical controls for playback, audio-visual enhancement, and precise timeline management.

---

## ✨ Features

### 🎬 Advanced Video Playback
- **Media3/ExoPlayer Core**: High-efficiency playback with support for various video formats.
- **Picture-in-Picture (PiP)**: Multi-task without interrupting your viewing experience.
- **Smart Transport Controls**: Comprehensive controls including Play/Pause, Rewind/Forward, Next/Prev, Stop, and a dedicated **Restart** function.
- **Immersive Fullscreen Overlay**: A dedicated fullscreen mode with:
  - Persistent seekbar with skip markers.
  - Quick-access screen rotation.
  - Independent transport controls.
  - Toggleable UI panels for an unobstructed view.

### 🎨 Visual & Audio Suite
- **Cyberpunk UI Aesthetic**: A dark-mode interface featuring neon color accents (Neon Pink, Cyan, Red, Green, Yellow).
- **Pitch / Key Adjustment**: Precision audio tuning from **-6 to +6 semitones**, allowing for real-time key shifting without affecting speed.
- **Playback Speed**: Variable speed control from **0.5x to 2.0x** with quick reset capability.
- **Advanced Volume Control**: Percentage-based volume adjustment with boost, mute, and a one-tap reset to 100%.
- **Visual Enhancements**: Real-time post-processing filters selectable via a quick-access menu to improve clarity and style.

### ✂️ Timeline & Skip Management
- **Precision Timeline Trim**: Interactive seekbars for setting **Start and End Points** with fine-tuning buttons for focus.
- **Timeline Skips**: Define and manage specific segments to skip automatically during playback.
- **Visual Skip Markers**: See your trim and skip segments directly on the playback seekbar.

### 📚 Smart Media Library
- **Integrated Mini Player**: Continue watching in a docked mini-player while browsing your collection.
- **Folder-Based Indexing**: Automatic scanning and organization of video folders.
- **Playback Memory**: Per-video preference persistence, including last position, pitch, speed, and enhancement settings.
- **Favorites & Playlists**: Quick-mark videos as favorites or organize them into custom playlists.

---

## 🛠️ Technical Stack

- **Language**: Kotlin 2.0+
- **Platform**: Android 8.0 (API 26) - Android 15+ (API 36)
- **Engine**: [Media3 ExoPlayer](https://developer.android.com/guide/topics/media/exoplayer)
- **Database**: SQLite with custom **CrowDbHelper** for metadata and per-video state.
- **UI Framework**: Android Material Components with **ConstraintLayout** and **View Binding**.
- **Concurrency**: Kotlin Coroutines for non-blocking file scanning and DB operations.

---

## 📂 Project Structure

```text
CrowTheatron/
├── app/
│   ├── src/main/
│   │   ├── java/com/crowtheatron/app/
│   │   │   ├── data/           # SQLite schema, Models, and Repository
│   │   │   ├── player/          # Video player implementation (ExoPlayer)
│   │   │   ├── library/         # Library and playlist management
│   │   │   ├── ui/              # Custom views and System UI helpers
│   │   │   └── util/            # Media extraction and format utilities
│   │   ├── res/
│   │   │   ├── layout/          # XML layouts (Futuristic neon UI)
│   │   │   ├── drawable/        # Custom vector icons and neon backgrounds
│   │   │   └── values/          # Themes, colors, and Orbitron font definitions
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2.1) or newer.
- Android SDK API level 26+.
- Java 17.

### Installation
1.  **Clone the Repository**:
    ```bash
    git clone https://github.com/your-repo/CrowTheatron.git
    ```
2.  **Open in Android Studio**: Wait for Gradle sync to complete.
3.  **Run**: Deploy to a physical device or emulator running API 26 or higher.

---

© 2024 Christopher Lee Cajes. All rights reserved.
