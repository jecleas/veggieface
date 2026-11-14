# Veggie Face (Android)

Take or pick a photo, find the face, and stick it on a fruit or veggie. Made for Lexi (a kid) and her dad. Kotlin + Jetpack Compose, portrait.

- `Produce.kt`: the 12 fruits and veggies, all drawn in code, each with a "face spot" (`faceX`, `faceY`, `faceSize`).
- `FaceCutter.kt`: loads the photo (fixes rotation), finds the biggest face with ML Kit (bundled, works offline), cuts it out along the face outline with soft edges, then saves or shares.
- `MainActivity.kt`: the screens, plus `drawVeggieFace` / `renderPicture` (the same drawing is used on screen and for the saved 1080×1080 picture).

## Building
```sh
export JAVA_HOME=$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home
./gradlew assembleRelease      # → app/build/outputs/apk/release/app-release.apk (~17 MB)
```
- Build **release** to share: it's shrunk with R8 and signed with this Mac's debug key. Debug builds are ~35 MB, too big to send in chat (30 MB limit).
- Only `arm64-v8a` is included, to keep the ML Kit face finder small. The family phone (Xiaomi Redmi 14C) is arm64.
- Bump `versionCode` for every APK you hand out so the phone treats it as an update.
- Android SDK and `local.properties` are set up like `../lexi_apk`; see `../lexi_apk/CLAUDE.md` for installing on the phone (WhatsApp as a Document works best).
- `example/` holds test photos of real family members. It's git-ignored on purpose: never commit or upload those photos.

## Clean up when you're done
**Always shut down emulators/simulators and background build daemons when you've finished.**
- `adb emu kill` (Android emulator), `./gradlew --stop` (Gradle and Kotlin daemons)
- iOS Simulator, if you used it: `xcrun simctl shutdown all && osascript -e 'quit app "Simulator"'`
