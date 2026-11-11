import { CastCreatePage } from '@/_pages/store-casts';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <PermissionGate codes={['CAST_MANAGE']}>
      <CastCreatePage />
    </PermissionGate>
  );
}
