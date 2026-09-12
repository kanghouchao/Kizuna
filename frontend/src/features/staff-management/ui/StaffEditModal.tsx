'use client';
import { platformStaffApi, platformRoleApi, type PlatformStaffResponse } from '@/entities/user';
import { StaffModal, type StaffEditModalProps } from './StaffModal';
export function StaffEditModal(props: StaffEditModalProps<PlatformStaffResponse>) {
  return (
    <StaffModal
      {...props}
      mode="edit"
      loadRoles={platformRoleApi.list}
      update={platformStaffApi.update}
      editStatus={false}
      honorific="さん"
    />
  );
}
