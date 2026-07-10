#!/bin/bash
# .claude/agents/*.md の frontmatter skills: エントリが実在の skill に解決できるか検証する（#290）。
# 裸名（`:` を含まない）→ .claude/skills/<name>/SKILL.md が無ければエラー（exit 1）。
# 名前空間付き（`plugin:skill` 形式）→ CI ではプラグイン導入状態を検証できないため skip（情報表示のみ）。
# skills: キーが無いファイルは正常。ブロックリスト以外の形（inline [...] 等）は「未対応形式」としてエラー。
set -u

# リポジトリルートは自身の位置から解決する（cwd がルートでも .claude/scripts でも動く）
script_dir=$(cd "$(dirname "$0")" && pwd)
repo_root=$(cd "$script_dir/../.." && pwd)
agents_dir="$repo_root/.claude/agents"
skills_dir="$repo_root/.claude/skills"

if [ ! -d "$agents_dir" ]; then
  echo "FATAL: agents ディレクトリが見つからない: $agents_dir" >&2
  exit 1
fi

AGENTS_DIR="$agents_dir" SKILLS_DIR="$skills_dir" python3 - <<'PY'
import os, re, sys, glob

agents_dir = os.environ["AGENTS_DIR"]
skills_dir = os.environ["SKILLS_DIR"]

errors = []
infos = []

def strip_item(raw):
    s = raw.strip()
    if len(s) >= 2 and s[0] == s[-1] and s[0] in ("'", '"'):
        s = s[1:-1]
    return s

for path in sorted(glob.glob(os.path.join(agents_dir, "*.md"))):
    name = os.path.basename(path)
    with open(path, encoding="utf-8") as f:
        lines = f.read().splitlines()

    # frontmatter は先頭の `---` ペアの間
    if not lines or lines[0].strip() != "---":
        continue
    end = None
    for idx in range(1, len(lines)):
        if lines[idx].strip() == "---":
            end = idx
            break
    if end is None:
        continue
    front = lines[1:end]

    # トップレベル `skills:` キーを探す（フロントマターは無インデント）
    skills_idx = None
    for idx, line in enumerate(front):
        if re.match(r"^skills:\s*(.*)$", line):
            skills_idx = idx
            break
    if skills_idx is None:
        continue  # skills: キーが無いのは正常

    rest = re.match(r"^skills:\s*(.*)$", front[skills_idx]).group(1).strip()
    if rest:
        # `skills: [tdd]` のような inline 形式は未対応
        errors.append(f"{name}: skills: が未対応形式です（ブロックリストのみサポート）: {front[skills_idx]!r}")
        continue

    # 後続のブロックリスト項目（`  - name`）を収集（空行・コメント行はブロック終端とみなさずスキップ）
    entries = []
    for line in front[skills_idx + 1:]:
        if not line.strip() or re.match(r"^\s*#", line):
            continue
        m = re.match(r"^\s+-\s*(.+)$", line)
        if not m:
            break
        entries.append(strip_item(m.group(1)))

    for entry in entries:
        if ":" in entry:
            infos.append(f"{name}: {entry} は名前空間付き参照のため skip（CI ではプラグイン導入状態を検証不可）")
            continue
        skill_file = os.path.join(skills_dir, entry, "SKILL.md")
        if not os.path.isfile(skill_file):
            errors.append(f"{name}: skills 参照 '{entry}' が {skill_file} に解決できない")

for info in infos:
    print(f"INFO: {info}")

if errors:
    for err in errors:
        print(f"NG: {err}", file=sys.stderr)
    sys.exit(1)

print("OK: すべての agents skills 参照が解決できた")
sys.exit(0)
PY
