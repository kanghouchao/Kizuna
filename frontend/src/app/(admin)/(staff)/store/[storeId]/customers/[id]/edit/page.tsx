import { CustomerEditPage } from '@/_pages/store-customers';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <PermissionGate codes={['CUSTOMER_MANAGE']}>
      <CustomerEditPage />
    </PermissionGate>
  );
}
