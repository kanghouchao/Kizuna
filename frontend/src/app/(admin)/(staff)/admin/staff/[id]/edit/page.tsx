import { StaffEditPage } from '@/_pages/admin-staff';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <PermissionGate codes={['STAFF_MANAGE']}>
      <StaffEditPage />
    </PermissionGate>
  );
}
