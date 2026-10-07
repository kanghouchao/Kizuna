# 出勤希望・シフト・実績11入口の共通監査

## 状態と目的

2026-10-07、master `3b39cc6655bf4f595ef7932b05f117a39c1dc515` を基点とする。親#395はOPEN継続。11入口・4service・副作用・許可値・既存3試験境界を一つの縦断バッチとする契約は承認済み。既存HTTP、授権、業務検証、schemaは変更しない。

管理者が本人の申請からシフト決定・公開・実績訂正までの系列を辿れるよう、共通の変更不可監査を接続する。自由記述の複写を避け、失敗した操作が成功履歴に残らないようにする。

## 利用者の要求

1. キャスト本人として、新規希望と変更申請が自分の操作・所属店舗として記録される。
2. 店舗担当者として、承認/却下と生成/更新シフトの関係を辿れる。
3. 店舗担当者として、直接作成・編集・公開変更・削除を確認できる。
4. 実績担当者として、記録・訂正・取消を既存訂正履歴と結び付けられる。
5. 管理者として、削除後もシフトIDと申請の関連解除を追跡できる。
6. 利用者として、同値入力や拒否・競合で成功変更を捏造されない。
7. 利用者として、note・待機場所・取消理由が共通監査へ複写されない。
8. 管理者として、通常主体/本人/緊急昇格を区別して照会できる。
9. 店舗担当者として、監査保存に失敗した操作が業務だけ確定しない。

## 承認済みの11入口

キャスト在籍の判定・所属守衛・ロックは既に存在するので、cast監査の先行実装は不要。11入口は4サービスに収まり、同じ業務系列・権限・検証基盤を使える。1メソッドずつ票を増やさず、この縦断単位で1仕様・1子票・1PRにする。

| サービス                | 入口                                         | 既存HTTP成功契約                                                             |
| ----------------------- | -------------------------------------------- | ---------------------------------------------------------------------------- |
| CastShiftRequestService | submit / submitChange                        | POST /platform/me/shift-requests と /changes、201                            |
| ShiftRequestService     | approve / decline                            | POST /store/shift-requests/{id}/approval と /rejection、200                  |
| ShiftService            | create / update / changePublication / delete | POST /store/shifts 201、PUT /{id} と /{id}/publication 200、DELETE /{id} 204 |
| AttendanceService       | record / correct / cancel                    | POST /store/attendances 201、PUT /{id} 200、POST /{id}/cancellation 204      |

本人2入口はROLE_CAST、店舗9入口はPERM_SHIFT_MANAGE。認証・権限・可視性・状態・競合の既存401/403/404/400/409とレスポンスを維持し、新しいHTTP項目や権限は導入しない。所属は業務正本から導出し、本人申請のstore IDを無検証で監査へ渡さない。SUSPENDEDは申請可能、WITHDRAWNは不可。実績は履歴補記が可能な現行規則を維持する。

## 監査契約

- 11入口の実変更を既存BusinessAudit→AuditWriterへ業務と同じ取引で記録。認証主体・対象店舗・緊急昇格IDの既存規則を再利用し、要求全体/Entity全体を保存しない。
- 共通の許可値: 対象/店舗/在籍/申請/シフト/実績/既存訂正/処理者のID、実在するversion、exists、種別、状態、published、勤務日/営業日、予定/実績時刻、処理/取消時刻。申請のoriginal勤務日・時刻も可。自由入力のnote、waitingPlace、取消理由は値・hashを除外し、固定の変更項目名のみ記録。名前・メール・写真・custom fieldsも除外。共通sinkに既存の主体スナップショットを渡すこととは区別する。
- 新規申請は申請イベント1。承認NEWは申請APPROVEDと生成CONFIRMED shiftの2イベント、承認CHANGEは申請APPROVEDと実変更したshiftを記録。shiftイベントのsourceは申請ID。却下は申請イベントのみ。
- 実績訂正は既存AttendanceCorrectionの保存IDをsourceとする。同値訂正も既存の訂正履歴が作られるため記録する。単なるシフト/公開の同値入力では新しい業務変更イベントを増やさない。ただしnoteだけの変更を、許可値が同じという理由で取り逃がさない。DBのversionやupdatedByの現行挙動を監査のために変更しない。
- シフト削除時は対象ID・削除前の許可値を残す。FK SET NULLとなる関連申請は削除前IDを確保し、実際の関連解除を申請ごとに同じ取引で記録して削除shift IDをsourceにする。実績参照があれば既存409、成功監査なし。cascade/SET NULLによる値とversionを架空に増加させない。
- 重複申請は新規申請として記録。処理済み申請の再承認/却下と取消済み実績の再取消は既存400、成功イベントなし。新しい冪等機構を導入しない。検索/履歴/公開読取は記録しない。
- 既存のstore→enrollment→shiftロック順序と@Versionを維持。更新系のshift快照取得のために、既存ロック取得より前の通常SELECTを足さない。DB副作用の捕捉に必要なロック/再取得は、同時削除・申請承認との競合試験で確認する。監査失敗・業務失敗・競合時は双方rollbackし、成功履歴を残さない。
- フロントの一括公開は既存の逐行API呼出しであり、成功した実変更だけを個別記録する。架空の一括トランザクションや一括IDを作らない。公開条件CONFIRMED＋published＋有効在籍/公開profileは変更しない。

## 変更範囲と検証

主な変更先は上記4 application service、shift内の許可値スナップショット補助、必要最小限の関連申請照会、対応する4 unit test、独立したPostgreSQL監査試験、shift監査HTTP E2E、仕様/coverage。共通BusinessAudit APIの拡張、castの状態遷移、#998/#997、TaskExecutor/POINT_EXPIRYは範囲外。

承認済みの3試験境界を再利用する。サービス→実BusinessAuditの試験では11入口・イベント数・source・本文非混入・同値/自由記述のみの差分を確認。実PGでは同取引rollback、FK解除と削除後の監査保持、古いversion/同時承認失敗の監査不増、通常STAFF/CAST/緊急昇格を確認。HTTP E2Eでは本人希望→店舗承認→変更申請→公開→実績訂正/取消の系列、異店舗/権限拒否と公開条件を確認する。既存shift-publicを回帰対象にする。最後はJDK25・Task lint/test/build/e2eをexit codeで判定し、親が割り当てる重E2E窓を使う。

## 対象外

castのライフサイクル、招聘/private添付、口コミ/通知、その他監査40入口の実装、平台/本人尾項の改修、schedule有効化・保持年限・月次解除は対象外。
