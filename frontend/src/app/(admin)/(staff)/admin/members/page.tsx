import { AdminMembersPage } from '@/_pages/admin-members';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <PermissionGate codes={['MEMBER_VIEW']}>
      <AdminMembersPage />
    </PermissionGate>
  );
}
