# LM Studio Conversation Reader

An offline Android app for reading conversation JSON files saved by LM Studio.

**Latest version: 0.6.0** · Android 8.0+ · Primary test device: Android 16

## Install

Download `Conversation-Reader-0.6.0.apk` from this repository's Releases page and open it on your phone. Android may ask you to allow installation from your file manager. Install updates over the existing app to retain your library and preferences.

The APK is a debug-signed personal testing build. Future in-place updates require the same signing certificate. Signing keys are not included in this repository.

## Features

- Import multiple conversation files, with duplicate detection and a 20 MB file limit.
- Link a phone folder and browse its subfolders, or view all conversation files together.
- Read conversation titles from JSON and show approximate word counts.
- Sort All files by modification date, title, or word count, ascending or descending.
- Open library search using the magnifier beside Settings.
- Read Markdown replies with compact headings, tables, code blocks, and copy actions.
- Inspect model names, token counts, generation speed, time to first token, total generation time, and stop reasons.
- Switch message versions and search individual highlighted occurrences.
- Adjust font size and light/dark/system themes.
- Optionally collapse conversations by prompt, with two-line previews and expandable replies.
- Remember reading position and show approximate reading progress.
- Back first collapses expanded prompt groups, then returns to the previous library view and position.

## Linked folder

Open Settings using the cog, choose Link folder, and select a folder such as `SyncFolder/conversations`. Android's picker grants access to that folder and its subfolders. Both `.json` and `.conversation` filenames are supported.

The folder list scans at app startup and after selecting/changing the linked folder. To see new files copied from your PC, restart the app. There is no background synchronization or periodic scanning.

Folders preserves the original structure; All files supports sorting and filtering. Opening a linked file reads its current contents into an offline snapshot. Those snapshots also appear under Imported. Source files are never modified.

## Privacy and limitations

No Internet permission, accounts, telemetry, or cloud backup. Images and HTML are not loaded. Web/email links open external apps when tapped. Removing an imported item deletes only its local copy; uninstalling deletes the app library and preferences.

Word counts are approximate whitespace-separated counts of prompt/reply text in selected message versions. Reading percentage is approximate. Image attachments, LaTeX rendering, conversation editing, and PC synchronization are not supported. Saved model answers are displayed without factual corrections.

## Build

Open the repository in Android Studio, or configure JDK 17 and Android SDK 36 with Build Tools 35.0.0. Set your SDK path in an untracked `local.properties` file or `ANDROID_HOME`.

Windows:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

macOS/Linux:

```sh
sh gradlew assembleDebug testDebugUnitTest lintDebug
```

The Gradle wrapper downloads Gradle 8.13. Kotlin and Compose dependencies are downloaded automatically. Output: `app/build/outputs/apk/debug/app-debug.apk`.

`build.ps1` additionally supports this workspace's optional project-local tools in `.tools/`.

## Validation

Version 0.6.0 passed 10 unit tests, Android lint with zero errors, and APK signature verification. Unit tests cover parsing, versions, unsupported content, missing metadata, search ranges, reading progress, and word counts. Touch gestures and layout require physical-device testing.

Only synthetic conversation fixtures are included in the repository. Personal conversations, screenshots, signing keys, local SDKs, and build outputs are excluded.

## Source

- `Conversation.kt`: LM Studio JSON parser and internal model.
- `LibraryStore.kt`: imports, linked-folder scanning, and preferences.
- `MainActivity.kt`: library and reader interface.
- `ReaderSearch.kt`: matching and progress calculations.
- `TextLength.kt`: approximate word counts.

This project is an independent reader and is not affiliated with LM Studio. LM Studio's saved JSON format may evolve; unknown fields are ignored and unsupported content receives a placeholder.
