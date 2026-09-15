# 本人サービス条件 API 契約案

状態: 承認済み（2026-09-15）。対象: [#930](https://github.com/kanghouchao/Kizuna/issues/930)。

## 授権と範囲

全端点に `ROLE_CAST` を要求する。認証主体から Cast を解決し、指定店舗の自己の有効在籍を取得する。要求に `cast_id` や `enrollment_id` を受け取らず、店舗・HQ による代理操作を認めない。サービスの照会は解決済み店舗の既存 store filter 内で行う。店舗選択は既存の本人所属店舗取得を再利用する。

## HTTP

| メソッド・パス                                             | 要求                                                                                                                    | 成功応答                               |
| ---------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- | -------------------------------------- |
| `GET /platform/me/service-conditions`                      | 必須 `store_id: string`、任意 `kind: COURSE \| SPECIAL_SERVICE \| SURCHARGE`、`page: integer = 0`、`size: integer = 20` | `200 Page<OwnServiceConditionSummary>` |
| `PUT /platform/me/service-conditions/{service_id}/consent` | パスの `service_id: string`、必須 query `store_id: string`、下記 JSON                                                   | `200 OwnServiceConditionSummary`       |

一覧は削除されていない現行項目を返す。並びは `id ASC` 固定、page は零以上、size は 1〜2000。応答外殻は既存の Spring Page（`content`、`number`、`size`、`total_elements`、`total_pages` 等）を使用する。履歴照会端点は追加せず、設定改定は既存の管理側 CursorPage と不変の設定版本を再利用する。本人意思の履歴はサービス側に永続保存する。

PUT の JSON は全項目必須・null 不可:

```json
{
  "terms_version": 1,
  "consent_version": 0,
  "decision": "ACCEPTED"
}
```

`terms_version` は正の整数、`consent_version` は零以上の整数、`decision` は `ACCEPTED | REJECTED`。後者の版は同じ本人による複数画面の競合も検出する。初期状態の版は 0。同じ意思・同じ版の再保存は変更なしとして 200 を返し、履歴を重複生成しない。

## 本人専用応答型

以下以外の個人属性・他人の報酬・設定担当者情報は含めない。省略可能とした項目は該当しない場合に省略し、null を返さない。

| フィールド              | 型            | 必須・意味                     |
| ----------------------- | ------------- | ------------------------------ |
| `id`, `store_id`        | string        | 必須。項目と店舗の ID          |
| `kind`                  | `COURSE       | SPECIAL_SERVICE                | SURCHARGE`                     | 必須                     |
| `name`                  | string        | 必須。現在名称                 |
| `duration_minutes`      | integer       | コースのみ必須。他は省略       |
| `charge_type`           | `PAID         | FREE`                          | 特殊サービスのみ必須。他は省略 |
| `price`, `remuneration` | integer       | 必須。整数円                   |
| `terms_version`         | integer       | 必須。名称以外の提供条件の版   |
| `consent_status`        | `NOT_ACCEPTED | ACCEPTED                       | REJECTED                       | RECONFIRMATION_REQUIRED` | 特殊サービスのみ必須。他は省略 |
| `consent_version`       | integer       | 特殊サービスのみ必須。他は省略 |

`NOT_ACCEPTED` は意思未登録、`REJECTED` は本人の能動的拒否、`RECONFIRMATION_REQUIRED` は旧条件に受諾した後の条件改定。拒否済み項目は改定後も拒否表示を維持する。名称のみの変更は terms_version と受諾を保持する。価格・固定報酬・有料無料区分の変更では terms_version を増やし、旧条件への受諾を新条件へ継承しない。

## 失敗

- `401`: 未認証・失効セッション。
- `403`: CAST 以外。店舗・HQ の代理操作を含む。
- `404`: 自己の有効在籍がない店舗、別店舗・不存在・削除済み項目。退店後も自己の有効在籍がないため拒否する。
- `400`: 不正な型・値・ページ指定、特殊サービス以外への意思保存。
- `409`: 表示後の条件改定・本人意思の版競合。保存せず最新条件の再確認を促す。

共通形式は `{ "error": string, "details"?: { [field_name: string]: string } }`。PUT ではロック後に在籍と両版を検証し、競合失敗時は意思・履歴とも残さない。

## 永続化と競合の設計

- `t_services` に `terms_version BIGINT NOT NULL DEFAULT 1 CHECK (> 0)` を追加する。既存の設定版本とは区別し、名称のみの改定で受諾を失効させない。
- サービス側の `t_service_consents` は `id`、`store_id`、`enrollment_id`、`service_id`、`decision`、`terms_version`、`service_revision_id`、`revision_number`、監査日時を持つ。在籍・項目の組を UNIQUE とし、意思を上書きするたびに履歴へ追記する。
- `t_service_consent_events` は `id`、`store_id`、`consent_id`、`revision_number`、`decision`、`terms_version`、`service_revision_id`、`actor_id`、`occurred_at` を持つ。`(consent_id, revision_number)` を UNIQUE とし、本人の受諾・拒否を旧条件への参照とともに保存する。
- 項目・在籍への FK は store_id を含む複合 FK とし、店舗を跨ぐ組合せを拒否する。設定版本・意思・操作者・店舗の FK は `ON DELETE NO ACTION` とし、追跡記録の暗黙消去を防ぐ。必要な参照側索引を同じ baseline に定義する。
- 設定改定は既存 ServiceRevision で追跡し、本人の拒否イベントと混同しない。退店失効は在籍状態との照合で即時に成立させ、退店後の履歴は保存する。再入店は別 enrollment_id のため初期状態から始まる。
- 設定改定・退店・本人操作は既存の店舗ロックを共通の先頭ロックとして直列化し、その後に在籍・項目・意思の順で必要なロックを取る。Cast 三層に意思の正本を追加しない。

## UI と検証

本人ポータルに「サービス条件」を追加し、店舗選択・ページング・受諾・拒否を接続する。再確認待ちと能動的拒否を区別し、409 では再取得した内容を確認するまで再送しない。取得中・失敗と再試行・権限不足を個別表示する。

親仕様で確認済みの境界を用いる: 認証済み HTTP と実 PostgreSQL、集約の条件判定、利用者操作の UI テスト、少数の日本語 Gherkin E2E。最終確認は `task lint`、`task test`、`task build`、`task e2e` と Standards / Spec のコードレビュー。
