import { Suspense } from 'react';
import { SurveyRevisePage } from '@/_pages/store-surveys';

export default function Page() {
  return (
    <Suspense fallback={<p>読み込み中...</p>}>
      <SurveyRevisePage />
    </Suspense>
  );
}
