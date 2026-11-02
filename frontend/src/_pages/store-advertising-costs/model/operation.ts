import { useEffect, useRef } from 'react';
import { sessionOperationStore, useOperation, useOperationRevision } from '@/shared/lib';
import { advertisingApi, type Mutation } from '../api/advertising';

const operationName = (month: string) => `advertising:${month}`;

/** 広告費の書き込みは本人・店舗・対象月ごとに一つ。再読込を越えてタブ内に保持する。 */
export function useAdvertisingOperation(month: string) {
  return useOperation<Mutation, unknown>(operationName(month), {
    store: sessionOperationStore,
    send: (mutation, requestId, config) =>
      advertisingApi.mutate(month, mutation, requestId, config),
  });
}

/** 対象月の操作が始まるか終わるたびに読み直す。費用記録の画面を離れている間の完了も拾う。 */
export function useReloadAfterOperation(month: string, reload: () => void) {
  const revision = useOperationRevision(operationName(month));
  const seen = useRef(revision);
  useEffect(() => {
    if (seen.current === revision) return;
    seen.current = revision;
    reload();
  }, [revision, reload]);
}
