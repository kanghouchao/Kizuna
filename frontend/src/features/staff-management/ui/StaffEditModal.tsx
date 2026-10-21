'use client';
import { platformStaffApi, type PlatformStaffResponse } from '@/entities/user';
import { StaffModal, type StaffEditModalProps } from './StaffModal';
export function StaffEditModal(props: StaffEditModalProps<PlatformStaffResponse>) {
  return (
    <StaffModal
      {...props}
      mode="edit"
      loadRoles={platformStaffApi.grantableRoles}
      update={platformStaffApi.update}
      honorific="さん"
    />
  );
}
