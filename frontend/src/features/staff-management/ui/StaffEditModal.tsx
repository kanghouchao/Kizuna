'use client';
import { staffApi, type StaffResponse } from '@/entities/user';
import { StaffModal, type StaffEditModalProps } from './StaffModal';
export function StaffEditModal(props: StaffEditModalProps<StaffResponse>) {
  return (
    <StaffModal
      {...props}
      mode="edit"
      loadRoles={staffApi.grantableRoles}
      update={staffApi.update}
      honorific="さん"
    />
  );
}
