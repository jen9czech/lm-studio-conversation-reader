# Version 0.8.4 verification

Verified locally on 16 November 2026. Detailed outcomes: [results-0.8.4.json](results-0.8.4.json).

## Behavior

- Assistant thinking sections start collapsed, independently of prompt-group expansion, and offer a Show/Hide thinking control. Reopening a conversation starts with thinking closed.
- Thinking uses the same Markdown renderer, typography, links, lists and code formatting as replies. Long thinking sections retain lazy text chunks.
- Recognizes thinking/reasoning styles on saved content blocks, typed thinking/reasoning content, analysis channels and inline think/thinking/analysis/reasoning tags. Literal tags in fenced or inline Markdown code and user prompts remain unchanged. Truncated thinking stays available.
- In-document search includes thinking and expands the matching section before navigating to it. Contents headings can also open the corresponding thinking section.
- Generation statistics remain attached once per original content block. Copy reply and Select text use the final response; expanded thinking has a separate Copy thinking action.
- The search index schema is refreshed once so newly recognized content is indexed. Imported and linked source files remain unchanged.
- Version name 0.8.4, version code 19; Settings update date 2026-11-16.

## Checks

Assembly and all 26 regular tests passed. The expanded Android API 35 simulation suite passed all 54 tests (includes regular tests), with zero failures/errors. Lint has 0 errors and 29 warnings. APK signing matches 0.8.3 for in-place upgrades.

No physical phone was connected; simulation limits are in [README.md](README.md). This version has not been published to GitHub.

APK SHA-256: `56803c6eed07356a25124260a202b24f12baebf4e325398bc7073690e70d7fe9`.
