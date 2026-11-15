# Version 0.8.3 verification

Verified locally on 16 November 2026. Detailed results: [results-0.8.3.json](results-0.8.3.json).

- Bundles Liberation Sans (Arial-style), Liberation Serif (Times New Roman-style), and Courier Prime (Courier-style). Each has regular, bold, italic and bold italic faces. Reader and Settings previews use the same bundled files instead of device-dependent generic font families.
- Settings show only the actual font name and one English preview line, with no Czech sample or style description.
- Native glyph tests compare actual rendered pixel images, verify fixed-width character advances in Monospaced, and confirm Czech glyph availability. Native reader view tests switch each family. A font comparison image is generated at `app/build/reports/font-preview-0.8.3.png`.
- Progress is first in the bottom row, aligned left, using the same labelLarge typography as Previous, End and Next. Collapsed/search views also keep it at bottom left.
- The main-screen icon opens all five library views and Settings, marks the selected view, and displays the version at the bottom. The same navigation callback serves tabs and menu items; a native UI regression exercises every menu view and Settings.
- Conversation view omits the user-role “You” label while preserving prompt text and navigation.
- Message and word counts appear only while collapsed; expanding text hides that metadata. Counts remain available in Conversation info.
- Full font licenses and upstream sources are included in the APK assets and [third-party notices](../THIRD_PARTY_NOTICES.md).

## Validation

APK assembly passed. All 22 regular tests and all 48 expanded tests passed (expanded count includes regular tests). Lint has 0 errors and 29 warnings. APK identity is 0.8.3, code 18, with the same signing certificate as 0.8.2.

No physical phone was connected; host simulation limits are in [README.md](README.md). This report records local verification; GitHub Actions results are available separately.

APK SHA-256: `0c6a4f678531e224b0d3ca0a9e6a79534a48f2af4e3dd28f3c24a57591d1bbc2`.
