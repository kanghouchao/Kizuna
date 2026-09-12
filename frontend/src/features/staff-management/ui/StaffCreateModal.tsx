'use client';
import { platformStaffApi, platformRoleApi } from '@/entities/user';
import { StaffModal, type StaffCreateModalProps } from './StaffModal';
export function StaffCreateModal(props: StaffCreateModalProps) {
  return (
    <StaffModal
      {...props}
      mode="create"
      identity="human"
      title="管理者を追加"
      initialScope="ALL_STORES"
      loadRoles={platformRoleApi.list}
      create={platformStaffApi.create}
      displayName={{ label: '氏名', required: '氏名を入力してください' }}
      successMessage="管理者を追加しました"
      failureMessage="管理者の追加に失敗しました"
    />
  );
}
