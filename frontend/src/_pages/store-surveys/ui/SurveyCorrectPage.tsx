'use client';
import { useParams } from 'next/navigation';
import { surveyApi } from '@/entities/survey';
import { answerHref } from '../lib/surveyHref';
import { SurveyAnswerIntake } from './SurveyAnswerIntake';
import { SurveyGate } from './SurveyGate';
import { useCurrentStoreId } from '@/entities/user';

export function SurveyCorrectPage() {
  const storeId = useCurrentStoreId();
  const { aid } = useParams<{ aid: string }>();
  return (
    <SurveyGate target={aid} record>
      {() => (
        <SurveyAnswerIntake
          title="回答を訂正"
          back={{ href: answerHref(storeId, aid), label: '回答へ戻る' }}
          load={async () => {
            const answer = await surveyApi.answer(aid);
            const revision = await surveyApi.revision(answer.survey_id, answer.revision_id);
            return { kind: 'CORRECT', answer, revision };
          }}
          unavailable={target =>
            target.kind === 'CORRECT' && target.answer.superseded_by_id
              ? 'この回答は訂正済みです。訂正先の回答から操作してください。'
              : null
          }
          notFoundMessage="回答が見つかりません"
        />
      )}
    </SurveyGate>
  );
}
