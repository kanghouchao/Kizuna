import { ReviewStatus, PermissionStatus, ReviewHistory, Publication } from '@/entities/review';
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
  PUBLICATION_CHANGED: '公開設定の変更',
};
export const viaLabels = { PAPER: '書面', VERBAL: '口頭', ELECTRONIC: '電子記録' };
export const basisLabels = { WRITTEN: '書面', VERBAL: '口頭', ELECTRONIC_RECORD: '電子記録' };
export function time(value: string) {
  return new Date(value).toLocaleString('ja-JP');
}
export function publicationSummary(p: Publication) {
  const period =
    p.publish_from || p.publish_until
      ? `${p.publish_from ? time(p.publish_from) : '開始指定なし'} 〜 ${p.publish_until ? time(p.publish_until) : '終了指定なし'}`
      : '期間指定なし';
  const order = p.display_order === null ? '表示順指定なし' : `表示順 ${p.display_order}`;
  return `${p.published ? '公開' : '非公開'} / ${period} / ${order}`;
}
