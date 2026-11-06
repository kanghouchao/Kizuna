import { useEffect, useRef, useState } from 'react';
import { useStoreContext } from '@/entities/user';
import { SurveyCommand, WriteResponse, surveyApi } from '@/entities/survey';
import {
  getApiErrorMessage,
  isBadRequest,
  isConflict,
  isNotFound,
  memoryOperationStore,
  useOperation,
} from '@/shared/lib';
import { notify } from '@/shared/notify';
import { accessError } from '../ui/surveyUi';

/**
 * アンケートの書き込み。操作名を画面ごとに分けないので、結果不明の操作はどのアンケート画面に
 * 移っても同じ要求で再確認できる。
 */
export function useSurveyWrite(onDone: (result: WriteResponse) => void) {
  const operation = useOperation('surveys', {
    storeId: useStoreContext().currentStoreId,
    store: memoryOperationStore,
    send: surveyApi.write,
  });
  const pending = operation.pending;
  const [conflict, setConflict] = useState(false);
  const [missing, setMissing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [denied, setDenied] = useState<string | null>(null);
  const lock = useRef(false);
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const deny = (message: string) => {
    if (active.current) setDenied(message);
  };
  const run = async (send: () => Promise<{ value: WriteResponse } | null>) => {
    if (lock.current || denied) return;
    lock.current = true;
    setBusy(true);
    try {
      const done = await send();
      if (!done || !active.current) return;
      setConflict(false);
      setMissing(false);
      notify.success('アンケートの操作を記録しました');
      onDone(done.value);
    } catch (error) {
      if (!active.current) return;
      const denial = accessError(error);
      if (denial) deny(denial);
      else if (isBadRequest(error) || isConflict(error) || isNotFound(error)) {
        setConflict(isConflict(error));
        setMissing(isNotFound(error));
      }
      notify.error(getApiErrorMessage(error, '操作結果が確認できません'));
    } finally {
      lock.current = false;
      if (active.current) setBusy(false);
    }
  };
  return {
    pending,
    uncertain: pending?.phase === 'unknown',
    busy,
    /** 送信中か、結果不明の操作が残っている間は新しい操作を送らない。 */
    blocked: busy || pending !== null,
    denied,
    deny,
    conflict,
    missing,
    /** 読み直しを終えたら 409・404 の案内を消す。 */
    settle: () => {
      setConflict(false);
      setMissing(false);
    },
    submit: (command: SurveyCommand) => run(() => operation.start(command)),
    replay: () => run(operation.replay),
  };
}

export type SurveyWrite = ReturnType<typeof useSurveyWrite>;
