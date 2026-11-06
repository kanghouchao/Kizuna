import { storePath } from '@/shared/lib';

/** 統合画面の URL。どちらが残るかを引数名で名指し、並びに頼らない。 */
export function customerMergeHref(survivingId: string, mergedId: string): string {
  const query = new URLSearchParams({ surviving: survivingId, merged: mergedId });
  return `${storePath('/customers/merge')}?${query}`;
}
