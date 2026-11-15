# LM Studio Conversation Reader

An offline Android app for reading conversations saved by LM Studio. Browse your chat archive and read formatted prompts and replies without running a model or connecting to LM Studio.

## Features

- **Formatted conversations:** Markdown replies, tables, code blocks, and selectable text with copy actions.
- **Flexible library:** browse a linked folder and its subfolders, or import individual conversation files.
- **Search and sorting:** find highlighted text within conversations and sort your library by date, title, or length.
- **Comfortable reading:** adjustable text size, light and dark themes, collapsible prompt groups, and remembered reading position.
- **Conversation details:** model names, word counts, file information, and generation statistics when available.
- **Message versions:** read alternate prompts and replies saved in the conversation.

## Install

Download the APK from [Releases](https://github.com/jen9czech/lm-studio-conversation-reader/releases/latest) and open it on your Android phone. Android 8.0 or later is required. Android may ask you to allow installation from your file manager.

Install updates over the existing app to retain your library and preferences. Published APKs are debug-signed testing builds; in-place updates require the same signing certificate.

## Add conversations

**Link a folder** containing your LM Studio conversation files to browse them in their original folder structure or as a single list. Linked files open directly and remain unchanged. The app checks the folder at startup and caches file information for faster loading. Restart the app after copying new files from your PC.

**Import individual files** to keep independent local copies in the Imported library. Opening a linked file does not import it.

Both `.json` and `.conversation` filenames are supported, with a 20 MB limit per file. Files must contain LM Studio conversation JSON.

## Privacy and limitations

Conversations stay on your device. The app has no Internet permission, accounts, telemetry, or cloud backup. Web and email links open external apps when selected.

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

Tests use synthetic conversations and cover parsing, message versions, search, reading progress, word counts, and metadata caching. Layout and touch gestures should also be tested on an Android device.

## License

[MIT](LICENSE). This is an independent project and is not affiliated with LM Studio.
