import { useEffect, useRef, useState } from 'react';
import { getApiErrorMessage, isNotFound } from '@/shared/lib';
import { accessError } from '../ui/surveyUi';
import type { SurveyWrite } from './useSurveyWrite';

/**
 * 409・404 の後に入力を保ったまま対象を読み直す。対象が消えていたら onGone へ渡し、
 * 読み直せなかったときは入力を残して案内だけ出す。画面を離れた後に返った結果は捨てる。
 */
export function useSurveyRefresh(write: SurveyWrite, onGone: () => void) {
  const [refreshing, setRefreshing] = useState(false);
  const [refreshError, setRefreshError] = useState<string | null>(null);
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const refresh = async (
    load: () => Promise<void>,
    gone: (error: unknown) => boolean = isNotFound
  ) => {
    if (refreshing || write.busy) return;
    setRefreshing(true);
    setRefreshError(null);
    try {
      await load();
      if (active.current) write.settle();
    } catch (error) {
      if (!active.current) return;
      const denial = accessError(error);
      if (denial) write.deny(denial);
      else if (gone(error)) {
        write.settle();
        onGone();
      } else
        setRefreshError(
          getApiErrorMessage(error, '最新状態を確認できません。入力は保持しています')
        );
    } finally {
      if (active.current) setRefreshing(false);
    }
  };
  return {
    refreshing,
    refreshError,
    /** 送信中・読み直し中、または 409・404 の案内が出ている間は入力を送らない。 */
    locked: write.blocked || refreshing || write.conflict || write.missing,
    refresh,
    clearError: () => setRefreshError(null),
  };
}
