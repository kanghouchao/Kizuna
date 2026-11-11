import { ApplicantDetailPage } from '@/_pages/store-applicants';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <PermissionGate codes={['RECRUITMENT_VIEW']}>
      <ApplicantDetailPage />
    </PermissionGate>
  );
}
