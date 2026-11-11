import { StoresPage } from '@/_pages/admin-stores';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <PermissionGate codes={['STORE_MANAGE']}>
      <StoresPage />
    </PermissionGate>
  );
}
