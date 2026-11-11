import { ServiceIdentityEditPage } from '@/_pages/admin-service-identities';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <PermissionGate codes={['SERVICE_ID_MANAGE']}>
      <ServiceIdentityEditPage />
    </PermissionGate>
  );
}
