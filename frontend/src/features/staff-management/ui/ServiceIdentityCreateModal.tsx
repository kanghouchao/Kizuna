'use client';
import { serviceIdentityApi } from '@/entities/user';
import { StaffModal, type StaffCreateModalProps } from './StaffModal';
export function ServiceIdentityCreateModal(props: StaffCreateModalProps) {
  return (
    <StaffModal
      {...props}
      mode="create"
      identity="service"
      title="サービスIDを追加"
      initialScope="SPECIFIC_STORES"
      loadRoles={serviceIdentityApi.grantableRoles}
      create={serviceIdentityApi.create}
      displayName={{
        label: '用途名',
        required: '用途名を入力してください',
        placeholder: '例: 夜間ポイント失効バッチ',
      }}
      successMessage="サービスIDを追加しました"
      failureMessage="サービスIDの追加に失敗しました"
      storeLabel="対象店舗"
    />
  );
}
