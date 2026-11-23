# Optimized APK smoke runner

`Runner.java` is an Android-only instrumentation entry point for the separate `cz.jan.conversationreader.quality` application. It refuses to seed the normal app. All conversations are synthetic. It checks rendered control availability and successful interaction, rather than inspecting obfuscated application classes.

Build the quality release APK with `build.ps1 -Tasks @('-I', 'audit/device.init.gradle', 'assembleRelease')`. On Windows, `audit/release-smoke/build.ps1` compiles the runner against Android SDK 36 with JDK 17, packages it, and signs it with the local development certificate. It writes `.tools/release-smoke-build/smoke.apk`.

After installing the quality APK and runner APK, run:

```sh
adb shell am instrument -w cz.jan.reader.releasesmoke/cz.jan.reader.releasesmoke.Runner
```

The stream must start with `PASS:`; the command exit code alone is insufficient. Remove `cz.jan.reader.releasesmoke` and `cz.jan.conversationreader.quality` afterward. Rebuild normally without `audit/device.init.gradle` before distributing an APK, since that init script changes the application ID.

To test data preservation, install the previous APK built with the quality application ID and the same certificate. Run the instrumentation with `-e mode seed-update -e expectedCode 36`, install the candidate quality APK with `adb install -r`, then run with `-e mode verify-update -e expectedCode 37`. Each mode reports `PASS:` separately. The update check verifies an imported file, bookmark, font, text size, and the saved prompt reached through Continue reading.
