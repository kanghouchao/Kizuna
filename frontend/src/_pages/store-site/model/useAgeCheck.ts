'use client';

import { useCallback, useEffect, useState } from 'react';

const STORAGE_KEY = 'kizuna_age_verified';

/** loading は同意の記録を読む前。記録はブラウザにあり、サーバの描画では分からない。 */
export type AgeCheckPhase = 'loading' | 'gate' | 'declined' | 'verified';

/**
 * 年齢確認の状態。同意は端末に記録して次の訪問から聞かず、拒否は記録しないので再訪でまた聞く。
 * 画面（全画面の見た目と出入りの演出）は各テンプレートが描く。
 */
export function useAgeCheck() {
  const [phase, setPhase] = useState<AgeCheckPhase>('loading');

  useEffect(() => {
    setPhase(localStorage.getItem(STORAGE_KEY) === 'true' ? 'verified' : 'gate');
  }, []);

  const accept = useCallback(() => {
    localStorage.setItem(STORAGE_KEY, 'true');
    setPhase('verified');
  }, []);

  const decline = useCallback(() => setPhase('declined'), []);

  const leave = useCallback(() => window.close(), []);

  return { phase, accept, decline, leave };
}
