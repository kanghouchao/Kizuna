#!/bin/bash
# PreToolUse hook — `gh pr create` を検出したら PR 規範チェック（check-pr.sh）を強制する。
# 対象外コマンドは即許可（exit 0）。違反は exit 2 で拒否し、stderr がエージェントに戻る。
# 許可する唯一の形式: gh pr create --title "..." --body-file <絶対パス>（create-pr skill 参照）
set -u

payload=$(cat)

# 高速素通し: 該当句が無ければ解析すらしない
case "$payload" in
  *"gh pr create"*) ;;
  *) exit 0 ;;
esac

# 判定は python3（JSON + shlex）。1 行を返す: ALLOW / REJECT<TAB>理由 / CHECK<TAB>title<TAB>body-file
# heredoc がプログラム本体を占有するため、payload は環境変数で渡す（stdin は使えない）
result=$(PRE_PR_GATE_PAYLOAD="$payload" python3 - <<'PY'
import json, os, shlex, sys

try:
    data = json.loads(os.environ.get("PRE_PR_GATE_PAYLOAD") or "{}")
except Exception:
    print("ALLOW"); sys.exit(0)
cmd = (data.get("tool_input") or {}).get("command") or ""
try:
    toks = shlex.split(cmd)
except ValueError:
    print("REJECT\tコマンドを解析できない — gh pr create は --title \"...\" --body-file <絶対パス> の単純形式のみ許可")
    sys.exit(0)
# トークン列としての gh pr create のみ対象（文字列中の言及は素通し）
if not any(toks[i:i+3] == ["gh", "pr", "create"] for i in range(len(toks) - 2)):
    print("ALLOW"); sys.exit(0)
title = body = None
for i, t in enumerate(toks):
    if t == "--title" and i + 1 < len(toks):
        title = toks[i + 1]
    elif t == "--body-file" and i + 1 < len(toks):
        body = toks[i + 1]
    elif t in ("--body", "-b", "--fill", "--fill-first", "--fill-verbose", "--web", "-w"):
        print(f"REJECT\t{t} は不可 — --title \"...\" --body-file <絶対パス> の形式のみ許可")
        sys.exit(0)
if not title or not body:
    print("REJECT\t--title と --body-file が必須")
    sys.exit(0)
if not body.startswith("/"):
    print("REJECT\t--body-file は絶対パスで指定する（hook は cwd に依存できない）")
    sys.exit(0)
print(f"CHECK\t{title}\t{body}")
PY
)

verdict=${result%%$'\t'*}
case "$verdict" in
  ALLOW)
    exit 0
    ;;
  REJECT)
    echo "NG: $(printf '%s' "$result" | cut -f2-)（create-pr skill 参照）" >&2
    exit 2
    ;;
  CHECK)
    title=$(printf '%s' "$result" | cut -f2)
    body_file=$(printf '%s' "$result" | cut -f3)
    hook_dir=$(cd "$(dirname "$0")" && pwd)
    if ! "$hook_dir/../skills/create-pr/check-pr.sh" "$title" "$body_file" >&2; then
      echo "NG: PR 規範チェック不合格 — 上記を修正してから再実行（create-pr skill 参照）" >&2
      exit 2
    fi
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
