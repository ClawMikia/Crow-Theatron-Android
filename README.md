# Crow Théatron

A comprehensive Android video player and media management application with advanced features for video organization, enhancement, and playback.

## Description

Crow Théatron is a feature-rich Android application designed for video enthusiasts who want a powerful yet intuitive media player. The app combines advanced video playback capabilities with sophisticated media management, including folder scanning, video enhancement, playback memory, and a modern user interface.

## Features

### Core Functionality
- **Advanced Video Playback**: Smooth video streaming using ExoPlayer (Media3) with comprehensive playback controls.
- **Local Database**: SQLite database for storing video metadata, preferences, and playback history.
- **Folder Scanning**: Automatic detection and indexing of video files from device storage.
- **Video Library**: Organized video collection with metadata management and search capabilities.
- **Playlists & Chapters**: Create custom playlists and add chapter markers to specific video positions for quick navigation.

### Enhanced Features
- **Video Enhancement**: Built-in tools for real-time adjustment of brightness, contrast, saturation, hue, and sharpness.
- **Timeline Trimming**: Precise start and end point selection for videos to focus on specific segments.
- **Audio & Subtitle Control**: Audio boost, equalizer presets, and highly customizable subtitle settings (offset, size, background).
- **Playback Memory**: Resume videos from where you left off across sessions with per-video preference persistence.
- **Multiple UI Themes**: Modern, responsive interface with a dark "Crow" aesthetic.

### User Interface
- **Main Activity**: Central hub for navigation (Home, Library, Favorites, Memory, Explore).
- **Player Activity**: Full-featured video player with gesture controls and advanced overlay panels.
- **Library View**: Organized video browsing by folder, recently played, or favorites.
- **Settings Screen**: Comprehensive app configuration for display and playback defaults.

## Technical Stack

- **Language**: Kotlin
- **Platform**: Android (API 26 - 36)
- **Database**: SQLite with custom database helper (CrowDbHelper)
- **Media Player**: ExoPlayer (Media3) for advanced video playback
- **Architecture**: Android MVC/Repository pattern with ViewBinding
- **Build System**: Gradle with Kotlin DSL and Version Catalogs
- **UI Framework**: Android Views with Material Design 3 components
- **Coroutines**: Kotlin Coroutines for asynchronous operations

## Project Structure

```
CrowTheatron/
├── app/
│   ├── src/main/
│   │   ├── java/com/crowtheatron/app/
│   │   │   ├── data/           # Database, Models, and Repository
│   │   │   │   ├── CrowDbHelper.kt      # SQLite schema and operations
│   │   │   │   ├── VideoEntity.kt       # Core video data model
│   │   │   │   └── VideoRepository.kt   # Data access abstraction
│   │   │   ├── player/          # Video player implementation
│   │   │   ├── library/         # Library and playlist management
│   │   │   ├── enhancement/    # Video processing and filters
│   │   │   ├── settings/        # App configuration
│   │   │   └── ui/              # Custom views and themes
│   │   ├── res/
│   │   │   ├── layout/          # XML layouts (activity_player.xml, etc.)
│   │   │   ├── drawable/        # Vector icons and backgrounds
│   │   │   └── values/          # Strings, colors, and themes
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml      # Centralized dependency management
└── README.md
```

## Key Components

### Data Layer
- **CrowDbHelper.kt**: Manages multiple tables including `videos`, `chapters`, and `playlists`. Handles complex metadata and per-video preference storage.
- **VideoEntity.kt**: Data class representing a video with over 30 properties including playback state, enhancement filters, and subtitle preferences.
- **FolderScanner.kt**: Automated media discovery using `DocumentFile` and content resolvers.

### Feature Modules
- **Player Module**: Implements ExoPlayer with custom `TextureView` rendering and `enhancementOverlay`.
- **Enhancement Module**: Provides real-time GL-based filters or software adjustments for video quality.
- **Trim & Memory**: Logic for managing playback segments and persistence of last-watched positions.

## Recent Improvements

- **Timeline Trimming**: Added interactive seekbars and fine-tune buttons for setting video start/end points.
- **Chapter Support**: Implemented ability to add, edit, and navigate via video chapters.
- **Playlist Management**: Added infrastructure for creating and managing custom video playlists.
- **Advanced Subtitles**: Enhanced subtitle customization including offset adjustments and styling.
- **UI Refresh**: Updated `activity_player.xml` with modern card-based control groups and Orbitron font integration.

## Database Schema

The SQLite database (`crow_theatron.db`) stores:
- **Video Metadata**: URI, duration, size, resolution, and format.
- **Playback State**: Last position, favorite status, and "last played" timestamps.
- **Visual Filters**: Per-video settings for brightness, contrast, zoom, crop, etc.
- **Audio/Subtitles**: Volume, pitch/key, speed, subtitle tracks, and offsets.
- **Organizational Data**: Custom chapters and playlist associations.

## Getting Started

### Prerequisites
- Android Studio Ladybug or later
- Android SDK (API level 26+)
- JDK 17

### Installation
1. Clone the repository.
2. Open in Android Studio.
3. Sync Gradle and run on a device with API 26+.

## App Features Showcase

### 🎬 Advanced Player
- **Gesture Controls**: Vertical swipes for brightness/volume, horizontal for seek, pinch-to-zoom.
- **Picture-in-Picture**: Seamless multi-tasking support.
- **Playback Speed & Pitch**: Adjust speed from 0.25x to 3.0x and pitch in semitones.

### 🎨 Visual & Audio Suite
- **Real-time Enhancement**: Toggleable filters for better clarity.
- **Equalizer**: Presets for different audio profiles.
- **Zoom/Crop**: Adjust aspect ratio to fit any screen.

### 📚 Smart Library
- **Automatic Indexing**: Background scanning of selected folders.
- **Continue Watching**: Quick access to partially watched videos.
- **Global Search**: Search across all indexed folders.

---
© 2024 Christopher Lee Cajes. All rights reserved.
