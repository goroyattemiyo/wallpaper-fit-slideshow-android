# Wallpaper Fit Slideshow

軽量な Android 向け壁紙フィット + スライドショーアプリです。

## Goal

どんなサイズ・縦横比の画像でも、見切れや不要な拡大を抑えて壁紙向けに整え、
複数画像を順番またはランダムで自動切り替えできることを目指します。

特に以下を重視します。

- 軽量
- 低メモリ
- 低消費電力
- 広告なし
- アカウント不要
- 原則オフライン
- 元画像を不要に複製・再圧縮しない
- ユーザーが決めた Crop / Scale / Position を画像ごとに保持する

## V1 Scope

- 複数画像の選択
- 順番 / ランダム切替
- ホーム / ロック / 両方への適用
- 15分以上の定期切替
- 「次の壁紙」手動切替
- 画像ごとの表示設定
  - Fit / Contain: 全体を表示し、低解像度画像は原則アップスケールせず、余白を背景色で埋める
  - Crop: 画面いっぱいに拡大し、位置と倍率をユーザーが調整
- 背景色
  - 手動色
  - 自動色（候補）
- 設定の永続化
- Redmi 12 5G / HyperOS での実機確認

## Lightweight Policy

V1では次を行いません。

- 常時 Foreground Service
- Live Wallpaper
- ネットワーク通信
- 広告 / Analytics
- クラウド同期
- 元画像の常時複製
- 大量 Bitmap の同時展開

## Status

V1 Gate 1 implementation in progress.

- package: `io.github.goroyattemiyo.wallpaperfitslideshow`
- minSdk: 24
- compileSdk / targetSdk: 36
- AGP: 9.4.0
- Gradle: 9.6.0
- UI: standard Android Views
- scheduling: WorkManager 2.11.1

現在のDraft実装には以下が入っています。

- 複数画像選択とpersistable URI
- 順番 / ランダム
- Home / Lock / Both
- CONTAIN（no-upscale） / CROP
- Cropのピンチ拡大・ドラッグ位置調整
- 背景色
- 画像ごとの表示設定保存
- EXIF orientation補正
- 1画像ずつのsampled decode
- desired wallpaper canvas + visible viewport crop hint
- 手動「次へ」
- WorkManagerによる15分以上の自動切替
- AtomicFile + JSONの設定保存

Android SDK上のcompile / lint / APK buildとRedmi実機確認はまだ未検証です。

## Documents

- [WALLPAPER_SPEC.md](WALLPAPER_SPEC.md) - V1仕様・受入条件
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) - 実装設計
- [AI_DEV_RULES.md](AI_DEV_RULES.md) - AI開発共通規約
- [ANDROID_DEV_RULES.md](ANDROID_DEV_RULES.md) - Android固有規約
- [CI_POLICY.md](CI_POLICY.md) - GitHub Actions節約方針
- [LATEST_HANDOFF.md](LATEST_HANDOFF.md) - 開発引き継ぎ

## License

無料配布を前提としています。OSSライセンスは未確定です。


## Local verification

GitHub Actionsを消費せずローカル確認できます。

```powershell
git checkout design/v1-foundation
powershell -ExecutionPolicy Bypass -File .\scripts\local-verify.ps1
```

USB接続済み端末へdebug APKまで入れる場合:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\local-verify.ps1 -Install
```

詳細は [docs/REAL_DEVICE_TEST.md](docs/REAL_DEVICE_TEST.md) を参照してください。
