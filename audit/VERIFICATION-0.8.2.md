# Version 0.8.2 verification

Verified locally on 15 November 2026. Detailed results: [results-0.8.2.json](results-0.8.2.json).

- One conversation-level star replaces Favorites and passage bookmarks. Every star uses the same state and toggle behavior, including the Bookmarked view. Notes, labels, export, and the separate Bookmark action are removed.
- Former Favorites and passage bookmarks migrate to Bookmarked. Explicit removal overrides legacy values, so saved items do not reappear after reopening the app.
- Reading uses fixed 1.0 line spacing, 4 dp side margins, reduced chunk padding, and compact Markdown block spacing. The spacing/margin selectors are removed, and older spacing preferences no longer override these defaults.
- Font choices are Sans-serif (default), Serif (Times-style), and Monospaced (Courier-style), using Android font families. All three are checked on native text views. Legacy font values migrate to the corresponding new choice.
- Reader actions share one top toolbar: Search, Contents, Star, Reading preferences. The percentage shares a single bottom line with Previous, End and Next; collapsed/search views keep progress while hiding prompt navigation. A native geometry test checks both rows.
- The conversation title uses the readable purple link color and continues to open Conversation info.
- README reflects the simplified features; LM Studio 0.4.28 (Build 2) compatibility remains documented.

## Validation

APK assembly passed. All 22 regular tests and all 46 expanded tests passed (expanded count includes regular tests). Lint has 0 errors and 29 warnings. Version identity is 0.8.2, code 17. APK signing matches 0.8.1 for install-over updates.

No physical device was connected; host simulation limitations are listed in [README.md](README.md). No GitHub publication was performed.

APK SHA-256: `c5e45cca028385352d4e35be926b898475dfc40452cff547daf9f404ec797a4d`.
