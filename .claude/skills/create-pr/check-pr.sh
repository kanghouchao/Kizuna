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

# 4) ゲートのチェックボックス検査 — diff がコード領域に触れる場合のみ [x] を必須とする。
#    ドキュメント域のみ（*.md / docs / .claude 等）の変更では task ゲートは情報を持たないため、
#    「対象外（コード変更なし）」の宣言があれば未チェックを許す。宣言の真偽は diff で検証する。
#    diff が取れない場合は fail-closed（コード変更ありとみなす）。
code_touched=1
base=$(git merge-base origin/master HEAD 2>/dev/null || true)
if [ -n "$base" ]; then
  code_touched=0
  while IFS= read -r f; do
    [ -z "$f" ] && continue
    case "$f" in
      frontend/*|backend/*|e2e/*|infrastructure/*|Taskfile*|.github/workflows/*|.github/scripts/*) code_touched=1 ;;
    esac
  done <<EOF
$(git diff --name-only "$base" HEAD)
EOF
fi
if [ "$code_touched" -eq 1 ]; then
  for gate in "task lint" "task test" "task build"; do
    grep -Eq "^- \[x\] \`?${gate}" "$body_file" || err "ゲート未チェック: ${gate}"
  done
else
  gates_ok=1
  for gate in "task lint" "task test" "task build"; do
    grep -Eq "^- \[x\] \`?${gate}" "$body_file" || gates_ok=0
  done
  if [ "$gates_ok" -eq 0 ]; then
    grep -q "対象外（コード変更なし）" "$body_file" \
      || err "ゲート未チェックだが免除宣言「対象外（コード変更なし）」も無い（docs-only 免除は宣言必須）"
  fi
fi

# 5) ローカル code-review 実施行 — diff がコード領域に触れる場合のみ [x] を必須とする。
#    docs-only は上のゲートチェックボックスと同じ免除宣言「対象外（コード変更なし）」で免除する
#    （$code_touched は上のブロックで確定済みの値を再利用する）。
review_line_re='^- \[x\] ローカル code-review 実施（effort: (medium|high)( |/|／).*指摘: [0-9]+件.*未修正: [0-9]+件）'
if [ "$code_touched" -eq 1 ]; then
  grep -Eq "$review_line_re" "$body_file" \
    || err "ローカル code-review 実施行が未チェック、または effort/指摘/未修正 の記載が不完全"
else
  grep -Eq "$review_line_re" "$body_file" \
    || grep -q "対象外（コード変更なし）" "$body_file" \
    || err "ローカル code-review 実施行が未チェック、または effort/指摘/未修正 の記載が不完全（免除宣言「対象外（コード変更なし）」も無い）"
fi

# 6) 埋め忘れプレースホルダが残っていない
grep -q '<!--' "$body_file" && err "テンプレートのコメント（<!-- -->）が残っている"

[ "$fail" -eq 0 ] && echo "PR check OK"
exit "$fail"
