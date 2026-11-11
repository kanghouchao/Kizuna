import { ApplicantEnrollmentPage } from '@/_pages/store-applicants';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <PermissionGate codes={['RECRUITMENT_VIEW', 'CAST_MANAGE']}>
      <ApplicantEnrollmentPage />
    </PermissionGate>
  );
}
