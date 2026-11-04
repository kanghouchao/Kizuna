'use client';
import type { Day } from '../api';
import { Badge, Button } from '@/shared/ui';
import { yen } from './yen';
const states: Record<Day['acceptance_state'], { label: string; className: string }> = {
  NOT_ACCEPTABLE: { label: '承諾不可', className: 'bg-muted text-foreground' },
  NOT_ACCEPTED: { label: '未承諾', className: 'bg-muted text-foreground' },
  ACCEPTED: { label: '承諾済み', className: 'bg-success/10 text-success-strong' },
  CHANGED_AFTER_ACCEPTANCE: {
    label: '承諾後に変更あり',
    className: 'bg-warning/10 text-warning-strong',
  },
};
const reasons: Record<NonNullable<Day['not_acceptable_reason']>, string> = {
  BUSINESS_DAY_NOT_ENDED: '営業日が終わっていません',
  GUARANTEE_UNDETERMINED: '退勤記録待ち・日額未設定',
  ALL_ZERO: '報酬がありません',
};
const acceptedAt = (value: string) =>
  new Date(value).toLocaleString('ja-JP', { timeZone: 'Asia/Tokyo' });
/**
 * 日の承諾状態。onAccept は本人の画面だけが渡す — 店舗側は読むだけで書く口を持たない。
 * 差分は最新の承諾と現在の三項を比べる。額が同じでも根拠が変われば「変更あり」になるので、その旨を書く。
 */
export function DayAcceptanceCell({
  day,
  onAccept,
  sending,
}: {
  day: Day;
  onAccept?: (day: Day) => void;
  sending: boolean;
}) {
  const state = states[day.acceptance_state];
  const latest = day.latest_acceptance;
  const changes =
    day.acceptance_state === 'CHANGED_AFTER_ACCEPTANCE' && latest
      ? (
          [
            ['受注報酬', latest.order_amount, day.order_amount],
            ['保証不足分', latest.guarantee_amount, day.guarantee_amount],
            ['ボーナス', latest.bonus_amount, day.bonus_amount],
          ] as const
        ).filter(([, before, after]) => before !== after)
      : [];
  const acceptable =
    day.acceptance_state === 'NOT_ACCEPTED' || day.acceptance_state === 'CHANGED_AFTER_ACCEPTANCE';
  return (
    <div className="space-y-1">
      <Badge variant="outline" className={`border-transparent ${state.className}`}>
        {state.label}
      </Badge>
      {day.not_acceptable_reason && <p>{reasons[day.not_acceptable_reason]}</p>}
      {day.acceptance_state === 'CHANGED_AFTER_ACCEPTANCE' &&
        (changes.length === 0 ? (
          <p>額は同じで内訳が変わりました</p>
        ) : (
          changes.map(([label, before, after]) => (
            <p key={label}>
              {label} {yen(before)} → {yen(after)}
            </p>
          ))
        ))}
      {latest && (
        <p className="text-sm text-muted-foreground">
          最新の承諾：{acceptedAt(latest.accepted_at)}
        </p>
      )}
      {onAccept && acceptable && (
        <Button
          type="button"
          size="sm"
          disabled={sending}
          aria-label={`${day.business_date} を${latest ? '再承諾' : '承諾'}`}
          onClick={() => onAccept(day)}
        >
          {latest ? '再承諾' : '承諾'}
        </Button>
      )}
    </div>
  );
}
