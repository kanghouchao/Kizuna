import { useEffect, useRef, useState } from 'react';
import { useStoreContext } from '@/entities/user';
import { Review, ReviewCommand, ReviewWriteResponse, reviewApi } from '@/entities/content/review';
import {
  getApiErrorMessage,
  isConflict,
  isNotFound,
  memoryOperationStore,
  useOperation,
} from '@/shared/lib';
import { notify } from '@/shared/notify';
import { accessError } from '../ui/reviewErrors';

/**
 * 口コミの書込み。結果不明の操作はタブ内のメモリに（操作名・本人・店舗）ごとに一つ残るので、
 * 一覧・詳細・受付・訂正のどの画面からも同じ要求で結果を確認できる。
 */
export function useReviewWrite(onDone: (review: Review) => void) {
  const storeId = useStoreContext().currentStoreId;
  const operation = useOperation('reviews', {
    storeId,
    store: memoryOperationStore,
    send: reviewApi.write,
  });
  const pending = operation.pending;
  const [denied, setDenied] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  const [missing, setMissing] = useState(false);
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const run = async (
    command: ReviewCommand,
    send: () => Promise<{ value: ReviewWriteResponse } | null>
  ) => {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    try {
      const result = await send();
      if (!result || !active.current) return;
      setDenied(null);
      setConflict(false);
      setMissing(false);
      notify.success('口コミの操作を記録しました');
      onDone(result.value.review);
    } catch (error) {
      if (!active.current) return;
      if (isConflict(error)) setConflict(true);
      let deniedReason = accessError(error);
      if (isNotFound(error)) {
        // 受付の 404 は関連受注の不在。訂正は元の口コミと関連受注のどちらが無いかを照会で分ける
        let reviewMissing = command.kind !== 'CREATE';
        if (command.kind === 'CORRECT') {
          try {
            await reviewApi.get(command.id);
            reviewMissing = false;
          } catch (lookupError) {
            reviewMissing = isNotFound(lookupError);
            deniedReason = accessError(lookupError);
          }
          if (!active.current) return;
        }
        if (reviewMissing) setMissing(true);
      }
      if (deniedReason !== null) setDenied(deniedReason);
      notify.error(getApiErrorMessage(error, '口コミの操作結果が確認できません'));
    } finally {
      lock.current = false;
      if (active.current) setBusy(false);
    }
  };
  return {
    pending,
    uncertain: pending?.phase === 'unknown',
    /** 送信中か、結果不明の操作が残っている間は新しい操作を送らない。 */
    blocked: busy || pending !== null,
    busy,
    denied,
    conflict,
    missing,
    submit: (command: ReviewCommand) => run(command, () => operation.start(command)),
    replay: () => (pending ? run(pending.payload, operation.replay) : Promise.resolve()),
    clearConflict: () => setConflict(false),
  };
}
