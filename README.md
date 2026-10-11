# SnapAutoOpener

Kotlin Android app, currently **5.2-safe-diagnostics** (versionCode 28).

This iteration is a manually started, 30-second diagnostic tool. START does not launch a guessed Snapchat profile. Open the intended app instance yourself. STOP is available in the activity and as an accessibility overlay. Notifications never start anything.

Interaction functions (tap, swipe, Back) remain in `AndroidActionExecutor`, as requested. All pass through `ActionPolicy`; diagnostic sessions cannot execute them. The UI currently exposes diagnostics only. Opening a Snap and advancing/backing out of its viewer also require evidence not implemented yet and remain blocked. This is not yet a reliable automatic Snap opener.

## Read first

- [Technical review and development plan (German)](docs/TECHNICAL_REVIEW.md)
- [Pixel 8 / Private Space test procedure (German)](docs/PIXEL_TEST_PLAN.md)

Snap's current [Terms](https://www.snap.com/terms) restrict automated/semi-automated access and unauthorized third-party interaction. Accessibility or diagnostic-only use is not explicitly exempt. No claim of Snapchat authorization or contractual compliance is made. Synthetic offline tests do not access Snapchat.

## Private Space

An unlocked private profile remains separate. Normal main-profile PackageManager launches cannot select its Snapchat copy. This app does not become a launcher or bypass Android profile restrictions. Manually open the intended instance through the system launcher. Installing both apps in the same profile may permit ordinary same-profile launching, but availability of Accessibility inside Private Space must be verified on the actual device; installation alone does not prove support.

## Recognition and privacy

Bounded accessibility snapshots feed a pure Kotlin recognizer. Exact visible DE/EN labels, selected state, list structure and element bounds replace fixed screen strips. Two stable observations are required. Missing semantics stays unknown. Editable fields, ads and My AI conservatively block capture. Colored connected components in semantic rows are only hints; they never authorize clicks.

On Android 14+, optional analysis uses screenshots of the identified window only. Older systems retain tree diagnostics. Screenshot failures are not bypassed. Pixel analysis runs on a worker; late results are discarded after STOP, navigation, restart or timeout. Images and raw node labels are never persisted. Only bounded diagnostic codes/counts/positions are saved locally; copying is explicit. No credentials, private Snapchat APIs, network permission, root or hooks.

## Build and APK

Existing **Build APK** GitHub Actions pipeline remains the delivery path. PRs run unit tests, Android Lint and a debug build. main runs the same checks and uses the existing release-signing secrets when available, otherwise the existing debug fallback. Download **Actions → successful Build APK run → Artifacts → SnapAutoOpener-apk**, then unzip `SnapAutoOpener.apk`. Test/Lint reports are in `validation-reports`.

Local requirements: JDK 17, Gradle 8.9, Android SDK 35. The inherited project has no Gradle wrapper.

```sh
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Unit tests cover synthetic selection/geometry, exclusion rules, session cancellation and expiry, retained action gates, color-shape limits and architecture boundaries. Real Pixel 8 behavior and Snapchat UI compatibility are not established by a successful build.
