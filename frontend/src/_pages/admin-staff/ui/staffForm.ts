import { staffApi } from '@/entities/user';

export const LIST_HREF = '/admin/staff';

/** 作成・編集の両画面で共通の授権フォームの設定。 */
export const STAFF_GRANT_FORM = {
  honorific: 'さん',
  loadRoles: staffApi.grantableRoles,
  grantDescription: '委譲できるのは自分が持つロールと、自分の担当店舗の範囲だけです。',
  listHref: LIST_HREF,
} as const;
