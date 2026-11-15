> Historical assessment of version 0.7.4. The recommendations are implemented in 0.8.0; see [release verification](VERIFICATION-0.8.0.md) for current results.

# Conversation Reader: test findings and improvement plan

Assessed version: **0.7.4**. Date: **15 November 2026**.

The app has a useful foundation for its specific purpose: direct LM Studio file reading, offline operation, versions, readable Markdown, prompt collapse, and generation metadata. The next development work should prioritize reliable resume behavior, long-text performance, and navigation through large archives.

## What was actually tested

| Check | Result | Scope |
|---|---|---|
| Regular build and existing tests | Passed | 18 existing logic tests; APK assembly |
| Android lint | 0 errors, 24 warnings | Existing style, dependency, and configuration findings |
| Expanded audit suite | 29 passed, 1 failed | 30 total tests, including the original 18 |
| Android simulation | API 35, 400 × 850 dp profile | Robolectric native graphics and Compose UI testing |
| Physical Motorola / Android 16 | Not tested | No phone or emulator connected |
| Competitor comparison | Official documentation reviewed | Competitor apps were not installed or benchmarked |

The passing simulations include main search and Back, Settings/theme access, imported-file reading and in-document search, prompt collapse/Back behavior, native Markdown and Czech text, cache reuse and invalidation, corrupt-file fallback, and opening linked files without creating imports.

The storage simulation counted document reads: the initial scan opened the file once, an unchanged scan did not reopen it, and a changed file was reread. A synthetic archive of 1,000 messages also parsed and produced 1,000 case-insensitive search matches.

The optional test harness has Windows-specific provider and file-replacement adapters. See [README.md](README.md) for their exact limits. Production file permissions and sharing still require Android device testing. [results.json](results.json) preserves the individual outcomes.

## Reproduced defect: linked conversation lost on activity recreation

**Priority: high. Evidence: failing Android simulation and matching source behavior.**

The test opens a linked conversation, verifies the reader is visible, recreates the activity, and checks that the reader remains open. The app instead returns to the library. Its active linked entry is kept only in `remember`, so it is not restored with the activity. Android may recreate activities on rotation or configuration changes.

Suggested fix: save a lightweight reference to the active file and its source, reopen it after recreation, then restore the reading anchor and expanded prompt group. Handle a moved/deleted file with a clear message and return path.

Acceptance check: open a linked file, scroll into a reply, rotate the phone, change system appearance, background/restore the app, and simulate process recreation. The same conversation should return to the same passage, without adding an Imported copy.

## Performance findings: confirmed work, device impact unmeasured

### 1. Replies and alternate versions are rendered eagerly

The reader constructs Markdown spans for every part in every version when it opens a conversation. A long reply is then displayed in one large native TextView. The synthetic native-layout tests produced views tens or hundreds of thousands of pixels tall. This is evidence of large layout work, but the host timings are not phone measurements.

Suggested improvement: render the selected versions first, defer alternate versions, parse away from the UI thread where safe, and divide long replies into stable paragraph/heading/code blocks. Cache rendered blocks with document/version/theme keys. Preserve text selection, code blocks, and search offsets while doing this.

Acceptance check: on the Motorola, test approximately 40 KB, 200 KB, and multi-megabyte synthetic conversations. Record opening latency, peak memory, and slow/janky frames. Repeated swipes should not repeatedly reparse the document.

### 2. Scrollbar geometry does whole-list work on scroll updates

The custom slider rebuilds an item-size list and sums prefixes as layout information changes. It also estimates unseen item heights. The cache is keyed to the list state rather than content order, font size, width, and expanded groups. Sorting or changing text layout can therefore leave stale estimates for off-screen items.

Suggested improvement: use stable content keys, invalidate measurements when layout changes, and cache cumulative sizes. Keep a thin visual thumb with a generous effective drag target. Check the actual target against Android's [48 dp touch-target guidance](https://developer.android.com/guide/topics/ui/accessibility/apps).

Acceptance check: fling and drag in a 1,000-file list, a long reply, and a collapsed conversation. Change font size, rotate, change sorting, and repeat. The thumb should remain stable and reach the beginning and end without unexpected jumps.

### 3. Search runs synchronously as the query changes

In-document matching runs across all rendered message versions for each query change. This is fine for small conversations, but large archives and short common queries deserve testing. Library indexing and sorting also perform work in the UI composition.

Suggested improvement: use a short typing debounce, cancellation of stale queries, and background matching/index queries. Keep result count and selected hit consistent when the data changes.

## Comparison with mature reading experiences

These are UX references, not substitutes claimed to support LM Studio JSON.

| Reference | Useful documented capability | Best adaptation for this app |
|---|---|---|
| [Readwise Reader](https://docs.readwise.io/reader/docs/faqs/searching) | Full-library text search and separate in-document search | Search reply contents across linked files, with snippets and a direct jump to the match |
| [Readwise reading behavior](https://docs.readwise.io/reader/docs/faqs) | Separates furthest reading progress from last location | Preserve the reading position while searching or jumping around |
| [Moon+ Reader](https://www.moondownload.com/) | Detailed typography, themes, bookmarks, and reading controls | Add line spacing, reading margins, a font choice, and bookmarks |
| [Obsidian Outline](https://obsidian.md/help/plugins/outline) | Heading-based navigation | Build a contents sheet from prompts and Markdown headings |
| [Obsidian Bookmarks](https://obsidian.md/help/plugins/bookmarks) | Saved navigation targets | Bookmark a reply or passage for quick return |

Readwise's [appearance documentation](https://docs.readwise.io/reader/docs/faqs/appearance) also describes a dedicated long-form reading layout. An optional focus mode that reduces fixed header/footer space would be useful for the long stories this app is used to read.

## Recommended improvements, in order

| Order | Improvement | Benefit | Relative effort |
|---|---|---|---|
| 1 | Restore linked reader state across recreation | Prevents losing the open conversation | Small–medium |
| 2 | Lazy long-reply rendering and cheaper scrollbar geometry | More reliable responsiveness as files and libraries grow | Medium–large |
| 3 | Stable reading anchors and a “Return to reading” action | Search, font changes, and navigation do not overwrite the user's place | Medium |
| 4 | Prompt-and-heading contents sheet | Quickly reach a chapter or answer without dragging through long text | Medium |
| 5 | Consistent offline full-text library search | Find content in linked files as well as Imported | Medium–large |
| 6 | Continue reading, recently opened, and favorites | Makes a growing archive easier to use | Small–medium |
| 7 | Passage bookmarks and optional notes | Saves useful answers and places in stories | Medium |
| 8 | Line spacing, font choice, margins, and focus mode | Improves long-form reading comfort | Small–medium |

The effort labels describe implementation scope, not delivery promises.

### Search consistency

Imported search currently covers conversation content; linked-file search covers titles and paths. The same search control therefore has different scope depending on the tab. Make that scope explicit, and then add an optional local content index with result snippets. Keep index data separate from Imported so searching does not create conversation copies. Czech accent-insensitive matching could be a useful optional setting.

### Reading anchors and navigation

Current reading positions are message indexes plus pixel offsets. Those offsets do not represent the same paragraph after text size or width changes. Save a message/paragraph anchor and a nearby character offset instead. Preserve a separate “last reading place” before a search jump.

Android Back in a folder should navigate to its parent before leaving the app. The current main-screen Back handlers cover reading and search, while folder navigation uses a separate Up action. Also consider resetting each newly entered folder to the top while preserving the parent's scroll position.

### Indexing feedback and file changes

Warm-cache reuse works. The first scan still waits for the full result set, so a large folder initially displays a spinner. Stream discovered files into the list and enrich titles/counts progressively, with a useful “indexed n of N” status. Record per-file errors rather than presenting every malformed/unreadable file as an ordinary entry with missing length.

Keep the user's chosen policy: scan at startup or after a folder change. An optional rescan action inside Settings would permit a refresh without restarting, while leaving the main screen uncluttered. Background polling is not part of this recommendation.

### Accessibility and release quality

Theme selection is communicated visually through surface colors/borders; add selected/radio semantics so assistive technology can announce the current theme. Verify slider discoverability when it fades away, text selection across blocks, large system fonts, contrast, and narrow-screen controls with TalkBack and a device accessibility scanner.

Add CI checks for the regular suite and the new lifecycle regression. Use reproducible release builds and retain the existing signing identity for install-over updates. Keep signing material outside the repository.

## Suggested next milestone

Start with lifecycle restoration, stable reading anchors, and a measured scrolling pass. Then add the contents sheet and local full-text search. This sequence gives the largest improvement in everyday reading and navigation while preserving the app's offline, direct-file design.

## Device acceptance checklist

1. Install over the existing version and verify imported files and reading preferences remain.
2. Open linked and imported conversations; repeat after rotation and system recreation.
3. Fling/drag through short and long replies, collapsed groups, and large libraries.
4. Search for zero, one, repeated, Czech, and alternate-version matches; clear, exit, and return to the original passage.
5. Change font size and theme while midway through a reply; verify the reading anchor and thumb.
6. Revoke/move the linked folder; replace, rename, delete, or temporarily truncate a source file; confirm recoverable messages.
7. Open a filename in an external text reader and navigate to its folder; test with more than one installed handler.
8. Test TalkBack, large system text, and effective touch targets.

No app functionality or version number was changed during this assessment. Only the optional audit harness, results, and this review were added.
