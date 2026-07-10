#!/bin/bash
# PreToolUse hook — `gh pr create` を検出したら PR 規範チェック（check-pr.sh）を強制する。
# 対象外コマンドは即許可（exit 0）。違反は exit 2 で拒否し、stderr がエージェントに戻る。
# 許可する唯一の形式: gh pr create --title "..." --body-file <絶対パス>（create-pr skill 参照）
set -u

payload=$(cat)

# 高速素通し: gh…pr…create が順に現れなければ解析すらしない
# （-R 等のグローバルフラグ挟み込みを拾うため、隣接ではなく順序一致で見る）
case "$payload" in
  *gh*pr*create*) ;;
  *) exit 0 ;;
esac

# 判定は python3（JSON + shlex）。1 行を返す: ALLOW / REJECT<TAB>理由 / CHECK<TAB>title<TAB>body-file
# heredoc がプログラム本体を占有するため、payload は環境変数で渡す（stdin は使えない）
result=$(PRE_PR_GATE_PAYLOAD="$payload" python3 - <<'PY'
import json, os, re, shlex, sys

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
# `gh` トークンから厳密照合する（隣接不要 — `gh -R <repo> pr create` のような
# グローバルフラグ挟み込みは対象、`gh pr list --search create` のような別 subcommand は非対象）。
# 規則: `gh` の後、フラグトークン（`-` 始まり）とその値のみを挟んで、次の非フラグトークンが
# `pr`、さらに同条件で次の非フラグトークンが `create` であれば検出。`=` を含まないフラグは
# 直後 1 トークンを値として消費し得るが、その値が次に期待する語なら語を優先する。
# どの `gh` からも成立しなければ ALLOW（引用文字列内の言及は 1 トークンなので `gh` に一致しない）。
def is_gh_pr_create(tokens):
    want = ["pr", "create"]
    n = len(tokens)
    i = 0
    while i < n:
        if tokens[i] != "gh":
            i += 1
            continue
        j = i + 1
        wi = 0
        matched = True
        while wi < len(want) and j < n:
            t = tokens[j]
            if t.startswith("-"):
                # フラグ。= 無しかつ直後が期待語でなければ、直後 1 トークンを値として消費。
                if "=" not in t and j + 1 < n and tokens[j + 1] != want[wi]:
                    j += 2
                else:
                    j += 1
                continue
            if t == want[wi]:
                wi += 1
                j += 1
            else:
                matched = False
                break
        if matched and wi == len(want):
            return True
        i += 1  # この `gh` からは不成立 — 次の `gh` 出現から再試行
    return False

if not is_gh_pr_create(toks):
    print("ALLOW"); sys.exit(0)
title = body = None
for i, t in enumerate(toks):
    if t == "--title" and i + 1 < len(toks):
        title = toks[i + 1]
    elif t.startswith("--title="):
        title = t[len("--title="):]
    elif t == "--body-file" and i + 1 < len(toks):
        body = toks[i + 1]
    elif t.startswith("--body-file="):
        body = t[len("--body-file="):]
    elif t.split("=")[0] in ("--body", "-b", "--fill", "--fill-first", "--fill-verbose", "--web", "-w", "-f"):
        # = 形式のブールフラグ（--web=true 等）も割った先頭で拒否判定する。-f は --fill の短縮形。
        print(f"REJECT\t{t} は不可 — --title \"...\" --body-file <絶対パス> の形式のみ許可")
        sys.exit(0)
    elif re.match(r"^-[A-Za-z]{2,}$", t) and any(c in t for c in ("b", "f", "w")):
        # 連結ブール短縮（-dw 等）: b/f/w のいずれかを含めば --body/--fill/--web 相当として拒否。
        # -d 単独や --draft は {2,} または `--` 始まりで対象外（現行どおり許可）。
        print(f"REJECT\t{t} は不可（連結短縮フラグに --body/--fill/--web 相当を含む）— --title \"...\" --body-file <絶対パス> の形式のみ許可")
        sys.exit(0)
if not title or not body:
    print("REJECT\t--title と --body-file が必須（長形式のみ・スペース区切りと = 形式どちらでも可。-t/-F 等の短縮形は不可）")
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
