# Wallpaper Fit Slideshow - V1 Specification

## 1. Product Goal

どんなサイズ・縦横比の画像でも、不要な見切れや粗い強制拡大を避けながら壁紙向けに整え、
複数画像を軽量に自動切り替えできるAndroidアプリを作る。

単なる Wallpaper Changer ではなく、

**Wallpaper Layout Adjuster + Slideshow**

を製品の核とする。

## 2. Primary User Story

1. ユーザーが複数画像を選ぶ
2. 各画像をプレビューする
3. 画像ごとに「全体表示」または「拡大Crop」を選ぶ
4. 必要なら背景色、拡大率、位置を調整する
5. 順番またはランダムを選ぶ
6. ホーム / ロック / 両方を選ぶ
7. 自動切替を開始する
8. 以後はアプリが必要な時だけ1枚ずつ壁紙を生成・適用する

## 3. V1 Functional Requirements

### FR-01 Multiple Image Selection

- 複数画像を選択できる
- Android Storage Access Framework を使用する
- 選択URIへの必要最小限の読み取り権限を保持する
- 元画像をアプリ領域へ常時コピーしない

### FR-02 Playlist

- 選択画像の一覧を保持する
- 順番再生
- ランダム再生
- 画像削除
- 手動で「次の壁紙」

### FR-03 Fit / Contain Mode

画像全体を必ず表示範囲へ収める。

- アスペクト比を維持
- 画像の切り落としをしない
- デフォルトでは元画像を100%より大きく拡大しない（no-upscale）
- 余白は背景色で埋める
- ユーザー指定背景色
- 自動背景色はV1候補。実装負担が大きい場合はV1.1へ送る

### FR-04 Crop Mode

画面を埋めるように画像を表示する。

- アスペクト比維持
- ピンチ操作で倍率変更
- ドラッグで表示位置変更
- 画像ごとに倍率・位置を保存
- 空白領域が出ない範囲へ制約する

### FR-05 Per-image Layout State

各画像ごとに最低限以下を保存する。

- source URI
- stable item ID
- mode: CONTAIN / CROP
- scale
- normalized offset X
- normalized offset Y
- background color
- enabled / disabled
- order

ピクセル絶対値だけに依存せず、端末サイズ変更に耐えやすい正規化値を優先する。

### FR-06 Preview

- 実際の端末表示比率を基準にプレビューする
- ステータスバー等を壁紙画像へ焼き込まない
- 実際にレンダリングするロジックとプレビューのレイアウト計算を共通化する

ただし Android / Launcher / OEM が最終壁紙を追加Cropする可能性があるため、
全端末で完全一致を保証しない。

初期受入基準は Redmi 12 5G / HyperOS 実機とする。

### FR-07 Wallpaper Target

選択可能:

- Home
- Lock
- Home + Lock

API / 端末が対応しない場合は、失敗を隠さずUIへ示す。

### FR-08 Automatic Change

V1は WorkManager の PeriodicWorkRequest を利用する。

初期候補:

- 15分
- 30分
- 1時間
- 3時間
- 6時間
- 12時間
- 24時間

15分未満はV1対象外。

実行時刻は厳密な時計時刻ではなく、Androidの省電力制御に従う。

### FR-09 Slideshow Control

- Start
- Stop
- Next now
- current image indication
- last success / last error

### FR-10 Invalid Source Handling

画像URIが読めなくなった場合:

- アプリ全体を停止しない
- 当該画像をスキップ
- 状態をエラーとして記録
- 次の有効画像を試す
- 無限ループを防ぐ

## 4. Lightweight Requirements

### LW-01 No Permanent Service
V1では常時サービスを起動しない。

### LW-02 No Network
V1ではインターネット通信を必要としない。
`INTERNET` permission を追加しない。

### LW-03 One-image Processing
壁紙変更時は原則1画像だけdecode / renderする。

### LW-04 No Original Duplication
選択元画像を常時複製保存しない。

### LW-05 Minimal Cache
必要な一時生成物のみ保持し、不要になれば破棄する。
キャッシュを導入する場合はサイズ上限を設ける。

### LW-06 Dependency Budget
重量依存を安易に追加しない。

## 5. Rendering Pipeline

`URI → metadata/orientation → sampled decode → layout transform → final Canvas render → WallpaperManager`

### Target Size

以下を入力として出力サイズを決める。

- current display size
- WallpaperManager desired minimum width / height

端末依存の挙動があるため、Redmi実機で結果を確認しながら調整する。

### Contain

`scale = min(1.0, min(targetWidth / imageWidth, targetHeight / imageHeight))`

余白へ背景色を描画し、その上へ画像全体を描画する。

### Crop

基準倍率:

`scale = max(targetWidth / imageWidth, targetHeight / imageHeight)`

ユーザー倍率を加え、offsetを安全範囲へclampする。

## 6. Scheduling

- WorkManager
- unique periodic work
- Stop時は該当unique workをcancel
- 設定変更時は必要な場合のみworkをupdate / replace
- 同一周期workerを重複生成しない

WorkManagerは端末再起動後も処理を再スケジュールできる仕組みを利用する。

## 7. Privacy

- 画像は原則端末内処理
- 広告なし
- Analyticsなし
- アカウントなし
- 外部アップロードなし

## 8. V1 Non-goals

V1では行わない。

- Live Wallpaper
- 数秒 / 数分単位の高頻度切替
- ロック解除ごとの切替
- 画面ONごとの切替
- クラウド同期
- AI顔認識Crop
- 自動被写体認識
- 動画壁紙
- 複数画像コラージュの自動分割
- 高度なぼかし背景

これらはV1完成後に必要性を評価する。

## 9. Acceptance Criteria

### Core

- [ ] 2枚以上を選択できる
- [ ] 再起動後も選択画像へアクセスできる
- [ ] CONTAINで画像全体が欠けない
- [ ] CONTAINで低解像度画像を自動アップスケールしない
- [ ] 背景色で余白を埋められる
- [ ] CROPでユーザーが倍率と位置を変更できる
- [ ] 画像ごとの調整値が復元される
- [ ] Homeへ適用できる
- [ ] Lockへ適用できる対応端末ではLockへ適用できる
- [ ] Home + Lockへ適用できる
- [ ] 順番切替できる
- [ ] ランダム切替できる
- [ ] 15分以上で自動切替を設定できる
- [ ] 「次へ」で即時変更できる

### Reliability

- [ ] 読めないURIが混ざってもクラッシュしない
- [ ] 大画像を複数登録しても全画像を同時decodeしない
- [ ] slideshow停止後に不要なperiodic workが残らない
- [ ] 同じperiodic workが重複登録されない

### Device

- [ ] Redmi 12 5G / HyperOSで実機確認
- [ ] プレビューと実壁紙の差異を記録
- [ ] HyperOSのバックグラウンド制限による遅延有無を記録

## 10. Build Baseline

- package: `io.github.goroyattemiyo.wallpaperfitslideshow`
- minSdk: API 24
- compileSdk: API 36
- targetSdk: API 36
- AGP: 9.3.0
- Gradle: 9.5.0
- JDK: 17
- UI: standard Android Views
- WorkManager: 2.12.0

Android 17 / API 37は2026-10-02時点の公式SDKページにPreview表記が残るため、
V1は安定性を優先してAPI 36をcompile / targetに使用する。

## 11. Open Decisions

- 自動背景色をV1に含めるか
- OSS license
