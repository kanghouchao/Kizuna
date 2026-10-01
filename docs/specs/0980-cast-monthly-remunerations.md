# 本人の月次給与明細

状態: 承認済み（2026-10-01）。対象: #980。

本人は店舗と原営業日の自然月を指定し、発生済み固定報酬を照会する。同店の退店・再入店は一人分に合算し、店舗間の金額は混ぜない。完了・訂正・無効化後の再照会では原月の最新額を返す。給与の確定・承認・締め・保存版本・支払状態・PDF は追加しない。

## HTTP 契約

いずれも要求 body のない GET。ROLE_CAST を要求し、Principal → PlatformUser → Cast で本人を解決する。person_id / cast_id / enrollment_id は新しいエンドポイントの入力に含めず、追加されても本人の解決に使わない。既存の在籍指定エンドポイントは所有権検証と他人の在籍に対する 404 を維持する。

| パス | query | 成功応答 |
| --- | --- | --- |
| `/platform/me/monthly-remunerations/stores` | page?: int32 = 0、size?: int32 = 20 | Page<SelfMonthlyRemunerationStoreSummary> |
| `/platform/me/monthly-remunerations` | store_id: int64、month: string、page?: int32 = 0、size?: int32 = 20 | SelfMonthlyRemunerationResponse |

store_id は正数。month は 0001-01〜9999-12 の厳密な YYYY-MM。page は非負、size は既存の clamp に従い 1〜2000 に収める（0・負値は 1）。page × 適用後 size が int32 の最大値を超える場合は 400。任意 sort は提供しない。

店舗候補は本人の全在籍から店舗単位で重複を除き、store_id ASC。停止・退店・非公開の在籍も含み、現在の授権店舗集合や選択中店舗に依存しない。店舗名は現在の名称で、当月の名称の保存版本ではない。Cast／在籍なしは 200 の空ページ。

月次照会は店舗との歴史上の本人関係を検証する。不存在または本人と関係のない店舗、Cast なしは 404。関係のある店舗の空月は 200、合計 0 と空ページ。末尾超過ページでも全月の合計と総件数を保持する。

JSON は snake_case。以下はすべて必須・非 null。金額は整数円。

- SelfMonthlyRemunerationStoreSummary: `store_id: int64`、`store_name: string`。
- SelfMonthlyRemunerationResponse: `store_id: int64`、`store_name: string`、`month: string`、`total_remuneration: int64`、`orders: Page<SelfMonthlyRemunerationOrderSummary>`。
- SelfMonthlyRemunerationOrderSummary: `order_id: string`、`business_date: date (YYYY-MM-DD)`、`service_summary: string`、`accrued_remuneration: int32`、`completion_invalidated: boolean`。

Page は既存 Spring Page の外殻（content、number、size、total_elements、total_pages と既存メタデータ）。明細は business_date DESC, order_id DESC の全順序。合計・件数・明細・所有権確認は公開 service の readOnly REPEATABLE_READ で一応答内の整合性を保つ。別 HTTP 要求間でページを凍結するものではない。

成功は 200。401 は未認証・失効、403 は本人種別不適合、400 は要求パラメータ不正、404 は対象不存在・不可視。エラーは既存の `{error: string, details?: Record<string,string>}`。Bearer の失効・不正はフィルタで拒否される。必須 query の欠落や型変換は controller メソッドの認可実行前に失敗することがあるため、認証・入力不備が重複した要求のエラー順序は既存設定に従う。グローバルのセキュリティ設定は変更しない。

## 再利用と表示

店舗向けの MonthlyRemunerationQuery の total / orders を共用し、計算を複製しない。対象は原営業月の COMPLETED のみ。無効化行は根拠として残し、有効報酬は 0。本人の応答は専用 DTO に写し、顧客個人情報・内部操作者・他人の報酬を型に含めない。

詳細は既存 `/platform/me/remunerations/{orderId}`、履歴は同 `/changes` を再利用する。既存の本人所有権・退店後参照・専用 DTO と、本人／受注に結び付いた履歴 cursor の拒否規則を保つ。

本人ポータルの `/cast/remunerations/monthly` を既存の報酬明細から開く。履歴店舗、対象月、月合計、ページ付き明細を表示する。同じ slice 内で本人詳細・履歴を共用し、戻る際に選択と適用済み検索を保つ。取得失敗では古い集計を隠し、再試行を提供する。

## 検証

日本語 BDD で退店・停止・同店再入店・多店舗・自然月・未完了／取消除外・訂正／無効化・ページング・空月・本人詳細／履歴の到達を確認する。HTTP 境界で本人種別、他人の店舗／在籍／受注／cursor、追加 ID による本人偽装、応答フィールドの不在を確認する。最終検証は task lint / test / build / e2e と実ブラウザの明暗・狭幅・長い名称・キーボード操作。
