import { AdminCastDetailPage } from '@/_pages/admin-casts';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <PermissionGate codes={['CAST_PERSON_VIEW']}>
      <AdminCastDetailPage />
    </PermissionGate>
  );
}
