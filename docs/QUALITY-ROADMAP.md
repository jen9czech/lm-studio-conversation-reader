# Quality roadmap

Direction: balanced polish across the app, requested 16 November 2026. Keep the offline, private LM Studio reading workflow. Improve reliability, reading comfort, library navigation, accessibility, and performance together.

## Verified baseline

- Current local build: 0.8.8 (Android version code 24), installed on the connected Motorola.
- 72 host tests pass; the suite includes Robolectric/native rendering checks. This is not evidence of measured phone scrolling performance.
- A real-device crash log identified Material3 child-semantics traversal after a lazy row detached. 0.8.8 uses the existing Compose UI legacy semantics path to avoid eager semantic-autofill recalculation; standard accessibility remains enabled. This workaround needs a tracked follow-up and real-device regression coverage.
- Library metadata is cached and unchanged files are reused. Full-text search uses a local SQLite FTS index.
- Reading text is split into blocks, but the reader currently renders all message versions selected for the conversation before publishing the rendered map. Large-conversation memory and time to first readable content need measurement.
- The APKs are currently debug-signed. No production signing-key migration has been performed.
- Models drill down into conversation lists. Sorting is separated between groups and conversations. Search matching and state handling still differ across views.

## Delivery order

Small focused changes increment the patch version: 0.8.9, 0.8.10, and so on. A broader feature release can use a minor increment. Every installable APK increments Android versionCode. No fixed schedule is promised.

### First: reliability and navigation

- Add Android-device regression coverage for Next/Previous, prompt previews, Thinking, alternate versions, search, Back/Up, and activity recreation using synthetic fixtures.
- Verify that leaving a conversation returns to its original library view, group, sort, query, and position. Preserve that state when switching views, rotating, or recreating the activity.
- Consolidate reader jump handling so superseded jumps cannot apply stale view/layout callbacks.
- Track the semantics workaround against a minimal reproduction and evaluate compatible stable framework fixes; remove it only after device validation.
- Handle revoked folder permission, moved files, unreadable JSON, and interrupted indexing with actionable recovery messages.
- Use atomic replacement for persisted metadata and retain the last valid index if a refresh is interrupted.

Acceptance: repeated complete Next/Previous passes survive on the physical device; no new app crash; saved reading positions remain within one rendered line on recreation and font changes. Test fixtures must never overwrite the user's conversations or reset their preferences.

### Next: performance

- Benchmark cold startup, cached-library opening, first readable text, search latency, prompt jumps, memory, and scroll-frame timing on the Motorola.
- Use synthetic libraries and several conversation sizes; report device, Android version, build type, sample count, median, and p95. Distinguish first scan from unchanged refresh.
- Publish readable content progressively and bound rendered-text work/memory; prioritize the current passage and nearby blocks.
- Keep indexing and parsing off the main thread. Measure before changing the cache/index design.
- Generate Baseline Profiles for startup, library navigation, opening a conversation, and scrolling when a benchmarkable release build is available.

Acceptance: establish reproducible measurements first, then compare every optimization to that baseline. Aim for frames within the active display refresh budget (16.7 ms at 60 Hz, 8.3 ms at 120 Hz); record actual results rather than claiming smoothness from unit-test timings. Provisional search target: p95 below 200 ms after debounce on a cached synthetic 1,000-conversation library; revise only with recorded evidence.

### Then: coherent visual and interaction polish

- Define shared spacing, typography, colors, icon sizes, action rows, breadcrumbs, and empty/loading/error states.
- Keep one appropriate sort control per browsing level. Make sort field/direction and active search scope clear.
- Preserve stable layouts while counts, progress, search matches, or expanded sections change; avoid visible jumps.
- Keep compact visual controls with comfortable accessible touch targets.
- Verify short/long titles, two-line model names, absent metadata, and narrow screens using neutral fixtures.
- Give library search a clear matching-result count and relevant snippets. Keep match-navigation arrows inside conversation text; review library-result arrows separately before changing behavior.

Acceptance: consistent actions and hierarchy across all views; no clipped essential controls at 200% font scale; clear distinction between informational text, links, selection, and search highlights in light/dark modes.

### Accessibility and adaptation

- Test TalkBack traversal, labels, selection state, result announcements, and focus after expanding/collapsing sections.
- Respect system font scaling, display size, contrast, reduced-motion preferences, keyboard navigation, and system insets.
- Exercise portrait, landscape, split screen, and a larger screen before designing a two-pane layout.

Acceptance: all primary workflows are possible without relying on icon appearance or color alone; focus does not disappear when rows are replaced or scrolled out.

### Release quality

- Add a real Android emulator/device job alongside existing host checks. Keep logs and reports free of private conversation content.
- Prepare a production-signed optimized build and a tested update/data-preservation strategy. Do not replace the current signing certificate or require uninstalling the app without an explicit migration decision.
- Keep APK, source tag, version, release notes, and checksum aligned. Publish only after checks pass.
- Review lint warnings and dependency maintenance by relevance; avoid broad dependency upgrades without regression evidence.

Acceptance: reproducible package, tested in-place update strategy, meaningful changelog, and an explicit distinction between simulated and physical-device validation.

## Useful scope after the foundation

Potential additions: user-controlled backup/restore of reading state, read/unread status, richer document support, and tablet navigation. Evaluate each against actual reading needs rather than using feature count as a quality measure. Retain no telemetry, no automatic cloud uploads, and no modification of linked source files.

## References

- Android core app quality: https://developer.android.com/docs/quality-guidelines/core-app-quality
- Baseline Profiles: https://developer.android.com/topic/performance/baselineprofiles/overview

## Progress: 0.8.9, 17 November 2026

Implemented model-view state preservation, stale-jump callback guards, atomic file replacement, and an isolated device test suite. Three checks passed on the Motorola (two UI workflows plus one Android filesystem test). Added an emulator CI workflow; remote execution awaits publication. The remaining roadmap still includes performance measurements, broader accessibility/adaptive checks, and a production-signing strategy.

## Progress: 0.9.0, 17 November 2026

Implemented the approved calm Material 3 visual direction with bottom library navigation, four visible browsing modes, tonal group/conversation rows, sort sheets, compact reading controls, and grouped settings. Validation: 75 host tests (including 200% system font scale) and four isolated tests on the Motorola/Android 16 passed. Neutral device screenshots are used in the README. Performance benchmarking, broader adaptive/TalkBack checks and production signing are still separate milestones.
