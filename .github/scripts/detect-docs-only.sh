#!/usr/bin/env bash
# PR のマージコミット（HEAD）と base 側の親（HEAD^1）の差分がコード領域に触れないとき docs_only=true を出力する。
# 重いゲートを持つ全 job がこの一つの境界を共有する。差分が取れない場合は fail-closed（フルゲート実行）。
# --no-renames: コード領域からの移動元も拾う。-z: 引用符で包ませず実際のパスを NUL 区切りで判定する。
set -u
files=$(mktemp)
trap 'rm -f "$files"' EXIT
if ! git diff --name-only --no-renames -z HEAD^1 HEAD >"$files"; then
  echo "diff 取得失敗 — fail-closed でフルゲートを実行する"
  docs_only=false
elif grep -qzE '^(frontend/|backend/|e2e/|infrastructure/|Taskfile|\.github/workflows/|\.github/scripts/)' "$files"; then
  docs_only=false
else
  docs_only=true
fi
echo "docs_only=$docs_only" >> "$GITHUB_OUTPUT"
echo "docs_only: $docs_only"
