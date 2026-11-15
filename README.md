# LM Studio Conversation Reader

An offline Android app for reading conversations saved by LM Studio. Compatible with **LM Studio 0.4.28 (Build 2)** conversation JSON files. Browse your chat archive and read formatted prompts and replies without running a model or connecting to LM Studio.

## Features

- **Formatted conversations:** Markdown replies, tables, code blocks, and selectable text with copy actions.
- **Flexible library:** browse a linked folder and its subfolders, or import individual conversation files.
- **Offline full-text search:** search titles and conversation contents in linked and imported files, preview matching excerpts, and open the matching passage. In-document search includes a result counter, previous/next arrows, and accent-insensitive matching.
- **Easy navigation:** browse prompts and Markdown headings in Contents, collapse prompt groups, or use the draggable scroll slider. Sort folders and files by date, title, or length.
- **Pick up where you left off:** Continue reading, recently opened conversations and a dedicated Bookmarked view. A single star adds or removes a conversation. Search and navigation keep a separate return-to-reading position.
- **Comfortable reading:** adjustable text size, bundled Sans-serif/Serif/Monospaced fonts with Settings previews, compact fixed spacing and margins, and light/dark/system themes. Reading positions use text anchors to survive layout changes; linked conversations reopen after activity recreation.
- **Conversation details:** model names, word counts, saved token totals, file information, and generation statistics when available. Open the source text in an external reader.
- **Message versions:** read alternate prompts and replies saved in the conversation.

## Install

Download the APK from [Releases](https://github.com/jen9czech/lm-studio-conversation-reader/releases/latest) and open it on your Android phone. Android 8.0 or later is required. Android may ask you to allow installation from your file manager.

Install updates over the existing app to retain your library and preferences. Published APKs are debug-signed testing builds; in-place updates require the same signing certificate.

## Add conversations

**Link a folder** containing your LM Studio conversation files to browse them in their original folder structure or as a single list. Folder counts include conversation files in nested subfolders. Both folder and flat-list views support sorting; folder length uses the combined word count of descendant files. Linked files open directly and remain unchanged. The app checks the folder at startup and caches file information for faster loading. After copying new files from your PC, restart the app or use **Update index now** in Settings. Indexing shows progress and per-file errors.

**Import individual files** to keep independent local copies in the Imported library. Opening a linked file does not import it.

Both `.json` and `.conversation` filenames are supported, with a 20 MB limit per file. Files must contain LM Studio conversation JSON.

## Privacy and limitations

Conversations stay on your device. The app has no Internet permission, accounts, telemetry, or cloud backup. A private local search index stores searchable text separately from the Imported library. Web and email links open external apps when selected.

This is a reader: it does not generate replies, edit conversations, or synchronize files with your PC. Image attachments and LaTeX rendering are not supported. Word counts and reading progress are approximate. Available metadata depends on what LM Studio saved in the file.

## Build from source

Open the project in Android Studio, or configure JDK 17 and Android SDK 36 with Build Tools 35.0.0. Set the SDK location in an untracked `local.properties` file or through `ANDROID_HOME`.

Windows:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

macOS/Linux:

```sh
sh gradlew assembleDebug testDebugUnitTest lintDebug
```

The Gradle wrapper downloads the required build dependencies. The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

Tests use synthetic conversations and cover parsing, message versions, search, reading anchors, text chunking, scrolling geometry, word counts, folder counts, token totals, and metadata caching. The optional [Android simulation suite](audit/README.md) also covers lifecycle restoration, library search, star bookmarks, legacy bookmark migration, local indexing, and native text rendering. Layout and touch gestures should also be tested on an Android device.

## License

[MIT](LICENSE). Bundled Liberation and Courier Prime fonts use the SIL Open Font License; see [font notices](THIRD_PARTY_NOTICES.md). This is an independent project and is not affiliated with LM Studio.
