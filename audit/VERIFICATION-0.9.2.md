# Version 0.9.2 verification

17 November 2026. [Exact results and checksum](results-0.9.2.json).

Conversations open with prompt-and-reply groups collapsed. Saved positions select the corresponding preview without expanding it. Explicit prompt expansion, search-result jumps and Back behavior remain available. The new default is applied once to existing installations; subsequent explicit changes to the Collapse by prompts setting are respected.

All 76 expanded host tests passed. The new regression starts with the old disabled preference and a saved position inside a reply, verifies collapsed opening, expands it, and checks reopening. Release build and lint passed. The optimized APK remains 2,506,132 bytes and was installed over the phone's existing app without clearing data; startup succeeded. The independent optimized-release smoke suite was not rerun for this patch.

No GitHub publication was performed.
