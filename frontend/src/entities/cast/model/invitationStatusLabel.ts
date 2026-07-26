import { CastInvitationStatus } from './types';

/** 招待状態の表示ラベルと配色（DESIGN.md Status pill の tint レシピ）を返す。 */
export function castInvitationStatusLabel(status: CastInvitationStatus): {
  text: string;
  color: string;
} {
  switch (status) {
    case 'LINKED':
      return { text: '連携済み', color: 'bg-success/10 text-success-strong' };
    case 'INVITED':
      return { text: '招待中', color: 'bg-primary/10 text-primary-strong' };
    case 'EXPIRED':
      return { text: '期限切れ', color: 'bg-destructive/10 text-destructive-strong' };
    case 'NOT_INVITED':
    default:
      return { text: '未招待', color: 'bg-muted text-foreground' };
  }
}
