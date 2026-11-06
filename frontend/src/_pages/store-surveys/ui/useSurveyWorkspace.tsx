import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { surveyApi } from '@/entities/survey';
import { Button } from '@/shared/ui';
import { surveyListHref, writtenHref } from '../lib/surveyHref';
import { useSurveyRefresh } from '../lib/useSurveyRefresh';
import { useSurveyWrite } from '../lib/useSurveyWrite';
import { ReasonTask, SurveyReasonDialog } from './SurveyReasonDialog';
import { SurveyWriteFeedback } from './SurveyWriteFeedback';

export type SurveyTask = ReasonTask;

/**
 * 一覧・版・回答の画面からダイアログで行う書き込みの置き場。成功したら結果の版・回答の画面へ移り、
 * 今の画面なら読み直す。
 */
export function useSurveyWorkspace(here: string) {
  const router = useRouter();
  const [epoch, setEpoch] = useState(0);
  const [task, setTask] = useState<SurveyTask | null>(null);
  const [taskKey, setTaskKey] = useState(0);
  const [open, setOpen] = useState(false);
  const write = useSurveyWrite(result => {
    setTask(null);
    setOpen(false);
    const target = writtenHref(result);
    if (target === here) setEpoch(n => n + 1);
    else router.push(target);
  });
  const refresher = useSurveyRefresh(write, () => {
    setTask(null);
    setOpen(false);
    router.replace(surveyListHref);
  });
  const refreshing = refresher.refreshing;
  const busy = write.busy || refreshing;
  const refresh = (current: SurveyTask) =>
    refresher.refresh(async () => {
      let updated: SurveyTask;
      if ('answer' in current) {
        updated = { ...current, answer: await surveyApi.answer(current.answer.id) };
      } else {
        updated = {
          ...current,
          revision: await surveyApi.revision(current.revision.survey_id, current.revision.id),
        };
      }
      setTask(updated);
      setEpoch(n => n + 1);
    });
  const edit = (value: SurveyTask) => {
    refresher.clearError();
    setTask(value);
    setTaskKey(n => n + 1);
    setOpen(true);
    write.settle();
  };
  const allowed =
    !task ||
    (task.kind === 'WITHDRAW' && task.answer.status === 'ACTIVE') ||
    (task.kind === 'OPEN' && task.revision.status === 'DRAFT') ||
    (task.kind === 'CLOSE' && task.revision.status !== 'CLOSED');
  const feedback = (
    <SurveyWriteFeedback
      write={write}
      busy={busy}
      onRefresh={() => task && void refresh(task)}
      refreshError={refresher.refreshError}
    >
      {!allowed && (
        <p role="alert">
          現在の状態ではこの操作を実行できません。入力内容を確認し、対象の版・回答から操作を選び直してください。
        </p>
      )}
    </SurveyWriteFeedback>
  );
  const dialogDisabled = refresher.locked || !allowed;
  return {
    epoch,
    edit,
    deny: write.deny,
    denied: write.denied,
    disabled: write.blocked || refreshing,
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
    dialog: task && (
      <SurveyReasonDialog
        key={taskKey}
        task={task}
        open={open}
        onOpenChange={setOpen}
        disabled={dialogDisabled}
        onSubmit={write.submit}
        feedback={feedback}
      />
    ),
  };
}
