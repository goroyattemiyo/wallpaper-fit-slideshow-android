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
3. 各画像をホーム用 / ロック用へ個別にチェックする
4. ホーム用とロック用それぞれの表示位置・倍率・背景を必要に応じて調整する
5. 順番またはランダムと共通の切替間隔を選ぶ
6. 自動切替を開始する
7. 以後はホーム / ロックそれぞれの対象リストから必要な時だけ1枚ずつ壁紙を生成・適用する

## 3. V1 Functional Requirements

### FR-01 Image Sources

- 複数画像を選択できる
- SAFでフォルダを選び、その配下の画像を再帰的に追加できる
- ZIPを選び、対応画像だけをアプリ内へ安全に展開して追加できる
- ZIP展開は画像500枚、1画像50MB、合計500MBを上限とする
- Android Storage Access Framework を使用する
- 選択URIへの必要最小限の読み取り権限を保持する
- 元画像をアプリ領域へ常時コピーしない

### FR-02 Playlist

- 追加した画像の一覧を保持する
- 一覧は原則1ファイル1行とする
- 一覧に小さいサムネイルを表示する
- ファイル名は1行表示し、長い場合は末尾を省略する
- 各画像に「ホーム対象」「ロック対象」の独立したON/OFFを持つ
- 行選択時は詳細カードで完全なファイル名・状態・操作を表示する
- すべてH/L ON / すべてOFFを提供する
- 順番再生
- ランダム再生
- 上へ / 下へで表示順を変更
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

### FR-04 Crop / Free Position Mode

- アスペクト比維持
- Crop基準から20%〜500%で縮小・拡大できる
- モード選択なしでプレビューを直接ピンチして縮小・拡大できる
- 1本指ドラッグでX/Y位置を直接変更できる
- 「全体表示に戻す」でContain基準へ戻せる
- 画像が画面より小さい場合は背景を表示する
- Home / Lockごとに倍率・位置を独立保存する

### FR-04B Background

- 単色背景
- 同じ画像をぼかした背景
- ぼかし量をHome / Lockごとに保存
- ぼかし背景の透明度をHome / Lockごとに保存
- 背景色をHome / Lockごとに保存

### FR-04C Wallpaper Blur

- 背景ぼかしとは別に、完成する壁紙全体へぼかしを適用できる
- 画像ごとの設定ではなくアプリ全体設定とする
- Home用とLock用の2値を独立して持つ
- ぼかし量は0〜30、0はOFF
- メインの「設定」ダイアログから変更する
- Home編集中のプレビューはHome用ぼかし、Lock編集中はLock用ぼかしを参照する
- 実レンダリングも対象ごとのグローバル値を使用する
- 画像と余白背景を合成した後に全体へ適用する
- 大解像度Bitmapをそのまま多重展開せず、縮小ワークBitmapで処理する
- 主用途はホーム画面のアイコン/文字の視認性向上

### FR-05 Per-image Layout State

各画像ごとに最低限以下を保存する。

- source URI
- stable item ID
- order
- homeEnabled
- lockEnabled
- homeLayout
- lockLayout

各Layoutは以下を持つ。

- mode: CONTAIN / CROP
- scale
- normalized offset X
- normalized offset Y
- background color
- background mode
- blur radius
- background image alpha

ピクセル絶対値だけに依存せず、端末サイズ変更に耐えやすい正規化値を優先する。
旧schemaの単一 enabled / layout はmigration時にHome / Lock双方へコピーする。

### FR-06 Preview

- 実際の端末表示比率を基準にプレビューする
- ステータスバー等を壁紙画像へ焼き込まない
- 実際にレンダリングするロジックとプレビューのレイアウト計算を共通化する

ただし Android / Launcher / OEM が最終壁紙を追加Cropする可能性があるため、
全端末で完全一致を保証しない。

初期受入基準は Redmi 12 5G / HyperOS 実機とする。

### FR-07 Wallpaper Target

グローバルな「適用先」は持たない。

- 各画像ごとにHome対象ON/OFFを設定できる
- 各画像ごとにLock対象ON/OFFを設定できる
- 同じ画像をHome / Lock両方へ登録できる
- Home / Lockで別々のLayoutを持てる
- 編集画面ではHome / Lockを切り替えて直接調整する
- 単体適用は現在編集中のHomeまたはLockへ適用する
- 自動切替時はHome / Lockそれぞれの対象リストから独立して次画像を選ぶ

API / 端末が対応しない場合は、失敗を隠さずUIへ示す。

### FR-08 Automatic Change

切替間隔:

- 10秒
- 30秒
- 1分
- 5分
- 15分
- 30分
- 1時間
- 3時間
- 6時間
- 12時間
- 24時間

15分以上:
- WorkManager PeriodicWorkRequest
- Androidの省電力制御に従い、実行時刻は厳密ではない

15分未満:
- ユーザーが明示的にStartした場合だけForeground Serviceの高速モードを使用
- 常駐通知を表示する
- Stopまたは15分以上への設定変更でServiceを終了する
- 端末/OEMの省電力制御によって停止する可能性は実機で確認する
- 最小値は10秒とし、それ未満はV1対象外

### FR-09 Slideshow Control

- HomeまたはLockのどちらかに対象画像2枚以上があれば自動切替を開始できる
- 2枚未満の側はperiodic切替対象にしない
- Start時の手動初回適用では1枚だけの側も適用可能
- StartにはHome / Lock対象枚数を表示する
- Stop
- Next nowはHome / Lockそれぞれの対象リストから独立して次画像を適用する
- currentHomeItemId / currentLockItemIdを別々に保持する
- last success / last error

### FR-09B Home Screen Widget

- Androidホーム画面へコントロールWidgetを追加できる
- 現在のHome壁紙ファイル名を表示する
- 現在のHome画像だけをZoom - / Zoom + で約10%刻み調整する
- Zoom RESETで現在のHome画像のuserScaleを1.0へ戻す
- Zoomは現在値と疑似スライダーバーを表示する
- Home全体ぼかしをBlur - / Blur + で1段階ずつ調整する
- Blurは現在値と疑似スライダーバーを表示する
- 疑似スライダーは表示専用で、ドラッグ式SeekBarにはしない
- Widget操作後は現在のHome壁紙へ即時再適用する
- LockぼかしはWidgetから変更しない
- Widgetの状態はスライドショー切替・編集保存・設定変更後に同期する
- WidgetはRemoteViews対応Viewだけで構成し、ドラッグ式SeekBarには依存しない
- Zoom / Blur の - / + / RESET は独立したButtonとしてクリック領域を持つ
- Widget操作の設定値は壁紙再描画完了を待たず先にWidget表示へ反映する
- 設定画面に「Widgetをホーム画面に追加」を置く
- 対応ランチャーではrequestPinAppWidgetでシステムの追加確認を要求する
- 非対応ランチャーではホーム画面長押しからの手動追加手順を案内する
- Widget設置済みの場合は設定画面に状態を表示する

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

### UI-01 Lightweight Visual Design

- UIはstandard Android Viewsを維持する
- Compose / Material3等の大型UI依存を追加しない
- 画面背景、角丸カード、余白、文字階層、アクセント色を統一する
- 画像一覧と操作領域を視覚的に分離する
- 主要操作のみアクセント色を使い、削除は危険操作として区別する
- 画像が主役で、装飾は最小限にする
- Widgetも同じ視覚方針で整理する
- 複雑なアニメーションや常時描画は追加しない

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
- 実ロック画面上での直接ピンチ / ドラッグ操作
- 10秒未満の高頻度切替
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
- [ ] CROPで縮小・拡大できる
- [ ] 全体表示/CROPとも上下位置を変更できる
- [ ] 単色/ぼかし背景を切替できる
- [ ] 背景ぼかし量と透明度を保存・復元できる
- [ ] 壁紙全体のぼかしを0〜30で調整できる
- [ ] Home / Lockで壁紙全体ぼかしを独立保存できる
- [ ] 画像ごとの調整値が復元される
- [ ] Homeへ適用できる
- [ ] Lockへ適用できる対応端末ではLockへ適用できる
- [ ] 一覧でHome / Lock対象を画像ごとに独立してON/OFFできる
- [ ] 一覧に小さいサムネイルを表示できる
- [ ] 長いファイル名は一覧で省略され、選択時の詳細カードでは完全名を確認できる
- [ ] Home OFFの画像はHome自動切替に出ない
- [ ] Lock OFFの画像はLock自動切替に出ない
- [ ] 同じ画像のHome用とLock用で異なる倍率・位置・背景を保存できる
- [ ] 単体編集からの即時適用は対象ON/OFFに関係なく、現在編集中のHomeまたはLockへ適用できる
- [ ] 順番切替できる
- [ ] ランダム切替できる
- [ ] 2枚以上ある場合に10秒以上で自動切替を設定できる
- [ ] 15分未満では高速モード通知が表示される
- [ ] 高速モード停止時にForeground Serviceが残らない
- [ ] 1枚だけでは不要なperiodic workを開始しない
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
- AGP: 9.4.0
- Gradle: 9.6.0
- JDK: 17
- UI: standard Android Views
- WorkManager: 2.11.1

Android 17 / API 37は2026-10-02時点の公式SDKページにPreview表記が残るため、
V1は安定性を優先してAPI 36をcompile / targetに使用する。

## 11. Open Decisions

- 自動背景色をV1に含めるか
- OSS license
