import { startTransition, useState } from 'react';
import { useStoreContext } from '@/entities/user';
import { Review, ReviewCommand, ReviewWriteResponse, reviewApi } from '@/entities/content/review';
import { isConflict, isNotFound, memoryOperationStore, useOperation } from '@/shared/lib';
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
  const options = (command: ReviewCommand) => ({
    success: '口コミの操作を記録しました',
    failure: '口コミの操作結果が確認できません',
    onSuccess: (result: ReviewWriteResponse) => {
      setDenied(null);
      setConflict(false);
      setMissing(false);
      onDone(result.review);
    },
    // 照会を返すので、照会が終わるまで busy が残る。照会の後の更新は transition の外なので包み直す
    onFailure: async (error: unknown) => {
      if (isConflict(error)) setConflict(true);
      let deniedReason = accessError(error);
      let reviewMissing = false;
      if (isNotFound(error)) {
        // 受付の 404 は関連受注の不在。訂正は元の口コミと関連受注のどちらが無いかを照会で分ける
        reviewMissing = command.kind !== 'CREATE';
        if (command.kind === 'CORRECT') {
          try {
            await reviewApi.get(command.id);
            reviewMissing = false;
          } catch (lookupError) {
            reviewMissing = isNotFound(lookupError);
            deniedReason = accessError(lookupError);
          }
        }
      }
      startTransition(() => {
        if (reviewMissing) setMissing(true);
        if (deniedReason !== null) setDenied(deniedReason);
      });
    },
  });
  return {
    pending,
    uncertain: pending?.phase === 'unknown',
    /** 送信中か、結果不明の操作が残っている間は新しい操作を送らない。 */
    blocked: operation.busy || pending !== null,
    busy: operation.busy,
    denied,
    conflict,
    missing,
    submit: async (command: ReviewCommand) => {
      await operation.start(command, options(command));
    },
    replay: async () => {
      // 失敗の後の照会に送った内容が要るので、送る前に取っておく
      if (pending) await operation.replay(options(pending.payload));
    },
    clearConflict: () => setConflict(false),
  };
}
