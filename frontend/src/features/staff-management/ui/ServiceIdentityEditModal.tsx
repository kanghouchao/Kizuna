'use client';
import { serviceIdentityApi, type ServiceIdentityResponse } from '@/entities/user';
import { StaffModal, type StaffEditModalProps } from './StaffModal';
export function ServiceIdentityEditModal(props: StaffEditModalProps<ServiceIdentityResponse>) {
  return (
    <StaffModal
      {...props}
      mode="edit"
      loadRoles={serviceIdentityApi.grantableRoles}
      update={serviceIdentityApi.update}
      editStatus={false}
      honorific=""
      storeLabel="対象店舗"
    />
  );
}
