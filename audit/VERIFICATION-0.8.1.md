# Version 0.8.1 verification

Verified locally on 15 November 2026. Full outcomes: [results-0.8.1.json](results-0.8.1.json).

## Changes

- Purple brand color `#6760CF`, with readable tonal variants in dark mode. Both launcher and main-screen artwork share the same transparent, rounded speech-bubble vector.
- Informational labels use neutral colors. Markdown links and actionable filenames are underlined and use the link color. Saved stars use yellow/gold tones with contrast appropriate to the theme.
- A Bookmarked library view lists conversations containing saved passage bookmarks, independently of Favorites. Saved stars indicate either favorites or passage bookmarks.
- Reader fonts apply to the actual native view after Markdown binding, to collapsed previews, and to full-reply selection. The preference preview also uses the selected font.
- Search-result jumps no longer hide the keyboard during typing or previous/next navigation. Explicitly exiting search or submitting the keyboard search action can still dismiss it.
- Focus mode and its stored-setting behavior are removed. Existing bookmark and reading preferences remain available.
- Prompt grouping uses one pass; saved-library lists use memoization and set-based deduplication; linked URI identifiers and query patterns are cached; heading extraction reuses the rendered string. Scroll measurement invalidation now uses actual window width.
- README records compatibility with LM Studio **0.4.28 (Build 2)**.

## Checks

| Check | Result |
|---|---|
| APK assembly | Passed; version 0.8.1, code 16 |
| Regular unit tests | 22 passed |
| Expanded Android API 35 simulation suite | 45 passed, zero failures/errors; includes the regular suite |
| Native reader fonts | Serif and Mono verified on actual Android TextView instances |
| Search keyboard regression | Visibility and focus retained across query edits and result navigation |
| Appearance | Rounded transparent icon pixels, purple fill, and link underlining verified |
| Bookmarked view | Saved conversation accessible after returning to the library |
| Lint | Zero errors, 30 warnings |
| APK signing | Verified; matches 0.8.0 for install-over updates |
| Physical phone | Not connected; real keyboard variations, launcher appearance, scrolling, and TalkBack remain device checks |

APK SHA-256: `58ad7973e84c882467bc047aac0f8d02dc6d3d6f9e27cc8af1e2805765da41d0`.

All added tests use synthetic content. Simulation limitations and reproducible commands are in [README.md](README.md). No GitHub publication was performed in this change.
