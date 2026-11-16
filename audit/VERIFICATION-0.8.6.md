# Version 0.8.6 verification

Verified locally on 16 November 2026. Detailed outcomes: [results-0.8.6.json](results-0.8.6.json).

- Thinking adds 8 dp visual spacing between rendered paragraphs, using a line-height span. The underlying text and search/reading offsets are unchanged. Fenced code blocks do not receive the extra gaps; ordinary replies remain compact.
- Expanded thinking has an end-of-section Collapse thinking action. It closes that section and scrolls back to its prompt's Thinking toggle; assistant-only messages return to their own action row.
- The reply copy icon is on the same row as Generation details. When a reply has multiple recorded statistics blocks, only the last row shows the copy icon. Prompts and replies without statistics retain a right-aligned copy icon.
- Version 0.8.6, build 22; APK signing matches 0.8.5 for in-place upgrades.

Assembly and all 62 expanded tests passed, including 30 regular tests. Native checks measure the added paragraph height, verify unchanged text and code spacing, exercise the end-collapse action, and compare the vertical centers of Generation details and the copy icon. Lint has 0 errors and 29 warnings.

No physical device was connected; host simulation limitations are in [README.md](README.md). The signed APK is published with the v0.8.6 GitHub release.

APK SHA-256: `53695a94be9167b42c24dfceac0dec8cd8078786ad1b45f1401bee625ef258eb`.
