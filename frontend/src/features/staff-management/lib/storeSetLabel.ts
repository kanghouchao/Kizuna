import { AuthorizedStore } from '@/entities/user';

/** 担当店舗集合が空のときの表示。空は全店舗を意味しない。 */
export const EMPTY_STORE_SET_LABEL = '担当店舗なし';

/** 担当店舗の表示文字列（店舗名を「・」区切り。空の集合は「担当店舗なし」）。 */
export function storeSetLabel(storeIds: number[] | undefined, stores: AuthorizedStore[]): string {
  const selected = storeIds ?? [];
  if (selected.length === 0) return EMPTY_STORE_SET_LABEL;
  const names = stores
    .filter(store => store.id !== undefined && selected.includes(store.id))
    // name も non_null 方針で欠けうるため、欠けた場合は id を代替表示する
    .map(store => store.name ?? String(store.id));
  // 目録で名前を引けない店舗だけでも、担当が無いとは述べず件数で示す
  return names.length > 0 ? names.join('・') : `${selected.length} 店舗`;
}
