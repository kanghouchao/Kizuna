import { OrderAttributionPage } from '@/_pages/store-orders';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <PermissionGate codes={['ORDER_MANAGE']}>
      <OrderAttributionPage />
    </PermissionGate>
  );
}
