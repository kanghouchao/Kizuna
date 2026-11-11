'use client';
import { useRouter } from 'next/navigation';
import { surveyListHref, writtenHref } from '../lib/surveyHref';
import { useSurveyWrite } from '../lib/useSurveyWrite';
import { SurveyDefinitionForm } from './SurveyDefinitionForm';
import { SurveyEditFrame, editCard } from './SurveyEditFrame';
import { SurveyDenied, SurveyGate } from './SurveyGate';
import { SurveyWriteFeedback } from './SurveyWriteFeedback';
import { useCurrentStoreId } from '@/entities/user';

export function SurveyCreatePage() {
  return (
    <SurveyGate target="create" manage>
      {() => <Create />}
    </SurveyGate>
  );
}

function Create() {
  const storeId = useCurrentStoreId();
  const router = useRouter();
  const write = useSurveyWrite(result => router.replace(writtenHref(storeId, result)));
  if (write.denied) return <SurveyDenied message={write.denied} />;
  return (
    <SurveyEditFrame
      title="アンケートを作成"
      back={{ href: surveyListHref(storeId), label: 'アンケート一覧へ' }}
      feedback={<SurveyWriteFeedback write={write} busy={write.busy} onRefresh={write.settle} />}
    >
      <section className={editCard}>
        <SurveyDefinitionForm
          disabled={write.blocked || write.conflict || write.missing}
          onSubmit={input => void write.submit({ kind: 'CREATE', input })}
        />
      </section>
    </SurveyEditFrame>
  );
}
