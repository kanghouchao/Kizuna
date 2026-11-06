'use client';
import { useParams } from 'next/navigation';
import { surveyApi } from '@/entities/survey';
import { revisionHref } from '../lib/surveyHref';
import { SurveyAnswerIntake } from './SurveyAnswerIntake';
import { SurveyGate } from './SurveyGate';

export function SurveyReceivePage() {
  const { sid, rid } = useParams<{ sid: string; rid: string }>();
  return (
    <SurveyGate target={`${sid}:${rid}`} record>
      {() => (
        <SurveyAnswerIntake
          title="回答を受付"
          back={{ href: revisionHref(sid, rid), label: '設問版へ戻る' }}
          load={async () => ({ kind: 'RECEIVE', revision: await surveyApi.revision(sid, rid) })}
          unavailable={({ revision }) =>
            revision.status === 'OPEN' ? null : 'この設問版は受付中ではありません。'
          }
          notFoundMessage="設問版が見つかりません"
        />
      )}
    </SurveyGate>
  );
}
