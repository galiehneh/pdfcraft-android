# PdfCraft Android

Single-Activity Java app (`com.galiehneh.pdfcraft`) wrapping the vendored pdfcraft-web build in a WebView.

## Commands
- Build debug: `./gradlew assembleDebug` (same as CI)
- Build release: `./gradlew assembleRelease` (signs only if `signing/release-signing.properties` exists; gitignored)
- Lint: `./gradlew lint`
- Tests: none yet. Use `./gradlew testDebugUnitTest` once added.

## Layout
- `app/src/main/java/com/galiehneh/pdfcraft/MainActivity.java`: all app code.
- `app/src/main/assets/web/`: vendored web bundle (wasm + minified JS). Do not edit by hand.
- JDK 17, compileSdk/targetSdk 35, minSdk 24.

## Releases
- Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
- Add an entry to `CHANGELOG.md`.
- Commit as `chore(release): bump vX with <summary>`.

## Notes
- `AGENTS.md` is gitignored (graphify notes). Leave it alone.
- Never commit keystores or `signing/release-signing.properties`.
