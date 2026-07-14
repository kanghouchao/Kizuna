import { PlatformStore, PlatformStoreScopeType } from '@/entities/user';

/** 担当店舗の表示文字列（「全店舗」または店舗名をカンマ区切り、#325 D6）。 */
export function storeSetLabel(
  storeScopeType: PlatformStoreScopeType,
  storeIds: number[],
  stores: PlatformStore[]
): string {
  if (storeScopeType === 'ALL_STORES') return '全店舗';
  const names = stores.filter(store => storeIds.includes(store.id)).map(store => store.name);
  return names.length > 0 ? names.join('・') : '未選択';
}
