# Architecture

## Design Goals

1. 軽量
2. 端末内完結
3. 小さい依存関係
4. 画像処理とUIを分離
5. プレビューと実壁紙で同じレイアウト計算を使う
6. Android / OEM差を局所化する

## Proposed Structure

```text
UI
 ├─ Main screen
 └─ Image layout editor
        ↓
Application / Use cases
 ├─ AddImages
 ├─ UpdateImageLayout
 ├─ ApplyNextWallpaper
 ├─ StartSlideshow
 └─ StopSlideshow
        ↓
Core
 ├─ Playlist
 ├─ LayoutCalculator
 └─ SlideshowSelector
        ↓
Android adapters
 ├─ DocumentImageSource
 ├─ WallpaperRenderer
 ├─ WallpaperApplier
 ├─ SlideshowScheduler
 └─ SettingsStore
```

大規模なClean Architecture化は行わない。
責務分離に必要な最小レイヤのみ作る。

## UI Technology

V1は標準Android Viewsを使用する。
Composeは採用しない。UI規模に対して依存・起動コストを増やさず、軽量性を優先する。

minSdkはAPI 24、compileSdk / targetSdkはAPI 36とする。AGP 9.4.0 + Gradle 9.6.0 + JDK 17をV1の初期ビルド基準とする。

## Main Components

### MainActivity

責務:

- 現在のslideshow状態表示
- 画像一覧
- interval / order / target設定
- start / stop
- next now

ロジックをActivityへ詰め込まない。

### ImageLayoutEditor

責務:

- プレビュー
- CONTAIN / CROP切替
- pinch zoom
- pan
- background color
- reset
- save

### LayoutCalculator

Pure Kotlinを優先する。

入力:

- source width / height
- target width / height
- mode
- user scale
- normalized offset

出力:

- final scale
- translation
- visible bounds

プレビューとrendererの両方が同じ計算を利用する。

Unit Test対象とする。

### WallpaperRenderer

責務:

- source URIを1枚だけ開く
- orientation確認
- target sizeに必要な範囲でdecode
- Canvasへ背景描画
- 画像をscale / translate
- final Bitmap生成

大量画像を保持しない。

### WallpaperApplier

`WallpaperManager` 呼び出しを隔離する。

責務:

- capability check
- Home / Lock / Both適用
- IOException / SecurityException等の失敗を結果型へ変換
- UI/Workerへ明確な結果を返す

### SlideshowScheduler

WorkManagerを隔離する。

- unique work name固定
- periodic work重複防止
- start / update / stop
- WorkManager固有コードをUIへ漏らさない

### WallpaperWorker

1回の実行で行うこと:

1. slideshow enabled確認
2. playlist読込
3. 次の有効画像選択
4. 1画像render
5. wallpaper適用
6. current index / result更新
7. Bitmap等解放

### SettingsStore

V1では小さい永続データのみ扱う。

V1は app-private JSON を `AtomicFile` で保存し、schemaVersionを持たせる。

- Android標準API中心
- crash途中の破損を避けるためAtomicFileを使用
- Room / DataStoreはV1では使用しない
- 保存件数や要件が増えた場合のみ再評価する。

## Data Model

```text
AppSettings
- schemaVersion
- slideshowEnabled
- intervalMinutes
- orderMode
- target
- currentItemId

WallpaperItem
- id
- uri
- order
- enabled
- layout

Layout
- mode
- userScale
- offsetXNormalized
- offsetYNormalized
- backgroundColor
```

URIそのものをIDにせず、内部stable IDを持つ。

## Threading

- UI threadで大画像decodeをしない
- decode / render / wallpaper applyはbackgroundへ
- state update競合を避ける
- workerとmanual Nextが同時実行しないよう排他する

初期案:

`Mutex / single application-level wallpaper operation gate`

実装時にprocess lifetimeとの整合を確認する。

## Error Model

例:

```text
Success
Unsupported
NotAllowed
SourceUnavailable
DecodeFailed
RenderFailed
ApplyFailed
NoEnabledImages
```

例外をUIまでそのまま投げず、ユーザーへ説明可能な結果へ変換する。

## Dependency Policy

V1で積極的に避ける:

- DI framework
- Room
- heavy image loader
- networking stack
- analytics SDK
- ads SDK

Jetpack WorkManagerなど、要件上明確に必要な依存のみ使用する。

## Test Strategy

### Unit
- Contain計算
- Crop計算
- clamp
- random/order selector
- invalid playlist behavior
- schema migration

### Android / Integration
- persisted URI read
- renderer
- WallpaperManager adapter
- WorkManager enqueue/cancel

### Real Device
Redmi 12 5G / HyperOS

- Home
- Lock
- Both
- reboot
- battery optimization
- large image
- portrait / landscape / square
- EXIF rotation
- missing source
