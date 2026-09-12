'use client';
import { storeStaffApi, type StoreStaffResponse } from '@/entities/user';
import { StaffModal, type StaffEditModalProps } from './StaffModal';
export function StoreStaffEditModal(props: StaffEditModalProps<StoreStaffResponse>) {
  return (
    <StaffModal
      {...props}
      mode="edit"
      loadRoles={storeStaffApi.grantableRoles}
      update={storeStaffApi.update}
      editStatus={true}
      honorific="さん"
    />
  );
}
