import { StoreProfilePage } from '@/_pages/store-settings';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <PermissionGate codes={['STORE_PROFILE_MANAGE']}>
      <StoreProfilePage />
    </PermissionGate>
  );
}
