# Conversation Reader audit

This optional harness tests the current app without adding dependencies to the production APK or changing its version. The assessment is in [APP_REVIEW.md](APP_REVIEW.md); baseline machine-readable results are in [results.json](results.json). Current release verification is in [VERIFICATION-0.9.2.md](VERIFICATION-0.9.2.md) and [results-0.9.2.json](results-0.9.2.json).

## Run

From the project root in PowerShell:

```powershell
.\build.ps1
.\build.ps1 -Tasks @('-I', 'audit/audit.init.gradle', 'testDebugUnitTest')
```

The first command runs the optimized release build, unit tests, and release lint. The second adds test-only Robolectric and Compose UI dependencies, Android resources, the app manifest, and the tests in `audit/tests`. First use downloads a substantial Android simulation runtime. JDK 17 runs these simulations at Android API 35. This is not a phone or emulator performance benchmark.

## Current result

App version **0.9.2 passes all 76 expanded host tests**. See [release verification](VERIFICATION-0.9.2.md) and [machine-readable results](results-0.9.2.json). The optimized release phone smoke check last passed on 0.9.1; 0.9.2 was installed and launched successfully. The four-test Compose device suite last passed on the unminified 0.9.0 build.

The current suite covers the redesigned bottom navigation, visible library views, sort sheets, reading controls, and large-font layouts. The 200% font check verifies that essential labels are not ellipsized and fit their measured bounds (with a one-pixel rounding tolerance). Physical tests cover search focus, prompt jumps, Thinking, settings, model restoration, and atomic storage. Neutral screenshots were captured from the isolated app on the Motorola.

Historical reports remain available for earlier versions.

## Simulation limitations

- Functional device checks passed on the Motorola with Android 16. Fling smoothness, frame timing, launcher artwork, external text apps, file-browser integration, TalkBack, and revoked storage permissions still need broader device coverage.
- Android's FileProvider uses path rules that do not match Windows canonical separators. `WindowsFileProviderShadow` and `AuditLocalFilesProvider` adapt imported-file access only in these host tests. They do not validate the production FileProvider's path containment or external grants.
- The fake documents provider retains the existing index. Atomic replacement is checked on the host and on Android; interrupted-refresh recovery is checked using the synthetic provider.
- Native text rendering/layout timings include host instrumentation and cold initialization. They must not be presented as phone frame times or compared with real competitor performance.
- All test conversations are synthetic. No personal conversation content is used in the added tests.

## Physical Android checks

The optional device suite uses the separate application ID `cz.jan.conversationreader.quality`. It seeds synthetic conversations and refuses to run against the normal reader application. It does not clear or access the normal app's files or preferences.

Build and run on an attached test device or emulator:

```powershell
.\build.ps1 -Tasks @('-I', 'audit/device.init.gradle', 'connectedDebugAndroidTest')
```

Or with a configured JDK/SDK:

```sh
sh gradlew --no-daemon -I audit/device.init.gradle connectedDebugAndroidTest
```

The test app and instrumentation APK can be removed afterward with `adb uninstall cz.jan.conversationreader.quality.test` and `adb uninstall cz.jan.conversationreader.quality`.

The new `Android device checks` GitHub workflow runs the isolated suite on an API 35 emulator. A successful Motorola run is recorded in [VERIFICATION-0.8.9.md](VERIFICATION-0.8.9.md); GitHub workflow execution is pending publication.
