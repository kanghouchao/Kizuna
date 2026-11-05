import type { RoleRef, StoreScopeType } from '@/entities/user';

export interface Grants {
  role_ids: number[];
  storeSet: { storeScopeType: StoreScopeType; storeIds: number[] };
}

/** 応答の授権をフォームの形へ。 */
export function grantsOf(holder: {
  roles?: RoleRef[];
  store_scope_type?: StoreScopeType;
  store_ids?: number[];
}): Grants {
  return {
    role_ids: (holder.roles ?? []).flatMap(role => (role.id === undefined ? [] : [role.id])),
    // 範囲の欠落を全店舗にすると保存だけで授与範囲が拡大するため、個別店舗へ倒す。
    storeSet: {
      storeScopeType: holder.store_scope_type ?? 'SPECIFIC_STORES',
      storeIds: holder.store_ids ?? [],
    },
  };
}

/** フォームの授権を API の形へ戻す。全店舗のときに個別店舗の残りを送らない。 */
export function toGrantRequest({ role_ids, storeSet }: Grants) {
  return {
    role_ids,
    store_scope_type: storeSet.storeScopeType,
    store_ids: storeSet.storeScopeType === 'ALL_STORES' ? [] : storeSet.storeIds,
  };
}
