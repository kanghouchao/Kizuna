import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useStoreContext } from '@/entities/user';
import { surveyApi, SurveyCommand, Revision, WriteResponse } from '@/entities/survey';
import {
  getApiErrorMessage,
  isBadRequest,
  isConflict,
  isNotFound,
  memoryOperationStore,
  useOperation,
} from '@/shared/lib';
import { Button } from '@/shared/ui';
import { notify } from '@/shared/notify';
import { answerHref, revisionHref, surveyListHref } from '../lib/surveyHref';
import { SurveyEditor, Editor } from './SurveyEditor';
import { ReasonTask, SurveyReasonDialog } from './SurveyReasonDialog';
import { accessError } from './surveyUi';

export type SurveyTask = Editor | ReasonTask;

const isReasonTask = (task: SurveyTask): task is ReasonTask =>
  task.kind === 'OPEN' || task.kind === 'CLOSE' || task.kind === 'WITHDRAW';

/**
 * アンケートの書き込みの置き場。操作名を画面ごとに分けないので、結果不明の操作はどのアンケート画面に
 * 移っても再確認の導線が出る。成功したら結果の版・回答の画面へ移り、今の画面なら読み直す。
 */
export function useSurveyWorkspace(here: string) {
  const router = useRouter();
  const [epoch, setEpoch] = useState(0);
  const [task, setTask] = useState<SurveyTask | null>(null);
  const [taskKey, setTaskKey] = useState(0);
  const [open, setOpen] = useState(false);
  const operation = useOperation('surveys', {
    storeId: useStoreContext().currentStoreId,
    store: memoryOperationStore,
    send: surveyApi.write,
  });
  const pending = operation.pending;
  const uncertain = pending?.phase === 'unknown';
  const [conflict, setConflict] = useState(false);
  const [missing, setMissing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [refreshError, setRefreshError] = useState<string | null>(null);
  const [revisionCandidate, setRevisionCandidate] = useState<{
    revision: Revision;
    draftId?: string;
  } | null>(null);
  const [denied, setDenied] = useState<string | null>(null);
  const lock = useRef(false),
    active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  const deny = (message: string) => {
    if (!active.current) return;
    setDenied(message);
    setTask(null);
    setOpen(false);
  };
  const run = async (send: () => Promise<{ value: WriteResponse } | null>) => {
    if (lock.current || denied) return;
    lock.current = true;
    setBusy(true);
    try {
      const done = await send();
      if (!done || !active.current) return;
      const result = done.value;
      setConflict(false);
      setMissing(false);
      setTask(null);
      setOpen(false);
      notify.success('アンケートの操作を記録しました');
      const target =
        'answer' in result
          ? answerHref(result.answer.id)
          : revisionHref(result.revision.survey_id, result.revision.id);
      if (target === here) setEpoch(n => n + 1);
      else router.push(target);
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
  const submit = (command: SurveyCommand) => run(() => operation.start(command));
  const refresh = async () => {
    if (!task || busy) return;
    setBusy(true);
    setRefreshError(null);
    let parentFound = false;
    try {
      let updated: SurveyTask = task;
      if (task.kind === 'REVISE') {
        const survey = await surveyApi.survey(task.revision.survey_id);
        parentFound = true;
        const latest = await surveyApi.revision(survey.id, survey.latest_revision_id);
        if (!active.current) return;
        setRevisionCandidate({ revision: latest, draftId: survey.draft_revision_id });
        setEpoch(n => n + 1);
        return;
      }
      if ('answer' in task) {
        const answer = await surveyApi.answer(task.answer.id);
        updated =
          task.kind === 'CORRECT'
            ? {
                ...task,
                answer,
                revision: await surveyApi.revision(answer.survey_id, answer.revision_id),
              }
            : { ...task, answer };
      } else if ('revision' in task) {
        updated = {
          ...task,
          revision: await surveyApi.revision(task.revision.survey_id, task.revision.id),
        };
      }
      if (!active.current) return;
      setTask(updated);
      setEpoch(n => n + 1);
      setConflict(false);
      setMissing(false);
    } catch (error) {
      if (!active.current) return;
      const denial = accessError(error);
      if (denial) deny(denial);
      else if (isNotFound(error) && !parentFound) {
        setTask(null);
        setOpen(false);
        setMissing(false);
        setConflict(false);
        router.replace(surveyListHref);
      } else
        setRefreshError(
          getApiErrorMessage(error, '最新状態を確認できません。入力は保持しています')
        );
    } finally {
      if (active.current) setBusy(false);
    }
  };
  const edit = (value: SurveyTask) => {
    setRefreshError(null);
    setRevisionCandidate(null);
    setTask(value);
    setTaskKey(n => n + 1);
    setOpen(true);
    setConflict(false);
    setMissing(false);
  };
  const disabled = busy || pending !== null;
  const allowed =
    !task ||
    task.kind === 'CREATE' ||
    task.kind === 'REVISE' ||
    (task.kind === 'CORRECT' && !task.answer.superseded_by_id) ||
    (task.kind === 'WITHDRAW' && task.answer.status === 'ACTIVE') ||
    (task.kind === 'REPLACE' && task.revision.status === 'DRAFT') ||
    (task.kind === 'OPEN' && task.revision.status === 'DRAFT') ||
    (task.kind === 'CLOSE' && task.revision.status !== 'CLOSED') ||
    (task.kind === 'RECEIVE' && task.revision.status === 'OPEN');
  const feedback = (
    <>
      {refreshError && <p role="alert">{refreshError}</p>}
      {revisionCandidate && task?.kind === 'REVISE' && (
        <div role="status" className="rounded-lg border p-4 space-y-3">
          <p>
            最新版は版 {revisionCandidate.revision.revision_number}{' '}
            です。入力した改版案は保持しています。
          </p>
          {revisionCandidate.draftId ? (
            <p>未完了の下書きがあります。設問版の一覧でその下書きを確認してください。</p>
          ) : (
            <Button
              disabled={busy}
              variant="outline"
              onClick={() => {
                setTask({ ...task, revision: revisionCandidate.revision });
                setRevisionCandidate(null);
                setConflict(false);
                setMissing(false);
              }}
            >
              この最新版を改版元にする
            </Button>
          )}
        </div>
      )}
      {uncertain && (
        <div role="alert" className="rounded-lg border p-4 space-y-3">
          <p>結果が確認できません。同じ要求で結果を確認するまで、新しい操作はできません。</p>
          <Button variant="outline" disabled={busy} onClick={() => void run(operation.replay)}>
            同じ要求で結果を確認
          </Button>
        </div>
      )}
      {(conflict || missing) && (
        <div role="alert" className="rounded-lg border p-4 space-y-3">
          <p>
            {missing
              ? '対象が見つかりません。入力は保持しています。'
              : '状態が更新されています。入力を保持したまま最新状態を確認してください。'}
          </p>
          <Button variant="outline" disabled={busy} onClick={() => void refresh()}>
            最新状態を確認
          </Button>
        </div>
      )}
      {!allowed && (
        <p role="alert">
          現在の状態ではこの操作を実行できません。入力内容を確認し、対象の版・回答から操作を選び直してください。
        </p>
      )}
    </>
  );
  const dialogDisabled = disabled || conflict || missing || revisionCandidate !== null || !allowed;
  return {
    epoch,
    edit,
    deny,
    denied,
    disabled,
    /** 入力のダイアログを閉じている間に画面へ出す、結果の確認と入力への戻り口。 */
    status: !open && (
      <>
        {feedback}
        {task && (
          <Button variant="outline" onClick={() => setOpen(true)}>
            入力画面へ戻る
          </Button>
        )}
      </>
    ),
    dialog:
      task &&
      (isReasonTask(task) ? (
        <SurveyReasonDialog
          key={taskKey}
          task={task}
          open={open}
          onOpenChange={setOpen}
          disabled={dialogDisabled}
          onSubmit={submit}
          feedback={feedback}
        />
      ) : (
        <SurveyEditor
          key={taskKey}
          editor={task}
          open={open}
          onOpenChange={setOpen}
          disabled={dialogDisabled}
          onSubmit={submit}
          feedback={feedback}
        />
      )),
  };
}
