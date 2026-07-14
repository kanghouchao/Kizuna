import { PlatformRole } from '@/entities/user';

// スタッフ管理で作成・編集可能なロール（CAST/MEMBER は別チケットの専用フローが扱うため対象外。#325）。
export const STAFF_ROLE_OPTIONS: { value: PlatformRole; label: string }[] = [
  { value: 'HQ_ADMIN', label: 'HQ管理者' },
  { value: 'STORE_MANAGER', label: '店長' },
  { value: 'STORE_STAFF', label: '店舗スタッフ' },
];

export function staffRoleLabel(role: PlatformRole): string {
  return STAFF_ROLE_OPTIONS.find(option => option.value === role)?.label ?? role;
}
