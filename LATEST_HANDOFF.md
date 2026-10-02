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

- Status: design
- Android project scaffold: not started
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

- [ ] V1 design review

## Remaining

- [ ] license決定
- [ ] package name決定
- [x] minSdk決定: API 24
- [ ] compileSdk / targetSdk最終決定
- [ ] Android project scaffold
- [ ] renderer実装
- [ ] layout editor実装
- [ ] scheduler実装
- [ ] tests
- [ ] Redmi 12 5G / HyperOS実機確認

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
- WorkManagerで15分以上の定期切替
- 常時Foreground Serviceなし
- Live Wallpaperなし
- networkなし
- broad storage permissionなし
- 画像は1枚ずつ処理
- CONTAINはno-upscaleをデフォルトとする
- UIは標準Android Views
- 設定保存はapp-private JSON + AtomicFile
- Fit/Contain + background color と manual CropをV1の核とする
- blur backgroundはV1 non-goal
- Redmi 12 5G / HyperOSを初期実機基準にする

## Verification

- [x] Android公式 WallpaperManager API確認
- [x] Android公式 WorkManager periodic interval確認
- [x] Android公式 Storage Access Framework / persistable URI確認
- [ ] compile
- [ ] unit test
- [ ] integration test
- [ ] CI
- [ ] real device

## Next Action

1. Design PRをレビュー
2. package name / compileSdk / targetSdkを確定
3. 最小Android projectをscaffold
4. LayoutCalculatorから実装開始

## Do Not

- いきなりLive Wallpaper化しない
- 常駐serviceを入れない
- 重い画像ライブラリを理由なく追加しない
- 全画像を同時decodeしない
- broad storage permissionを要求しない
- CIをデバッグに使わない
