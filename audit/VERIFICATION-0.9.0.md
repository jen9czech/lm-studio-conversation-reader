# Version 0.9.0 verification

Date: 17 November 2026. Detailed totals and APK checksum: [results-0.9.0.json](results-0.9.0.json).

## Design implementation

- Native, calm Material 3 styling based on the approved prototype, retaining the purple brand and light/dark/system modes.
- Library, Recent and Bookmarked bottom navigation; Library retains the last Folders/All files/Models/Imported view. Four visible browsing tabs reflow for large fonts/narrow layouts.
- Tonal folder/model rows with consistent icons, count badges and drill-down chevrons; compact conversation metadata and bookmark icons. Imported-file removal is in its overflow menu with the existing confirmation.
- Smaller Continue reading presentation with the actual title and a one-line preview. A saved reading-progress bar is shown when progress has been recorded; no percentage is invented for older records.
- Sort bottom sheets use one field selector and a direction switch, with concise contextual ordering labels. Conversation lists consistently support Date/Name/Length; folder/model groups retain Name/Conversation count.
- Tonal prompt and Thinking panels, aligned small copy icons and a compact reader navigation surface. Markdown heading sizing and existing collapse/search behavior are retained.
- Settings have section labels, tonal surfaces, compact folder controls, real font previews and theme choices that adapt to large text.

## Verification

All 75 host tests passed, including native Markdown/font rendering, storage, search, bookmark and lifecycle checks. A 200% system-font test verifies library and System-theme text without ellipsis or clipped line bounds. Lint reports zero errors; remaining warnings are recorded in the JSON report.

Four physical-device tests passed on the Motorola edge 60 neo with Android 16/API 36, using the separate quality application ID:

1. Repeated Next/Previous navigation, prompt/Thinking expansion, search, a search-result jump, activity recreation and Back.
2. Model selection and sort context retained across library view changes and returning from the reader.
3. Atomic replacement on the Android filesystem.
4. The redesigned library, model list, model conversations, reader and settings; search focus after successive edits; native screenshot capture.

The final suite ran after the list/sort/settings refinements. Screenshots use neutral synthetic conversations and were visually reviewed. README images were refreshed from these captures.

The APK uses the normal application ID, version code 26, and the same certificate as 0.8.9. The device suite uses a separate app ID and does not clear or access the normal app's library/preferences. No GitHub publication or production-signing migration was performed. Real-device scrolling frame timing, full TalkBack coverage and tablet layouts remain roadmap items; these functional checks are not performance benchmarks.
