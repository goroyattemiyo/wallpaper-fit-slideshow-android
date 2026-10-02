# AI Development Rules

## Purpose

正確・安全・シンプル・保守可能・仕様変更に強い実装を作る。
過剰設計より、必要十分で検証可能な実装を優先する。

## Accuracy First

確認していない事実を確認済みとして扱わない。

API、SDK仕様、CLI option、ファイル、branch、commit、release、実行結果、テスト結果等を推測で作らない。

不明な場合は次の順で扱う。

1. コード・実行結果・ログ・公式情報で確認する
2. 確認できなければ「未確認」と明示する
3. 推定する場合は「推定」と明示する
4. 必要なら確認方法を示す

外部仕様が結果に影響する場合は最新の公式情報を優先する。

## No Sycophancy

ユーザー案を自動的に肯定しない。

技術的弱点、互換性、セキュリティ、バッテリー、メモリ、保守負担、メーカー独自制限などがある場合は明示する。

より単純・安全・安定・保守しやすい方法があれば提示する。

## Source of Truth

### Expected behavior
1. 最新の明示的ユーザー要求
2. 最新の受入条件
3. 最新仕様書

### Current behavior
1. 現在のコード
2. 設定
3. 実行結果
4. ログ
5. テスト結果

### External specification
1. 最新Android公式ドキュメント
2. AndroidX / Jetpack公式ドキュメント
3. 実機で確認した挙動

### Historical intent
- LATEST_HANDOFF.md
- README / 設計書
- Issue / PR / commit履歴

「コードにある = 正しい仕様」「仕様書にある = 実装済み」と決めつけない。

## Specification Changes

最新の明示的要求を現在仕様として扱う。

仕様変更時は最低限確認する。

- 旧仕様 → 新仕様
- 影響範囲
- 後方互換性
- 保存データ
- permission
- lifecycle / state
- 呼び出し元
- test
- documentation

## Minimal Change

要求を満たす必要最小差分を優先する。

避けるもの:

- ついでの rename
- ついでの全面 refactor
- 無関係な変更
- 不要な依存追加

## Development Cycle

複雑な作業は以下で進める。

`Plan → Execute → Test/Check → Review → Improve`

### Plan
目的、現状、制約、変更範囲、受入条件、主要リスクを短く整理する。

### Execute
最小変更で実装する。

### Test / Check
今回の変更で壊れる可能性がある範囲を選び、確認する。

### Review
要求漏れ、回帰、複雑化、セキュリティ、互換性、保守性を確認する。

### Improve
`原因特定 → 最小修正 → 再検証` で改善する。

## Bug Fixing

`再現条件 → 証拠 → 原因候補 → 根本原因 → 最小修正 → 回帰確認`

原因未確認なら断定しない。
可能なら regression test を追加する。

## Testing

テストは現在の実装ではなく期待される挙動を確認する。

Unit / Integration / Emulator / Real device を区別する。
mock 成功を実機成功と同一視しない。

## Documentation / Handoff

重要な仕様変更は関連文書へ反映する。
中断時は LATEST_HANDOFF.md を更新する。

## Definition of Done

「コードを書いた」だけでは完了としない。

- 要求を満たす
- 主要異常系を考慮する
- 重大な回帰がない
- 必要なテストを行う
- 必要な文書を同期する
- 検証結果を説明できる

未検証事項は成功扱いしない。
