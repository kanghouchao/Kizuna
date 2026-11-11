import { BenefitRulesPage } from '@/_pages/admin-benefit-rules';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <PermissionGate codes={['BENEFIT_MANAGE']}>
      <BenefitRulesPage />
    </PermissionGate>
  );
}
