# Android Development Rules

## Priority

このアプリでは、軽量・低消費電力・低メモリを機能追加より優先する。

## Permissions

最小権限を原則とする。

V1で想定する権限:

- `android.permission.SET_WALLPAPER`

画像選択は Storage Access Framework を優先し、広範囲なストレージ権限を要求しない。

`MANAGE_EXTERNAL_STORAGE` は使用しない。

## Image Access

V1は `ACTION_OPEN_DOCUMENT` による複数画像選択を基本とする。

選択した URI は必要な read permission のみ永続化する。

ファイルが移動・削除され URI が無効になった場合は、クラッシュせず対象画像をスキップしてユーザーへ状態を示す。

## Bitmap / Memory

- 元画像を全解像度で大量に同時 decode しない
- 必要な出力サイズを先に決め、sampled decode を行う
- 1回の壁紙生成で基本1画像のみ処理する
- 一時 Bitmap の参照を長時間保持しない
- EXIF orientation を考慮する
- 不要な再圧縮を避ける
- キャッシュを追加する場合は上限を持たせる

OOM 対策を受入条件の一部とする。

## Wallpaper

Android標準の `WallpaperManager` を優先する。

ホーム / ロックの個別指定は API 24 以降の `FLAG_SYSTEM` / `FLAG_LOCK` を利用する。

適用前に可能な範囲で以下を確認する。

- `isWallpaperSupported()`
- `isSetWallpaperAllowed()`
- `getDesiredMinimumWidth()`
- `getDesiredMinimumHeight()`

システムやメーカーが最終表示を再調整する可能性があるため、
プレビューとの完全一致を全Android端末で保証しない。
Redmi 12 5G / HyperOS を初期実機基準とする。

## Background Work

V1の定期切替は WorkManager を使用する。

- 最小間隔は15分
- 正確な時刻実行を保証しない
- unique periodic work として重複登録を防ぐ
- 常時 Foreground Service は使用しない
- Live Wallpaper はV1では使用しない

## Manufacturer Differences

Xiaomi / Redmi / HyperOS のバックグラウンド制限はAndroid標準仕様と分けて扱う。

メーカー固有対応を入れる場合は、標準コードパスを汚染しないよう分離する。

実機で確認していないメーカー固有挙動を事実として記載しない。

## Dependencies

依存追加前に以下を確認する。

- 標準APIで代替できないか
- APKサイズへの影響
- runtime memoryへの影響
- maintenance cost
- license

DI framework、画像ロードライブラリ、DBなどを「便利だから」という理由だけで追加しない。

## Official References

- WallpaperManager: https://developer.android.com/reference/android/app/WallpaperManager
- WorkManager: https://developer.android.com/develop/background-work/background-tasks/persistent
- WorkRequest: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- Storage Access Framework: https://developer.android.com/training/data-storage/shared/documents-files
