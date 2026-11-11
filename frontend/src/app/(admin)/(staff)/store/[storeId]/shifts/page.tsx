import { ShiftsPage } from '@/_pages/store-shifts';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <PermissionGate codes={['SHIFT_MANAGE']}>
      <ShiftsPage />
    </PermissionGate>
  );
}
