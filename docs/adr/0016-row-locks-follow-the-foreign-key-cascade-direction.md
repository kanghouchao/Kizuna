# 行ロックは外部キーの連鎖の向きに押さえる

Status: Accepted

## Context

#732（予実交差の守衛）の実装で、レビューの指摘が 4 巡続いた。1 巡目は元実装の check-then-act の欠陥
だったが、2〜4 巡目はいずれも 1 巡目の修正が持ち込んだロック順序の環である。同じ盲点が 3 回に分かれて
現れた形で、盲点そのものはひとつに尽きる。

> **どの書き込みが、どの親行のロックを暗黙に要求するか**が順序の検討に入っていなかった。

明示的に `SELECT ... FOR UPDATE` する行だけを数えて「1 行しか押さえないから環にならない」と判断したが、
INSERT や外部キー列を変える UPDATE は、外部キー検査を通じて親行のロックを要求する。#740 はその都度
1 件ずつ潰して収束させたが、順序の規則そのものは書かれないままだった。

### PostgreSQL 側の事実（PostgreSQL 18.4 で実測）

順序を論じるには、どの操作がどの強さのロックを載せるかが要る。以下は本リポジトリの開発環境と同じ
`postgres:18-alpine` に対して実際に走らせて確かめた。

1. 行ロックは 4 段（KEY SHARE < SHARE < NO KEY UPDATE < UPDATE）。**FOR KEY SHARE が衝突する相手は
   FOR UPDATE だけ**である。
2. 子表への INSERT と、外部キー列の値を変える UPDATE は、参照先の親行に **FOR KEY SHARE** を要求する。
   DELETE は自分の行を **FOR UPDATE** で押さえる。
3. UPDATE が自分の行に載せる強さは、変えた列が「キー列」かどうかで決まる。**キー列とは、部分でも式でも
   ない一意索引の列**（＝外部キーの参照先になれる索引の列）である。実測した 6 例：

   | 変える列 | 載る強さ |
   | --- | --- |
   | 主キー列 | FOR UPDATE |
   | 部分でない一意索引のキー列 | FOR UPDATE |
   | **部分**一意索引のキー列（`t_attendances.business_date` など） | FOR NO KEY UPDATE |
   | 部分索引の述語にだけ出る列（`t_attendances.cancelled_at`） | FOR NO KEY UPDATE |
   | 一意でない索引の列 | FOR NO KEY UPDATE |
   | どの索引にも出ない列 | FOR NO KEY UPDATE |

4. 外部キー検査の走る順序は、INSERT 側（親行への key share）も DELETE 側（検査と連鎖）も **制約の作成順**
   である（名前順ではない）。同名の 2 制約を作成順だけ入れ替えると、違反として鳴る制約が入れ替わることを
   両方向で確認した。本リポジトリの作成順は `db.changelog-master.yaml` の include 順＝ファイル番号順で決まる。

事実 3 から効く帰結がある。本 ADR が扱う 5 表で**主キーを書き換える経路は無い**ので、これらの表の行に
FOR UPDATE を載せるのは DELETE と、明示的な `@Lock(PESSIMISTIC_WRITE)` の読み口の 2 種だけである
（UPDATE 文はどれも FOR NO KEY UPDATE で済む）。

そこから、**待ちの結節点になれる行は 3 通り**に絞れる — DELETE が押さえた行、明示ロックの読み口が押さえた
行、そして UPDATE された行（FOR NO KEY UPDATE どうしも衝突する）である。逆に、外部キー検査が要求する
key share どうしは衝突せず、key share と FOR NO KEY UPDATE も衝突しないので、**書き込みが親行へ要求する
だけの key share は、それ単独では誰も待たせない**。洗うべき対はこの 3 通りに対応する 3 群で尽きる。

## Decision

**行ロックは外部キーの連鎖の向き（上流 → 下流）に押さえる。**

```
t_stores ─┬─ t_casts ─┬─ t_shifts ─┬─ t_shift_requests
          │           │            └─ t_attendances ── t_attendance_corrections
          └─ （t_user_stores ほかの店舗スコープ表）
```

この向きを選ぶ理由は二つある。

- **削除の連鎖は必ず上流から下流へ進む。** 逆向きは選べない。店舗削除はキャストへ、キャストの削除は
  シフトへ連鎖する（いずれも CASCADE）。
- **INSERT が要求する親行の key share もこの向きに並ぶ。** 5 表とも外部キーの宣言順が
  store → cast → shift で揃っているため（前節の事実 4）、書き込み側は放っておけばこの順で進む。

**`t_users` はこの向きの、明示された例外である。** 監査列（`created_by` / `updated_by` / `processed_by`）と
`t_casts.platform_user_id` により身分は 5 表すべての親だが、どの表も身分への外部キーを**最後に**宣言して
いるため、書き込みが要求する key share は必ず最後に来る — 上流でありながら末尾で取られる。これが問題に
ならないのは、身分の行に FOR UPDATE を載せる経路が招待受諾の 1 本
（`PlatformUserRepository#findByEmailForUpdate`）しか無く、それも档案を押さえた後だからである。key share
どうしは衝突しないので、身分の行が待ちの結節点になるのはその 1 本が押さえている間に限られる。
**身分の行を押さえる経路（身分の削除を含む）を足すときは、この表を引き直すこと。**

#740 が固定した「キャスト → シフト」はこの規則の一断面である。順序の契約を述べる正本はこの ADR で、
`...ForUpdate` の読み口の Javadoc はその入口を指すだけに留める。

**明示的に行を押さえる経路は、この向きに従う** — 例外は下に窓①として挙げた 2 経路だけで、理由を付けて
残している。破りやすいのは、下流の行を先に `FOR UPDATE` してから上流の親を要求する形である。

## 一覧表

5 表（`t_casts` / `t_shifts` / `t_shift_requests` / `t_attendances` / `t_attendance_corrections`）を
書く応用層の経路をすべて挙げ、明示ロック・書き込みが暗黙に要求する親行・削除の連鎖を書き出したもの。
判定の列は上の向きに従うか否かである。

| 経路 | 明示的に押さえる行 | 暗黙に要求する親行（KS） | 削除の連鎖 | 判定 |
| --- | --- | --- | --- | --- |
| `CastService#create` | — | stores | — | 順路 |
| `CastService#update` | — | （FK 列を変えない） | — | 順路 |
| `CastService#delete` | casts（DELETE 自身） | — | invitations → orders 検査 → shifts（さらに shift_requests を SET NULL・attendances を検査）→ shift_requests → attendances 検査 | 順路 |
| `CastInvitationAcceptanceService#acceptAsNewUser` | **stores** → **casts** → invitations | users（档案の紐づけ）、stores・users（身分の所属店舗） | — | **本票で修正** |
| `CastInvitationAcceptanceService#acceptAsExistingUser` | **stores** → **casts** → invitations → users | users（档案の紐づけ）、stores・users（所属店舗の追加） | — | **本票で修正** |
| `ShiftService#create` | — | stores, casts, users | — | 順路 |
| `ShiftService#update` | casts（付け替え時）→ shifts | casts, users | — | 順路（#740） |
| `ShiftService#changePublication` | — | users | — | 順路 |
| `ShiftService#delete` | shifts | — | shift_requests を SET NULL → attendances 検査 | 順路 |
| `ShiftRequestService#approve`（NEW） | — | stores, casts, shifts, users | — | 順路 |
| `ShiftRequestService#approve`（CHANGE） | shifts | users（申請行の書き込みは取引の終わりまで遅れる） | — | **本票でテストに固定** |
| `ShiftRequestService#decline` | — | users | — | 順路 |
| `CastShiftRequestService#submit` | — | stores, casts | — | 順路 |
| `CastShiftRequestService#submitChange` | —（対象シフトを押さえずに読む） | stores, casts, shifts | — | 順路・**窓②** |
| `AttendanceService#record` | casts → shifts | **stores**, casts, shifts, users | — | **窓①** |
| `AttendanceService#correct` | shifts | **stores**, attendances, users | — | **窓①** |
| `AttendanceService#cancel` | — | users | — | 順路 |
| `StoreRegistryService#delete` | stores（DELETE 自身） | — | 店舗スコープ表すべてへ CASCADE（user_stores → … → casts → shifts → …）、attendances と attendance_corrections は検査 | 順路・**窓③** |

## 対ごとの洗い出し

待ちの結節点になれる行は前述の 3 通りなので、順序を作りうる経路の対も次の 3 群に尽きる。この 3 群を
尽くせば洗い出しは完全である。

### (i) 明示ロックの読み口を 2 つ以上使う経路どうし

| 経路 | 取る順 |
| --- | --- |
| `AttendanceService#record` | casts → shifts |
| `ShiftService#update`（付け替え時） | casts → shifts |
| `CastInvitationAcceptanceService#acceptAsNewUser` | stores → casts |
| `CastInvitationAcceptanceService#acceptAsExistingUser` | stores → casts → users |

1 つしか使わない経路（`correct` / `approve`(CHANGE) / `ShiftService#delete` はいずれも shifts）は、それだけ
では順序を作らない。同じ組を逆順に取る対は無い — `{casts, shifts}` は 2 経路とも casts → shifts、
`{stores, casts}` は 2 経路とも stores → casts、`{casts, users}` は 1 経路のみである。**環なし。**

なお受諾が取る stores は key share（`StoreRepository#lockAgainstDeletion`）で、阻むのは店舗の削除だけである。
待ちの結節点にはならないが、順序には参加する。

### (ii) DELETE の 3 経路 × 他のすべて

- **`ShiftService#delete`**（shifts を押さえ、shift_requests・attendances へ届く）。shift_requests や
  attendances を押さえてから shifts を欲しがる経路は無い — `approve`(CHANGE) と `correct` はどちらも
  shifts が先で、`cancel` は shifts を要らない。`submitChange` の INSERT はシフト行に key share を要求
  するので相互排他になるが、新しい申請行は他取引から見えないため削除側がそこで待つことも無い。**環なし。**
- **`CastService#delete`**（casts を押さえ、invitations・orders・shifts・shift_requests・attendances へ
  届く）。これらを押さえてから casts を欲しがっていたのが招待受諾で、**本票で直した**。記録と付け替えは
  casts が先（#740）。受注の担当付け替えは受注行を FOR NO KEY UPDATE で持つが、キャスト削除の NO ACTION
  検査は FOR KEY SHARE なので衝突しない — 待ちは「付け替えが削除を待つ」の片側通行だけで環にならず、
  削除が先に通れば付け替えは外部キー違反で落ちる（正しい結末である）。**修正後は環なし。**
- **`StoreRegistryService#delete`**（stores を押さえ、店舗スコープ表すべてへ届く）。下流を押さえてから
  stores の key share を要求していた経路が 4 本あった。招待受諾の 2 本は**本票で直した**（店舗行を先に
  押さえる）。実績の記録・訂正の 2 本は **窓①**として残す。

### (iii) 既存の行を 2 つ以上書く経路どうし

FOR NO KEY UPDATE どうしも衝突するので、UPDATE だけでも順序は生まれる。5 表のうち既存行を 2 つ以上書く
経路は 3 本しかない。

| 経路 | 書く行の組 | 組を並べているもの |
| --- | --- | --- |
| `ShiftRequestService#approve`（CHANGE） | shifts, shift_requests | shifts の明示ロック（先に取る） |
| `CastInvitationAcceptanceService#acceptAs*` | casts, invitations, users | casts の明示ロック（先に取る） |
| `AttendanceService#correct` | attendances（＋ shifts を押さえるだけ） | shifts の明示ロック（先に取る） |

いずれも組の順序は先に取る明示ロックで決まっており、(i) で洗った順と同じである。残りの経路
（`cancel` / `decline` / `changePublication` / `CastService#update` / `ShiftService#update`）は既存行を
1 つしか書かないので、それだけでは順序を作らない。**環なし。**

## 残す窓

**窓①: 店舗削除 × 実績の記録・訂正。** 記録と訂正はキャスト・シフトを押さえてから実績（訂正履歴）行を
建て、その INSERT が店舗行に key share を要求する。一方で店舗削除は店舗行を押さえてから配下へ連鎖する
ので、向きが逆で形の上では環である。

同じ形が招待受諾にもあった（身分の所属店舗 `t_user_stores` の INSERT が店舗行を要求する）。**そちらは
閉じた** — 受諾は既に store モジュールを参照しており、配下を押さえる前に店舗行を
`FOR KEY SHARE` で押さえるだけで揃う（`StoreRepository#lockAgainstDeletion`）。準備中の店舗に
キャストを招くのは通常の段取りなので、その最中に店舗を消す並びは十分起こりうる。

実績側を閉じないのは現実性が桁違いに低いためである。店舗削除は準備中かつ記録ゼロが条件で、営業して
いない店舗に当日実績は建たない — 成立には「記録ゼロと判定された店舗へ、判定と DELETE の間に実績が
建つ」が要る。加えて shift モジュールから店舗表を押さえる経路を新たに通し、記録・訂正のたびに問い合わせを
1 本増やすことになる。**代価に見合わないと判断して残す。** 当たった場合の結末は、双方が業務上そもそも
両立しない操作であるところ、片方が deadlock（500）を受け取ることに留まる。

**窓②: 変更申請の提出が対象シフトを押さえない。** 権威的な判定は承認側（ロック内）にあり、提出面は
助言的である。押さえても「承認できない申請が inbox に残る」窓は閉じない — 実績は提出が成功した後の
任意の時点で記録されうるからで、それが #732 で三面（提出・承認・承認可否導出）を要求した理由でもある。
押さえない方を残す。

**窓③: 店舗削除の前置判定と DELETE の間。** 判定は行を押さえずに読む。店舗行を押さえてから判定し直せば
閉じるが、窓①の環は消えない（向きの問題であって、押さえる時点の問題ではない）。準備中かつ記録ゼロの
店舗に並行して記録が建つ想定が現実的でないため、閉じずに残す。

## Consequences

- 招待受諾は店舗行 → 档案（キャスト行）→ 招待行の順に押さえる。档案が招待の後だったためキャスト削除
  （キャスト行 → 招待行へ CASCADE）と環になり、店舗行を取っていなかったため店舗削除とも環になっていた。
  どちらも統合テストで順序そのものを固定する。
- 承認は申請行を書く前に対象シフトを押さえる。この順序は「申請行の書き込みが取引の終わりまで遅れる」
  ことでしか保たれておらず、間に申請行を触る問い合わせが挟まれば Hibernate が先に流して黙って逆転する。
  実装は動かさず、順序を統合テストで固定した。
- 順序を直したときの固定は、**「待つかどうか」では書けない**。逆順の実装でも外部キー検査で結局待つ
  ためである。押さえた行の隣を `FOR UPDATE NOWAIT` で試し、`55P03`（lock_not_available）で落ちるか否かで
  分ける（`ShiftAttendanceGuardIT`・`PlatformCastInvitationAcceptanceIT` が先例）。
- 5 表に外部キーを足す・削除規則を変えるときは、この表の該当行を書き直す。特に **NO ACTION から CASCADE
  へ変える**と、その表が上流の削除の到達先に加わり、下流で明示ロックを取る経路と新しい対ができる。
  新しく `@Lock(PESSIMISTIC_WRITE)` の読み口を足すときも同じで、「対ごとの洗い出し」の 3 群を引き直す。
- 一意索引を**部分**にすると、そのキー列を変える UPDATE は FOR UPDATE ではなく FOR NO KEY UPDATE に
  落ちる（事実 3）。同時実行の強さが変わるので、部分化は一意性の話だけでは決められない。
