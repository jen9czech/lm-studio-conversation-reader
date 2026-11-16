# Version 0.8.5 build 21 verification

Verified locally on 16 November 2026. Detailed outcomes: [results-0.8.5.json](results-0.8.5.json).

- Copy thinking and Select text actions are removed. Prompt/reply copy actions are right-aligned icons with accessible labels. Thinking remains available only for non-empty thinking content.
- The supplied JSON fragment exposed list lines indented by four to eight spaces, which CommonMark treated as code and displayed literal emphasis markers. A non-sensitive fixture with the same indentation reproduced the failure before the fix. Thinking-only normalization now removes accidental standalone-list indentation, preserves relative nested-list structure, and protects fenced code. Actual native reader tests verify rendered text and formatting spans, not merely the parsed model.
- Existing delimiter and whole-section Markdown-fence normalization remains in place. Source JSON files are unchanged.
- Folders search matches file names, conversation titles and paths only, and does not query the full-text index. Matching descendants keep their parent folders navigable. Opening a metadata-filtered result opens normal reading mode.
- All files also queries the local full-text index. Search hints communicate these scopes. Native tests switch between views with a body-only query and verify different results.
- Settings identifies version 0.8.5, build 21, to distinguish this tested update from earlier builds with the same version name.

Assembly and all 61 expanded tests passed, including 30 regular tests. Lint has 0 errors and 29 warnings. Signing matches earlier 0.8.5 builds for in-place upgrades.

The supplied text was not copied into tests or project files; only neutral text with equivalent indentation was used. No physical device was connected. This update has not been published to GitHub.

APK SHA-256: `eb41274a89e47510e630ab98a0be8e57748815bbdc9705a69ab85fd69311b9af`.
