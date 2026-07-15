import { CastInvitationStatus } from './types';

/** 招待状態の表示ラベルと配色（DESIGN.md Status pill: *-100 bg + *-800 text）を返す。 */
export function castInvitationStatusLabel(status: CastInvitationStatus): {
  text: string;
  color: string;
} {
  switch (status) {
    case 'LINKED':
      return { text: '連携済み', color: 'bg-green-100 text-green-800' };
    case 'INVITED':
      return { text: '招待中', color: 'bg-blue-100 text-blue-800' };
    case 'EXPIRED':
      return { text: '期限切れ', color: 'bg-red-100 text-red-800' };
    case 'NOT_INVITED':
    default:
      return { text: '未招待', color: 'bg-gray-100 text-gray-800' };
  }
}
