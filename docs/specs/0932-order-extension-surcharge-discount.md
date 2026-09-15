# 延長・加算・固定割引 — API 契約

状態: **承認・実装・検証済み（2026-09-15）。**

対象: [#932](https://github.com/kanghouchao/Kizuna/issues/932)。業務規則は #384、既存契約は [#931 の契約](0931-order-course-application.md)。以下に記載しない既存字段の型・必須性・応答外殻は維持する。

## 共通規約

- JSON は snake_case。ID は string、金額・分数は 32 bit 整数。加算途中もオーバーフローを検出し、範囲外は 400。日時は ISO 8601。
- M は ORDER_MANAGE、C は ORDER_MANAGE と ORDER_CORRECT の両方。全端点に認証、店舗コンソール資格、X-Role: store、X-Store-ID、店舗作用域の検証を要する。
- 共通失敗: 400（入力・状態・重複・過大割引）、401（未認証）、403（資格・権限不足）、404（対象なし・別店舗）、409（取得時の受注版または確認済み試算との競合）。エラーは既存の `{error: string, details?: Record<string,string>}`。
- 試算は業務レコードを書かない。保存は既存 confirmation_token を必須とし、試算要求では省略する。条件改定は保存せず 409、再試算で旧表示と新表示の名称・版本・費用・報酬・総額を比較し、利用者の再確認を要する。

## HTTP 一覧

| メソッド・パス | 権限 | 要求 | 成功応答 |
| --- | --- | --- | --- |
| GET `/store/orders/surcharge-candidates`（新設） | M | search?: string、page?: integer=0、size?: integer=20 | 200 Page<SurchargeCandidate> |
| GET `/store/orders/{id}/surcharge-revisions`（新設） | C | search?: string、cursor?: string、size?: integer=20 | 200 CursorPage<SurchargeRevision> |
| POST `/store/orders/preview` | M | CreateInput | 200 Preview |
| POST `/store/orders` | M | CreateInput + confirmation_token | 201 OrderResponse（id を含む） |
| POST `/store/order-applications/{id}/confirmation-preview` | M | ConfirmationInput | 200 Preview |
| POST `/store/order-applications/{id}/confirmation` | M | ConfirmationInput + confirmation_token | 201 OrderResponse |
| POST `/store/orders/{id}/preview` | M | UpdateInput | 200 Preview |
| PUT `/store/orders/{id}` | M | UpdateInput + confirmation_token | 200 既存 OrderWorkQueueResponse |
| POST `/store/orders/{id}/completion-preview` | M | CompletionInput | 200 Preview |
| POST `/store/orders/{id}/completion` | M | CompletionInput + confirmation_token | 200 既存 OrderCompletionResponse |
| POST `/store/orders/{id}/correction-preview` | C | CorrectionInput | 200 Preview |
| POST `/store/orders/{id}/corrections` | C | CorrectionInput + confirmation_token | 201 CorrectionResult |
| GET `/store/orders/{id}` | M | パス id のみ | 200 OrderResponse |
| GET `/store/orders` | M | 既存 customer_id・Pageable | 200 Page<OrderSummaryResponse>。extension_minutes を明細合計から返す |

新設候補は page >= 0、size 1..100、範囲外は 400。名称部分一致、name ASC・service_id ASC。現在有効な SURCHARGE のみ。
歴史は size 1..100、occurred_at DESC・revision_id DESC の一意順と同じ cursor 比較を使う。対象受注と同店舗の全加算の CREATED/UPDATED 版本を返し、削除済み設定を含む。DELETED 版本は条件選択肢に含めない。CursorPage は `{content: T[], next_cursor?: string}`。不正 cursor は 400、末尾は next_cursor 省略。
他の表中操作にページングはない。surcharge-candidates は ID として予約する。

## 入力の変更

CreateInput / UpdateInput / CorrectionInput のトップレベル extension_minutes は撤去する。延長行の和から導出し、旧字段の直送は 400。

fee_lines は編集可能行の全量。作成・申請確定は省略/null で追加行なし、通常編集は省略/null で保持、空配列で編集可能行を全除去。完了・訂正では非 null 必須。コースとシステム専有行はこの集合の外で保持・生成する。

各要素は以下の排他的な形とする。異なる形の字段併用・不明字段・null 要素は 400。

| 用途 | 要求形（? は省略可能） |
| --- | --- |
| 保存済み行の維持 | `{line_id: string}` |
| 延長の新規・置換 | `{kind: "EXTENSION", name: string, duration_minutes: integer > 0, amount: integer >= 0, remuneration: integer >= 0}` |
| 通常の加算追加・選び直し | `{kind: "SURCHARGE", service_id: string}` |
| 訂正の加算追加・選び直し | `{kind: "SURCHARGE", revision_id: string}` |
| 固定割引の新規・置換 | `{kind: "DISCOUNT", name: string, amount: integer > 0}` |
| 既存 OPTION / CREDIT_SURCHARGE | `{kind: "OPTION" または "CREDIT_SURCHARGE", name: string, amount: integer >= 0}` |

name は非空白・最大 255 文字。延長報酬は amount 以下、無料延長では両方 0。割引は報酬 0 をサーバで設定する。OPTION / CREDIT_SURCHARGE は現在の入力規則・経路を維持し、この票で固定報酬の入力を追加しない。

line_id は同じ受注の編集可能行だけを指定できる。作成・申請確定では指定不可。重複指定とシステム行指定は 400、存在しない・他受注の ID は 404。維持指定では名称・金額・分数・報酬・版本・採用根拠・採用日時をすべて保存時のまま保持する。新規行はサーバが ID を発行する。手入力行の修正は旧行を除き新しい行を送る。

通常の加算は現在条件、訂正は歴史版本の条件を採る。維持行を含め service_id の重複を拒否する。加算の name / amount / remuneration の直送は 400。同じ設定の選び直しは旧 line_id を除いて選択 ID を送る。同一加算設定の選び直しでは明細 ID を維持し、採用条件を更新する。

MANUAL_ADJUST は enum・入力・通常画面・訂正画面から撤去し直送を 400 にする。BASE_COURSE と POINT_REDEMPTION の直送も引き続き拒否する。ポイント台帳の手動調整は維持する。

expected_version、reason、course_id / course_revision_id、use_points、実績時刻等は #931 の契約を維持する。コース変更でも明細維持指定はそのまま採用し、割引を自動減額しない。

## 応答型

SurchargeCandidate は全字段必須: `{service_id: string, revision_id: string, revision_number: integer, name: string, price: integer, remuneration: integer}`。
SurchargeRevision はこれに必須の `{occurred_at: datetime, service_deleted: boolean}` を加える。

FeeLineResponse は既存 kind / name / amount / system_owned を維持し、次を返す。

- line_id?: string — 保存済み行に必須。未保存の試算行では省略。
- remuneration: integer >= 0 — 全行必須。コース・延長・加算の固定報酬、それ以外は 0。
- duration_minutes?: integer > 0 — コース・延長で必須、他の種別は省略。
- service_id?: string、revision_id?: string、revision_number?: integer、adoption_basis?: "CURRENT_SETTING" | "HISTORICAL_CORRECTION"、adopted_at?: datetime — コース・加算の保存済み行に必須。他の種別は省略。未保存試算では adopted_at のみ省略。

Preview は既存 confirmation_token / course / fee_lines / total_fee / points に、必須 total_duration_minutes: integer と total_remuneration: integer を追加する。確認値は安定した入力・採用条件・合計に結び、未保存行のランダム ID・現在日時は照合対象にしない。points の意味は #931 のまま。

OrderResponse も同じ合計二字段と拡張 FeeLineResponse を返す。extension_minutes は読取専用の延長分数合計。未確定でコースのない受注は total_duration_minutes と total_remuneration を 0 とする。

CorrectionResult は既存字段に必須 previous_total_remuneration / total_remuneration / previous_total_duration_minutes / total_duration_minutes（integer）と previous_fee_lines / fee_lines（FeeLineResponse[]）を追加する。訂正 ID と前後の費用・報酬・採用条件を対応させる。

総時間はコースと各延長分数の和。請求は明細の符号付き和、報酬は採用した固定報酬の和。割引・ポイントで報酬を減額しない。割引後請求が報酬以下でも合法だが、普通割引後・ポイント控除後の請求はともに負数不可。

## 保存と検証の方針

- 既存 t_order_fee_lines の ID を維持指定に使い、分数・報酬・設定版本・採用根拠・採用日時を baseline に追加する。設定と版本の整合・店舗一致は FK と集約で保証し、版本削除は NO ACTION。設定の論理削除で採用済み条件を消さない。
- 同一受注・加算設定の部分一意索引、延長分数と金額・報酬の CHECK、MANUAL_ADJUST 撤去後の種別 CHECK を設ける。具体的な列・FK・索引名と型は実装時に baseline へ一箇所で定義する。
- 訂正前快照に全明細の ID・版本・採用根拠・分数・報酬を含める。履歴への FK 追加で過去明細の置換・除去を阻害しない。受注・既存訂正履歴の削除方針は維持する。
- 合意済み境界で集約テスト、認証 HTTP と実 PostgreSQL の統合テスト、利用者操作の UI テスト、少数の日本語 Gherkin E2E を追加する。無料・複数延長、端数、重複、過大割引、設定改定競合、歴史採用、店舗切替・権限不足・表示漏洩を検証する。
- ADR 0018 / 0019 / 0027 と CONTEXT を実装に合わせる。最後に task lint / task test / task build / task e2e、code-review を行い、exit code と未実施事項を記録してコミットする。


## 最終検証

2026-09-15 に以下を実行し、各終了コードが 0 であることを確認した。

| 検証 | 結果 |
| --- | --- |
| `task lint` | 成功。最終のモーダル修正後も `task lint service=frontend` が成功 |
| `task test` | 前端 1,415 件、後端単体テスト・カバレッジ検査、DB 統合 702 件が成功 |
| `task test service=frontend` | 最終のモーダル修正後に 1,415 件が再度成功 |
| `task build` | 最終コードの backend / frontend Docker イメージ構築が成功 |
| `task e2e` の全手順 | 44 シナリオが成功。申請確定、完了、歴史訂正、延長・加算・割引の保存と再表示を含む |
| 前端 `tsc --noEmit` | 成功 |

同時実行する別 worktree との競合を避け、統合テストは `COMPOSE_PROJECT_NAME=kizuna-backend-test-c069-932` を明示した。E2E は一時コピーした同じ Taskfile の `STACK_ID` のみを `c069-932` に固定し、専用イメージ・専用スタックで全手順を実行した。検証後のテストスタックは破棄済み。未実施の必須検証はない。

Standards / Spec の並行レビューと修正後の再レビューを実施し、未修正の規約違反・契約不一致はない。歴史選択と保存時競合を表す二つの boolean を具名型へ置く非ブロッキング提案は検討し、独立した二つの意味が明確な現行設計を維持する。
