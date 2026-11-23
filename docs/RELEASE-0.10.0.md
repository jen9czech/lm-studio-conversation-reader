# Conversation Reader 0.10.0

20 November 2026 · Android build 37

- Find conversations by folder, model, filename, or full text. Full-text matches show highlighted previews, and mixed folders can sort folders and conversations separately.
- Start with compact prompt previews, open Contents at the current heading, and change size, font, or theme from the reader. The reader restores the passage after text reflows.
- Browse and read at larger text sizes. Search results have accessible announcements, and keyboard focus stays on the prompt control through expansion and collapse.
- The optimized APK uses the same application ID and signing certificate as the existing installation. Install it over the current app to preserve the library and preferences.

## Build and signing

Build with `./build.ps1 -Tasks @('assembleRelease','testReleaseUnitTest','lintRelease')`, or run the equivalent Gradle wrapper tasks. The APK is at `app/build/outputs/apk/release/app-release.apk`. The local distribution includes the APK and its SHA-256 checksum.

The default release build uses the local Android development keystore. The same certificate is required for an in-place update to existing installations. Keep a secure backup of that keystore. Generating another development keystore produces a different certificate.

To build with a dedicated production certificate, provide `READER_SIGNING_STORE_FILE`, `READER_SIGNING_STORE_PASSWORD`, `READER_SIGNING_KEY_ALIAS`, and `READER_SIGNING_KEY_PASSWORD` as environment variables through your secret manager or build environment. All four are required when production signing is enabled. Keep the keystore and passwords out of the repository. Approve and test a signing migration before distributing an APK signed with a different certificate to existing users.

## Verification

- Release unit tests passed (41 tests), as did 29 focused simulator checks. Release lint reported zero errors and 41 warnings, mostly Kotlin API suggestions and dependency version notices. Dependency versions were kept stable for this release.
- All six isolated Android regressions passed on a Motorola edge 60 neo running Android 16. The optimized APK smoke test passed separately. An in-place upgrade from build 36 to build 37 retained the synthetic import, bookmark, font, text size, and saved prompt reached through Continue reading.
- The installed build 37 on the Motorola matched the distributed APK by SHA-256. The production signing configuration was tested with a disposable certificate.
- The broader Windows Android simulation suite still had intermittent UI failures. GitHub publication and the release source tag remain pending.
