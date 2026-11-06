'use client';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { surveyApi } from '@/entities/survey';
import { useResource } from '@/shared/lib';
import { Button, RegionError } from '@/shared/ui';
import { answerHref, correctHref, revisionHref, surveyListHref } from '../lib/surveyHref';
import { SurveyCapabilities, SurveyDenied, SurveyGate } from './SurveyGate';
import { SurveyHistory } from './SurveyHistory';
import { answerLabels, time, useGuardedRead, viaLabels } from './surveyUi';
import { SurveyTask, useSurveyWorkspace } from './useSurveyWorkspace';

export function SurveyAnswerPage() {
  const { aid } = useParams<{ aid: string }>();
  return <SurveyGate target={aid}>{caps => <AnswerScreen aid={aid} {...caps} />}</SurveyGate>;
}

function AnswerScreen({ aid, ...caps }: SurveyCapabilities & { aid: string }) {
  const workspace = useSurveyWorkspace(answerHref(aid));
  if (workspace.denied) return <SurveyDenied message={workspace.denied} />;
  return (
    <div className="space-y-6">
      {workspace.status}
      <AnswerDetail
        key={workspace.epoch}
        aid={aid}
        edit={workspace.edit}
        deny={workspace.deny}
        disabled={workspace.disabled}
        {...caps}
      />
      {workspace.dialog}
    </div>
  );
}

function AnswerDetail({
  aid,
  canRecord,
  edit,
  deny,
  disabled,
}: SurveyCapabilities & {
  aid: string;
  edit: (task: SurveyTask) => void;
  deny: (message: string) => void;
  disabled: boolean;
}) {
  const guard = useGuardedRead(deny);
  const detail = useResource(
    () =>
      guard(async () => {
        const answer = await surveyApi.answer(aid);
        const revision = await surveyApi.revision(answer.survey_id, answer.revision_id);
        return { answer, revision };
      }),
    [aid]
  );
  if (detail.failure === 'notFound')
    return (
      <div role="alert" className="space-y-3">
        <p>回答が見つかりません</p>
        <Button variant="outline" render={<Link href={surveyListHref} />}>
          一覧へ戻る
        </Button>
      </div>
    );
  if (detail.failure)
    return <RegionError message="回答を取得できませんでした" onRetry={detail.reload} />;
  if (!detail.data || detail.isLoading) return <p>読み込み中...</p>;
  const { answer, revision } = detail.data;
  return (
    <div className="space-y-6">
      <Button
        variant="outline"
        render={<Link href={revisionHref(answer.survey_id, answer.revision_id)} />}
      >
        設問版へ戻る
      </Button>
      <h1 className="text-2xl font-bold">回答 #{answer.id}</h1>
      <p>スタッフによる記録。回答者の本人確認は行っていません。保持期限は未設定です。</p>
      <p>
        {answerLabels[answer.status]} ／ 設問版 {answer.revision_number} ／ 受領{' '}
        {time(answer.received_at)} ／ {viaLabels[answer.received_via]}
      </p>
      <p>
        記録者: {answer.recorded_by.display_name} ／ 記録 {time(answer.created_at)}
      </p>
      <div className="flex gap-3 flex-wrap">
        {canRecord && (
          <>
            {answer.status === 'ACTIVE' && (
              <Button
                variant="outline"
                disabled={disabled}
                onClick={() => edit({ kind: 'WITHDRAW', answer })}
              >
                回答を取り下げ
              </Button>
            )}
            {!answer.superseded_by_id && (
              <Button variant="outline" render={<Link href={correctHref(answer.id)} />}>
                回答を訂正
              </Button>
            )}
          </>
        )}
        {answer.supersedes_id && (
          <Button variant="outline" render={<Link href={answerHref(answer.supersedes_id)} />}>
            訂正元を表示
          </Button>
        )}
        {answer.superseded_by_id && (
          <Button variant="outline" render={<Link href={answerHref(answer.superseded_by_id)} />}>
            訂正先を表示
          </Button>
        )}
      </div>
      <dl className="space-y-6">
        {revision.questions.map(q => {
          const value = answer.answers.find(a => a.question_key === q.question_key);
          return (
            <div key={q.question_key} className="rounded-lg border p-4 space-y-2">
              <dt className="font-semibold whitespace-pre-wrap wrap-anywhere">{q.prompt}</dt>
              <dd className="whitespace-pre-wrap wrap-anywhere">
                {value
                  ? (value.text ??
                    q.options.find(o => o.option_key === value.option_key)?.label ??
                    '選択肢を確認できません')
                  : '未回答'}
              </dd>
            </div>
          );
        })}
      </dl>
      <SurveyHistory target={{ aid }} denied={deny} />
    </div>
  );
}
