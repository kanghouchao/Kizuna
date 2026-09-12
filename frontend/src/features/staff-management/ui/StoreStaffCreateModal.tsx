'use client';
import { storeStaffApi } from '@/entities/user';
import { StaffModal, type StaffCreateModalProps } from './StaffModal';
export function StoreStaffCreateModal(props: StaffCreateModalProps) {
  return (
    <StaffModal
      {...props}
      mode="create"
      identity="human"
      title="スタッフを追加"
      initialScope="SPECIFIC_STORES"
      loadRoles={storeStaffApi.grantableRoles}
      create={storeStaffApi.create}
      displayName={{ label: '氏名', required: '氏名を入力してください' }}
      successMessage="スタッフを追加しました"
      failureMessage="スタッフの追加に失敗しました"
    />
  );
}
