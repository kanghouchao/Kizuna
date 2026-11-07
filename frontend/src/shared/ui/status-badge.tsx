import { cn } from '@/shared/lib/utils';
import { Badge } from './badge';

/**
 * 本人ポータルの状態の語彙。両ポータルでこの語だけを使い、API の状態からの写しは各画面が持つ。
 * 待ちは warning、済みは success、店舗による否定は destructive、本人が自分で閉じたものは中立。
 */
const TONE_CLASS = {
  waiting: 'bg-warning/10 text-warning-strong',
  done: 'bg-success/10 text-success-strong',
  rejected: 'bg-destructive/10 text-destructive-strong',
  closed: 'bg-muted text-foreground',
} as const;

const TONES = {
  申請中: 'waiting',
  承認: 'done',
  却下: 'rejected',
  取り下げ: 'closed',
  受諾待ち: 'waiting',
  受諾済み: 'done',
  拒否済み: 'closed',
  承諾待ち: 'waiting',
  承諾済み: 'done',
  再承諾が必要: 'waiting',
} as const satisfies Record<string, keyof typeof TONE_CLASS>;

export type StatusWord = keyof typeof TONES;

export function StatusBadge({ status, className }: { status: StatusWord; className?: string }) {
  return (
    <Badge
      variant="outline"
      className={cn('border-transparent', TONE_CLASS[TONES[status]], className)}
    >
      {status}
    </Badge>
  );
}
