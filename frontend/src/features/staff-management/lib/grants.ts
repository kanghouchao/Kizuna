import type { RoleRef } from '@/entities/user';

export interface Grants {
  role_ids: number[];
  store_ids: number[];
}

/** 応答の授権をフォームの形へ。欠けた店舗集合は空の集合（どの店舗も担当しない）として扱う。 */
export function grantsOf(holder: { roles?: RoleRef[]; store_ids?: number[] }): Grants {
  return {
    role_ids: (holder.roles ?? []).flatMap(role => (role.id === undefined ? [] : [role.id])),
    store_ids: holder.store_ids ?? [],
  };
}

/** フォームの授権を API の形へ戻す。更新端点は未知のキーを 400 で拒むため、授権以外の欄を混ぜない。 */
export function toGrantRequest({ role_ids, store_ids }: Grants) {
  return { role_ids, store_ids };
}
