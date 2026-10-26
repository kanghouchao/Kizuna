#!/usr/bin/env bash
# PR のマージコミット（HEAD）と base 側の親（HEAD^1）の差分がコード領域に触れないとき docs_only=true を出力する。
# 重いゲートを持つ全 job がこの一つの境界を共有する。差分が取れない場合は fail-closed（フルゲート実行）。
set -u
if ! files=$(git diff --name-only HEAD^1 HEAD); then
  echo "diff 取得失敗 — fail-closed でフルゲートを実行する"
  docs_only=false
elif printf '%s\n' "$files" | grep -qE '^(frontend/|backend/|e2e/|infrastructure/|Taskfile|\.github/workflows/|\.github/scripts/)'; then
  docs_only=false
else
  docs_only=true
fi
echo "docs_only=$docs_only" >> "$GITHUB_OUTPUT"
echo "docs_only: $docs_only"
