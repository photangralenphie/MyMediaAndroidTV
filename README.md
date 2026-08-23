# MyMedia for Android TV

A remote-first streaming client for the local MyMedia REST API. The app checks `/api/v1/health` before loading library content and opens the IP/port setup screen whenever the server cannot be reached.

## Run

1. Open this folder in Android Studio Quail 3 or newer.
2. Let Android Studio sync the Gradle 9.5.0 / AGP 9.3.0 project.
3. Run the `app` configuration on an Android TV or Google TV device (Android 8.0/API 26 or newer).
4. Enter the IP address and port of the machine hosting MyMedia. The TV and server must be reachable on the same network.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Included

- TV sidebar navigation with Library, Movies, TV Shows, dynamic Pinned items, and bottom-anchored Settings
- Unwatched, genres, collections, favorites, search, movies, TV shows, and pinned discovery
- Year, runtime, favorite, watched, multi-genre, genre-kind, and search-scope filtering
- Movie, TV show, episode, person, and collection detail screens showing all API metadata
- Linked show episodes, credited people, person filmographies, parent shows, and collection items
- Byte-range compatible movie and episode playback through the API video endpoint
- Embedded audio-stream and subtitle selection from the TV playback controls
- System/light/dark appearance and eight selectable accent colors
- 2:1 artwork presentation, D-pad focus scaling, launcher icon, and Android TV banner
