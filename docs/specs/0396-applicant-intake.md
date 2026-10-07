# 応募受付・面接・選考記録

## 目的と範囲

応募者 Applicant を独立した店舗正本として登録し、受付・面接・選考状況を追跡する。Cast（本人）、CastEnrollment（在籍）、PlatformUser（アカウント）は作成しない。担当者名・紹介者名・スカウト名・面接担当者名は記録用テキストであり、アカウントへの割当や権限を表さない。操作主体は認証済み PlatformUser ID から取得する。

第一片は一覧・登録・詳細・受付編集・単一面接記録の編集・状態変更・状態履歴を提供する。採否確定の最終責任者、保存期間と匿名化期限は未設定と明示し、採否の実行と自動削除を許可しない。第二片の在籍登録・明示的招待との接続、複数面接の履歴、媒体別採用効果の集計、実データ移行は含めない。

本人確認書類・添付ファイルの受入は含めない。現行の S3 保存経路はバケット全体が公開設定となるため、私有ファイルの要件を満たさない。新たな私有ファイル機構と実際のアクセス設定を別途検証する必要がある。

## 利用者の操作

1. 採用担当者は店舗ごとの応募者を氏名と選考状態で検索する。一覧に連絡先・住所・経歴・希望条件・面接内容を出さない。
2. 採用担当者は受付チャネルと応募元を区別して登録する。電話による受付でも媒体経由を表せる。
3. 採用担当者は詳細画面で受付情報と面接記録を編集する。編集競合は409となり、入力を残して再読み込みを案内する。
4. 採用担当者は理由を付けて選考状態を変更し、操作主体と日時のある履歴を確認する。
5. 閲覧だけの担当者は詳細と履歴を確認できるが、更新できない。
6. 採用担当以外は既定ロールで応募者を閲覧できない。採否確定権限も既定付与しない。

## API 契約

店舗ヘッダと授権店舗集合、サービスの `@StoreScoped` により分離する。全て認証必須。読みは `RECRUITMENT_VIEW`、更新は `RECRUITMENT_VIEW` と `RECRUITMENT_MANAGE`、採否確定は `RECRUITMENT_VIEW` と `RECRUITMENT_DECIDE` を要求する。3権限はいずれも STORE に属し、既定ロールへの付与は空とする。

| メソッド・パス | 入出力と成功コード |
| --- | --- |
| GET `/store/applicants` | `search?:string`（氏名のみ、100文字以内）、`status?:enum`、`page`（0始まり）、`size`（最大100）。Spring Page、200。`created_at DESC,id DESC` 固定 |
| POST `/store/applicants` | 受付情報を登録。最新詳細（idを含む）、201 |
| GET `/store/applicants/{id}` | 詳細、200 |
| PUT `/store/applicants/{id}` | `{version:integer,intake:受付情報}`。受付情報を全置換、最新詳細、200 |
| PUT `/store/applicants/{id}/interview` | `{version,interview_at,interviewer,notes,checklist}`。単一面接記録を全置換、最新詳細、200 |
| POST `/store/applicants/{id}/transitions` | `{version,status,reason}`、最新詳細、200 |
| POST `/store/applicants/{id}/decision` | `{version,status:HIREDまたはREJECTED,reason}`。最終責任者未設定につき409 |
| GET `/store/applicants/{id}/history` | `cursor?:string,size?:integer`。CursorPage、最大100、`created_at DESC,id DESC` のタプル比較、200 |
| GET `/store/applicants/policy` | `{final_decision_configured:false,retention_periods:null}`、200。policy は予約済みパス |

共通失敗は401（未認証）、403（権限・店舗集合外）、400（入力・不正遷移）、404（不在・行が現在店舗から不可視）、409（旧版・重複・終了済み・方針未設定）。既存の `{error,details?}` を使う。

受付情報は `name:string`（必須100文字）、`channel:PHONE|WEB|MEDIA|OTHER`、`source_type:DIRECT|MEDIA|REFERRAL|SCOUT` を必須とする。任意文字列は `source_media`（200）、`referrer`（100）、`assignee`（100）、`phone`（50）、`email`（254）、`address`（500）、`experience` と `desired_conditions`（各3000）。空白はnullに正規化する。MEDIAだけ媒体名を必須とし、REFERRAL/SCOUTだけ紹介者またはスカウト名を必須とする。他区分への値の残存を拒否する。媒体名は受付時の記録であり、広告費集計の媒体マスターへの同一性を主張しない。

面接日時は時差付きISO日時、面接担当者名は必須100文字、メモは任意5000文字。checklist は必須 `map<string,boolean>`、最大30項目、キー最大100文字、空白・NUL・JavaScript予約キーを拒否する。キーとboolean以外の値を持たず、JSON表現はエスケープ込みで概ね20KB以内に上限がある。任意の文書・ファイルを格納する用途には用いない。

一覧型は `id,name,status,channel,source_type,source_media,assignee,created_at,version` のみ。詳細は受付情報、単一面接記録、状態、版、作成・更新日時、最終更新主体IDを持つ。履歴は `id,previous_status?,new_status,actor_id,created_at,reason`。

## 状態と整合性

| 現在 | 許可する通常遷移 |
| --- | --- |
| RECEIVED（応募受付） | SCREENING、WITHDRAWN |
| SCREENING（選考中） | INTERVIEWED、WITHDRAWN |
| INTERVIEWED（面接済） | SCREENING、WITHDRAWN |
| HIRED / REJECTED / WITHDRAWN | なし |

INTERVIEWEDには面接記録が必要。WITHDRAWNは応募者の辞退・撤回であり、従業員の解雇を表さない。HIRED/REJECTEDは通常遷移から到達できず、専用操作も方針未設定のため拒否する。全遷移で理由が必要。同一状態への再実行を拒否する。

変更は悲観行ロック後に要求versionを照合し、受付・面接・状態の保存ごとに版を進める。同じ値を再保存しても変更番号を進め、古いフォームの書込みを検出する。状態更新と履歴記録は同一トランザクションで確定する。生の面接メモ・住所・電話・変更理由を汎用監査へ複製しない。監査基盤の `BusinessAudit.recordCurrent` に対象ID・操作区分と変更前後の状態・版だけを渡す。監査保存も同一トランザクションに参加し、失敗した場合は応募者と状態履歴を含む全更新を取り消す。保持・匿名化の方針が未設定のため、自動処理の TaskHandler は登録しない。

## 検証境界と残件

領域テストは六状態の全組合せ、面接前提、終端、必須理由、入力上限を検証する。HTTPテストは権限の組合せと機微情報を持たない一覧型を確認する。使い捨ての実スタックで店舗分離、旧版・二重送信、トランザクション、画面の登録から履歴までを検証する。実ブラウザでライト・ダーク・狭幅・長文・キーボード・通知を確認する。

親課題 #396 は在籍登録・明示的招待との接続、私有ファイル、媒体参照の確定、最終責任者、保持・匿名化方針が残るため本片だけで閉じない。
