import { RolesPage } from '@/_pages/admin-roles';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <PermissionGate codes={['ROLE_MANAGE']}>
      <RolesPage />
    </PermissionGate>
  );
}
