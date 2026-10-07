import { ReviewStatus, PermissionStatus, ReviewHistory } from '@/entities/review';
export const statuses: Record<ReviewStatus, string> = {
  PENDING: '審査待ち',
  APPROVED: '内部承認済み',
  REJECTED: '却下',
  WITHDRAWN: '取り下げ済み',
};
export const permissions: Record<PermissionStatus, string> = {
  NOT_GRANTED: '公開許可未取得',
  GRANTED: '公開許可あり',
  REVOKED: '公開許可撤回済み',
};
export const operations: Record<ReviewHistory['type'], string> = {
  RECEIVED: '手動受付',
  APPROVED: '内部承認',
  REJECTED: '却下',
  WITHDRAWN: '取り下げ',
  PERMISSION_GRANTED: '公開許可の記録',
  PERMISSION_REVOKED: '公開許可の撤回',
  CORRECTION_RECEIVED: '訂正再受付',
  CORRECTION_LINKED: '訂正先の記録',
};
export const viaLabels = { PAPER: '書面', VERBAL: '口頭', ELECTRONIC: '電子記録' };
export const basisLabels = { WRITTEN: '書面', VERBAL: '口頭', ELECTRONIC_RECORD: '電子記録' };
export function time(value: string) {
  return new Date(value).toLocaleString('ja-JP');
}
