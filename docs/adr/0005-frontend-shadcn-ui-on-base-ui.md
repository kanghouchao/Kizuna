# フロントエンドの UI 基盤を Radix から Base UI へ移す

Status: Accepted

Supersedes: [0004](0004-frontend-shadcn-ui-on-radix.md)

## Context

[0004](0004-frontend-shadcn-ui-on-radix.md) は shadcn/ui を vendoring する方式を採り、その base として
Radix を選んだ。当時すでに shadcn/ui の既定 base は Base UI へ切り替わっていたが、Radix も引き続き
サポートされており、移行コストを払う理由が無かった。

その後 Radix 側の更新は Toast の撤回（shadcn は `sonner` へ誘導）に代表されるように部品単位の縮小へ
向かい、shadcn の新しいレシピ群（`base-*` スタイル）は Base UI を前提に整備されている。基盤を分けた
まま部品ごとに別の base を混ぜると、`asChild` と `render`、`data-[state=open]` と `data-open` という
二つの語彙が同じ画面に同居し、規約が「どちらの部品か」を条件に分岐しはじめる。

加えて `radix-ui` は直接依存だけの問題ではない。Combobox を持たない穴を埋めていた `cmdk` が
`@radix-ui/react-dialog` などを推移的に引き込んでおり、直接依存を外しても Radix は依存ツリーに残る。

## Decision

base を **Base UI（`@base-ui/react`）** に一本化する。`radix-ui` と `cmdk` は依存から外す。

- **見た目は据え置く。** shadcn の `radix-*` と `base-*` は同一スタイル族どうしならクラス列が一致し、
  差分は基盤の配線だけである。この差分を移植の手本とし、当リポジトリが持つクラス列
  （`new-york-v4` 由来）はそのまま残す。スタイル族の乗り換えは行わない。
- **状態属性の読み替えは規定に従う。** `data-[state=open]` → `data-open`、`data-[state=checked]` →
  `data-checked`、`data-[state=active]` → `data-active`、`data-[disabled]` → `data-disabled`。
  位置決めの CSS 変数も `--radix-*-transform-origin` → `--transform-origin`、
  `--radix-*-trigger-width` → `--anchor-width` へ移る。
- **合成は `asChild` から `render` へ。** `Slot` に相当するものは `useRender` フックであり、
  `Button` / `Badge` / `FormControl` はこれで組む。
- **`label.tsx` は素の `<label>` とする。** Base UI の `Field.Label` は `Field.Root` を祖先に要求し、
  採ると消費者全件の構造改造を巻き込む。ラベルは Radix でも実質 `<label>` の薄い包みであり、
  外して失うものは無い。
- **`Button` に Base UI の `Button` は使わない。** 同コンポーネントは `type` を既定で `button` に
  倒すため、フォーム内の送信ボタンが黙って送信をやめる。`useRender` で素の `<button>` を描き、
  ネイティブの型付けを保つ。
- **`command.tsx`（cmdk）は削除する。** 唯一の消費者は指名候補のコンボボックスであり、
  Base UI の `Combobox` が同じ役目をそのまま担う。

0004 が定めた残りの決定——トークン層、ダークモードの方式、意味色の語彙、kebab-case とバレル経由の
参照、生成物を無改変で保つ方針——はいずれも base に依存しないため、そのまま引き継ぐ。

## Consequences

`Select` の引き金に出る文言の出どころが変わる。Radix は選択中 `SelectItem` の `ItemText` を写して
いたが、Base UI は `Select.Root` の `items` から引く。渡さなければ生の値がそのまま利用者に見える
ため、**全ての `Select` 消費者が候補一覧を持つ**ことになった。項目描画と同じ配列を読ませることで
定義は一箇所に留まるが、「候補に無い値」は自分で未選択へ倒す必要がある（素通しすると ID が出る）。

`onValueChange` は第二引数に出来事の詳細を渡し、利用者の操作以外——候補から消えた値の巻き戻し
など——でも鳴る。値だけを見て処理を書くと、背景の巻き戻しが利用者の操作として扱われる。
必要な箇所では `details.reason` で選り分ける。

テストの叩き方も変わる。開くのは `click`、項目の確定は `pointerdown` を経た `click` でなければ
無視される。jsdom には `PointerEvent` が無く、`Switch` / `Checkbox` はこれを構築して隠し input へ
中継するため、共通スタブを 1 件足した。具体的な作法は `frontend/DESIGN.md` が持つ。

依存ツリーから Radix は完全に消えた。プリミティブの追加は今後 shadcn の `base-*` レシピから行う。
ただし当リポジトリのクラス列は `new-york-v4` 由来であり `base-*` 族とは見た目が異なるため、
生成物をそのまま貼るのではなく、配線だけを取り込んでクラス列は既存に合わせる。
