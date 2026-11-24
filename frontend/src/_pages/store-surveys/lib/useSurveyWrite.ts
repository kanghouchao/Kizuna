import { useState } from 'react';
import { useStoreContext } from '@/entities/user';
import { SurveyCommand, WriteResponse, surveyApi } from '@/entities/survey';
import {
  isBadRequest,
  isConflict,
  isNotFound,
  memoryOperationStore,
  useOperation,
} from '@/shared/lib';
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
  const [denied, setDenied] = useState<string | null>(null);
  const options = {
    success: 'アンケートの操作を記録しました',
    failure: '操作結果が確認できません',
    onSuccess: (result: WriteResponse) => {
      setConflict(false);
      setMissing(false);
      onDone(result);
    },
    onFailure: (error: unknown) => {
      const denial = accessError(error);
      if (denial) setDenied(denial);
      else if (isBadRequest(error) || isConflict(error) || isNotFound(error)) {
        setConflict(isConflict(error));
        setMissing(isNotFound(error));
      }
    },
  };
  return {
    pending,
    uncertain: pending?.phase === 'unknown',
    busy: operation.busy,
    /** 送信中か、結果不明の操作が残っている間は新しい操作を送らない。 */
    blocked: operation.busy || pending !== null,
    denied,
    deny: setDenied,
    conflict,
    missing,
    /** 読み直しを終えたら 409・404 の案内を消す。 */
    settle: () => {
      setConflict(false);
      setMissing(false);
    },
    submit: async (command: SurveyCommand) => {
      if (!denied) await operation.start(command, options);
    },
    replay: async () => {
      if (!denied) await operation.replay(options);
    },
  };
}

export type SurveyWrite = ReturnType<typeof useSurveyWrite>;
