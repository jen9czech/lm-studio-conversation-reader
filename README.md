# LM Studio Conversation Reader

**A pocket library for your LM Studio conversations.**

Take your stories, research, coding answers and late-night rabbit holes with you. Conversation Reader turns saved LM Studio chats into a comfortable Android reading experience, with formatted replies, a searchable library and a place waiting for you when you come back. A calm purple interface keeps Library, Recent and Bookmarked within easy reach.

**Private and open source.** Read offline without an account or connection to LM Studio.

[**Download the Android APK**](https://github.com/jen9czech/lm-studio-conversation-reader/releases/latest) · Android 8.0+ · [MIT license](LICENSE)

Compatible with **LM Studio 0.4.28 (Build 2)** conversation JSON files.

## A look inside

<table>
  <tr><th>Browse by model</th><th>Prompt overview</th><th>Bookmarked conversations</th></tr>
  <tr>
    <td><img src="docs/screenshots/models.png" width="260" alt="Models view grouping conversations by model"></td>
    <td><img src="docs/screenshots/prompts.png" width="260" alt="Several prompt previews with reply word counts and nested Markdown headings"></td>
    <td><img src="docs/screenshots/bookmarked.png" width="260" alt="Bookmarked conversations with highlighted stars"></td>
  </tr>
</table>
<table>
  <tr><th>Sort conversations</th><th>Reading view</th><th>Settings</th></tr>
  <tr>
    <td><img src="docs/screenshots/sorting.png" width="260" alt="Conversation sorting options for date, name, length and direction"></td>
    <td><img src="docs/screenshots/reading.png" width="260" alt="Formatted conversation with prompt and Markdown reply"></td>
    <td><img src="docs/screenshots/settings.png" width="260" alt="Settings for linked folder, prompt previews, text size, theme and updates"></td>
  </tr>
</table>

## Give your conversations a second life

- **Bring the whole archive.** Link a folder on your phone and browse its original subfolders, or see every conversation in one list. Titles come from the conversation itself. Sort folders and models by name, and conversations by date, name or length. Models groups linked and imported conversations by the models used, including conversations with several models.
- **Read comfortably.** Enjoy Markdown headings, tables, code blocks and selectable text. Thinking sections start collapsed and use Markdown when expanded. Choose the app’s system font, Times-like Liberation Serif or Courier Prime, adjust the text size, and switch between light, dark and system themes.
- **Find that answer again.** Library search filters the folder names, model names, or conversation titles shown at the current level. In All files, turn on **Include conversation text** to search replies and prompts offline with highlighted snippets. Search inside a conversation with a result counter and previous/next controls.
- **Pick up where you left off.** Continue reading and Recent bring you back to your archive. A single star saves a conversation to Bookmarked. Remembered text positions help you pick up where you left off.
- **Move through long conversations.** Collapse prompt-and-reply groups, jump through prompts and headings in Contents, or drag the scroll slider. Reading progress and navigation stay together at the bottom.
- **Keep the useful details.** Inspect model names, word counts, saved token totals and generation statistics when available. Read alternate saved message versions, copy text or code, and open a source file in an external reader. Hugging Face links open a repository when its identifier is saved, or search by the saved model name.

All of this works on your phone, without running a model. Your next great reading session might already be sitting in your conversations folder.

## Start reading in a minute

1. Download the APK from [Releases](https://github.com/jen9czech/lm-studio-conversation-reader/releases/latest) and open it on your phone. Android may ask you to allow installation from your file manager.
2. Copy your LM Studio conversation files from your PC to a dedicated phone folder, then link that folder in Settings. You can also import individual files.
3. Open a conversation and read. Star the ones you want to revisit.

After copying more files, restart the app to update the library. The app also checks the linked folder after changing folders. Cached metadata speeds up later scans; indexing shows progress and per-file errors.

Install updates over the existing app to retain your library and preferences. Optimized release builds remove unused code and resources. They retain the existing testing signing certificate so current installations can update in place; a production signing-key migration has not been made.

## Your files stay yours

Linked conversations open directly and remain unchanged. Importing an individual file creates an independent local copy in Imported; simply opening a linked conversation does not import it.

The app uses Internet access only when you choose to check GitHub for updates. Conversation files and the search index stay on your device; the app has no accounts, telemetry or cloud backup. Web and email links open external apps only when you select them.

## Compatibility and scope

- Reads LM Studio conversation JSON in `.json` and `.conversation` files, up to 20 MB per file.
- Tested with **LM Studio 0.4.28 (Build 2)** files. Available metadata depends on what LM Studio saved.
- Designed for reading saved conversations. It does not generate replies, edit conversations or synchronize files with your PC.
- Image attachments and LaTeX rendering are not supported. Word counts and reading progress are approximate.

<details>
<summary><strong>Build from source and run checks</strong></summary>

Open the project in Android Studio, or configure JDK 17 and Android SDK 36 with Build Tools 35.0.0. Set the SDK location in an untracked `local.properties` file or through `ANDROID_HOME`.

Windows:

```powershell
.\gradlew.bat assembleRelease testDebugUnitTest lintRelease
```

macOS/Linux:

```sh
sh gradlew assembleRelease testDebugUnitTest lintRelease
```

The Gradle wrapper downloads the required build dependencies. The APK is generated at `app/build/outputs/apk/release/app-release.apk`.

Small updates increment the patch number (for example, 0.8.6 → 0.8.7); larger feature releases may increment the minor number. Every installable update also increases Android’s version code.

Tests use synthetic conversations. The optional [Android simulation suite](audit/README.md) also checks navigation, search, bookmarks, reading-position restoration, indexing and native font rendering. GitHub Actions runs build, test and lint checks. Layout, touch gestures and file-manager integration should also be checked on an Android device.

</details>

## License and credits

[MIT](LICENSE). Bundled Liberation and Courier Prime fonts use the SIL Open Font License; see [font notices](THIRD_PARTY_NOTICES.md).

An independent project, made for LM Studio readers and not affiliated with LM Studio.
