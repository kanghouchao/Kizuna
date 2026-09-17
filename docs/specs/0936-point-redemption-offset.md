# ポイント利用取消とシステム相殺

対象: #936。2026-09-17 に以下の HTTP 契約を承認済み。

## HTTP 契約

全て店舗ヘッダと認証を要し、ORDER_MANAGE と POINT_ADJUST の両方を検証する。

| メソッド・パス | 成功 | 要求 |
| --- | --- | --- |
| GET /store/orders/{id}/point-rollback-preview | 200 | 本文なし |
| POST /store/orders/{id}/point-rollback | 201 | reason: string（空白除去後1〜500文字）、expected_total_fee: integer、expected_offset_amount: integer。全て必須、金額は非負整数円 |

下見は already_rolled_back: boolean、cancellable_points / reversible_used_points: int64、current_total_fee / offset_amount / resulting_total_fee: integer を必須で返す。member_code: string は現在帰属なしなら省略する。rollback は未実行なら省略し、実行済みなら以下の確定結果を返す。

確定結果は id: string、reason: string、actor_user_id: int64、created_at: ISO-8601日時、cancelled_points / restored_points: int64、before_total_fee / offset_amount / after_total_fee: integer。全項目必須。処置時点の金額を保持し、その後の通常訂正で書き換えない。

受注明細の kind に POINT_REDEMPTION_OFFSET を追加する。表示 amount は正整数、system_owned は true。既存の利用行を残し、相殺行を一度だけ加える。利用なしは金額0・相殺行なし。

400 は不正入力・未完了・金額上限超過、401 は未認証、403 は権限不足、404 は不存在・店舗作用域外、409 は二重実行・競合・確認額の変化、500 は予期しない失敗。エラー形式は既存の error と省略可能な details。全ての失敗で部分保存しない。

受注あたり高々一件の操作記録を単一オブジェクトで返すため、ページングは不要。既存の一覧と増加する訂正履歴は既存の Page / CursorPage 契約を維持する。

## 保存と不変量

pre-launch baseline を直接更新する。t_point_rollbacks は既存の受注単位 UNIQUE と受注 FK（削除 CASCADE）を維持し、cancelled_points / restored_points（BIGINT）、before_total_fee / after_total_fee（INT）を NOT NULL で追加する。非負と after = before + restored を CHECK する。操作者を NOT NULL にし、ユーザー FK の削除を NO ACTION として説明主体を失わせない。

t_order_fee_lines は POINT_REDEMPTION_OFFSET の正数 CHECK と受注単位の部分 UNIQUE 索引を持つ。既存の受注・店舗複合 FK（CASCADE）を維持する。元利用と相殺の等額性は受注集約が台帳の実返還量と照合する。通常入力と完了後訂正ではシステム行を直送・改変・削除できず、元明細と固定報酬は保持する。

受注行、伝票トークン行、会員ロットの順でロックし、台帳返還、付与取消、操作記録、相殺明細、請求の更新を同一トランザクションで確定する。下見も受注行を押さえ、巻き戻しと同時に読んでも請求と履歴が別時点にならない。事後申領禁止と未消費付与のみの取消を維持する。

## 検証境界

集約で 10,000 − 3,000 + 3,000 = 10,000、利用なし、金額不一致、整数上限を検証する。認証済み HTTP と実 PostgreSQL で返還・履歴・二重実行・競合・中途保存失敗の全巻き戻り・システム行の保護を検証する。UI は再確認、権限不足、取得失敗と再試行、店舗切替の情報非表示を扱い、日本語 E2E は専用救済入口から再訪時の履歴までを通す。
