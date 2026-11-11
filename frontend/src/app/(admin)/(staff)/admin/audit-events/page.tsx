import AuditEventsPage from '@/_pages/admin-audit-events';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <PermissionGate codes={['AUDIT_VIEW']}>
      <AuditEventsPage />
    </PermissionGate>
  );
}
