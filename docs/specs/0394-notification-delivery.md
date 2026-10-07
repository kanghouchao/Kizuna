# 顧客業務連絡の送信正本 — 最初の縦断スライス

親課題: #394。依存: #395 の実行・監査基盤、受注・ゲスト申請の今回限りの業務連絡許可。API 契約は主タスクで承認済み。実行主体の再検証は #820 のコミット済み基盤に依存する。

## 課題と解決

担当者が許可された業務連絡を作成・確認・予約し、結果と再試行を追える送信正本を設ける。受注とゲスト申請を最初の二つの業務起点とし、メールの実際の送信結果を保持する。販促許可を推定しない。

## 利用者の要求

1. 担当者は、受注またはゲスト申請を指定して業務メールを作成する。
2. 閲覧権限を持つ担当者は、本文を含まない一覧から内容・現在の連絡可否を確認する。
3. 送信権限を持つ担当者は、内容を確認して理由を記録し送信待ちにする。
4. 担当者は予約日時、状態、試行履歴を確認する。
5. 担当者は設定不足と送信成功を区別できる。
6. 担当者は、確実に未送信である失敗に限り、理由を記録して明示的に再試行する。
7. 担当者は、結果不明の送信を安易に再送できず、重複送信の可能性を理解できる。
8. 監査担当者は、本文や宛先を汎用監査に複製せず、主体・起点・状態変更を追跡する。
9. 店舗と権限が変わった画面は古い一覧・詳細・操作結果を表示しない。
10. 設定されていない自動実行や伝送能力は利用不可として表示する。

## 領域と実装判断

送信正本は店舗に属する。起点種別は ORDER / APPLICATION、チャネルは EMAIL、用途は BUSINESS のみ。内容と起点は作成後不変とし、送信履歴を後から書き換えない。顧客台帳や会員ログインの宛先を送信要求へコピーしない。毎回の実送信・再試行直前に起点の公開 contact インターフェースを呼び、現在の店舗拒否も通過した戻り値の宛先だけを使う。本文・件名は正本だけに保持し、一覧・汎用監査・ログに載せない。

作成時の冪等キーは店舗内で一意。同じキーと同じ内容は同じ正本を返し、異なる内容は競合。queue と retry はそれぞれ状態遷移として扱い、同じ操作の再実行を拒否する。UI は通信失敗後も作成キーを保持する。

TaskExecutor の handler は、到期した送信待ちを上限付きで拾い、永続イベントを同じ取引内で発行する。Task の完了件数は送信器への引き渡し件数であり、メールの到達件数ではない。独自 scheduler は追加しない。未設定の定期実行は自動で動作しているように表示しない。

送信 listener は取引の外で動作する。短い取引で試行を claim し、SERVICE 本人の有効性・店舗・機能権限と連絡可否を再検する。DB 取引と行ロックを解放してから外部 I/O を実施し、結果は別の短い取引で保存する。持続的イベントの再配達は claim 済みの attempt を再送しない。実際のメール提供元の受理と結果保存の間に原子的確定は存在せず、exactly-once を保証しない。送信中のクラッシュや曖昧な例外は UNKNOWN として扱い、通常の再試行を許可しない。実際の送信後に SERVICE が無効化されても、保存済み主体名とIDで発生済み結果を記録する。結果記録では再認可しないが、この履歴の主体情報を次の送信権限に用いない。結果保存自体が失敗した場合は、再配達時に UNKNOWN に確定し再送しない。

伝送結果は SENT（メール提供元が受理）、UNAVAILABLE（未設定）、FAILED（明確な未送信）、UNKNOWN（受理の可能性あり）を区別する。SENT は顧客の受信・閲覧を意味しない。既存 MailService の void 呼び出しの互換性は保持し、新しい構造化境界を送信正本から利用する。機密情報を含む例外メッセージはログへ渡さない。

## 承認済み API 契約

全端点は店舗コンソールの STAFF と指定店舗を要する。権限は NOTIFICATION_VIEW / NOTIFICATION_MANAGE / NOTIFICATION_SEND、実行 SERVICE は NOTIFICATION_DELIVER。既定授与なし。

| メソッド・パス | 要求 | 成功 | 権限 |
| --- | --- | --- | --- |
| POST /store/notification-deliveries | 下記作成要求 | 201 詳細、同一要求再生 200 | VIEW + MANAGE |
| GET /store/notification-deliveries | cursor 任意、size 既定 50・最大 100 | 200 CursorPage<Summary> | VIEW |
| GET /store/notification-deliveries/{id} | id: 数字文字列 | 200 詳細 | VIEW |
| POST /store/notification-deliveries/{id}/queue | version: 非負整数、reason: 1〜500文字 | 200 詳細 | VIEW + SEND |
| POST /store/notification-deliveries/{id}/retries | version: 非負整数、reason: 1〜500文字 | 201 詳細 | VIEW + SEND |
| GET /store/notification-deliveries/{id}/attempts | cursor 任意、size 既定 50・最大 100 | 200 CursorPage<Attempt> | VIEW |

作成要求はすべて必須: source_type（ORDER/APPLICATION）、source_id（文字列）、channel（EMAIL）、purpose（BUSINESS）、subject（1〜200文字）、body（1〜10000文字）、scheduled_at（offset付きISO日時）、dedupe_key（英数字・`.`・`:`・`_`・`-`、1〜120文字）。宛先の入力は受け付けない。source_id は32桁以内の数字文字列。起点ID・件名・キーの前後空白、本文の改行、日時のUTCマイクロ秒精度を正規化して比較する。新規の予定日時は2000年以降・現在から366日以内。過去日時は次の手動実行の対象となる。同一要求の再生では移動する日時上限を再評価しない。

Summary は id、source_type、source_id、channel、purpose、scheduled_at、status、attempt_count、created_at。詳細は Summary に version、scheduling_availability（MANUAL_TASK_ONLY）、subject、body、dedupe_key、現在の contact_decision、transport_availability を加える。Attempt は id、attempt_number、status、failure_code（任意）、started_at、finished_at（任意）、task_execution_id、reason。宛先は応答型にも含めない。カーソル順は id DESC とし、比較も同じ一意列を使う。

共通失敗: 401 未認証、403 権限・店舗拒否、404 不在または店舗外、400 入力不正、409 冪等キー不一致・不正な状態遷移・反復操作。共通 error/details 形式を使用する。

## 共通タスク画面との接続

`GET /platform/task-executions/task-types` は登録上限32件の処理を名前順で返す。各要素は name / scope（PLATFORM・STORE）/ manual_allowed。手動権限のない処理も false として表示する。`GET /platform/task-executions/stores` は page 既定0・size既定20最大100の店舗選択候補を返し、IDは文字列。TASK_MANAGE・STAFF・ALL_STORES の既存境界を使用する。

既存の service-identities 候補読出しに task_name と store_id を追加する。未知の処理・範囲不整合は400、不在店舗は404。候補は最新の有効なSERVICE・TASK_EXECUTE・処理固有権限・対象店舗で絞る。手動権限のない担当者も候補の閲覧はでき、実行時は権限を別途再検証する。通知の処理名は NOTIFICATION_DELIVER、手動担当者も SHARED の NOTIFICATION_DELIVER を要する。対象日は実行記録の日付で、予約日時の絞込みには使わない。

起動時の永続イベント再発行は `REPUBLISH_OUTSTANDING_EVENTS` 既定 false のまま。中断後の再配達は同一試行を再送せず UNKNOWN に確定するが、自動復旧運用の有効化は別途必要となる。

## 検証

公開 HTTP 境界と UI 操作を主な検証境界とする。transport だけを注入 fake に差し替え、実 DB・TaskExecutor・監査・同意判定を通したテストで許可、拒否、宛先変更、取り消し、別店舗、重複操作、予約前、成功、未設定、明確な失敗、UNKNOWN、明示再試行、権限剥奪を確認する。伝送境界の単体テストで未設定・曖昧なメール提供元結果を検証する。UI は権限、古い応答、連打、失敗からの回復を検証する。実在顧客・外部宛先への送信と実アカウント資格情報の利用は禁止し、fake またはローカル隔離 SMTP のみを使う。

実DB検証は `KIZUNA_NOTIFICATION_TEST_JDBC_URL` で明示した隔離 PostgreSQL にテストごとのschemaを作り、実TaskExecutor・監査・同意判定・永続イベントを接続する。`NotificationPostgresTest` の注入transportは外部へ接続しない。実スタック E2E は SMTP を設定せず、未送信結果と再試行を確認する。

最終確認は Taskfile の lint / test / build / e2e と独立したローカルレビュー。重い E2E の開始は主タスクと調整し、専用 Docker tag と stack を使う。

## 対象外・残件

本スライスだけで #394 は閉じない。アンケート回答、口コミ収集と承認、公開連携、販促、SMS/LINE/サイト内チャネル、期限前案内・有料会員通知への接続、UNKNOWN のメール提供元照会・管理者による確定処理、運用の定期実行設定は別スライス。外部送信や本番稼働の許可はこの機能実装の承認に含めない。
