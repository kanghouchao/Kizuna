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
    # クォート付き（`"x" # c` 等）: 内側の値を取り出す。閉じクォート後の行内コメントは許容する
    m = re.match(r"^(['\"])(.*?)\1\s*(?:#.*)?$", s)
    if m:
        return m.group(2)
    # 非クォート: 行全体がコメントなら空エントリ扱い
    if s.startswith("#"):
        return ""
    # 空白+# 以降は行内コメントとして除去する（`foo#bar` のように空白を伴わない # は名前の一部）
    m = re.search(r"\s+#", s)
    if m:
        return s[: m.start()]
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

    # キー行の行内コメント（`skills: # ...`）は YAML として正当なので除去してから形式判定する（#310）
    rest = strip_item(re.match(r"^skills:\s*(.*)$", front[skills_idx]).group(1))
    if rest:
        # `skills: [tdd]` のような inline 形式は未対応
        errors.append(f"{name}: skills: が未対応形式です（ブロックリストのみサポート）: {front[skills_idx]!r}")
        continue

    # 後続のブロックリスト項目（`  - name`）を収集する。
    # ・空行/コメント行はスキップ（ブロック終端にしない）。
    # ・非インデント行（次のトップレベルキー）のときだけブロック終端とする。
    # ・`-` のみの行（YAML null item）や、項目にもコメントにも一致しないインデント行は、
    #   黙って走査を打ち切らずエラーとして記録したうえで走査を継続する（#290）。
    entries = []
    for line in front[skills_idx + 1:]:
        if not line.strip() or re.match(r"^\s*#", line):
            continue
        if re.match(r"^\S", line):
            break
        m = re.match(r"^\s+-\s*(.+)$", line)
        if m:
            raw_entry = m.group(1)
            entries.append((raw_entry, strip_item(raw_entry)))
            continue
        if re.match(r"^\s+-\s*$", line):
            errors.append(f"{name}: skills エントリが不正です（null item）: {line!r}")
            continue
        errors.append(f"{name}: skills ブロック内の行を解釈できません: {line!r}")

    for raw_entry, entry in entries:
        if not entry:
            errors.append(f"{name}: skills エントリが不正です（空、またはコメントのみ）: {raw_entry!r}")
            continue
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
