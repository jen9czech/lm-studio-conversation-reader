# Version 0.9.1 size optimization

Date: 17 November 2026. Exact totals and checksum: [results-0.9.1.json](results-0.9.1.json).

The universal signed APK decreased from 12,707,544 bytes (0.9.0) to 2,506,132 bytes (0.9.1), an 80.28% reduction. These are APK download sizes, not installed storage usage.

Release builds now enable R8 code optimization and integrated resource shrinking. The unused bundled Sans family and unused preview dependency were removed. Sans-serif already uses the system app font; all four styles of Liberation Serif and Courier Prime remain. No language glyphs were stripped and no ABI-specific APK is required. The default Windows build and main CI build now produce/check the optimized release variant.

## Validation

- All 75 expanded host tests passed, including native Markdown/font rendering, search, bookmarks, navigation, storage and large-font layouts.
- Release lint: zero errors, 36 warnings.
- The optimized isolated APK passed an independent Android-only instrumentation smoke check on Motorola edge 60 neo, Android 16/API 36: synthetic JSON loading, Markdown/table/code display without crashes, repeated Next/Previous, Thinking expansion/collapse, all three font choices, search entry/exit and Back.
- Smoke sources are retained in [release-smoke](release-smoke). This runner uses Android APIs only, so it does not depend on Compose test libraries or target class names surviving optimization. It checks usability and resource loading; it is not a pixel-level typography or scrolling performance benchmark.
- The standard Compose instrumentation harness could not run against the minified target because its runner referenced a shared tracing class removed by R8. Its configuration remains unchanged for debug builds; this report does not claim that suite passed on the minified variant. The independent smoke check above covers the optimized APK separately.
- The final normal-package APK uses version code 27 and the same testing certificate as 0.9.0. It was installed over the existing phone app and launched successfully without clearing its files or preferences. Only isolated test apps were removed afterward.

No production signing-key migration or GitHub publication was performed. Release optimization does not change the existing testing certificate. Broader device coverage and measured scrolling frame timing remain separate work.
