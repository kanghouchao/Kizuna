# キャスト本人・跨店在籍照会 API

## 裁定

本人の検索と跨店在籍照会をプラットフォームコンソールに設ける。本名・生年月日は読み取り専用とし、編集口は設けない。店舗コンソールには本人情報や他店在籍の存在を返さない。

`CAST_PERSON_VIEW` は Console.PLATFORM の専用閲覧権限で、既定 HQ_ADMIN に授与する。担当店舗集合に関係なく全店舗を照会できる。全 handler は `PERM_CAST_PERSON_VIEW` を要求する。メニューは同じ権限を持つ「キャスト」グループの「キャスト在籍照会」、画面は `/platform/casts` と `/platform/casts/[id]`。

## 契約

| メソッド・パス | 入力 | 応答 |
| --- | --- | --- |
| GET `/platform/casts` | 任意 `search`、`page=0`、`size=20` | Page：`id: number`、`display_name: string`、`real_name: string or null` |
| GET `/platform/casts/{id}` | 本人 ID | `id: number`、`platform_user_id: number`、`display_name: string`、`real_name: string or null`、`birth_date: YYYY-MM-DD or null` |
| GET `/platform/casts/{id}/enrollments` | 本人 ID、`page=0`、`size=20` | Page：`id: string`、`store_id: number`、`store_name: string`、`name: string`、`status: ENROLLED / SUSPENDED / WITHDRAWN`、`ended_at: ISO日時 or null` |

成功は 200、未認証は 401、権限不足は 403、入力不正は 400。詳細と在籍照会で本人が存在しなければ 404。存在する本人の在籍がなければ空ページを返す。nullable 項目は省略せず null を返す。

検索は前後空白を除き、表示名・本名への大小文字を区別しない部分一致とする。`%`、`_`、バックスラッシュは文字そのものとして照合する。検索なし・空白だけなら全本人が対象。招待未受諾の在籍は本人行を持たないため対象外。

両一覧は総件数とページ番号を使う Spring Page（`content`、`number`、`size`、`total_pages`、`total_elements`）。`page >= 0`、`1 <= size <= 100`。本人は ID 昇順、在籍は作成日時降順・ID 降順の固定全順序。退店・再入店によって無界に増えるため在籍もページングする。生年月日を一覧 DTO に含めない。

在籍の `name` は公開プロフィールの源氏名。公開状態によるフィルタは掛けず、停止・退店も別々のエピソードとして示す。内部 custom fields と変更履歴は返さない。店舗の既存 API の ID は在籍 ID のまま、本 API の本人 ID と混同しない。
