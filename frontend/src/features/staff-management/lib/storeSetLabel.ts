import { PlatformStore, PlatformStoreScopeType } from '@/entities/user';

/** 担当店舗の表示文字列（「全店舗」または店舗名をカンマ区切り）。 */
export function storeSetLabel(
  storeScopeType: PlatformStoreScopeType | undefined,
  storeIds: number[] | undefined,
  stores: PlatformStore[]
): string {
  if (storeScopeType === 'ALL_STORES') return '全店舗';
  const selected = storeIds ?? [];
  const names = stores
    .filter(store => store.id !== undefined && selected.includes(store.id))
    // name も non_null 方針で欠けうるため、欠けた場合は id を代替表示する
    .map(store => store.name ?? String(store.id));
  return names.length > 0 ? names.join('・') : '未選択';
}
