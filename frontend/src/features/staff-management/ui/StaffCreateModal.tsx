'use client';
import { staffApi } from '@/entities/user';
import { StaffModal, type StaffCreateModalProps } from './StaffModal';
export function StaffCreateModal(props: StaffCreateModalProps) {
  return (
    <StaffModal
      {...props}
      mode="create"
      identity="human"
      title="従業員を追加"
      initialScope="SPECIFIC_STORES"
      loadRoles={staffApi.grantableRoles}
      create={staffApi.create}
      displayName={{ label: '氏名', required: '氏名を入力してください' }}
      successMessage="従業員を追加しました"
      failureMessage="従業員の追加に失敗しました"
    />
  );
}
