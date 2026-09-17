# 退店後も参照できる本人報酬明細 — API 契約案

状態: **承認済み・実装済み（2026-09-17）。**

対象: [#938](https://github.com/kanghouchao/Kizuna/issues/938)。業務仕様は #384、既存基盤は #935・#937、設計判断は ADR 0019・0026。

## 共通契約

- 以下はすべて新設する読み取り専用 GET。要求 body はない。認証済み ROLE_CAST を要求し、認証主体 → Cast → 全 CastEnrollment で本人を解決する。WITHDRAWN を除外せず、現在選択中の店舗・授権店舗集合・公開プロフィールに依存しない。X-Store-ID / X-Role は不要。
- JSON は snake_case。`?` のある字段だけが省略可能で、値なしは null ではなく省略。ID は string、store_id は int64、版は int64、金額・分数は int32、金額は整数円。date は YYYY-MM-DD、datetime はオフセット付き ISO 8601。
- 成功は 200。共通エラーは `{error: string, details?: Record<string,string>}`。401 未認証・失効、403 本人種別不適合、400 パラメータ・カーソル不正、404 対象なし・他人の在籍／受注。読み取りに 409 は導入しない。
- 本人に Cast／在籍がない場合、一覧は空。存在しない、または他人の enrollment_id 指定は 404。詳細・履歴の存在確認は必ず所有権検証と一体で行う。
- 店舗／平台 DTO・ドメイン快照を HTTP 型に再利用しない。本人専用 DTO は顧客 ID・氏名・連絡先、会員情報、受付・備考・伝言、操作者アカウント情報、他人の在籍／報酬、ポイント台帳、実回収・実支払を字段として持たない。

## 端点

| パス | query | 応答 |
| --- | --- | --- |
| `/platform/me/remuneration-enrollments` | page?: int（既定 0）、size?: int（既定 20） | Page<SelfRemunerationEnrollmentSummary> |
| `/platform/me/remunerations` | enrollment_id?: string、page?: int（既定 0）、size?: int（既定 20） | Page<SelfRemunerationSummary> |
| `/platform/me/remunerations/{orderId}` | なし。orderId: string 必須 | SelfRemunerationResponse |
| `/platform/me/remunerations/{orderId}/changes` | cursor?: string、size?: int（既定 20）。orderId: string 必須 | CursorPage<SelfRemunerationChangeResponse> |

enrollment_id 省略時は全在籍の受注。受注一覧には予定・進行中・完了・取消・無効化を含む。在籍一覧は `created_at DESC, id DESC`、受注一覧は `business_date DESC, id DESC` の固定全順序で任意ソートは提供しない。page は非負、size は 1〜2000 に収める。Page は既存 Spring Page の外殻（content・number・size・total_elements・total_pages・first・last・number_of_elements・empty と既存ページメタデータ）を使う。

変更履歴は `{content: SelfRemunerationChangeResponse[], next_cursor?: string}`。size は CursorPage.MAX_SIZE と同じ 1〜2000 に収める。`after_version DESC, id DESC` の全順序と同じ組で比較し、カーソルを本人・受注に結び付ける。不正・別本人・別受注のカーソルは 400。新規変更は先頭の再取得で表示し、途中追加で既読行を重複させず、既存の未読行を欠落させない。変更なしは空で、末尾は next_cursor を省略する。

## 本人専用応答型

### SelfRemunerationEnrollmentSummary

- `enrollment_id: string`、`store_id: int64`、`store_name: string`。
- `status: ENROLLED | SUSPENDED | WITHDRAWN`。
- `ended_at?: datetime`。終了した在籍だけに値を持つ。

### SelfRemunerationSummary

- `order_id: string`、`enrollment_id: string`、`store_id: int64`、`store_name: string`。
- `business_date: date`（原営業日）、`completed_at?: datetime`（原完了日時。完了だけ）。
- `status: CONFIRMED | IN_SERVICE | COMPLETED | CANCELLED`、`completion_invalidated: boolean`。
- `agreed_remuneration: int32`（現在の保存済み採用条件の報酬合計。取消・無効化でも原条件を保持）。
- `planned_remuneration: int32`（CONFIRMED / IN_SERVICE のみ約定額、それ以外零）。
- `accrued_remuneration: int32`（有効な COMPLETED の発生済み額、それ以外零）。

予定・発生済み・原条件を別字段とし、支払済みという名前・表示にしない。

### SelfRemunerationResponse

SelfRemunerationSummary の全字段に `version: int64` と `items: SelfRemunerationItem[]` を追加する。items は報酬根拠となるコース・特殊サービス・延長・加算だけ。割引・ポイント・決済加算・顧客支払情報は含めない。

### SelfRemunerationItem

- `kind: COURSE | SPECIAL_SERVICE | EXTENSION | SURCHARGE`、`name: string`、`price: int32`（採用条件の顧客費用）、`remuneration: int32` は必須。
- `line_id?: string`（延長・加算）、`duration_minutes?: int32`（コース・延長）。
- `service_id?: string`、`revision_id?: string`、`revision_number?: int64`、`adoption_basis?: CURRENT_SETTING | ACCEPTED_TERMS | HISTORICAL_CORRECTION`、`adopted_at?: datetime` は設定採用項目に限り必須、手入力延長では省略。
- `charge_type?: FREE | PAID`、`terms_version?: int64` は特殊サービスだけ必須。
- `consent_event_id?: string`、`consent_version?: int64` は本人の受諾根拠が保存されている特殊サービスだけ。現在の受諾状態と混同しない。

配列の採用条件から本人の報酬根拠を説明できる。顧客個人情報や他人の条件は混在させない。

### SelfRemunerationChangeResponse

- `change_id: string`（既存の不変・一意な訂正 ID）、`change_type: CORRECTION | COMPLETION_INVALIDATION`、`order_id: string`。
- `business_date: date`、`completed_at: datetime`（原記録）、`changed_at: datetime`、`reason: string`（保存済み訂正／無効化理由）。
- `before_version: int64`、`after_version: int64`、`before: SelfRemunerationSnapshot`、`after: SelfRemunerationSnapshot`。

SelfRemunerationSnapshot は `items: SelfRemunerationItem[]`、`agreed_remuneration: int32`、`accrued_remuneration: int32`、`completion_invalidated: boolean`。すべて必須。前後は独立した保存済み快照から作り、現在設定から復元しない。操作者情報は返さず、変更 ID で追跡可能にする。

## 所有権と保存境界

担当変更後の受注は変更前担当者の一覧・詳細・履歴から外す。現担当の在籍が本人の全在籍集合に属する受注だけを返す。完了後は既存不変量により担当変更不可であり、訂正快照もその本人のものに限られる。旧担当の特殊サービス拒否・処置イベントはこの API に混ぜない。

既存 t_orders・t_order_corrections・CastEnrollment の正本を再利用し、報酬台帳や快照の二重保存は追加しない。追加列・FK・削除規則・索引の変更は不要。本人解決は uq_t_casts_platform_user、在籍は idx_t_cast_enrollments_cast、担当受注は idx_t_orders_cast、履歴は uq_t_order_corrections_order_version と既存主キーを利用できる。本人／在籍の保護 FK、店舗削除時の CASCADE、操作者削除時の SET NULL を維持する。互換経路を設けない。退店後の閲覧権は既存サービス条件の受諾権から分離し、退店在籍での受諾は引き続き拒否する。

## 画面・検証

本人ポータルに「報酬明細」を追加し、全在籍の一覧 → 詳細 → 変更履歴へ進める。在籍選択には退店履歴を含むページ付き一覧を使い、現在店舗の選択集合で入口を消さない。完了日時・原営業日・項目別条件と予定／発生済みを表示し、取消は発生なし、無効化は原条件と有効報酬零を区別する。

既存 Base UI 部品と useListPage / useResource / useCursorList を使用し、ロード・取得失敗／再試行・403・404・空データを区別する。退店だけを権限不足として扱わない。

承認済み境界で TDD を行う。認証済み HTTP と実 PostgreSQL で全在籍・退店・再入店・他人 ID 直送・担当変更・専用 DTO・前後快照・ページング・退店後受諾拒否を確認する。UI は利用者操作で一覧／詳細／履歴、失敗と再試行、権限不足、表示漏洩を検証する。日本語 Gherkin で退店本人の履歴到達と他人の情報が見えない主要導線を固定する。

実装と同時に CONTEXT・ADR 0019・0026 を現行動作へ更新する。最終検証は task lint・task test・task build・task e2e と Standards / Spec のコードレビュー。完了後にコミットする。開始時の作業ツリーは detached HEAD のため、実装には `codex/issue-938-cast-remuneration-history` を作成する。

## 検証結果

2026-09-17 に `task lint`・`task test`・`task build`・`task e2e` がすべて exit code 0。フロントエンドは 157 スイート・1,461 テスト、実 PostgreSQL の統合テストは 86 スイート・758 テストが成功した。本人報酬の 6 統合テストには、退店・同店再入店・他店在籍の三受注のページング、他人 ID の拒否、担当変更、受諾条件、取消、無効化前後と履歴途中追加を含む。

E2E は 48 シナリオが成功し、退店本人のログインから報酬一覧・詳細・無効化履歴までの導線と、顧客情報の不在・他人受注の拒否を確認した。Standards / Spec の独立レビューは、共有 ListPage の利用と JPQL の全限定名を修正後、両軸とも残件なし。
