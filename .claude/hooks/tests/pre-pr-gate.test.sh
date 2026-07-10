#!/bin/bash
# pre-pr-gate.sh の回帰テスト（#261）。
# 方式: 一時ディレクトリに hook 実物と check-pr.sh スタブを配置して密閉し、
#       hook JSON payload（{"tool_input":{"command":"..."}}）を stdin で流して
#       exit code（許可=0 / 拒否=2）と、CHECK 経路はスタブ到達を断言する。
# テスト対象 hook は第 1 引数または $PRE_PR_GATE_HOOK で差し替え可（既定=リポジトリ実物）。
set -u

script_dir=$(cd "$(dirname "$0")" && pwd)
hook_src=${1:-${PRE_PR_GATE_HOOK:-"$script_dir/../pre-pr-gate.sh"}}

if [ ! -f "$hook_src" ]; then
  echo "FATAL: テスト対象の hook が見つからない: $hook_src" >&2
  exit 1
fi

# 密閉テスト木: hook 実物のコピー + check-pr.sh スタブ（引数を記録して exit 0）。
# hook は check-pr.sh を自身の位置からの相対（../skills/create-pr/）で解決するため、
# この配置でスタブへ確実に到達する。
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/.claude/hooks" "$work/.claude/skills/create-pr"
cp "$hook_src" "$work/.claude/hooks/pre-pr-gate.sh"
chmod +x "$work/.claude/hooks/pre-pr-gate.sh"
hook="$work/.claude/hooks/pre-pr-gate.sh"

stub_log="$work/stub-args.log"
cat > "$work/.claude/skills/create-pr/check-pr.sh" <<STUB
#!/bin/bash
# check-pr.sh スタブ — 受け取った引数を記録して合格（exit 0）を返す
printf '%s\n' "\$@" > "$stub_log"
exit 0
STUB
chmod +x "$work/.claude/skills/create-pr/check-pr.sh"

pass=0
fail=0

# コマンド文字列を安全に JSON payload へ埋め込む
make_payload() {
  PAYLOAD_CMD="$1" python3 -c 'import json,os; print(json.dumps({"tool_input":{"command":os.environ["PAYLOAD_CMD"]}}))'
}

# run <期待exit> <説明> <コマンド> — exit code のみ断言（許可/拒否ケース）
run() {
  local want=$1 desc=$2 cmd=$3 got
  make_payload "$cmd" | "$hook" >/dev/null 2>&1
  got=$?
  if [ "$got" -eq "$want" ]; then
    printf 'PASS [exit %s]        %s\n' "$got" "$desc"
    pass=$((pass + 1))
  else
    printf 'FAIL [exit %s want %s] %s\n' "$got" "$want" "$desc"
    fail=$((fail + 1))
  fi
}

# check <説明> <コマンド> — CHECK 経路: hook exit 0 かつスタブ到達を断言
check() {
  local desc=$1 cmd=$2 got reached
  rm -f "$stub_log"
  make_payload "$cmd" | "$hook" >/dev/null 2>&1
  got=$?
  [ -f "$stub_log" ] && reached=到達 || reached=未到達
  if [ "$got" -eq 0 ] && [ "$reached" = 到達 ]; then
    printf 'PASS [exit 0 スタブ到達]  %s\n' "$desc"
    pass=$((pass + 1))
  else
    printf 'FAIL [exit %s スタブ%s] %s\n' "$got" "$reached" "$desc"
    fail=$((fail + 1))
  fi
}

echo "== 許可（exit 0）=="
run 0 '許可: git status'                        'git status'
run 0 '許可: gh issue create --title x'         'gh issue create --title x'
run 0 '許可: gh pr list --search create'        'gh pr list --search create'
run 0 '許可: gh pr view 123'                    'gh pr view 123'
run 0 '許可: gh pr checks 42'                   'gh pr checks 42'
run 0 '許可: gh pr edit 5 --title x'            'gh pr edit 5 --title x'
run 0 '許可: echo "gh pr create"（引用内言及）' 'echo "gh pr create"'

echo "== 拒否（exit 2）=="
run 2 '拒否: gh pr create（title/body 欠如）'          'gh pr create'
run 2 '拒否: gh pr create --title x --body y'          'gh pr create --title x --body y'
run 2 '拒否: gh pr create --fill'                      'gh pr create --fill'
run 2 '拒否: gh pr create -f（--fill 短縮）'           'gh pr create -f'
run 2 '拒否: gh pr create --title x --body-file /a -w' 'gh pr create --title x --body-file /a -w'
run 2 '拒否: gh pr create --title x --body-file /a -dw' 'gh pr create --title x --body-file /a -dw'
run 2 '拒否: gh pr create --title x --body-file rel/path（相対）' 'gh pr create --title x --body-file rel/path'
run 2 '拒否: gh pr create --title x --body-file=/a --web=true'   'gh pr create --title x --body-file=/a --web=true'
run 2 '拒否: gh -R o/r pr create -f（グローバルフラグ越し）'      'gh -R o/r pr create -f'

echo "== CHECK 到達（exit 0 + スタブ到達）=="
check 'CHECK: gh pr create --title "t" --body-file /abs'          'gh pr create --title "t" --body-file /abs'
check 'CHECK: gh -R o/r pr create --title t --body-file /abs'     'gh -R o/r pr create --title t --body-file /abs'
check 'CHECK: cd /x && gh pr create --title t --body-file /abs'   'cd /x && gh pr create --title t --body-file /abs'

echo "----------------------------------------"
printf '合計: PASS=%s FAIL=%s\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
