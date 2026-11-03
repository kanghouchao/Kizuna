'use client';

import { useRef, useState } from 'react';
import { notify } from '@/shared/notify';
import { getApiErrorMessage, isConflict, isNotFound } from '../apiError';

/** 書き込みの結末。表示（案内・ダイアログ・行の取り直し）は呼び出し側がこれで選ぶ。 */
export type WriteOutcome = 'success' | 'conflict' | 'notFound' | 'failed' | 'refused';

interface WriteMessages<R> {
  success: string;
  /** サーバが理由を返さなかったときの文言 */
  failure: string;
  /** 成功応答の反映（setData・行の差し替え・遷移等） */
  onSuccess?: (result: R) => void;
}

/**
 * version を往復する書き込みのライフサイクル。409 は「表示中の対象が古いかもしれない」合図で、
 * 競合フラグを立て、取り直す（reload）まで次の書き込みを送らない。409 は業務上の競合でも返り
 * 応答では区別できないが、取り直しはどちらでも安全なので区別しない。404 は対象が消えたので即座に
 * 取り直す。useOperation の 409（受領内容の不一致）は確定した結果で取り直さない。別物として扱う。
 */
export function useVersionedWrite(reloadTarget: () => void | Promise<void>) {
  // state だけでは同じ描画内の二度目の呼び出しを止められないため、送信中は ref でも持つ
  const inFlight = useRef(false);
  const conflictRef = useRef(false);
  const [busy, setBusy] = useState(false);
  const [conflict, setConflict] = useState(false);

  const markConflict = (value: boolean) => {
    conflictRef.current = value;
    setConflict(value);
  };

  const write = async <R>(
    send: () => Promise<R>,
    { success, failure, onSuccess }: WriteMessages<R>
  ): Promise<WriteOutcome> => {
    if (inFlight.current || conflictRef.current) return 'refused';
    inFlight.current = true;
    setBusy(true);
    let result: R;
    try {
      result = await send();
    } catch (error) {
      notify.error(getApiErrorMessage(error, failure));
      if (isConflict(error)) {
        markConflict(true);
        return 'conflict';
      }
      if (isNotFound(error)) {
        // 取り直しが終わるまで送信中のまま。終わる前に次を受けると古い版で送る
        await reloadTarget();
        return 'notFound';
      }
      return 'failed';
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
    // 反映は catch の外で行う。反映側の例外を書き込みの失敗として報告しない（サーバでは確定している）
    notify.success(success);
    onSuccess?.(result);
    return 'success';
  };

  /** 取り直してから競合を畳む。取り直しはすべてこれを通す。畳むのが先だと取得中に古い版で送れる。 */
  const reload = async () => {
    try {
      await reloadTarget();
    } finally {
      markConflict(false);
    }
  };

  return { busy, conflict, write, reload };
}
