'use client';

import { startTransition, useRef, useState } from 'react';
import { isConflict, isForbidden, isNotFound } from '../apiError';
import { announceFailure, useWrite } from './useWrite';

/** 失敗の種類。画面は状態コードを自分で見分けず、これで案内と表示を選ぶ。 */
export type WriteFailure = 'conflict' | 'notFound' | 'forbidden' | 'failed';

/** 書き込みの結末。表示（案内・ダイアログ・行の取り直し）は呼び出し側がこれで選ぶ。 */
export type WriteOutcome = 'success' | WriteFailure | 'refused';

interface VersionedWriteOptions<R> {
  /** 省くと成功の案内を出さない */
  success?: string;
  /** サーバが理由を返さなかったときの文言。関数を渡すとエラーと種類を受け取り、案内を任される。 */
  failure: string | ((error: unknown, kind: WriteFailure) => unknown);
  /** 成功応答の反映（setData・行の差し替え・遷移等）。busy の残り方は useWrite に従う */
  onSuccess?: (result: R) => unknown;
  /** 競合の標識・404 の取り直しの後に呼ぶ、失敗の反映。busy の残り方は useWrite に従う */
  onFailure?: (error: unknown, kind: WriteFailure) => unknown;
}

const failureKind = (error: unknown): WriteFailure => {
  if (isConflict(error)) return 'conflict';
  if (isNotFound(error)) return 'notFound';
  return isForbidden(error) ? 'forbidden' : 'failed';
};

/**
 * version を往復する書き込みのライフサイクル。409 は「表示中の対象が古いかもしれない」合図で、
 * 競合フラグを立て、取り直す（reload）まで次の書き込みを送らない。409 は業務上の競合でも返り
 * 応答では区別できないが、取り直しはどちらでも安全なので区別しない。404 は対象が消えたので即座に
 * 取り直す。useOperation の 409（受領内容の不一致）は確定した結果で取り直さない。別物として扱う。
 */
export function useVersionedWrite<T = void>(reloadTarget: () => T | Promise<T>) {
  const conflictRef = useRef(false);
  const latestReload = useRef(0);
  const base = useWrite();
  const [conflict, setConflict] = useState(false);

  const markConflict = (value: boolean) => {
    conflictRef.current = value;
    setConflict(value);
  };

  const write = async <R>(
    send: () => Promise<R>,
    { success, failure, onSuccess, onFailure }: VersionedWriteOptions<R>
  ): Promise<WriteOutcome> => {
    if (conflictRef.current) return 'refused';
    let failedAs: WriteFailure = 'failed';
    const outcome = await base.write(send, {
      success,
      failure: error => {
        failedAs = failureKind(error);
        announceFailure(
          typeof failure === 'function' ? cause => failure(cause, failedAs) : failure,
          error
        );
      },
      onSuccess,
      onFailure: async error => {
        if (failedAs === 'conflict') {
          // busy が戻るのと同じ描画で競合を見せる。先に見せると、押しても送信中として捨てられる案内が出る
          startTransition(() => markConflict(true));
        } else if (failedAs === 'notFound') {
          // 取り直しが終わるまで送信中のまま。終わる前に次を受けると古い版で送る
          await reloadTarget();
        }
        await onFailure?.(error, failedAs);
      },
    });
    return outcome === 'failed' ? failedAs : outcome;
  };

  /**
   * 取り直してから競合を畳む。取り直しはすべてこれを通す。畳むのが先だと取得中に古い版で送れる。
   * 重なったときは最後に始めた取り直しの完了で畳む。先のものは無効化されて反映されず、終わる順も決まらない。
   * 409 の直後に取り直す画面は、反映の中ではなく write の結末を受けた後で呼ぶ。反映の中は transition で、
   * 取得中の表示が描かれない。
   */
  const reload = async (): Promise<T> => {
    const own = ++latestReload.current;
    try {
      return await reloadTarget();
    } finally {
      if (own === latestReload.current) markConflict(false);
    }
  };

  return { busy: base.busy, conflict, write, reload };
}
