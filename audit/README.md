# Conversation Reader audit

This optional harness tests the current app without adding dependencies to the production APK or changing its version. The assessment is in [APP_REVIEW.md](APP_REVIEW.md); baseline machine-readable results are in [results.json](results.json). Current release verification is in [VERIFICATION-0.8.6.md](VERIFICATION-0.8.6.md) and [results-0.8.6.json](results-0.8.6.json).

## Run

From the project root in PowerShell:

```powershell
.\build.ps1
.\build.ps1 -Tasks @('-I', 'audit/audit.init.gradle', 'testDebugUnitTest')
```

The first command runs the regular build, unit tests, and lint. The second adds test-only Robolectric and Compose UI dependencies, Android resources, the app manifest, and the tests in `audit/tests`. First use downloads a substantial Android simulation runtime. JDK 17 runs these simulations at Android API 35. This is not a phone or emulator performance benchmark.

## Current result

App version **0.8.6 passes all 62 expanded tests**, including 30 regular tests. See [release verification](VERIFICATION-0.8.6.md) and [machine-readable results](results-0.8.6.json).

New checks verify larger thinking paragraph gaps without changed text offsets or extra spacing inside code blocks, end-of-thinking collapse behavior, and copy-icon alignment with Generation details. Existing Markdown, search-scope, parser, fonts, library, bookmarking and lifecycle checks pass.

Historical reports remain available for earlier versions.

## Simulation limitations

- No physical device was connected. Fling smoothness, frame timing, Android 16 behavior, launcher artwork, external text apps, file-browser integration, TalkBack, and real storage permissions remain device checks.
- Android's FileProvider uses path rules that do not match Windows canonical separators. `WindowsFileProviderShadow` and `AuditLocalFilesProvider` adapt imported-file access only in these host tests. They do not validate the production FileProvider's path containment or external grants.
- The fake documents provider removes the test index after the app reads it, before the app replaces it. This avoids Windows `File.renameTo` replacement semantics interfering with Android cache tests. Atomic index replacement still needs an Android test.
- Native text rendering/layout timings include host instrumentation and cold initialization. They must not be presented as phone frame times or compared with real competitor performance.
- All test conversations are synthetic. No personal conversation content is used in the added tests.
