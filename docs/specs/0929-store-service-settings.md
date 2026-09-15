# 店舗別サービス設定 API 契約案

状態: 2026-09-14 承認済み。#929 の実装契約。

## 共通規則

全端点は認証と `PERM_SERVICE_MANAGE`、授権された店舗コンテキストを必須とする。
`X-Role: store`、`X-Store-ID: <店舗 ID>` を使用し、本文に店舗 ID は受け取らない。
権限は STORE_MANAGER に既定授与し、自作店舗ロールへ委譲可能とする。ORDER_MANAGE 単独と通常 HQ には授与しない。
ID は文字列、日時はタイムゾーン付き ISO 8601、JSON キーは snake_case。

## 端点

| メソッド・パス | 要求 | 成功応答 |
| --- | --- | --- |
| GET /store/services | 下記一覧クエリ | 200、Page<ServiceSummary> |
| POST /store/services | ServiceCreateRequest | 201、`{id: string}` |
| GET /store/services/{id} | 本文なし | 200、ServiceResponse |
| PUT /store/services/{id} | ServiceUpdateRequest | 200、ServiceResponse |
| DELETE /store/services/{id} | 必須クエリ `expected_version: integer`、本文なし | 204、本文なし |
| GET /store/services/{id}/revisions | 任意 `cursor: string`、`size: integer = 20` | 200、CursorPage<ServiceRevisionResponse> |

GET 詳細と履歴は削除済みも読める。PUT と DELETE は削除済みを 400 とし、復活は提供しない。
全端点の主要失敗は 401（未認証）、403（権限・店舗資格不足）。単件端点は不在・他店舗 ID を 404。
400 は入力不備・不正カーソル・削除済みへの変更、409 は更新・削除時の版衝突。
エラーは既存の `{error: string, details?: Record<string,string>}` を使う。

## 入出力の型

ServiceCreateRequest の全フィールド:

| フィールド | 型・省略可否 | 制約 |
| --- | --- | --- |
| kind | 必須 enum | COURSE / SPECIAL_SERVICE / SURCHARGE |
| name | 必須 string | 前後空白除去後 1〜255 文字。同名を一意性の根拠にしない |
| duration_minutes | COURSE で必須 integer、それ以外は省略または null | 1〜2147483647 |
| charge_type | SPECIAL_SERVICE で必須 enum、それ以外は省略または null | PAID / FREE |
| price | 必須 integer | 0〜2147483647 円。COURSE、SURCHARGE、有料特殊サービスは正、無料は零 |
| remuneration | 必須 integer | 0〜price 円。無料は零 |

ServiceUpdateRequest は kind を除く上記全フィールドと必須 `expected_version: integer >= 1`。
全量置換とし、kind は作成後変更不可。該当しない条件字段は省略または null のみ。

ServiceSummary は `id: string`、`kind`、`name`、`price`、`remuneration`、
`version: integer`、`deleted: boolean` が必須。`duration_minutes` と `charge_type` は該当種別のみ返し、それ以外では省略する。
ServiceResponse は同じ現在値に必須 `created_at`、`updated_at` を加える。履歴と操作者は一覧 DTO に含めない。

ServiceSnapshot は ServiceSummary と同じフィールド。
ServiceRevisionResponse は以下を返す。

- 必須 `id: string`（歴史版本 ID）、`version: integer`（項目内で一意な版本番号）。
- 必須 `operation: CREATED | UPDATED | DELETED`、`actor_id: string`、`occurred_at: datetime`。
- `before: ServiceSnapshot` は作成時のみ省略。それ以外は必須。
- 必須 `after: ServiceSnapshot`。削除時も最終条件を保持し、deleted が true となる。

## ページング・競合

一覧は任意 `kind`、`deleted: boolean = false`、`page: integer = 0`、`size: integer = 20` を受ける。
page は非負、size は 1〜2000。`id ASC` の固定一意順とし、独自 sort は提供しない。
応答は既存 Spring Page の形式（content、number、size、total_elements、total_pages 等）。
deleted=true は削除済み一覧であり、画面から削除後の履歴へ到達できる。

履歴は既存 CursorPage の `{content: ServiceRevisionResponse[], next_cursor?: string}`。
size は既存 clampSize に従い 1〜MAX_SIZE（2000）へ制限する。
項目内で一意な version DESC を順序・カーソル比較に共用する。末尾では next_cursor を省略する。

作成は版本 1。実変更・削除は版本を一つ進める。同値の PUT は現在値を返し、空の変更履歴を作らない。
更新・削除は行ロック下で expected_version を検証し、古い画面からの変更を 409 で拒否する。
画面は再取得・再確認を促し、自動上書きしない。現在値と履歴は同一トランザクションで保存する。

## 永続化と実装範囲

店舗作用域の service モジュールに t_services と t_service_revisions を置く。
前者は id、store_id、kind、name、duration_minutes、charge_type、price、remuneration、revision_number（API の version）、deleted、created_at、updated_at、version（JPA 楽観ロック版）。
後者は id、store_id、service_id、revision_number、operation、actor_id、occurred_at、created_at、updated_at、version（JPA 楽観ロック版）と before_ / after_ 接頭辞付きの条件列を保持する。
金額と種別別入力は集約および DB CHECK、版本は UNIQUE(service_id, revision_number) で守る。
店舗 FK、操作者 FK、(service_id, store_id) の複合 FK を維持し、物理削除・連鎖削除で履歴を失わせない。
一覧用 (store_id, deleted, id)、種別一覧用 (store_id, deleted, kind, id)、履歴用 (service_id, revision_number) の索引を定義する。
両エンティティは既存の静的店舗 filter 規則に従う。baseline を直接編集する。

サービス設定画面の作成・変更・削除確認・現在値・削除済み一覧・履歴を接続する。
受注の既存入力、本人受諾、公開店面、給与は変更しない。将来の参照に備えて項目 ID と歴史版本を保存するが、未接続の機能を実装済みと記述しない。

認証済み HTTP と実 PostgreSQL で店舗隔離・権限・履歴・競合を検証する。
金額境界は集約テスト、再確認・権限不足・表示漏洩は UI テスト、主要導線は日本語 Gherkin E2E。
最終検証は task lint、task test、task build、task e2e と code-review を実施する。
