# Version 0.8.3 verification

Verified locally on 15 November 2026. Detailed results: [results-0.8.3.json](results-0.8.3.json).

- Bundles Liberation Sans (Arial-style), Liberation Serif (Times New Roman-style), and Courier Prime (Courier-style). Each has regular, bold, italic and bold italic faces. Reader and Settings previews use the same bundled files instead of device-dependent generic font families.
- Settings show each font name and two larger sample lines in that font, including numbers and Czech characters.
- Native glyph tests compare actual rendered pixel images, verify fixed-width character advances in Monospaced, and confirm Czech glyph availability. Native reader view tests switch each family. A font comparison image is generated at `app/build/reports/font-preview-0.8.3.png`.
- Progress is first in the bottom row, aligned left, using the same labelLarge typography as Previous, End and Next. Collapsed/search views also keep it at bottom left.
- Conversation view omits the user-role “You” label while preserving prompt text, version controls and prompt navigation.
- Message and word counts appear only while collapsed; expanding text hides that metadata. Counts remain available in Conversation info.
- Full font licenses and upstream sources are included in the APK assets and [third-party notices](../THIRD_PARTY_NOTICES.md).

## Validation

APK assembly passed. All 22 regular tests and all 47 expanded tests passed (expanded count includes regular tests). Lint has 0 errors and 29 warnings. APK identity is 0.8.3, code 18, with the same signing certificate as 0.8.2.

No physical phone was connected; host simulation limits are in [README.md](README.md). No GitHub publication was performed.

APK SHA-256: `b9e29af75a48f41c30589be917c71b6a43224a777d87cbfca49d0e9aebc812eb`.
