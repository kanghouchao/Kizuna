'use client';

import { useEffect, useRef } from 'react';

/**
 * 外から渡される条件が変わったら useListPage の search で 1 ページ目から取り直す。フックは適用済み条件を
 * 自分で持つので、渡し直すだけでは動かない。初回はフック自身の取得と重なるため飛ばす。
 */
export function useSearchOnCriteriaChange<C>(criteria: C, search: (criteria: C) => Promise<void>) {
  const appliedRef = useRef(criteria);
  useEffect(() => {
    if (appliedRef.current === criteria) {
      return;
    }
    appliedRef.current = criteria;
    void search(criteria);
  }, [criteria, search]);
}
