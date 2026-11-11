import { Suspense } from 'react';
import { CustomerMergePage } from '@/_pages/store-customers';

export default function Page() {
  return (
    <Suspense fallback={<p>読み込み中...</p>}>
      <CustomerMergePage />
    </Suspense>
  );
}
