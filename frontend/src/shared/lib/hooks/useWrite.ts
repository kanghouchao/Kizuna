'use client';

import { useEffect, useRef, useTransition } from 'react';
import { notify } from '@/shared/notify';
import { getApiErrorMessage } from '../apiError';

interface WriteOptions<R> {
  /** 省くと成功の案内を出さない */
  success?: string;
  /**
   * サーバが理由を返さなかったときの文言。関数を渡すとエラーを受け取り、案内を任される。
   * 関数が promise を返したら、その完了まで busy が残る。
   */
  failure: string | ((error: unknown) => unknown);
  /** 成功応答の反映（遷移・取り直し・setData 等）。promise を返したら、その完了まで busy が残る。 */
  onSuccess?: (result: R) => unknown;
}

/**
 * 書き込みのライフサイクル。busy は応答で戻さず、onSuccess が始めた遷移の反映、または返した promise の
 * 完了まで残す。応答の時点で戻すと、画面が外れるまでの間に同じ要求をもう一度送れる。
 * onSuccess の中で同期に出した更新は transition になり、onSuccess が返した promise の完了まで描かれない。
 */
export function useWrite() {
  // isPending は次の描画まで変わらず、同じフレームの二度目の呼び出しを止められないため、ref でも持つ
  const inFlight = useRef(false);
  const mounted = useRef(false);
  const [busy, startTransition] = useTransition();

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const run = async <R>(
    send: () => Promise<R>,
    { success, failure, onSuccess }: WriteOptions<R>
  ): Promise<'success' | 'failed'> => {
    let result: R;
    try {
      result = await send();
    } catch (error) {
      if (typeof failure === 'function') await failure(error);
      else notify.error(getApiErrorMessage(error, failure));
      return 'failed';
    }
    // 反映は catch の外で行う。反映側の例外を書き込みの失敗として報告しない（サーバでは確定している）
    if (success) notify.success(success);
    // 画面が外れた後は反映しない。離れた利用者を遷移で連れ戻さない
    if (!onSuccess || !mounted.current) return 'success';
    // await の後の更新は transition に入らないので、包み直す
    await new Promise<void>((resolve, reject) => {
      startTransition(async () => {
        try {
          await onSuccess(result);
          resolve();
        } catch (error) {
          reject(error);
        }
      });
    });
    return 'success';
  };

  /** 送信中の呼び出しは送らずに refused を返す。反映側の例外は返す promise の reject で伝える。 */
  const write = <R>(
    send: () => Promise<R>,
    options: WriteOptions<R>
  ): Promise<'success' | 'failed' | 'refused'> => {
    if (inFlight.current) return Promise.resolve('refused');
    inFlight.current = true;
    return new Promise((resolve, reject) => {
      // 渡した関数が reject すると React が描画中に投げ直し、画面がエラー境界に替わる。必ず中で受ける
      startTransition(async () => {
        try {
          resolve(await run(send, options));
        } catch (error) {
          reject(error);
        } finally {
          inFlight.current = false;
        }
      });
    });
  };

  return { busy, write };
}
