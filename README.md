# SnapAutoOpener

Kotlin Android app, currently **5.4-visual-single-snap** (versionCode 33).

Two explicit modes are available: diagnostics without interactions, and **Öffnungsmodus bereitstellen**, which adds a floating START/STOP panel. Open the intended Snapchat chat list yourself within 30 seconds, then press START in that panel. It attempts one real native `ACTION_CLICK` on an exact, directly clickable unopened-Snap status, or one gesture tap on a separately verified filled red/purple symbol in an unopened-Snap row. STOP is available in the activity and panel. Notifications never start anything.

Pressing START over the visible Snapchat list defines the initial selection at that moment. A missing, incomplete, ambiguous or empty initial selection ends immediately; it never waits for later incoming Snaps. Later targets are never added; any change to that snapshot before clicking ends the attempt. A clickable conversation ancestor is never substituted for a Snap status. Without a native status action, Android 14+ window screenshots must show one uniquely matching filled square in a recognized unopened-Snap row. Two images 300–2500 ms apart must have identical icon bounds and identical pixels throughout that row; the complete accessibility snapshot must also remain unchanged. The tap uses the detected icon center, with session/STOP/deadline and snapshot checks immediately before dispatch. A missing or changed visual target ends the attempt without later discovery. The attempt stops after one click/tap, including rejection or exception; it never retries another target. Preparation and opening each have a 30-second limit.

**This is an experimental actual opening attempt, but not yet automatic batch processing.** Android accepting a click/tap does not prove that Snapchat displayed a Snap. Viewer recognition, automatic advance/return, scrolling to unseen existing Snaps and individual Snap identifiers are not implemented. Snapchat itself [automatically plays multiple received Snaps in order](https://help.snapchat.com/hc/en-us/articles/7012316500372-How-do-I-view-Snaps); one opening action does not mean exactly one displayed Snap. Snapchat can change content without exposing distinct Snap IDs; strict separation of old and newly arriving Snaps within a conversation is not established. These limits must be resolved before enabling a batch loop. Interaction APIs remain isolated in `AndroidActionExecutor`. STOP prevents pending work; a gesture already dispatched to Android may finish its 80 ms stroke.

## Read first

- [Technical review and development plan (German)](docs/TECHNICAL_REVIEW.md)
- [Pixel 8 / Private Space test procedure (German)](docs/PIXEL_TEST_PLAN.md)

Snap's current [Terms](https://www.snap.com/terms) restrict automated/semi-automated access and unauthorized third-party interaction. Accessibility or diagnostic-only use is not explicitly exempt. No claim of Snapchat authorization or contractual compliance is made. Synthetic offline tests do not access Snapchat.

## Private Space

An unlocked private profile remains separate. Normal main-profile PackageManager launches cannot select its Snapchat copy. This app does not become a launcher or bypass Android profile restrictions. Manually open the intended instance through the system launcher. Installing both apps in the same profile may permit ordinary same-profile launching, but availability of Accessibility inside Private Space must be verified on the actual device; installation alone does not prove support.

## Recognition and privacy

Bounded accessibility snapshots feed a pure Kotlin recognizer. Exact visible DE/EN labels, list structure and relative element bounds replace fixed screen strips. The title is resolved before the filter or Snap statuses, ignoring a bottom navigation label. An opening attempt does not require the unread filter; diagnostic image analysis and retained scrolling still require its selected state. Missing or incomplete semantics stays unknown. Editable fields block actions and capture. My AI and advertising rows are excluded individually; an exclusion without a clear row blocks actions and capture. Color alone never authorizes a tap: semantic unopened status, bounded non-excluded list row, shape, image agreement and fresh authorization are required together. A same-shaped red/purple decoration in such a row remains a possible false positive. Shape thresholds have not been calibrated against actual Pixel screenshots; device verification is essential.

Reports distinguish recognized statuses, direct click controls, attempted clicks and accepted clicks. They never report accepted clicks as opened Snaps. No contact names or UI text are logged. See [diagnostic findings](docs/DIAGNOSTIC_FINDINGS.md).

5.4 fixes the reported first list item whose bounds begin above the unread filter: exclusion rows may begin below the header, while opening rows must remain below the filter. Complete rows still own their descendants. Semantic row recognition no longer requires native clickability, which the reported full-row Snap statuses lack. Large intermediate wrappers still require contained, vertically separate branches. Failed START records a bounded structural trace; raw status clickability and ACTION_CLICK capabilities are counted independently. The report's ancestor bounds are regression-tested with synthetic header/filter positions; this is not a complete device-tree replay.

On Android 14+, optional analysis uses screenshots of the identified window only. Older systems retain tree diagnostics. Screenshot failures are not bypassed. Pixel analysis runs on a worker; late results are discarded after STOP, navigation, restart or timeout. Images and raw node labels are never persisted. Only bounded diagnostic codes/counts/positions are saved locally; copying is explicit. No credentials, private Snapchat APIs, network permission, root or hooks.

## Build and APK

Existing **Build APK** GitHub Actions pipeline remains the delivery path. PRs run unit tests, Android Lint and a debug build. main runs the same checks and uses the existing release-signing secrets when available, otherwise the existing debug fallback. Download **Actions → successful Build APK run → Artifacts → SnapAutoOpener-apk**, then unzip `SnapAutoOpener.apk`. Test/Lint reports are in `validation-reports`.

Local requirements: JDK 17, Gradle 8.9, Android SDK 35. The inherited project has no Gradle wrapper.

```sh
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Unit tests cover synthetic selection/geometry, exclusion rules, session cancellation and expiry, retained action gates, color-shape limits and architecture boundaries. Real Pixel 8 behavior and Snapchat UI compatibility are not established by a successful build.
