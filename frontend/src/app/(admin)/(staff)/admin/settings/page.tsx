import { SettingsPage } from '@/_pages/admin-settings';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <PermissionGate codes={['SYSTEM_CONFIG_MANAGE']}>
      <SettingsPage />
    </PermissionGate>
  );
}
