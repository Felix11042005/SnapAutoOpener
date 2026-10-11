# SnapAutoOpener

Kotlin Android app, currently **5.6-visible-batch** (versionCode 35).

Three explicit modes are available: diagnostics without interactions, one opening attempt, and **Sichtbaren Bestand öffnen – bereitstellen**. Open the intended Snapchat chat list yourself within 30 seconds, then press START in the floating panel. The batch mode freezes up to ten uniquely re-identifiable visible initial rows, attempts one opening per row, waits at least five seconds, issues at most one contextual Android BACK and requires repeated chat-list recognition before continuing. It never scrolls or adds later rows. STOP is available throughout; the total batch limit is 120 seconds. Notifications never start anything. See [visible-batch behavior and device checks](docs/VISIBLE_BATCH.md).

Pressing START over the visible list defines the initial selection. A missing, incomplete, ambiguous or empty selection ends immediately. The initial tree must remain unchanged through visual roster capture. Later row matching uses in-memory hashes of complete visible row labels; changed, missing or duplicate signatures are skipped permanently. Position alone never identifies a remaining target. A clickable conversation ancestor is never substituted for a Snap status. Without a native status action, Android 14+ window screenshots must show one uniquely matching filled square in a recognized unopened-Snap row. Every visual opening requires two images 300–2500 ms apart with identical icon bounds and row pixels plus an unchanged complete accessibility snapshot. Session/STOP/deadline and fresh snapshot checks run immediately before dispatch. Rejected actions or exceptions end the run without replacement attempts. Each opening/return cycle has a 30-second limit.

**Batch processing is experimental and limited to visible initial rows.** The user confirmed one successful opening with 5.5; the full 5.6 cycle still needs Pixel testing. A completed accepted opening action and a changed, complete same-window tree without chat/list/input/ad/AI markers permit one recovery BACK. This does not claim positive viewer identification. No viewer screenshot or viewer tap is performed. Android accepting a click/tap does not prove Snap display. The five-second dwell can shorten a video and does not prove loading/viewing completion. Snapchat itself [automatically plays multiple received Snaps in order](https://help.snapchat.com/hc/en-us/articles/7012316500372-How-do-I-view-Snaps). Individual Snap IDs, unseen inventory and strict old/new separation inside the same conversation remain unavailable. New row signatures are never added; changed counts cause conservative skipping. Interaction APIs remain isolated in `AndroidActionExecutor`. STOP prevents pending work; an already dispatched 80-ms gesture may finish.

## Read first

- [Technical review and development plan (German)](docs/TECHNICAL_REVIEW.md)
- [Pixel 8 / Private Space test procedure (German)](docs/PIXEL_TEST_PLAN.md)

Snap's current [Terms](https://www.snap.com/terms) restrict automated/semi-automated access and unauthorized third-party interaction. Accessibility or diagnostic-only use is not explicitly exempt. No claim of Snapchat authorization or contractual compliance is made. Synthetic offline tests do not access Snapchat.

## Private Space

An unlocked private profile remains separate. Normal main-profile PackageManager launches cannot select its Snapchat copy. This app does not become a launcher or bypass Android profile restrictions. Manually open the intended instance through the system launcher. Installing both apps in the same profile may permit ordinary same-profile launching, but availability of Accessibility inside Private Space must be verified on the actual device; installation alone does not prove support.

## Recognition and privacy

Bounded accessibility snapshots feed a pure Kotlin recognizer. Exact visible DE/EN labels, list structure and relative element bounds replace fixed screen strips. Counted statuses include `9+ neue Snaps`; unread labels may include a count badge. The title is resolved before the filter or Snap statuses, ignoring a bottom navigation label. An opening attempt does not require the unread filter; diagnostic image analysis and retained scrolling still require its selected state. Missing or incomplete semantics stays unknown. Editable fields block actions and capture. My AI and advertising rows are excluded individually; an exclusion without a clear row blocks actions and capture. Color alone never authorizes a tap: semantic status candidate, bounded non-excluded list row, shape, image agreement and fresh authorization are required together. A strictly counted clipped label such as `4 neue…` is only a visual candidate; it cannot authorize a native click. Native status controls must occupy at most half their owning row's height, preventing whole-conversation actions from substituting for status clicks. A same-shaped red/purple decoration in a candidate row remains a possible false positive. Device verification is essential.

5.5 derives the normalized rounded-square corner profile from an isolated symbol in the supplied video. Ten small reference fixtures cover filled red symbols, the app's downscaling, viewed red/purple outlines, blue arrows/Chat shapes and a red notification dot. The icon center must lie in the lower half of its recognized row, matching the status below the contact name in the supplied layout; upper name decorations cannot substitute for it. Tests contain only isolated glyphs, quantized to 16 colors; no full screenshots, contact names or Snap contents are committed. A filled purple example is tested synthetically because the supplied video does not show one. These samples calibrate shape decisions, not full-frame recognition, gesture behavior or all Snapchat versions/themes. See [reference-media findings](docs/REFERENCE_MEDIA.md).

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
