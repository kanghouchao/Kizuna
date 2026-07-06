#!/bin/bash
# PR 規範チェック — create-pr skill が `gh pr create` の前に必ず実行する（PreToolUse hook でも強制）。
# Usage: check-pr.sh "<PR title>" <body-file>
# 判定はすべて exit code（0=合格 / 1=違反あり、違反内容は stderr）。
set -u
if [ $# -ne 2 ] || [ ! -f "$2" ]; then
  echo "usage: check-pr.sh \"<title>\" <body-file>" >&2
  exit 1
fi
title="$1"
body_file="$2"
fail=0
err() { echo "NG: $1" >&2; fail=1; }

# 1) タイトル: conventional commit 形式（feat/fix/chore/…、scope 任意）
printf '%s' "$title" | grep -Eq '^(feat|fix|chore|refactor|docs|test|perf|ci)(\([a-zA-Z0-9._-]+\))?: .+' \
  || err "タイトルが conventional commit 形式でない: $title"

# 2) 本文: テンプレート必須セクション（.github/pull_request_template.md）
for h in "## 概要" "## 変更内容" "## 検証" "## 決定ログ"; do
  grep -q "^$h" "$body_file" || err "本文に必須セクションがない: $h"
done

# 3) Closes #<issue 番号>（Refs 運用の場合は Refs #n でも可）
grep -Eq '(Closes|Refs) #[0-9]+' "$body_file" || err "'Closes #<n>' / 'Refs #<n>' がない"

# 4) ゲートのチェックボックスが実際にチェック済み（[x]）
for gate in "task lint" "task test" "task build"; do
  grep -Eq "^- \[x\] \`?${gate}" "$body_file" || err "ゲート未チェック: ${gate}"
done

# 5) 埋め忘れプレースホルダが残っていない
grep -q '<!--' "$body_file" && err "テンプレートのコメント（<!-- -->）が残っている"

[ "$fail" -eq 0 ] && echo "PR check OK"
exit "$fail"
