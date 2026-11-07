# デザインシステム

UI の追加・変更前に読む規約。コード構成とデータ取得の使い分けは [フロントエンド規約](AGENTS.md)を参照する。以下は有効な設計規則であり、全画面の適合を保証する一覧ではない。

## 適用範囲

| 画面                                    | 正本                                          | 適用                                                |
| --------------------------------------- | --------------------------------------------- | --------------------------------------------------- |
| 管理画面・[本人ポータル](#本人ポータル) | `src/app/globals.css`、`src/shared/ui`        | Base UI ベースの shadcn/ui と Tailwind の意味 token |
| 公開店舗サイト                          | `_pages/store-site/templates/<key>/theme.css` | `--storefront-*` と共有 `_sections/`                |
| AuthLayout 内の認証画面                 | `src/styles/auth.css`                         | 認証専用のクラス                                    |

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

通常文字は 4.5:1、意味を持つ図形は 3:1 を両モードで満たす。以下は配色 A（藍 × 生成り）への変更時に、本文の手順（oklch → sRGB、gamma-encoded 合成）で再計測した値である。token や背景を変えた場合は再計測する。`/10` はカード／ページへ直接合成した条件である。

| 組合せ                                                          | ライト     | ダーク     | 必要比 |
| --------------------------------------------------------------- | ---------- | ---------- | ------ |
| `text-foreground` / `bg-background` / `bg-card`                 | 15.86      | 15.25      | 4.5    |
| `text-muted-foreground` / `bg-background` / `bg-card`           | 5.58       | 7.42       | 4.5    |
| `text-foreground` / `bg-muted` （hover 行）                     | 14.65      | 13.01      | 4.5    |
| `bg-primary` + `text-primary-foreground` (CTA)                  | 8.01       | 5.44       | 4.5    |
| `text-primary-strong` / `bg-card`                               | 8.37       | 7.94       | 4.5    |
| `text-primary-strong` / `bg-background`                         | 7.95       | 8.73       | 4.5    |
| `bg-primary/10` + `text-primary-strong`                         | 7.13       | 7.30       | 4.5    |
| `bg-primary-strong` 図形 / `bg-card`                            | 8.37       | 7.94       | 3      |
| `bg-primary-strong` 塗り / `bg-muted` トラック                  | 7.34       | 6.78       | 3      |
| `text-success-strong` / `bg-card`                               | 8.13       | 8.99       | 4.5    |
| `bg-success/10` + `text-success-strong`                         | 7.31       | 7.54       | 4.5    |
| `bg-success` + `text-success-foreground`                        | 4.83       | 8.62       | 4.5    |
| `bg-success/90` hover + `text-success-foreground`               | 5.56       | 7.24       | 4.5    |
| `bg-success` 図形 / `bg-card`                                   | 3.46       | 7.85       | 3      |
| `border-success-strong` 境界 / `bg-muted` トラック              | 7.13       | 7.67       | 3      |
| `bg-success/10` hover / `bg-muted` + `text-foreground`          | 13.28      | 10.81      | 4.5    |
| `text-warning-strong` / `bg-card`                               | 7.63       | 9.67       | 4.5    |
| `bg-warning/10` + `text-warning-strong`                         | 6.86       | 8.00       | 4.5    |
| `bg-warning` + `text-warning-foreground`                        | 4.83       | 9.91       | 4.5    |
| `bg-warning/90` hover + `text-warning-foreground`               | 5.54       | 8.26       | 4.5    |
| `bg-warning` 図形 / `bg-card`                                   | 3.46       | 9.01       | 3      |
| `text-destructive-strong` / `bg-card`                           | 8.71       | 7.00       | 4.5    |
| `text-destructive-strong` / `bg-background`                     | 8.27       | 7.69       | 4.5    |
| `bg-destructive/10` + `text-destructive-strong`                 | 7.46       | 6.19       | 4.5    |
| `bg-destructive` + `text-destructive-foreground`                | 5.75       | 6.05       | 4.5    |
| `bg-destructive` 図形 / `bg-card`                               | 6.00       | 5.50       | 3      |
| `bg-destructive` マーカー / `bg-muted` トラック                 | 5.27       | 4.70       | 3      |
| `text-destructive` / `bg-card` (FormMessage)                    | 6.00       | 5.50       | 4.5    |
| `bg-chart-1/10` … `bg-chart-5/10` + `text-foreground`           | 14.24 以上 | 12.25 以上 | 4.5    |
| `text-foreground` / `bg-primary/10`                             | 14.24      | 14.01      | 4.5    |
| `text-primary-strong` / `bg-accent` （ghost hover）             | 7.27       | 6.34       | 4.5    |
| `border-primary` / `ring-primary` / `bg-card` / `bg-background` | 7.95       | 3.02       | 3      |
| `bg-destructive/90` hover 塗り + アイコン                       | 4.84       | 5.14       | 3      |
| `border-muted-foreground` ドロップ領域 境界 / `bg-card`         | 5.88       | 7.42       | 3      |

新しい組合せは oklch → sRGB（gamma と gamut clamp を含む）→相対輝度で計算し、半透明は gamma-encoded sRGB で合成する。黒／白 = 21.00、`#767676`／白 = 4.54 で輝度計算を確認し、既存の plain・tint・track の行で変換と合成も確認する。不一致は計算器と token の両方を調べ、既存表を副作用で書き換えない。

- 二重 tint は別の組合せ。`bg-primary/10` の親と子に primary-strong を重ねるとライトで 6.12（配色 A）。親の hover 中は `group-hover:text-foreground` 等で切り替え、入れ子を計測する。
- 背景が不定の画像上の半透明 veil は最悪条件で測る。必要なら `bg-card` の不透明面にする。
- 1px 境界と、同じ意味の文字に隣接する凡例の点は装飾例外。位置・長さが情報の図形には自動適用しない。
- **シフト timeline**: `bg-muted` 上の success / warning バーは配色 A でライト 3.03 / 3.04 となり 3:1 を満たす。以前（2.92 / 2.90）の画面限定例外は不要になった。非公開の確定シフトの hollow 境界は `border-success-strong` を使い、この例外に含めない。
- timeline 内の操作ボタンはブラウザ既定 outline を保持する。背景と overflow によって検証済み ring の配置が成立しないための限定例外であり、他画面へ広げない。

### 公開サイト

default / modern / classic は同一の `--storefront-*` 契約を各 theme.css に定義する。共有 `_sections/` は `var()` と `color-mix()` だけで色を読む。新しい token は全テンプレートへ同時追加し、テンプレート別の section 複製や inline hex を使わない。値一覧は CSS を正本とし、本書に複製しない。

## 文字・アイコン・余白

- 管理画面は system sans と日本語 fallback。本文／ラベル 14px、主要数値 30px bold、ページ見出しは全 breakpoint で `text-2xl`。default 店舗サイトの見出しは Noto Serif JP 系と広い字間を使う。
- アイコンは `lucide-react` の `Icon` 接尾辞の export、既定 strokeWidth = 2。メニューの ICON_MAP が許可名を定める。
- console layout が `p-8 max-w-7xl mx-auto` を供給する。ページは padding・幅制限・ナビゲーションを重ねない。
- カード内周 24px、カード間／フィールド間 24px、ラベルと入力 8px、toolbar 12px、行内操作 4px。sidebar 256px（文脈レール 56px + パネル 200px。レールが無ければパネルが 256px 全幅）、header 64px、カード `rounded-xl shadow-sm`。
- 表は `TableCard` と `Table` を使う。左右端セル 24px、内部セル横 8px・縦 12px、header `h-11`。カードの内周とセル密度を別々に保つ。暦セルは `p-3`、行カードは `p-4`。
- TableCard の子孫 selector はセルの単純 class より強い。密度変更はそこで行い、最初／最後の checkbox セルでは primitive の余白規則との競合を確認する。1024px で表の scrollWidth を確認してから横余白を広げる。
- 管理 console の内容列は `min-w-[44rem]`。狭い viewport は横スクロールで操作可能性を保つ。新しい toolbar は最大長の店名・人名で intrinsic width を測り、label の幅上限を決めた後に床幅を確認する。
- 公開サイトの余白は既存 sections の `max-w-7xl`、`px-5 lg:px-10` に揃える。

## コンポーネント

既存 primitive を `@/shared/ui` から使う。vendored への画面別変更は consumer の className で表す。

| 用途             | 規則                                                                                                                                                                                                  |
| ---------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 主／副／破壊操作 | Button の default / outline / destructive。遷移は `render={<Link href="…" />}`、ボタン本文は Button の children                                                                                       |
| 入力             | Input / Textarea / Select / Checkbox / Switch / RadioGroup / Label                                                                                                                                    |
| モーダル         | [下記の基準](#ダイアログか画面か)で先に判定する。中央の Dialog を DialogFrame で描く（下記）。横スライド Drawer は追加しない。本人ポータル（キャスト）の下部シートだけは例外（[本人ポータル](#部品)） |
| 破壊確認         | ConfirmDialog。window.confirm や画面内での AlertDialog 再構築をしない                                                                                                                                 |
| 検索選択         | 共有 Combobox を公開入口から使う。サーバ検索は `filter={null}`。候補取得・検索語・選択の業務制御はページ slice に置く                                                                                 |
| 表／タブ         | TableCard + Table / Tabs                                                                                                                                                                              |
| 状態             | outline Badge と tint、`border-transparent`                                                                                                                                                           |
| 読み込み         | 形が既知なら Skeleton、未知または小領域は「読み込み中...」。手製 animate-pulse を作らない                                                                                                             |
| 進捗             | `bg-muted h-2 rounded-full` と `bg-primary-strong`                                                                                                                                                    |

- DialogFrame は題名帯（DialogTitle・DialogDescription）と操作帯（DialogFooter）を固定し、本文だけをスクロールする。主操作は操作帯の右端、副操作はその左。本文の部品が自前の form と操作を持つときは DialogFrameActions で操作帯へ置き、submit は `form={id}` で結び付ける。
- 幅は段の名前で選ぶ: `sm` 28rem（確認・1 列の小フォーム）/ `md` 42rem（キー値の詳細・2 列のフォーム）/ `lg` 56rem（本文が多列の表）。ほかの幅を足さない。
- 最大高さは `calc(100dvh - 2rem)`。スマホでアドレスバーが出ている間も操作帯が画面内に残るよう `vh` ではなく `dvh`。角丸は primitive の `rounded-lg` のまま。
- CardTitle が章見出しなら `role="heading" aria-level={N}` を渡す。h1 直下は 2、入れ子は 3 とし、飛ばさない。単なるラベルには付けない。
- destructive の DropdownMenuItem は consumer で `text-destructive-strong focus:bg-destructive/10 focus:text-destructive-strong`。vendored の destructive variant は tint 上の文字対比が不足する。
- 裸の操作要素は `focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary`。この ring はカード／ページ面でのみ認証済み。overflow で切れる場合は ring-inset を検討し、背景も再検証する。
- sidebar は左端の文脈レール（56px）と、その文脈のメニューだけを出すパネル（200px）からなり、全画面幅で表示する。レールは「グループ」（icon のみ）、区切り線、授権店舗の略称ボタン（40px 角、`aria-label` / `title` は店名）を縦に並べ、はみ出せばレールだけが縦スクロールする。「グループ」はグループ級の項目を見られる利用者にだけ、店舗ボタンは店舗級の項目の有無によらず授権店舗すべてに出す。文脈が 1 つしか無い利用者にはレールを出さない。店舗級の画面にいる間は選択中の文脈を現在店舗に合わせる。レールの選択は `bg-primary/10 text-primary-strong ring-2 ring-primary`、非選択 hover は `bg-muted text-foreground`。パネルは見出しに文脈名（長い名前は省略表示）、最上段にダッシュボード、続けて項目のある業務領域の見出しだけを出す。パネルの項目は高さ 40px・icon 20px・文字 14px。選択は primary tint と strong の縁、非選択 hover は `bg-muted text-foreground`。グループ見出しは折り畳まない。ヘッダーに店舗切替は置かない。
- 統計カードは注釈・数値・増減の順。カテゴリ chip は chart tint と foreground。ランキングは 32px の primary tint の順位と名前、右端に金額／件数。
- 選択 preview は label 内の sr-only radio、選択時 `border-primary ring-2 ring-primary bg-primary/10`。説明は未選択時だけ muted、hover／選択時は foreground。focus-visible を label に伝播する。

### ダイアログか画面か

作業をダイアログに置く前に、次の基準で判定する。いずれかに当たる作業は独立画面にする。

- (a) 内容が伸びる: 増減する行、高さ上限の無い選択一覧、入れ子の一覧、大きな表。
- (b) 2 件を見比べる、またはダイアログの外の情報を見ないと決められない。
- (c) 1 つの枠で複数の段階・複数のモードを切り替える。同じフォームを初期値だけ変えて使う登録・変更は「複数モード」に数えない。
- (d) その上にさらにダイアログを重ねる必要がある。ConfirmDialog と一度きりの値の表示は数えない。
- (e) 日常的に繰り返す業務操作。

ダイアログに残すのは、目的が 1 つ・1 段階・フォームが固定で枠の中の情報だけで完結する短い操作、破壊確認、一度きりの値の表示に限る。「URL で共有したいか」は基準に入れない。画面にした結果であって、画面にする理由ではないため。

一度きりの値（伝票QRコードの生値、一時パスワードなど、後から取り直せない値）は閉じないダイアログで出す。

- 作業画面で送信が成功したらその画面の上に、一覧の行の操作で発行したら一覧の上に出す。作業画面の上に出した値は、閉じたら一覧へ遷移する。
- Esc・背景押下では閉じず、閉じるボタン（×）も出さない（`onOpenChange` の閉じる要求を無視し `showCloseButton={false}`）。操作帯のボタンでだけ閉じる。
- 値はメモリにだけ持ち、画面遷移・再読込で再表示しない。
- 離脱を防ぐのはモーダルであること自体（サイドバーへ触れられない）に任せる。App Router には画面内遷移を止める仕組みが無く、サイドバーのリンクを自前で横取りするのは脆いため。

### キー値と変更前後

ダイアログ内の詳細と履歴は `@/shared/ui` の 2 部品で描き、`dl` や前後の表を画面ごとに手組みしない。ページ上の `dl`（会員詳細・ダッシュボード等）はこの規則の対象外。

- キー値は KeyValueList + KeyValueRow の明細書型。行ごとに区切り線、ラベルは左に muted、値は右寄せ。金額は `money` で `tabular-nums`。理由・本文・備考など複数行になりうる値は `long` でラベルの下に左寄せで置く。値には Badge 等の要素も置ける。
- 変更前後は ChangeTable。「項目・変更前・変更後」の 3 列に全項目を並べ、変わった行を `bg-muted` と太字で示す。値は表示文字列で渡し、その一致で変更を判定する。全列左寄せ・`tabular-nums`、存在しない側は「—」。変わらない行の変更前は muted、変わった行は `bg-muted` に muted を重ねない規則により変更前も foreground とする。
- 変更の一覧は ChangeLog + ChangeEntry。日時・操作者・理由等の見出し（h3）の下に 1 件ずつ ChangeTable を置く。
- ダイアログ本文の ChangeTable は TableCard で包まない。カードの内周がダイアログの余白と二重になるため。

### 一覧

ListPage（`@/widgets/list-page`）が見出し・検索 form・表枠・読み込み／失敗／空・ページングを所有する。props の正本はコンポーネントの型。ページは表内容と固有操作を渡す。

- useListPage の PageResult は rows / page / pageCount / total（page は 0 始まり）。裸配列では pagination props を省略する。
- 検索欄は入力中の値と適用済み条件を分ける。submit で ref へ確定して search() を呼び、fetcher は ref を読む。Enter は shell の form に任せる。
- 離れて戻る作業キューの一覧（受注一覧）は適用済み条件を URL の query に持つ。submit は history.pushState で履歴に積み（次の条件は描画済みの値ではなく今の URL から組む）、search() は URL の変化から呼ぶ。既定値は query に書かず、不正な値は既定へ倒す。
- offset sort は一意の副キーまで指定する（例 `displayOrder,id,asc`）。
- ダイアログは ListPage の外に置く。children は読み込み中や空一覧で unmount される。
- 通常の閉じる操作は `open=false` のみとし、退出中も本文を保つ。選択対象と開閉状態を分け、保存成功の通知・再取得を退出完了まで遅らせない。親によるアンマウントや一度きりの資格情報の破棄が必要な場合だけ `onOpenChangeComplete` を使う。
- モーダルを開く操作は button、画面遷移は link のまま保つ。見た目だけで役割を交換しない。

## 本人ポータル

キャスト（`/cast/**`）と会員（`/member/**`）の画面は共通の外枠 PortalShell（`@/widgets/portal-shell`）に載る。スマートフォンでの利用を前提とし、管理画面の規則のうち以下は適用しない。

- **外枠**: 全画面を中央の `max-w-md` の列に置き、列の外は `bg-muted`。上部の題名帯と下部タブも列の幅に収める。ページは幅制限・外周の余白・戻る導線を重ねない（外枠の `main` が `p-4` を供給する）。
- **画面の表**: 画面名・戻り先・所属タブは各ポータルの layout が持つ表で引く。ルートを足したら表にも足す（`src/__tests__/portal-screens.test.ts` が固定する）。
- **題名帯**: 「戻る（下位画面だけ）＋画面名」だけで、画面名が 1 画面 1 個の h1。店舗名は帯に出さず、各カード・各行に出す。下位画面は戻る導線を出し、下部タブを出さない。
- **下部タブ**: アイコン 24px＋文字、4 個。選択は `aria-current="page"` と primary-strong、非選択 hover は背景と文字を同時に変える。件数バッジは外枠が受け、0 件では出さない（タブの accessible name も変えない）。キャスト: ホーム / スケジュール / 報酬 / アカウント。会員: ホーム / 予約 / ポイント / アカウント。
- **タブの中の切替**: 一つのタブに複数の画面がある場合（スケジュール = 出勤予定 / 希望提出、報酬 = 明細 / 月次）は題名帯の下の segmented で切り替え、どちらの URL でもタブは選択状態にする。
- **safe-area**: portal の layout が `viewport-fit=cover` を指定し、題名帯は `env(safe-area-inset-top)`、下部タブと本文の下端は `env(safe-area-inset-bottom)` を足して home indicator に重ねない。
- **管理画面から外れる点**: 見出しは `text-2xl` ではなく題名帯の `text-lg`。ListPage・TableCard・Table・`min-w-[44rem]` の横スクロールを使わない（作り直しの済んでいない画面に残る ListPage は自前の h1 を持ち、各画面の作り直しで外す）。本文の大きさは利用者層の段に従う。
- **色**: 管理画面と同じ意味 token だけを使い、ポータル専用の色を足さない。キャストの柔らかさは角丸・余白・文字の太さ・カードの重なりで出す。

### 利用者層の段

| 段       | 本文 | 押せる領域 | 方針                                                                       |
| -------- | ---- | ---------- | -------------------------------------------------------------------------- |
| 会員     | 18px | 48px 以上  | 1 画面の情報を少なく。スワイプ・下部シートを使わず、見える操作で縦に並べる |
| キャスト | 16px | 44px 以上  | 下部シート（Drawer）と segmented を使ってよい                              |

段は外枠の `tier` で渡し、外枠が本文の大きさと題名帯・タブの押せる領域を決める。ページの操作要素も同じ段の押せる領域に揃える。

### 部品

本人ポータルの画面は `@/shared/ui` の次の部品で組み、管理画面の ListPage・TableCard・Table を使わない。

| 用途           | 部品                           | 規則                                                                                                                                                                                                               |
| -------------- | ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 一覧           | ListRows + ListRow             | 区切り線のあるカード 1 枚。行は題・補足（muted、hover の面では foreground）・右端の値。`href` を渡すと行全体が詳細へのリンク。高さ 48px 以上                                                                       |
| キー値         | KeyValueList + KeyValueRow     | 管理画面と同じ明細書型。`className` で本文の大きさ（`text-base` 等）に合わせる                                                                                                                                     |
| 金額           | Money                          | 書式表の金額・変更の書式と `tabular-nums`。合計など大きく出す額は className で大きさだけ変える                                                                                                                     |
| 状態           | StatusBadge                    | 下記の状態の語だけを受け、語から見た目が決まる                                                                                                                                                                     |
| 読み込み中     | ListRowsSkeleton ／ Skeleton   | 中身の形の骨組み。一覧は ListRowsSkeleton、それ以外は区画の形に Skeleton を並べる                                                                                                                                  |
| 空             | EmptyState                     | 何が無いか＋次の操作（例:「まだ予約はありません」＋「新しく予約」）                                                                                                                                                |
| 失敗           | RegionError                    | その区画に `onRetry` 付きで出す（[領域内エラー](#領域内エラー)）                                                                                                                                                   |
| 月の選択       | MonthPicker                    | 「‹ 2026年11月 ›」と、中央を押すと今月から遡る 12 か月の一覧。値は `YYYY-MM`。手入力させない。今月より先と 12 か月より前へは進めない                                                                               |
| 絞り込みの切替 | Segmented                      | 常にどれか一つが選ばれた button の組（ToggleGroup）。URL が変わる画面の切替は外枠の区分で行い、これを使わない                                                                                                      |
| 店舗の切替     | StoreChips                     | 複数店舗に在籍するときだけ横スクロールのチップ（ToggleGroup）。1 店舗ならチップを出さず、その店舗を自動で選ぶ                                                                                                      |
| 下部シート     | BottomSheet + BottomSheetClose | キャストのみ。Base UI の Drawer。題名帯と操作帯（全幅・主操作が先頭）を固定し本文だけをスクロール。背景幕は Dialog の overlay と同じ `bg-black/50`（自作ファイルでの生の色名の唯一の例外）。会員の画面では使わない |

### 書式

`@/shared/lib` の純粋関数で書式化し、ISO 形式の値・内部 ID を利用者に見せない。関数は現在時刻を読まないので、今日は `tokyoDate(new Date())` で求めて渡す。

| 値             | 関数                | 例                                                    |
| -------------- | ------------------- | ----------------------------------------------------- |
| 日付           | `formatDate`        | `11/5（水）`、今年でなければ `2025/11/5（水）`        |
| 時間帯         | `formatTimeRange`   | `19:00〜21:00`、終了が開始以前なら `19:00〜翌2:00`    |
| 日時           | `formatDateTime`    | `11/5（水）19:00`（時差付きの瞬間は東京の時刻に直す） |
| サービスの長さ | `formatMinutes`     | `90分`（コース等）                                    |
| 経過時間       | `formatDuration`    | `1時間30分`、`2時間`、`45分`（出勤時間等）            |
| 金額           | `formatMoney`       | `¥12,000`、負は `−¥1,000`（U+2212）                   |
| 金額の変更     | `formatMoneyChange` | `¥8,000 → ¥10,000`                                    |
| 版             | `formatRevision`    | `第2版`（必要な所だけ）                               |

時は 0 埋めしない（`9:30`）。

### 状態の語彙

両ポータル共通。API の状態からこの語への写しは各画面が持ち、同じ意味の状態に別の語を使わない（変更申請の「謝絶」も「却下」）。

| 語                                          | 見た目                                          |
| ------------------------------------------- | ----------------------------------------------- |
| 申請中 / 受諾待ち / 承諾待ち / 再承諾が必要 | warning（`bg-warning/10 text-warning-strong`）  |
| 承認 / 受諾済み / 承諾済み                  | success（`bg-success/10 text-success-strong`）  |
| 却下                                        | destructive（店舗による否定）                   |
| 取り下げ / 拒否済み                         | 中立 `bg-muted text-foreground`（本人が閉じた） |

### ホームの告知・お知らせ区画

- 会員ホームは上から 会員コード（QR）→ ポイント残高 → 次の予約 → 自社の告知。告知は会員コードより上に置かない（店頭で提示する用途を妨げない）。
- キャストホームは上から 今日の出勤 → やること → 店舗からのお知らせ → 今月の累計報酬。
- 告知・お知らせはカード 1 枚の区画で、見出し（h2）と新しい順の行。行は題と日付、本文は開いて読む。色は `bg-card` と既存の状態ラベルだけを使う。情報源ができるまで区画ごと描画しない（空の区画を置かない）。

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

vendored は combobox / alert-dialog / button / card / dialog / select / form / table / badge / popover / skeleton / tabs / dropdown-menu / checkbox / switch / radio-group / input / label / textarea。整形以外は生成状態を保つ。

image-upload / auth-layout / theme-provider / confirm-dialog / dialog-frame / table-card / toast / region-error / pager / key-value / change-table / status-badge / segmented / store-chips / month-picker / bottom-sheet / list-row / money / empty-state は自作で、用途に応じて編集する。分類の正本はこの一覧。data-slot や Base UI import だけでは区別できず、未知のファイルは git log で確認する。

- 管理画面の raw palette 検索は page だけでなく参照する entities と自作 shared/ui も対象にする。例外は storefront、AuthLayout 内、vendored のみ。生 hex も確認する。
- setupTests.ts が ResizeObserver、scrollIntoView、PointerEvent を供給する。追加 shim は実際の失敗を確認してから同ファイルにまとめる。
- popup は fireEvent.click(trigger) で開く。Select / Combobox の項目決定は pointerDown(item) → click(item)。片方だけでは選択されないことがある。
- FormControl は data-slot を上書きするため role で選ぶ。Checkbox / RadioGroupItem は button ではなく、disabled の見た目は data-disabled。Button / Select trigger / Tabs は disabled を使う。
- UI 変更時は両テーマ、長い文字列、狭い幅、overlay 上の通知、キーボード移動を実ブラウザで確認する。jsdom は色・重なり・実寸法の検証にならない。
