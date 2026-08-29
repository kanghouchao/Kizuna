// バックエンド API の JSON キーに一致（Jackson グローバル SNAKE_CASE）。
// 応答の任意性は Java 側の可空性が正本。wrapper 型のフィールドは
// default-property-inclusion: non_null によりキーごと応答から消えるため optional にする。

/** ポイント仕訳の種別。point/domain/PointEntryType.java に対応。 */
export type PointEntryType =
  | 'ORDER_GRANT'
  | 'BENEFIT_GRANT'
  | 'USE'
  | 'MANUAL_ADJUST'
  | 'CANCEL'
  | 'USE_CANCEL'
  | 'EXPIRE'
  | 'WITHDRAWAL_CLEAR';

/** 種別の日本語表示。会員に見せる語なので、台帳の内部語ではなく持ち主から見た出来事の名で呼ぶ。 */
export const POINT_ENTRY_TYPE_LABELS: Record<PointEntryType, string> = {
  ORDER_GRANT: '獲得',
  BENEFIT_GRANT: '特典',
  USE: '利用',
  MANUAL_ADJUST: '調整',
  CANCEL: '取消',
  USE_CANCEL: '利用取消',
  EXPIRE: '失効',
  WITHDRAWAL_CLEAR: '退会消去',
};

/** 会員本人のポイント残高。point/api/dto/MemberPointBalanceResponse.java に対応。 */
export interface MemberPointBalance {
  /** Java 側が primitive の long のため、キーは必ず応答に含まれる。 */
  balance: number;
}

/** 会員本人のポイント明細 1 行。point/api/dto/MemberPointEntryResponse.java に対応。 */
export interface MemberPointEntry {
  occurred_on?: string;
  /** 発生店舗名。失効のような系統イベントと削除済み店舗では応答から消える。 */
  store_name?: string;
  entry_type?: PointEntryType;
  /** Java 側が primitive の int のため、キーは必ず応答に含まれる。符号付き（加算は正、減算は負）。 */
  amount: number;
  /** 加算ロットの有効期限。期限なしと減算では応答から消える。 */
  expires_on?: string;
}
