import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useResource } from '@/shared/lib';
import { RegionError } from '@/shared/ui';
import { surveyListHref, writtenHref } from '../lib/surveyHref';
import { useSurveyRefresh } from '../lib/useSurveyRefresh';
import { useSurveyWrite } from '../lib/useSurveyWrite';
import { AnswerTarget, SurveyAnswerForm } from './SurveyAnswerForm';
import { SurveyEditFrame, editCard } from './SurveyEditFrame';
import { SurveyDenied } from './SurveyGate';
import { SurveyWriteFeedback } from './SurveyWriteFeedback';
import { useGuardedRead } from './surveyUi';

/**
 * 回答の受付・訂正の画面。読み直しは入力を残したまま対象だけを差し替え、操作できない状態になれば
 * 送信を止める。開いた時点で操作できない対象でも、結果不明の操作の再確認は出す。
 */
export function SurveyAnswerIntake({
  title,
  back,
  load,
  unavailable,
  notFoundMessage,
}: {
  title: string;
  back: { href: string; label: string };
  load: () => Promise<AnswerTarget>;
  /** この対象には入力を出せない理由。 */
  unavailable: (target: AnswerTarget) => string | null;
  notFoundMessage: string;
}) {
  const router = useRouter();
  const write = useSurveyWrite(result => router.replace(writtenHref(result)));
  const [gone, setGone] = useState(false);
  const refresher = useSurveyRefresh(write, () => setGone(true));
  const guard = useGuardedRead(write.deny);
  const detail = useResource(() => guard(load), []);
  const [latest, setLatest] = useState<AnswerTarget | null>(null);
  if (write.denied) return <SurveyDenied message={write.denied} />;
  const opened = detail.data;
  const current = latest ?? opened;
  const blocked = opened ? unavailable(opened) : null;
  const allowed = current !== null && unavailable(current) === null;
  return (
    <SurveyEditFrame
      title={title}
      back={back}
      feedback={
        <SurveyWriteFeedback
          write={write}
          busy={write.busy || refresher.refreshing}
          refreshError={refresher.refreshError}
          onRefresh={() => void refresher.refresh(async () => setLatest(await load()))}
        >
          {opened && !blocked && !allowed && (
            <p role="alert">
              現在の状態ではこの操作を実行できません。入力内容を確認し、対象の版・回答から操作を選び直してください。
            </p>
          )}
        </SurveyWriteFeedback>
      }
    >
      {gone || detail.failure === 'notFound' ? (
        <RegionError
          message={notFoundMessage}
          fallback={{ href: surveyListHref, label: '一覧へ戻る' }}
        />
      ) : detail.failure ? (
        <RegionError message={`${title}の対象を取得できませんでした`} onRetry={detail.reload} />
      ) : detail.isLoading || !opened || !current ? (
        <p>読み込み中...</p>
      ) : blocked ? (
        <RegionError message={blocked} fallback={back} />
      ) : (
        <section className={editCard}>
          <div className="space-y-1">
            <p className="font-semibold wrap-anywhere">{current.revision.title}</p>
            <p>
              スタッフによる記録。回答者の本人確認は行っていません。設問版{' '}
              {current.revision.revision_number} に記録します。
            </p>
          </div>
          <SurveyAnswerForm
            target={current}
            disabled={refresher.locked || !allowed}
            onSubmit={write.submit}
          />
        </section>
      )}
    </SurveyEditFrame>
  );
}
