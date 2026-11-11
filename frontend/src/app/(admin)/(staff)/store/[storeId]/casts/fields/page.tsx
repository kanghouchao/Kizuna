import { CastFieldsPage } from '@/_pages/store-cast-fields';
import { PermissionGate } from '@/widgets/admin-shell';

export default function Page() {
  return (
    <PermissionGate codes={['CAST_FIELD_DEF_VIEW']}>
      <CastFieldsPage />
    </PermissionGate>
  );
}
