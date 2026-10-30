'use client';
import { serviceIdentityApi, type ServiceIdentityResponse } from '@/entities/user';
import { CustomRoleRequiredHint } from './CustomRoleRequiredHint';
import { StaffModal, type StaffEditModalProps } from './StaffModal';
export function ServiceIdentityEditModal(props: StaffEditModalProps<ServiceIdentityResponse>) {
  return (
    <StaffModal
      {...props}
      mode="edit"
      loadRoles={serviceIdentityApi.grantableRoles}
      rolesEmptyHint={<CustomRoleRequiredHint />}
      update={serviceIdentityApi.update}
      honorific=""
      storeLabel="対象店舗"
    />
  );
}
