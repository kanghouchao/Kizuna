import { Suspense } from 'react';
import { OrdersPage } from '@/_pages/store-orders';

export default function Page() {
  return (
    <Suspense fallback={<p>読み込み中...</p>}>
      <OrdersPage />
    </Suspense>
  );
}
