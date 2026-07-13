#!/bin/bash
# check-pr.sh の回帰テスト（#346 D: ローカル code-review 実施行ゲート）。
# 方式: 一時 git リポジトリを作り、base commit を origin/master として偽装した上で
#       diff パターン別（コード域 / docs-only）に PR 本文を変えて check-pr.sh を実行し、
#       exit code のみで合否判定する。
set -u

script_dir=$(cd "$(dirname "$0")" && pwd)
check_pr=${1:-${CHECK_PR_SCRIPT:-"$script_dir/check-pr.sh"}}

if [ ! -f "$check_pr" ]; then
  echo "FATAL: テスト対象の check-pr.sh が見つからない: $check_pr" >&2
  exit 1
fi

pass=0
fail=0

# full_body <review_line> <gates_checked:0|1> <declaration>
full_body() {
  local review_line=$1 gates_checked=$2 declaration=$3
  local gate_box="[ ]"
  [ "$gates_checked" -eq 1 ] && gate_box="[x]"
  cat <<EOF
## 概要

テスト用の変更

Closes #1

## 変更内容

- テスト

## 検証

- ${gate_box} \`task lint\` exit 0
- ${gate_box} \`task test\` exit 0
- ${gate_box} \`task build\` exit 0
${review_line}
${declaration}

## 決定ログ

テスト

## 備考 / レビュー観点

テスト
EOF
}

# setup_repo <fixture_file> — 一時 git リポジトリを作り、base commit を origin/master とし、
#             fixture_file を追加したコミットを HEAD に積む。リポジトリの絶対パスを返す。
setup_repo() {
  local fixture_file=$1
  local repo
  repo=$(mktemp -d)
  git -C "$repo" init -q
  git -C "$repo" config user.email test@example.com
  git -C "$repo" config user.name test
  git -C "$repo" commit -q --allow-empty -m base
  git -C "$repo" update-ref refs/remotes/origin/master HEAD
  mkdir -p "$repo/$(dirname "$fixture_file")"
  echo "x" > "$repo/$fixture_file"
  git -C "$repo" add "$fixture_file"
  git -C "$repo" commit -q -m change
  echo "$repo"
}

# run <期待exit> <説明> <repo> <body内容>
run() {
  local want=$1 desc=$2 repo=$3 body_content=$4 got body_file
  body_file=$(mktemp)
  printf '%s\n' "$body_content" > "$body_file"
  ( cd "$repo" && "$check_pr" "feat: test" "$body_file" ) >/dev/null 2>&1
  got=$?
  rm -f "$body_file"
  if [ "$got" -eq "$want" ]; then
    printf 'PASS [exit %s]        %s\n' "$got" "$desc"
    pass=$((pass + 1))
  else
    printf 'FAIL [exit %s want %s] %s\n' "$got" "$want" "$desc"
    fail=$((fail + 1))
  fi
}

echo "== コード域 diff =="
repo_code=$(setup_repo "frontend/src/App.tsx")
run 1 'コード域diff + レビュー行なし → 不合格' "$repo_code" "$(full_body "" 1 "")"
run 0 'コード域diff + レビュー行あり → 合格' "$repo_code" "$(full_body "- [x] ローカル code-review 実施（effort: medium/指摘: 0件/未修正: 0件）" 1 "")"
rm -rf "$repo_code"

echo "== docs-only diff =="
repo_docs=$(setup_repo "docs/note.md")
run 0 'docs-only + 免除宣言 + レビュー行なし → 合格' "$repo_docs" "$(full_body "" 0 "対象外（コード変更なし）")"
rm -rf "$repo_docs"

echo "----------------------------------------"
printf '合計: PASS=%s FAIL=%s\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
