# Changelog: Search & Lyrics Fixes (August 2026)

## Overview
This update addresses critical application crashes and restores the core functionality of Lyricify, specifically focusing on search and synchronized lyrics rendering. 

The previous search endpoint (`/apple-music/search`) from the Lyrically API was completely discontinued, which broke the app's ability to search for songs. Furthermore, several critical modules and utility classes were accidentally deleted in past commits, preventing the app from compiling.

## Changes & Fixes

### 1. Replaced Discontinued Search API with iTunes Search API
* The original Lyrically API discontinued Apple Music search endpoints.
* A previous attempt was made to use Spotify search, but it generated Spotify Track IDs which the custom Cloudflare lyrics worker (`https://lyricify.amanraj.workers.dev/lyrics?id=`) could not process, resulting in `HTTP 500` errors.
* **Resolution**: Completely re-wrote `SearchRepository.java` to utilize the public **iTunes Search API** (`https://itunes.apple.com/search?entity=song&term=`). This ensures the app safely retrieves Apple Music IDs for searches, seamlessly integrating with the backend lyrics worker that expects Apple Music IDs. 

### 2. Restored `youLy` Submodule
* Restored the missing `youLy` module which handles synchronized TTML lyrics parsing and the `LyricsWebViewFragment` rendering engine. Without this, the app lacked its core functionality of rendering animated lyrics.

### 3. Restored Networking Utilities
* Recovered `ApiClient.java`, `LyricsConverter.java`, `TtmlParser.java`, and `UpdateManager.java` from git history to their respective correct locations in `aman.lyricify.Networking`.
* Fixed package declarations (`package aman.lyricify;`) in the Networking folder to allow Fragments and Activities (like `MotionAdapter` and `ArtworkBottomSheetFragment`) to discover them correctly.

### 4. Added MotionRepository Fallback
* `MotionRepository.java` and `DirectAppleSearchRepository.java` were missing from the codebase.
* Because the Lyrically API no longer serves Apple Music motion covers directly without custom headers, a fallback `MotionRepository.java` was created. It gracefully rejects fetching motion covers without crashing the application interface.

### 5. Configured Build Environment
* Generated `local.properties` with the correct `ANDROID_HOME` configuration to allow Gradle to successfully build and assemble the debug application.

## Testing & Validation
* Executed `.\gradlew assembleDebug` to verify that all modules are present and fully compile without missing symbol errors.
* End-to-end user flow: `Search -> Select Track -> Fetch Lyrics -> Render Synced Lyrics` was verified to successfully bypass the previous `HTTP 500` network failures.
