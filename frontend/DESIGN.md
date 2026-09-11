# デザインシステム

UI の追加・変更前に読む規約。コード構成とデータ取得の使い分けは [フロントエンド規約](AGENTS.md)、基盤の選定理由は [ADR 0005](../docs/adr/0005-frontend-shadcn-ui-on-base-ui.md)を参照する。以下は有効な設計規則であり、全画面の適合を保証する一覧ではない。

## 適用範囲

| 画面                    | 正本                                          | 適用                                                |
| ----------------------- | --------------------------------------------- | --------------------------------------------------- |
| 管理画面・本人ポータル  | `src/app/globals.css`、`src/shared/ui`        | Base UI ベースの shadcn/ui と Tailwind の意味 token |
| 公開店舗サイト          | `_pages/store-site/templates/<key>/theme.css` | `--storefront-*` と共有 `_sections/`                |
| AuthLayout 内の認証画面 | `src/styles/auth.css`                         | 認証専用のクラス                                    |

AuthLayout の利用で認証画面を判定する。アカウント設定内の password-change は管理画面に属す。`(public)` の 404 画面は管理 token を使うがライト専用であり、公開 layout に theme wiring を追加しない。テーマの配置は `src/__tests__/theme-provider-scope.test.tsx` が固定する。

## 色

管理画面は `:root` / `.dark` の oklch 値を `@theme inline` で公開する。生の色名クラス・hex を追加せず、意味で token を選ぶ。`--spacing` の変更は全画面に波及するため禁止する。

| 用途                                 | クラス                                           |
| ------------------------------------ | ------------------------------------------------ |
| ページ／カード                       | `bg-background` / `bg-card text-card-foreground` |
| 本文／注釈                           | `text-foreground` / `text-muted-foreground`      |
| 装飾境界／無操作の面                 | `border` / `bg-muted`                            |
| 主操作                               | `bg-primary text-primary-foreground`             |
| リンク・意味を持つ図形               | `text-primary-strong` / `bg-primary-strong`      |
| 成功・確定／注意・保留／破壊・エラー | `success` / `warning` / `destructive`            |
| 状態ラベル                           | `bg-<token>/10 text-<token>-strong`              |
| 塗りのある状態面                     | `bg-<token> text-<token>-foreground`             |
| 装飾カテゴリ                         | `bg-chart-N/10 text-foreground`（N = 1〜5）      |

- 基本 token は塗り用、`-strong` は文字と意味を持つ図形用。管理画面で手書きする `text-primary` は使わない。状態アイコンは基本 token を使えるが背景との対比を確認する。
- `FormMessage` 等の vendored 出力にある `text-destructive` は既存例外。手書きは `text-destructive-strong`。
- `chart-*` は薄い背景専用。`text-chart-N` やラベルを載せた未計測の単色カテゴリを作らない。中立カテゴリは `bg-muted text-foreground`。
- `text-muted-foreground` を `bg-muted` や色付き面に重ねない。注釈パネルは `rounded-lg border p-4` として塗りを外すか、文字を `text-foreground` にする。hover で背景が変わる場合も文字を同時に切り替える。
- danger は追加せず destructive に統一する。単一画面の都合だけで専用色を増やさない。新しい意味色は token と本書を同時に更新する独立した変更で扱う。

### 対比の基準と記録

通常文字は 4.5:1、意味を持つ図形は 3:1 を両モードで満たす。以下は既存文書の計算記録であり、今回の整理で再計測した値ではない。token や背景を変えた場合は再計測する。`/10` はカード／ページへ直接合成した条件である。

| 組合せ                                                          | ライト     | ダーク     | 必要比 |
| --------------------------------------------------------------- | ---------- | ---------- | ------ |
| `text-foreground` / `bg-background` / `bg-card`                 | 19.89      | 16.98      | 4.5    |
| `text-muted-foreground` / `bg-background` / `bg-card`           | 4.83       | 6.74       | 4.5    |
| `text-foreground` / `bg-muted` （hover 行）                     | 18.07      | 14.26      | 4.5    |
| `bg-primary` + `text-primary-foreground` (CTA)                  | 5.03       | 5.03       | 4.5    |
| `text-primary-strong` / `bg-card`                               | 5.26       | 6.72       | 4.5    |
| `text-primary-strong` / `bg-background`                         | 5.26       | 7.54       | 4.5    |
| `bg-primary/10` + `text-primary-strong`                         | 4.55       | 6.23       | 4.5    |
| `bg-primary-strong` 図形 / `bg-card`                            | 5.26       | 6.72       | 3      |
| `bg-primary-strong` 塗り / `bg-muted` トラック                  | 4.78       | 5.65       | 3      |
| `text-success-strong` / `bg-card`                               | 9.07       | 9.99       | 4.5    |
| `bg-success/10` + `text-success-strong`                         | 8.10       | 8.35       | 4.5    |
| `bg-success` + `text-success-foreground`                        | 6.18       | 11.20      | 4.5    |
| `bg-success/90` hover + `text-success-foreground`               | 5.10       | 9.04       | 4.5    |
| `bg-success` 図形 / `bg-card`                                   | 3.22       | 9.99       | 3      |
| `border-success-strong` 境界 / `bg-muted` トラック              | 8.24       | 8.39       | 3      |
| `bg-success/10` hover / `bg-muted` + `text-foreground`          | 16.23      | 11.75      | 4.5    |
| `text-warning-strong` / `bg-card`                               | 9.09       | 10.33      | 4.5    |
| `bg-warning/10` + `text-warning-strong`                         | 8.15       | 8.50       | 4.5    |
| `bg-warning` + `text-warning-foreground`                        | 6.23       | 11.58      | 4.5    |
| `bg-warning/90` hover + `text-warning-foreground`               | 5.14       | 9.35       | 4.5    |
| `bg-warning` 図形 / `bg-card`                                   | 3.19       | 10.33      | 3      |
| `text-destructive-strong` / `bg-card`                           | 10.06      | 6.13       | 4.5    |
| `text-destructive-strong` / `bg-background`                     | 10.06      | 6.88       | 4.5    |
| `bg-destructive/10` + `text-destructive-strong`                 | 8.42       | 5.39       | 4.5    |
| `bg-destructive` + `text-destructive-foreground`                | 4.56       | 6.88       | 4.5    |
| `bg-destructive` 図形 / `bg-card`                               | 4.76       | 6.13       | 3      |
| `bg-destructive` マーカー / `bg-muted` トラック                 | 4.33       | 5.15       | 3      |
| `text-destructive` / `bg-card` (FormMessage)                    | 4.76       | 6.13       | 4.5    |
| `bg-chart-1/10` … `bg-chart-5/10` + `text-foreground`           | 16.87 以上 | 14.40 以上 | 4.5    |
| `text-foreground` / `bg-primary/10`                             | 17.22      | 15.75      | 4.5    |
| `text-primary-strong` / `bg-accent` （ghost hover）             | 4.78       | 5.65       | 4.5    |
| `border-primary` / `ring-primary` / `bg-card` / `bg-background` | 5.26       | 3.37       | 3      |
| `bg-destructive/90` hover 塗り + アイコン                       | 4.32       | 5.64       | 3      |
| `border-muted-foreground` ドロップ領域 境界 / `bg-card`         | 4.83       | 6.74       | 3      |

新しい組合せは oklch → sRGB（gamma と gamut clamp を含む）→相対輝度で計算し、半透明は gamma-encoded sRGB で合成する。黒／白 = 21.00、`#767676`／白 = 4.54 で輝度計算を確認し、既存の plain・tint・track の行で変換と合成も確認する。不一致は計算器と token の両方を調べ、既存表を副作用で書き換えない。

- 二重 tint は別の組合せ。`bg-primary/10` の親と子に primary-strong を重ねるとライトで 3.97。親の hover 中は `group-hover:text-foreground` 等で切り替え、入れ子を計測する。
- 背景が不定の画像上の半透明 veil は最悪条件で測る。必要なら `bg-card` の不透明面にする。
- 1px 境界と、同じ意味の文字に隣接する凡例の点は装飾例外。位置・長さが情報の図形には自動適用しない。
- **シフト timeline の既存例外**: `bg-muted` 上の success / warning バーはライトで 2.92 / 2.90。所有者が、時刻の併記・shadow・既存表示との関係を理由にこの画面だけ認めた。非公開の確定シフトの hollow 境界は `border-success-strong` を使い、この例外に含めない。
- timeline 内の操作ボタンはブラウザ既定 outline を保持する。背景と overflow によって検証済み ring の配置が成立しないための限定例外であり、他画面へ広げない。

### 公開サイト

default / modern / classic は同一の `--storefront-*` 契約を各 theme.css に定義する。共有 `_sections/` は `var()` と `color-mix()` だけで色を読む。新しい token は全テンプレートへ同時追加し、テンプレート別の section 複製や inline hex を使わない。値一覧は CSS を正本とし、本書に複製しない。

## 文字・アイコン・余白

- 管理画面は system sans と日本語 fallback。本文／ラベル 14px、主要数値 30px bold、ページ見出しは全 breakpoint で `text-2xl`。default 店舗サイトの見出しは Noto Serif JP 系と広い字間を使う。
- アイコンは `lucide-react` の `Icon` 接尾辞の export、既定 strokeWidth = 2。メニューの ICON_MAP が許可名を定める。
- console layout が `p-8 max-w-7xl mx-auto` を供給する。ページは padding・幅制限・ナビゲーションを重ねない。
- カード内周 24px、カード間／フィールド間 24px、ラベルと入力 8px、toolbar 12px、行内操作 4px。sidebar 256px、header 64px、カード `rounded-xl shadow-sm`。
- 表は `TableCard` と `Table` を使う。左右端セル 24px、内部セル横 8px・縦 12px、header `h-11`。カードの内周とセル密度を別々に保つ。暦セルは `p-3`、行カードは `p-4`。
- TableCard の子孫 selector はセルの単純 class より強い。密度変更はそこで行い、最初／最後の checkbox セルでは primitive の余白規則との競合を確認する。1024px で表の scrollWidth を確認してから横余白を広げる。
- 管理 console の内容列は `min-w-[44rem]`。狭い viewport は横スクロールで操作可能性を保つ。新しい toolbar は最大長の店名・人名で intrinsic width を測り、label の幅上限を決めた後に床幅を確認する。
- 公開サイトの余白は既存 sections の `max-w-7xl`、`px-5 lg:px-10` に揃える。

## コンポーネント

既存 primitive を `@/shared/ui` から使う。vendored への画面別変更は consumer の className で表す。

| 用途             | 規則                                                                                                             |
| ---------------- | ---------------------------------------------------------------------------------------------------------------- |
| 主／副／破壊操作 | Button の default / outline / destructive。遷移は `render={<Link href="…" />}`、ボタン本文は Button の children  |
| 入力             | Input / Textarea / Select / Checkbox / Switch / RadioGroup / Label                                               |
| モーダル         | 中央の Dialog。長いフォームは `max-h-[calc(100vh-2rem)] overflow-y-auto`。横スライド Drawer は追加しない         |
| 破壊確認         | ConfirmDialog。window.confirm や画面内での AlertDialog 再構築をしない                                            |
| 検索選択         | CastSearchCombobox の Base UI Combobox。サーバ検索は `filter={null}`。現状は単一消費者のためページ内で組み立てる |
| 表／タブ         | TableCard + Table / Tabs                                                                                         |
| 状態             | outline Badge と tint、`border-transparent`                                                                      |
| 読み込み         | 形が既知なら Skeleton、未知または小領域は「読み込み中...」。手製 animate-pulse を作らない                        |
| 進捗             | `bg-muted h-2 rounded-full` と `bg-primary-strong`                                                               |

- CardTitle が章見出しなら `role="heading" aria-level={N}` を渡す。h1 直下は 2、入れ子は 3 とし、飛ばさない。単なるラベルには付けない。
- destructive の DropdownMenuItem は consumer で `text-destructive-strong focus:bg-destructive/10 focus:text-destructive-strong`。vendored の destructive variant は tint 上の文字対比が不足する。
- 裸の操作要素は `focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary`。この ring はカード／ページ面でのみ認証済み。overflow で切れる場合は ring-inset を検討し、背景も再検証する。
- sidebar は高さ 40px・icon 20px・文字 14px。選択は primary tint と strong の縁、非選択 hover は `bg-muted text-foreground`。グループ見出しは折り畳まない。
- 統計カードは注釈・数値・増減の順。カテゴリ chip は chart tint と foreground。ランキングは 32px の primary tint の順位と名前、右端に金額／件数。
- 選択 preview は label 内の sr-only radio、選択時 `border-primary ring-2 ring-primary bg-primary/10`。説明は未選択時だけ muted、hover／選択時は foreground。focus-visible を label に伝播する。
- 本人ポータルの固定下部 tab は 24px icon + 12px label、content に `pb-16`。選択文字は primary-strong、非選択 hover は背景と文字を同時に変更する。

### 一覧

ListPage（`@/widgets/list-page`）が見出し・検索 form・表枠・読み込み／失敗／空・ページングを所有する。props の正本はコンポーネントの型。ページは表内容と固有操作を渡す。

- useListPage の PageResult は rows / page / pageCount / total（page は 0 始まり）。裸配列では pagination props を省略する。
- 検索欄は入力中の値と適用済み条件を分ける。submit で ref へ確定して search() を呼び、fetcher は ref を読む。Enter は shell の form に任せる。
- offset sort は一意の副キーまで指定する（例 `displayOrder,id,asc`）。
- ダイアログは ListPage の外に置く。children は読み込み中や空一覧で unmount される。
- モーダルを開く操作は button、画面遷移は link のまま保つ。見た目だけで役割を交換しない。

## 通知と失敗状態

先に表示場所、次に重大度を選ぶ。表示直後に root layout を跨いで画面が破棄されるなら toast は使わず、現画面で読んでから移動する導線、または遷移先の固定文言 reason を使う。reason は呼び出し側が渡し、遷移先の allowlist で解決する。未知値を表示せず、query の文言を直接描画せず、logout の既定遷移先も変更しない。

| 優先 | 状況                                                       | 表示場所                               |
| ---- | ---------------------------------------------------------- | -------------------------------------- |
| 1    | 取得内容がない／誤っている（一覧・詳細・選択肢・部分領域） | その領域に失敗を表示。toast は使わない |
| 2    | ブラウザで判定できる修正可能な入力不備                     | 入力のそばの関連付け済みエラー         |
| 3    | その他の操作結果                                           | toast                                  |

server の details は現行クライアントでは field error に戻さず、getApiErrorMessage の文言を操作失敗に使う。ブラウザで可能な検証は送信前の規則として追加し、権限・一意性等のサーバ状態の判定はサーバに任せる。

### 操作結果

| 呼び出し       | 意味                                                           | 表示時間 |
| -------------- | -------------------------------------------------------------- | -------- |
| notify.success | 完了                                                           | 3000ms   |
| notify.error   | 失敗し、画面に入力等が残って失敗を理解できる                   | 5000ms   |
| notify.warning | 失敗後に値が置換される等、画面だけでは失われた事実が分からない | 10000ms  |

- 呼び出しは `@/shared/notify` の三メソッドへ事実の一文だけを渡す。色・時間・icon・例外変換を持ち込まない。warning の「警告：」は語義層が付ける。
- Base UI toast manager への直接アクセスは禁止。import の例外は notify と shared/ui/toast.tsx だけ。動的 import・require・mock も同じ規則で読む。
- 描画は toast.tsx に集約する。success/error/warning は各対比表の単色と foreground、icon は CircleCheckIcon / CircleXIcon / TriangleAlertIcon、20px・currentColor・shrink-0。
- 全段に不透明の × dismiss を置く。ghost Button の hover 背景は流用しない。toast 自体は currentColor の inset ring、dismiss は currentColor ring。viewport はブラウザ outline を保持し outline-none を付けない。F6 → toast → dismiss のフォーカス経路を確認する。
- 通知は `z-[60]` で z-50 の overlay より上。年齢ゲートの z-[9999] は通知より上。重なりと icon の縮みは実ブラウザで確認する。
- title は accessible name に接続した span とし、heading にしない。viewport の role/status 等はライブラリの live-region 配線を維持する。dismiss の aria-hidden は hover/focus までライブラリが制御するためテストは適切な slot を選ぶ。
- 無段 toast は notify から発行しない。公開側はライト token の通知となり、中立色の新しい通知を置くなら認証・公開の背景で検証する。viewport の既定 outline と挿入時読み上げにはブラウザ／支援技術差がある。

### 領域内エラー

RegionError の `message` と、`onRetry` または `fallback: {href,label}` のどちらか一つを渡す。className は配置だけに使う。赤い一文と outline の回復操作で示す。

- 失敗時は古い値と paging を消す。load-more 失敗も最初のページから再試行する。取得失敗だけを理由に送信を止めず、必須入力検証は維持する。
- 詳細の 404 はその場で一覧リンクを出し、retry を出さない。削除済み対象のモーダルは alert の本文と閉じる操作に置換し、保存を外して閉じる際に一覧を更新する。
- container は role=alert。フォーカスを奪わない。再試行開始で failure を畳み、再失敗を再通知する。
- 単一値は useResource。`failure !== null` と isLoading を明示分岐し、data=null を失敗と同一視しない。null fetcher は未取得条件であり、既取得値を捨てる指示ではない。
- フォームが見える最初の render で値も揃える。useState の draft は元 data と対応付けて導出、react-hook-form は seed が入るまで fields を出さない。更新応答を setData で反映できるときは再取得しない。

### 入力検証とフォーム

- ブラウザの bubble ではなく、メッセージ付き規則と入力横表示を使う。`noValidate` は、それまで効いていた required / min / email 等の全制約を置換する規則と同時に追加する。先に無効化しない。
- Form + FormField + rules + FormControl + FormMessage を使う。裸の Controller と二重 register を作らない。ネイティブ入力で field error が不要なら register のままでよい。
- すべての規則に文言を付ける（`required: '…'`、validate は説明文字列を返す）。制御 component の field.ref は focusable trigger まで渡す。FormControl の aria-invalid / aria-describedby を保持する。
- required/type/min は操作・読み上げのヒントとして残す。制御 component にも required を渡す。「N 個から一つ以上」は各 checkbox を required にせず、group の label・説明と error を関連付ける。不要になった minLength は除く。
- 認証画面では auth.css に適合する field error を使い、aria-invalid / aria-describedby を自分で接続する。既存 auth-alert--error の記録値は 4.42 であり、field error への流用を認めない。
- validation 未完了を理由に submit を disabled にしない。押してエラーと先頭不備へのフォーカスを示す。isSubmitting の二重送信防止は維持する。
- 値変換は onChange 境界。数値 Input は `e.target.valueAsNumber` を使い、空欄を Number('') で 0 にしない。Select の数値／boolean と Checkbox の値も API 型へ戻す。
- 未選択 Select は `__none__` 等の sentinel を境界で空文字に戻す。defaultValues を全項目に与える。trigger の表示名には items を渡す。Base UI の未選択 null と API の空値を混同しない。
- 見た目変更では送信 payload、空欄の意味、確認操作を維持する。free text を Select に変える等の契約変更を混ぜない。

## 共有 UI とテスト

vendored は alert-dialog / button / card / dialog / select / form / table / badge / popover / skeleton / tabs / dropdown-menu / checkbox / switch / radio-group / input / label / textarea。整形以外は生成状態を保つ。

image-upload / auth-layout / theme-provider / confirm-dialog / table-card / toast / region-error は自作で、用途に応じて編集する。分類の正本はこの一覧。data-slot や Base UI import だけでは区別できず、未知のファイルは git log で確認する。

- 管理画面の raw palette 検索は page だけでなく参照する entities と自作 shared/ui も対象にする。例外は storefront、AuthLayout 内、vendored のみ。生 hex も確認する。
- setupTests.ts が ResizeObserver、scrollIntoView、PointerEvent を供給する。追加 shim は実際の失敗を確認してから同ファイルにまとめる。
- popup は fireEvent.click(trigger) で開く。Select / Combobox の項目決定は pointerDown(item) → click(item)。片方だけでは選択されないことがある。
- FormControl は data-slot を上書きするため role で選ぶ。Checkbox / RadioGroupItem は button ではなく、disabled の見た目は data-disabled。Button / Select trigger / Tabs は disabled を使う。
- UI 変更時は両テーマ、長い文字列、狭い幅、overlay 上の通知、キーボード移動を実ブラウザで確認する。jsdom は色・重なり・実寸法の検証にならない。
