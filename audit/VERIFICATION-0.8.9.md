# Version 0.8.9 verification

Date: 17 November 2026.

## Changes

- The Models view retains the selected model and list states when switching library views; selecting another model starts its conversation list at the top. Group and conversation sorting remain independent.
- Reader layout callbacks check the current jump generation, target, and row key before applying a deferred scroll. A superseded jump cannot apply its old scroll index after the list changes.
- Metadata index and imported conversations use synced sibling temporary files followed by an atomic filesystem replacement. A failed folder scan retains the previous complete index. The host-test provider no longer deletes the index to work around Windows rename behavior.
- Added an optional isolated physical-device test suite and an emulator GitHub Actions workflow. The test application has a separate ID and uses synthetic conversations.

## Physical validation

Passed on the connected Motorola edge 60 neo running Android 16:

1. Prompt expansion/collapse, Thinking expansion/collapse, two complete Next/Previous passes through six prompt groups (20 jumps), in-conversation search across 300 matches, a search-result jump, activity recreation, and returning to the library.
2. Opening a model, switching to Imported and back, returning from its conversation, and navigating Up to the model list.
3. Replacing an existing file atomically on the actual Android filesystem.

The first two workflows ran together; the storage check ran separately after it was added. Device tests target `cz.jan.conversationreader.quality`, not the normal app. No private conversation content is included in fixtures or reports.

These are functional device checks, not scrolling FPS, memory, or startup benchmarks. Production signing and performance benchmarks remain future roadmap items. The new GitHub emulator job has not yet run remotely.

Final host-test totals and APK checksum are in [results-0.8.9.json](results-0.8.9.json).
