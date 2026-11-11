import { Suspense } from 'react';
import { OrdersPage } from '@/_pages/store-orders';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <Suspense fallback={<p>読み込み中...</p>}>
      <PermissionGate codes={['ORDER_MANAGE']}>
        <OrdersPage />
      </PermissionGate>
    </Suspense>
  );
}
