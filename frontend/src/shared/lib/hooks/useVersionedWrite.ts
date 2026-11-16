'use client';

import { useRef, useState } from 'react';
import { notify } from '@/shared/notify';
import { getApiErrorMessage, isConflict, isNotFound } from '../apiError';
import { useWrite } from './useWrite';

/** 書き込みの結末。表示（案内・ダイアログ・行の取り直し）は呼び出し側がこれで選ぶ。 */
export type WriteOutcome = 'success' | 'conflict' | 'notFound' | 'failed' | 'refused';

interface WriteMessages<R> {
  success: string;
  /** サーバが理由を返さなかったときの文言 */
  failure: string;
  /** 成功応答の反映（setData・行の差し替え・遷移等）。busy の残り方は useWrite に従う */
  onSuccess?: (result: R) => unknown;
}

/**
 * version を往復する書き込みのライフサイクル。409 は「表示中の対象が古いかもしれない」合図で、
 * 競合フラグを立て、取り直す（reload）まで次の書き込みを送らない。409 は業務上の競合でも返り
 * 応答では区別できないが、取り直しはどちらでも安全なので区別しない。404 は対象が消えたので即座に
 * 取り直す。useOperation の 409（受領内容の不一致）は確定した結果で取り直さない。別物として扱う。
 */
export function useVersionedWrite(reloadTarget: () => void | Promise<void>) {
  const conflictRef = useRef(false);
  const latestReload = useRef(0);
  const { busy, write: send } = useWrite();
  const [conflict, setConflict] = useState(false);

  const markConflict = (value: boolean) => {
    conflictRef.current = value;
    setConflict(value);
  };

  const write = async <R>(
    request: () => Promise<R>,
    { success, failure, onSuccess }: WriteMessages<R>
  ): Promise<WriteOutcome> => {
    if (conflictRef.current) return 'refused';
    let failed: WriteOutcome = 'failed';
    const outcome = await send(request, {
      success,
      failure: async error => {
        notify.error(getApiErrorMessage(error, failure));
        if (isConflict(error)) {
          markConflict(true);
          failed = 'conflict';
        } else if (isNotFound(error)) {
          failed = 'notFound';
          // 取り直しが終わるまで送信中のまま。終わる前に次を受けると古い版で送る
          await reloadTarget();
        }
      },
      onSuccess,
    });
    return outcome === 'failed' ? failed : outcome;
  };

  /**
   * 取り直してから競合を畳む。取り直しはすべてこれを通す。畳むのが先だと取得中に古い版で送れる。
   * 重なったときは最後に始めた取り直しの完了で畳む。先のものは無効化されて反映されず、終わる順も決まらない。
   */
  const reload = async () => {
    const own = ++latestReload.current;
    try {
      await reloadTarget();
    } finally {
      if (own === latestReload.current) markConflict(false);
    }
  };

  return { busy, conflict, write, reload };
}
