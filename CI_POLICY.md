# GitHub Actions / CI Policy

## Purpose

GitHub Actionsの無料枠と実行時間を浪費せず、
CIを完成候補の最終検証として使用する。

優先順位:

`Actionsを使わない確認 → 軽量確認 → Full CI`

CIを試行錯誤型デバッグへ使わない。

## Standard Flow

`branch → Draft PR → 実装 → 静的確認 → test追加 → docs更新 → 実機手順作成 → Ready for review → Full CI → 実機確認 → merge`

- 開発中はDraft PR
- Draft PRへの通常commitでは重いCIを自動実行しない
- 1 Gate / 1機能につきFull CI 1回を目標
- main merge後に同じ内容のFull CIを不要に再実行しない
- 必要時のみ workflow_dispatch

## Before CI

可能な限りCI前に確認する。

- 実装漏れ
- syntax / import / API / 型
- lifecycle / cancellation / race
- error handling / cleanup
- permission / Manifest
- WorkManager
- unit test
- docs
- 実機確認手順

「とりあえずCIを回してエラーを見る」は避ける。

## Failure / Rerun

失敗原因を確認せずrerunしない。
同じ失敗を連続rerunしない。

runner開始前、steps空/null/0件の場合はコードエラーと即断しない。

先に確認する。

- Actions minutes
- Billing / Budget
- Actions permissions
- workflow permissions
- runner availability
- GitHub incident

## Artifact

APK等の大きなArtifactは長期間保持しない。

推奨:

- retention-days: 1
- 最新1〜2個のみ
- 共通Artifact名
- 不要な再buildをしない

Artifact削除はstorage節約であり、消費済みActions minutesは回復しない。

## Concurrency

同一PR / branchで複数実行する場合:

```yaml
concurrency:
  group: android-ci-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: true
```

## Workflow Design

Full CIは原則:

- `pull_request: types: [ready_for_review]`
- `workflow_dispatch`

に限定する。

docs変更だけでFull Android buildを回さない。

## Do Not

- 各commitごとのFull build
- Draft PRへの重い自動CI
- 原因不明のrerun
- runner開始前失敗をコードエラー扱い
- CI結果の過剰polling
- APK確認だけの不要な再build

CIはデバッグ手段ではなく、完成候補を検証する最後のGate。
