# 月次給与明細の PDF ダウンロード・印刷

状態: 承認済み（2026-10-01）。対象: #982。本人源氏名、同一快照の全件、PDF 閲覧印刷、資源超過時の完全失敗を含め承認。

基線: `e2fefec76a60fc7f455c81bff9e64124946b0af8`。店舗・本人・平台の既存月次照会と同じ一店舗・一人・原営業日の自然月を対象とする。PDF は生成日時点の参照結果であり、給与台帳、締め、承認、支払管理、保存版本、配信は作らない。

## HTTP 契約

すべて body のない認証付き GET。パスは backend のマッピングを示す。既存の API クライアントと gateway の prefix を変更しない。

| メソッド・パス | 必須 query | 認可 |
| --- | --- | --- |
| `GET /store/monthly-remunerations/pdf` | `person_id: int64`, `month: string` | `PERM_ORDER_MANAGE`、既存店舗コンテキストと `@StoreScoped` |
| `GET /platform/me/monthly-remunerations/pdf` | `store_id: int64`, `month: string` | `ROLE_CAST`、認証主体から本人を解決し、その本人の歴史在籍と店舗を照合 |
| `GET /platform/monthly-remunerations/pdf` | `store_id: int64`, `person_id: int64`, `month: string` | `PERM_ORDER_SET_MANAGE`、既存授権店舗集合と `@StoreSetScoped` |

すべての query は必須・非 null。ID は正数。month は `0001-01`〜`9999-12` の厳密な `YYYY-MM`。任意 query、body、page、size、sort、cursor、出力列指定、保存版本 ID は設けない。余分な page/size を送っても出力範囲を狭めない。

店舗側は既存と同じ `X-Role: store` と `X-Store-ID` を要求する。本人側は person_id / cast_id / enrollment_id を入力契約に含めず、追加されても本人解決に使用しない。停止・退店・同店再入店を含む歴史所有権を維持し、現在の店舗集合や選択中店舗に依存しない。平台側は ORDER_MANAGE や CAST_MANAGE で代用せず、授権集合が解決不能な場合も拒否する。

### 成功応答

- `200 OK`、body は PDF のバイナリ（非 null）。JSON envelope、URL、file ID は返さない。
- `Content-Type: application/pdf`。
- `Content-Disposition: attachment; filename="monthly-remuneration-YYYY-MM.pdf"`。人名・顧客情報・内部 ID をファイル名に含めない。
- `Cache-Control: no-store`。生成物を公開ファイルストレージへ置かず、共有 URL や期限付き取得トークンも発行しない。
- 参照可能な対象の空月も 200。対象情報、月合計 0 円、「対象月の完了受注はありません」を含む PDF を返す。
- ページングのない一文書で全対象受注を出力する。画面ページ数や既存 API の size 上限で切らない。

### 失敗応答

| コード | 条件 |
| --- | --- |
| 400 | 必須 query 欠落、型・正数・対象月の形式不正 |
| 401 | 未認証、失効・不正な認証情報 |
| 403 | 権限または本人種別不適合、店舗コンテキスト不許可、平台の授権集合外または集合解決不能 |
| 404 | 既存の閲覧境界内で店舗／本人が存在しない、店舗から不可視の本人、本人の Cast 未作成または歴史関係のない店舗 |
| 500 | 予期しない生成失敗。既存の固定エラーメッセージ |
| 503 | 同時生成枠の使用中、協調的期限・DB 読取期限・文字数・scratch・出力容量の超過、埋込フォントの欠字 |

失敗 body は既存の `application/json`、`{error: string, details?: Record<string,string>}`。error は必須、details は省略可。認証・型変換・method security の複合エラー優先順序は既存の設定に従う。途中まで生成した PDF を成功として返さず、内部例外・SQL・顧客情報をエラーへ出さない。409 等の新しい失敗体系は追加しない。

## 出力内容と時点の整合性

PDF の表示情報は以下に限定する。以下は HTTP JSON DTO ではなく、描画用の許可項目と型である。すべて必須・非 null、金額は整数円。

| 粒度 | 項目・型 |
| --- | --- |
| 文書 | 店舗名 string、当該店舗の源氏名 string、対象月 string、生成日時 offset datetime、月次報酬合計 int64 |
| 受注 | 原営業日 date、受注番号 string、サービス概要 string、有効な発生済み報酬 int32、無効化済み boolean |

店舗名は現在名、源氏名は既存月次照会と同じ当該店舗の最新在籍プロフィール名を使う。サービス概要は既存の受注項目快照を用いる。生成日時は共通 Clock の業務タイムゾーンと UTC offset を明記する。本文に「生成日時点の報酬集計」と「支払済み額ではありません」を表示する。顧客情報・他人の報酬・内部操作者・内部メモ・訂正理由は描画データに含めない。

共通 `MonthlyRemunerationQuery` の条件・報酬計算・サービス概要を再利用する。原営業月の COMPLETED のみ、同店の同一 Cast の全在籍をまとめる。無効化行は原条件を残して有効報酬 0 円と表示し、未完了・取消は対象外。明細順は `business_date DESC, order_id DESC`。

認可・名前・全月合計・全明細とサービス概要の読み取りを、公開 service の readOnly REPEATABLE_READ の同一 DB snapshot で完了する。内部の分割取得も同じ transaction 内で行い、自己呼び出しで transaction／filter の proxy を迂回しない。描画はその取得済みデータだけを使い、途中で再照会しない。合計と全行の有効報酬和を照合し、矛盾した文書を返さない。並行する訂正は全体が訂正前または全体が訂正後となり、次の生成要求では原月の最新額を読む。DB schema と既存 JSON API は変更しない。

## UI と版面

三つの月次画面の適用済み検索結果に「PDF ダウンロード」「PDFを開いて印刷」を置く。編集中の未適用条件や表示中の一ページを出力元にせず、適用済みの店舗・本人・月を使って都度生成する。空月でも利用できる。条件変更中の古い応答を別対象の成果物として表示しない。

既存 axios クライアントで認証付き blob を取得する。ダウンロードはその blob を保存し、印刷は同じ PDF をブラウザの PDF 表示から印刷する導線とする。トークンを URL に載せない。取得失敗は操作領域で再試行でき、未完成の blob を開かない。PDF 表示を別タブに開けない場合も取得済み PDF のダウンロード導線を残す。object URL は利用終了後に解放する。

日本語フォントを埋め込む Java の PDF 描画処理を採用する。A4 縦、余白、折り返し、表見出し反復、ページ番号を設ける。長い店名・源氏名・受注番号・サービス概要を省略せず、ページ高さを超える内容も分割して読むことができる。ブラウザ画面の HTML をそのまま印刷しない。使用ライブラリ・フォントの版とライセンスは実装時に一次資料で確認する。

## 検証計画

- 日本語 BDD: 三画面のダウンロード／印刷導線、空月、画面二ページ以上の全件出力、名称・対象月・日時・受注番号・サービス概要・合計、退店・再入店、訂正／無効化後の原月再生成。
- 直接 HTTP: 未認証、異なる種別・権限、店舗外の本人、平台集合外、本人の ID 偽装・無関係店舗、ROLE_CAST の退店後参照、顧客・他人・内部操作者情報の不在。
- 実 DB 上の並行訂正: 読み取り中に別 transaction の訂正を commit させ、実際に返る PDF の全行と合計が同一時点であることを確認する。タイミングの偶然や固定 sleep、mock の金額だけで判定しない。
- PDF から全件・一意受注番号・合計を抽出して検証し、長い日本語と多ページの実 PDF を画像へ描画して文字欠け・重なり・明細欠落・改ページを目視する。初頁・中間頁・最終頁と一行が頁を跨ぐ場合を含む。
- 実ブラウザで明暗・狭幅・長い名称・キーボード・取得失敗・条件変更を確認する。
- 最終検証: `task lint`、`DOCKER_TAG=issue982 task test`、`DOCKER_TAG=issue982 task build`、`task e2e`、ローカルコードレビュー。成功は終了コード 0 のみで判定する。
- 独立 worktree basename は `kizuna-issue982`。E2E の compose project は `kizuna-e2e-kizuna-issue982`、image tag は `e2e-kizuna-issue982`。既定 E2E は host port を公開しない。手動ブラウザ確認に公開 port が必要なら空きを確認し、この project だけに追加する。既存スタック・タグ・DB volume・旧 worktree を変更しない。

実装・検証の結果は PR に記録する。PR は実装と検証後に日本語テンプレートで準備し、マージ・デプロイは行わない。

## 承認時の補足

源氏名は当該店舗の最新在籍の CastProfile.name のみ。Cast.realName やアカウント名へフォールバックせず、既存の本人 JSON DTO は変更しない。

PDFBox 3.0.8（Apache License 2.0）と公式 IPAex Gothic 004.01（IPA Font License v1.0）を使用し、原フォントとライセンスを同梱する。欠字は脱落・代替記号で成功させず生成失敗とする。

各インスタンスで同時生成 1 件、待ち行列なし。全体の協調的期限は 25 秒、DB transaction は 10 秒、描画入力の文字数は 400 万 code points、PDF は 16 MiB。scratch はメモリ 8 MiB、ディスクを含む総量 64 MiB。設定は AppProperties で管理する。超過・繁忙は既存 ServiceUnavailableException による 503 と JSON error を返す。ライブラリ呼出中の強制中断やヒープの厳密な上限は保証せず、処理境界と書込時に検査して、遅れて完成しても成功応答にしない。内部取得は 200 件ずつ同一 transaction 内で最後まで進め、2,000 件の API size 上限を全件数上限にしない。

完成前に HTTP body を送信しない。PDFBox の scratch と出力は要求専用の非公開一時ディレクトリへ置き、成否を問わず削除する。保存版本・取得 URL は作らない。

取得には Accept: application/pdf, application/json を指定する。JSON のエラー Blob、代理の非 JSON 502/504、通信失敗を失敗として扱う。取消と対象変更は古い結果を破棄し、再試行は新しく生成する。印刷は「PDFを開いて印刷」からブラウザの PDF 閲覧機能を使う。

BDD の PDF 解析に PDF.js 6.3.289（Apache-2.0）、専用 DB の大量 fixture と待機同期に pg 8.23.1 と @types/pg 8.23.1（MIT）を使用する。DB 接続は E2E runner の database ホストだけに限定する。欠字は 503 とし、生成失敗を明示する。
