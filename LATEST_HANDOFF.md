# Latest Handoff

## Project

- Project: Wallpaper Fit Slideshow
- Repository: goroyattemiyo/wallpaper-fit-slideshow-android
- Target branch: design/v1-foundation
- Base branch: main
- Updated: 2026-10-02 JST

## Current Goal

V1の仕様・アーキテクチャを確定してからAndroid実装へ入る。

受入条件の正本:

- WALLPAPER_SPEC.md

## Current Status

- Status: new-feature-implementation-unverified
- Android project scaffold: checked
- V1 functional path: implemented-unverified
- CI: not configured / not run
- Real device test: not started

## Completed

- [x] Product goal整理
- [x] Lightweight policy整理
- [x] V1 scope整理
- [x] Android標準API中心の方針整理
- [x] Wallpaper rendering基本設計
- [x] WorkManager scheduling基本設計
- [x] CI節約方針

## In Progress

- [x] V1 design review
- [x] Static review of V1 implementation
- [ ] Android scaffold compile verification

## Remaining

- [ ] license決定
- [x] package name決定: `io.github.goroyattemiyo.wallpaperfitslideshow`
- [x] minSdk決定: API 24
- [x] compileSdk / targetSdk決定: API 36
- [x] Android project scaffold
- [x] renderer実装
- [x] layout editor実装
- [x] scheduler実装
- [x] Pure Kotlin unit tests written
- [x] Android/Gradle tests executed
- [ ] Redmi 12 5G / HyperOS実機確認
  - [x] Debug APK install: PASS (2026-10-03)
  - [ ] Launch / runtime behavior

## Known Risks

### OEM wallpaper crop

- 症状: プレビューと実壁紙でcrop/zoom差が出る可能性
- 確認済み: Android WallpaperManagerはvisible crop hintを提供するが、最終表示には端末/Launcher差があり得る
- 未確認: Redmi 12 5G / HyperOSでの実際の差
- 次の確認: 最小renderer完成後に実機比較

### Background timing

- 症状: 指定時刻ぴったりに変更されない可能性
- 確認済み: PeriodicWorkRequestは最小15分で、正確な実行時刻を保証しない
- 次の確認: HyperOS実機で遅延傾向を記録

## Decisions

- 静的WallpaperManager方式をV1採用
- 15分以上はWorkManager、15分未満はユーザー開始のForeground Service高速モード
- 常時Foreground Serviceなし（15分未満の高速モード実行中のみForeground Service）
- Live Wallpaperなし
- networkなし
- broad storage permissionなし
- 画像は1枚ずつ処理
- CONTAINはno-upscaleをデフォルトとする
- UIは標準Android Views
- 設定保存はapp-private JSON + AtomicFile
- Fit/Contain + background color と manual CropをV1の核とする
- blur backgroundは実機要望によりV1へ追加
- Redmi 12 5G / HyperOSを初期実機基準にする

## Verification

- [x] Android公式 WallpaperManager API確認
- [x] Android公式 WorkManager periodic interval確認
- [x] Android公式 Storage Access Framework / persistable URI確認
- [x] Pure Kotlin core smoke check: PASS (layout / selector / decode / geometry)
- [x] WallpaperManager / WorkManager / SAF APIs reviewed against Android official docs
- [x] Previous baseline Android Gradle compile: PASS (2026-10-03, Windows local)
- [x] Current branch after target-selection/crop/blur/folder/ZIP changes: local verify PASS (2026-10-03)
- [x] unit test: PASS (`testDebugUnitTest`, 2026-10-03)
- [ ] integration test
- [ ] CI
- [ ] real device
  - [x] APK install
  - [ ] app launch / wallpaper behavior

## Next Action

1. ADB authorizationをRedmi側で許可してdebug APKをinstall
2. `docs/REAL_DEVICE_TEST.md` に沿ってRedmi 12 5Gで確認
3. 失敗があれば原因特定 → 最小修正 → 再検証
4. 実機PASS後にのみPRをReady for reviewへ変更しFull CIを1回実行

## Do Not

- いきなりLive Wallpaper化しない
- 常駐serviceを入れない
- 重い画像ライブラリを理由なく追加しない
- 全画像を同時decodeしない
- broad storage permissionを要求しない
- CIをデバッグに使わない


## Current Implementation Snapshot

Latest application code is tracked on branch head; exact commit updated with each verified step.

Implemented:

- multi-image SAF picker + persisted read permission
- folder import via ACTION_OPEN_DOCUMENT_TREE
- ZIP image import with extraction limits
- AtomicFile JSON settings
- sequential/random selector
- CONTAIN no-upscale + free position
- CROP shrink/zoom/pan (20%〜500%)
- vertical position slider
- solid / blurred image background
- blur radius / background transparency
- background color
- EXIF orientation handling
- memory-bounded one-image decode
- desired wallpaper output canvas
- physical-screen visible viewport
- WallpaperManager visibleCropHint
- Home / Lock / Both
- slideshow target checkbox (enabled/disabled per image)
- select all / clear all slideshow targets
- explicit target count in Start button/status
- manual Next uses slideshow targets only
- direct editor action: apply this one image now
- WorkManager periodic schedule
- lifecycle / race / missing-source handling
- local verification script
- Redmi real-device checklist

Verified locally on Windows (2026-10-03):

- `testDebugUnitTest`: PASS
- `lintDebug`: PASS
- `assembleDebug`: PASS
- Debug APK generated: `app/build/outputs/apk/debug/app-debug.apk`

Unverified:

- App launch / wallpaper behavior on Redmi
- Redmi runtime behavior
- actual HyperOS preview-to-wallpaper crop difference
- EXIF edge cases on real files


## Latest Verification Update - 2026-10-03

Latest local verification after target-selection/crop/blur/folder/ZIP changes:

- `testDebugUnitTest`: PASS
- `lintDebug`: PASS
- `assembleDebug`: PASS
- Debug APK install to connected Redmi: PASS

Still unverified:

- slideshow target checkbox behavior on device
- Crop shrink / zoom / vertical positioning on device
- blurred background / transparency on device
- folder import on device
- ZIP import on device
- preview vs actual wallpaper alignment on HyperOS


## Latest UI / Interval Changes

Implemented but current branch re-verification is pending:

- compact slideshow settings UI
- main screen summary: order / target / interval
- interval choices from 10 seconds
- 15 minutes or longer: WorkManager
- below 15 minutes: user-started foreground fast mode
- foreground service uses Android `specialUse` type
- no exact-alarm permission


## Latest Editor UI Change

Implemented, pending local re-verification:

- gesture-first editor
- smaller aspect-correct preview
- top "Back" action
- Back saves current layout
- pinch for shrink/enlarge in Free Adjust mode
- one-finger drag for positioning
- persistent zoom/position sliders removed from main editor
- blur/transparency moved into Background dialog


## Local Verification Failure / Fix - 2026-10-03

Observed on Windows local verification:

- `compileDebugKotlin`: PASS
- `testDebugUnitTest`: PASS
- `lintDebug`: FAIL
- lint error: `GestureBackNavigation` caused by `ImageEditorActivity.onBackPressed()`

Fix applied:

- removed deprecated `onBackPressed()` override
- system predictive-back now uses normal Activity behavior
- current editor state is persisted from `onStop()`
- explicit top Back button still saves before `finish()`
- foreground notification action updated away from deprecated builder overload

Re-verification required.


## Latest Local Verification PASS - 2026-10-03

After the predictive-back fix and latest UI changes:

- `testDebugUnitTest`: PASS
- `lintDebug`: PASS
- `assembleDebug`: PASS
- Debug APK installation to connected Redmi: PASS

Latest implemented UI now includes:

- compact slideshow settings summary + dialog
- seconds interval options
- fast mode for intervals below 15 minutes
- gesture-first image editor
- smaller aspect-correct preview
- top Back action
- pinch shrink/enlarge
- one-finger drag positioning
- background controls moved into a dialog

Still pending real-device behavioral verification:

- compact settings usability
- 10s/30s/1m/5m fast slideshow timing on HyperOS
- foreground notification behavior
- gesture editor movement/scale UX
- preview vs actual wallpaper alignment
- lock-screen clock overlap avoidance


## Real-device UX Verification - 2026-10-03

Verified by user on Redmi 12 5G / HyperOS:

- direct one-finger image positioning works
- pinch zoom/shrink works without selecting a separate edit mode first
- direct gesture editing UX is acceptable in the tested flow

Still unverified separately:

- fast slideshow timing / notification behavior
- blurred background / transparency behavior
- folder import
- ZIP import
- preview-to-actual wallpaper alignment across Home / Lock / Both


## Home / Lock Split + Thumbnail List - 2026-10-03

Implemented on `design/v1-foundation`, local re-verification pending.

Current design:

- settings schema bumped to 4
- legacy `enabled` migrates to `homeEnabled` + `lockEnabled`
- legacy `layout` migrates to `homeLayout` + `lockLayout`
- legacy current item migrates to Home/Lock current IDs according to old target
- global slideshow target selector removed
- order mode and interval remain shared
- Home and Lock candidate lists / current positions are independent
- periodic execution advances only sides with 2+ targets
- manual Next can apply sides with 1+ targets
- list is one file per row
- small sampled thumbnail + ellipsized filename + Home/Lock checkboxes
- selected row shows an elevated detail card with full filename/status/actions
- editor has Home / Lock switch and independent layout drafts
- direct apply targets the currently edited Home or Lock side
- thumbnail cache is bounded (~8 MiB) and does not full-decode the list

Commits in this feature batch:

- `56603e0` split Home/Lock data and operation state
- `ece051c` one-line thumbnail Home/Lock list UI
- `f87f1e0` wire Home/Lock selection and detail card
- `7a5a204` independent Home/Lock editor layouts
- `ffdd56f` independent-state unit coverage

Verification status:

- static review performed
- unit/lint/build/install for this batch: NOT YET VERIFIED
- CI: NOT RUN


## Static Review - Home / Lock Split Batch

Static review completed after the Home/Lock split implementation:

- old single `WallpaperItem.enabled` references: none in reviewed runtime files
- old single `WallpaperItem.layout` references: none in reviewed runtime files
- old `settings.target` references: none in reviewed runtime files
- old `settings.currentItemId` references: none in reviewed runtime files
- renderer call sites reviewed for new explicit layout parameter
- MainActivity layout IDs: no missing IDs
- ImageEditorActivity layout IDs: no missing IDs
- WallpaperItemAdapter layout IDs: no missing IDs
- escaped Kotlin interpolation artifacts: none in reviewed runtime files

Current verification status remains:

- static review: PASS
- unit tests: NOT YET RUN for this batch
- lint: NOT YET RUN for this batch
- assembleDebug: NOT YET RUN for this batch
- Redmi install / behavior: NOT YET VERIFIED for this batch
- GitHub Actions: NOT RUN


## Whole Wallpaper Blur - 2026-10-03

Implemented, local verification pending:

- settings schema bumped to 5
- `WallpaperLayoutState.wallpaperBlurRadius` added (0..30, default 0)
- Home / Lock store blur independently through their existing separate layouts
- blur is applied after foreground + background composition
- preview and final wallpaper both use `WallpaperBlurRenderer`
- shared box-blur logic extracted into `BitmapBlur`
- final wallpaper blur uses a bounded 360px-wide work bitmap to reduce memory cost
- editor now has a separate "壁紙ぼかし" control
- selected-item detail shows "壁紙ぼかし" when enabled
- background blur remains a separate setting

Verification status for this latest batch:

- static code review: PASS
- unit/lint/assemble/install: NOT YET RUN after these changes
- real-device blur quality/performance: NOT YET VERIFIED
- CI: NOT RUN


## Global Blur + Home Widget Superseding Update - 2026-10-03

This supersedes the earlier per-image whole-wallpaper-blur design.

Current source of truth:

- settings schema: 6
- whole-wallpaper blur is NOT stored per image
- AppSettings.homeWallpaperBlurRadius: 0..30
- AppSettings.lockWallpaperBlurRadius: 0..30
- Home and Lock blur are independent global settings
- main Settings dialog owns both blur controls
- ImageEditor has no wallpaper-blur control
- editor preview reads the global blur for the currently edited target
- Background blur remains per-image/per-target layout state
- WallpaperRenderer receives whole-wallpaper blur explicitly from AppSettings

Home screen widget added:

- current Home filename
- Zoom - / +
- Zoom 100%
- Home Blur - / +
- Zoom modifies only currentHomeItemId.homeLayout
- Blur modifies only global Home blur
- widget action reapplies current Home wallpaper
- widget updates after slideshow/direct apply/editor save/settings refresh
- widget uses RemoteViews buttons + PendingIntent broadcasts
- custom widget broadcast work uses goAsync and a single-thread executor

Official Android widget guidance checked:

- RemoteViews-based widgets support only a restricted set of views
- user interactions can be delivered with PendingIntent
- AppWidgetManager can update widgets from the app process
- BroadcastReceiver work should avoid blocking the main thread; goAsync is used

Verification status:

- static review in progress / latest batch not locally built yet
- Unit/Lint/assemble/install: NOT YET RUN for schema 6 + widget
- Redmi widget behavior: NOT YET VERIFIED
- CI: NOT RUN


## Static Review - Global Blur + Widget

Static review completed after schema 6 + widget implementation:

- per-image whole-wallpaper blur field/access: none
- Home global blur storage/apply path: present
- Lock global blur storage/apply path: present
- old per-image wallpaper blur dialog/button references: none
- widget provider registered in AndroidManifest
- AppWidgetProviderInfo references the widget layout
- all RemoteViews R.id references exist in widget XML
- updatePeriodMillis = 0; widget is interaction-driven
- widget PendingIntent broadcasts use foreground priority flag
- slideshow/direct apply updates widget state
- editor save and main settings refresh update widget state
- static review: PASS
- unit/lint/assemble/install: NOT YET RUN
- Redmi / HyperOS widget behavior: NOT YET VERIFIED
- CI: NOT RUN


## UI Polish + Widget Pin - 2026-10-03

Implemented on `design/v1-foundation`.

Visual direction:

- standard Android Views only
- no Compose / Material3 dependency added
- shared light app background + white rounded cards
- one accent blue for primary actions
- softer selected-item detail card
- list row activated state + rounded thumbnail
- editor sections visually separated while keeping gesture-first preview
- settings split into Slideshow / Wallpaper Display / Home Widget sections
- widget restyled as compact dark control card
- no animation framework or polling added

Widget discovery:

- Settings now contains "Widgetをホーム画面に追加"
- existing widget instances are detected with AppWidgetManager
- API 26+ supported launchers use requestPinAppWidget
- unsupported launchers receive manual long-press widget-picker guidance
- already-installed widget state is shown and the add button is disabled

Lock-screen direct gesture decision:

- experimental Live Wallpaper PoC was kept off the main branch
- Redmi / HyperOS real-device result reported: no Lock-only picker item and actual lock screen did not respond to the pinch gesture
- direct lock-screen pinch/drag is therefore not a V1 feature
- Lock image position/scale remains app-editor functionality
- Home quick adjustment remains Widget functionality

Verification status:

- static R.id review: PASS
- MainActivity brace/static review: PASS
- requestPinAppWidget API is guarded behind API 26 check
- Unit/Lint/assemble/install after UI polish: NOT YET RUN
- Redmi visual/widget pin verification: NOT YET RUN
- CI: NOT RUN


## Local Verification Pass - UI Polish Build

User ran:

`powershell -ExecutionPolicy Bypass -File .\scripts\local-verify.ps1 -Install`

Observed final result:

- Unit tests: PASS
- Android lint: PASS
- assembleDebug: PASS
- APK install via ADB: PASS
- Device install output: `Success`
- GitHub Actions: NOT RUN

Reason these are recorded as PASS:
`local-verify.ps1` executes Unit -> Lint -> assembleDebug -> install in order and throws immediately on any non-zero exit. The script reached and completed the install stage.

Still not yet verified for this UI batch:

- Redmi visual layout quality
- Settings Widget pin flow
- Widget button behavior after placement
- Home/Lock blur visual result after the latest UI-only changes
- editor layout clipping on device


## Widget Control Fix + Pseudo Slider - 2026-10-03

Real-device finding before this fix:

- Redmi / HyperOS: Widget placement PASS
- previous - / + / RESET controls: not reliably responding
- widget controls are NOT yet recorded as PASS

Current implementation:

- Widget controls changed from TextView controls back to Button controls
- Zoom controls: - / + / RESET
- Blur controls: - / +
- each broadcast PendingIntent now has a unique action, request code and data URI
- Widget state refreshes immediately after settings mutation, before wallpaper rendering finishes
- Zoom and Blur show display-only pseudo slider bars with a moving knob
- no SeekBar/custom View added to RemoteViews
- updatePeriodMillis remains 0; no polling added
- added WidgetSliderFormatter and unit tests for min/middle/max/clamp output

Static review:

- Widget R.id references: PASS
- provider/controller brace balance: PASS
- five Button controls present
- unique PendingIntent data URI path present
- immediate Widget refresh callback present

Verification still required:

- Unit/Lint/assemble/install after this fix: NOT YET RUN
- Redmi - / + / RESET behavior after this fix: NOT YET VERIFIED
- CI: NOT RUN
