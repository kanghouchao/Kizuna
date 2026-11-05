import type { StaffGrants } from './StaffForm';

/** フォームの授権を API の形へ戻す。全店舗のときに個別店舗の残りを送らない。 */
export function toGrantRequest({ role_ids, storeSet }: StaffGrants) {
  return {
    role_ids,
    store_scope_type: storeSet.storeScopeType,
    store_ids: storeSet.storeScopeType === 'ALL_STORES' ? [] : storeSet.storeIds,
  };
}
