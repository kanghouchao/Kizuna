import { CustomRoleRequiredHint } from '@/features/staff-management';

export const LIST_HREF = '/admin/service-identities';

/** 作成・編集の両画面で共通の授権フォームの設定。 */
export const SERVICE_IDENTITY_GRANT_FORM = {
  honorific: '',
  rolesEmptyHint: <CustomRoleRequiredHint />,
  grantDescription: '付与できるのは自作ロールだけです。',
  storeLabel: '対象店舗',
  listHref: LIST_HREF,
} as const;
