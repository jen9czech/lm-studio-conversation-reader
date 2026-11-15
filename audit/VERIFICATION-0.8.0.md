# Version 0.8.0 verification

Verified locally on 15 November 2026. Machine-readable outcomes: [results-0.8.0.json](results-0.8.0.json). The original [0.7.4 assessment](APP_REVIEW.md) is retained as the baseline.

## Implemented improvements

- **Reading restoration:** lightweight linked-file references survive activity recreation. Message/version/part/character anchors restore passages after layout changes. Search and navigation retain a separate Return to reading position.
- **Long conversations:** selected versions parse on a background dispatcher; alternatives render when requested. Long replies use stable smaller text chunks in the lazy list. Native selection across a whole reply remains available through Select text, with copy actions for replies and code.
- **Scroll geometry:** cached cumulative extents and binary search replace repeated whole-list size calculations during slider movement. Measurements reset when content order or reading layout changes; the drag target is 48 dp wide.
- **Contents:** prompts and Markdown headings navigate directly to the corresponding passage, expanding its prompt group as necessary.
- **Search:** a private SQLite full-text index covers linked and imported content, including alternate versions. Lists show snippets and open in-document matching results. Searches are debounced and run in the background; reader matching ignores Czech accents.
- **Reading library:** Continue reading, Recent, Favorites, passage bookmarks, optional notes, editing/removal, and user-selected Markdown bookmark export.
- **Reading comfort:** font selection, line spacing, margins, and focus mode, alongside the existing text-size and theme controls. Theme choices expose selected/radio semantics.
- **Library maintenance:** progressive indexing with counts and per-file errors, a Settings action to update the index, and parent-folder Back navigation with saved folder positions. Automatic scans remain limited to startup and linked-folder changes.
- **Release checks:** a GitHub Actions workflow builds, runs regular and Android simulation tests, and runs lint. It uploads verification reports without publishing an APK or signing material.

## Results

| Check | Outcome |
|---|---|
| Debug APK assembly | Passed |
| Regular JVM tests | 21 passed |
| Expanded JVM / Android API 35 simulation suite | 40 passed, zero failures/errors |
| Linked-file recreation regression | Passed after scrolling deep into a long conversation; font-change passage check also passed |
| Full-text search, bookmarks, favorites, native text chunks | Passed |
| Lint | Zero errors; 32 warnings, principally dependency-update and Kotlin convenience suggestions |
| APK identity | `cz.jan.conversationreader`, version 0.8.0, version code 15 |
| Signing | Verified; same certificate as 0.7.4 for install-over updates |
| Network access | APK declares no Internet permission |
| GitHub workflow execution | Not run remotely; workflow is prepared locally |
| Physical phone / emulator | Not available in this session |

The expanded count includes the 21 regular tests. All added fixtures are synthetic. Host native-layout checks verify chunk sizes and text preservation; they do not measure phone frame timing or establish a performance comparison with other apps.

## Upgrade notes and remaining device checks

The first scan builds the new local full-text index and therefore reads existing files once. Later scans reuse unchanged metadata and indexed content. Linked files remain outside Imported and their source files are unchanged.

Older saved positions migrate to the corresponding message. Their old pixel offsets cannot be retained exactly after switching to text chunks; subsequent reading positions use character anchors.

Install over 0.7.4 and check scrolling/flinging, rotation, narrow-screen and large-font layouts, TalkBack, real storage permission revocation, external text readers/file managers, and bookmark export on the Motorola. The host-specific storage adapters and their limits are documented in [README.md](README.md).

APK SHA-256: `49781a9712680b013ab1c74049ac54ca2e649037ba3fe53f82047bd354545645`.
