# 課題管理: GitHub

このリポジトリの課題と PRD は GitHub issue で管理する。操作には `gh` CLI を使う。

## 操作の規約

- **課題の作成**: `gh issue create --title "..." --body "..."`。複数行の本文にはヒアドキュメントを使う。
- **課題の参照**: `gh issue view <number> --comments`。必要なコメントを `jq` で絞り込み、ラベルも取得する。
- **課題の一覧**: `gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'`。適切な `--label` と `--state` で絞り込む。
- **コメントの追加**: `gh issue comment <number> --body "..."`
- **ラベルの追加・削除**: `gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- **課題の終了**: `gh issue close <number> --comment "..."`

対象リポジトリは `git remote -v` から判断する。クローン内で実行すれば `gh` が自動で判断する。

## PR を要望の受付窓口として扱うか

**PRs as a request surface: no.** この設定キーを `/triage` が参照する。外部からの PR を機能要望として扱う場合は、値を `yes` にする。

`yes` の場合、PR も課題と同じラベル・状態で管理し、対応する `gh pr` コマンドを使う。

- **PR の参照**: `gh pr view <number> --comments`。差分は `gh pr diff <number>` で読む。
- **仕分け対象の外部 PR の一覧**: `gh pr list --state open --json number,title,body,labels,author,authorAssociation,comments`。取得後、`authorAssociation` が `CONTRIBUTOR`、`FIRST_TIME_CONTRIBUTOR`、`NONE` のものだけを残し、`OWNER` / `MEMBER` / `COLLABORATOR` は除外する。
- **コメント・ラベル・終了**: `gh pr comment`、`gh pr edit --add-label` / `--remove-label`、`gh pr close`。

GitHub では issue と PR が番号を共有する。`#42` だけではどちらか分からないため、まず `gh pr view 42` で確認し、PR でなければ `gh issue view 42` を使う。

## スキルが課題管理への公開を指示した場合

GitHub issue を作成する。

## スキルが関連チケットの取得を指示した場合

`gh issue view <number> --comments` を実行する。

## 作業探索の操作

`/wayfinder` が使う規約。全体の作業地図を一つの課題（map）とし、その子課題（child）を作業チケットにする。

- **作業地図**: `wayfinder:map` ラベルを付けた一つの課題。本文にメモ（Notes）、これまでの決定（Decisions-so-far）、未解明点（Fog）を記録する。`gh issue create --label wayfinder:map` で作成する。
- **子チケット**: GitHub の子課題として地図に紐づける。`gh api` で子課題の端点を使う。子課題が使えない場合は地図の本文のタスクリストへ追加し、子の本文先頭に `Part of #<map>` を記す。ラベルは `wayfinder:<type>`（`research` / `prototype` / `grilling` / `task`）。着手時に担当開発者へ割り当てる。
- **依存関係**: GitHub 標準の課題依存関係を正本とし、画面からも確認できる形にする。`gh api --method POST repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>` で追加する。`<blocker-db-id>` は先行課題の数値のデータベース ID（`gh api repos/<owner>/<repo>/issues/<n> --jq .id`）であり、`#number` や `node_id` ではない。`issue_dependencies_summary.blocked_by` は未完了の先行課題数を示す。依存関係が使えない場合は、子の本文先頭に `Blocked by: #<n>, #<n>` を記す。先行課題がすべて終了したら着手可能になる。
- **次の作業の選定**: `gh issue list --state open` を地図の子課題またはタスクリストの範囲に絞る。未完了の先行課題があるもの（`issue_dependencies_summary.blocked_by > 0`、または `Blocked by` に未完了課題があるもの）と担当者がいるものを除き、地図上の順序で先頭を選ぶ。
- **担当の確保**: `gh issue edit <n> --add-assignee @me`。セッションで最初に行う書き込みとする。
- **解決**: `gh issue comment <n> --body "<answer>"` で回答し、`gh issue close <n>` で閉じる。その後、地図の Decisions-so-far に要点とリンクを追記する。
