'use client';

import { useId } from 'react';
import Link from 'next/link';
import { ChevronDownIcon } from 'lucide-react';
import { formatDate, formatDateTime, formatDuration, formatMoney } from '@/shared/lib';
import { cn } from '@/shared/lib/utils';
import { Button, Money, StatusBadge, type StatusWord } from '@/shared/ui';
import type { Day } from '@/widgets/remuneration-statement';
import { closedMinutes, type StatementDay } from '../lib/monthlyStatement';

const STATUS: Partial<Record<Day['acceptance_state'], StatusWord>> = {
  NOT_ACCEPTED: '承諾待ち',
  ACCEPTED: '承諾済み',
  CHANGED_AFTER_ACCEPTANCE: '再承諾が必要',
};

const NOT_ACCEPTABLE: Record<NonNullable<Day['not_acceptable_reason']>, string> = {
  BUSINESS_DAY_NOT_ENDED: '営業日の終了後に承諾できます',
  GUARANTEE_UNDETERMINED: '退勤の記録と日額が確定すると承諾できます',
  ALL_ZERO: '報酬が無いため承諾はありません',
};

const PENDING_GUARANTEE: Partial<Record<Day['guarantee_status'], string>> = {
  PENDING_ATTENDANCE: '退勤記録待ち',
  NOT_CONFIGURED: '日額未設定',
};

/** その日の合計。保証が未確定なら出さない（月の合計と同じく部分の和を出さない）。 */
function dayTotal(day: Day): number | undefined {
  return day.guarantee_amount === undefined
    ? undefined
    : day.order_amount + day.guarantee_amount + day.bonus_amount;
}

// 項目の途中では折り返さず、項目の間で折り返す（狭い幅で「ボーナスあ／り」と割らない）
function Headline({ entry }: { entry: StatementDay }) {
  const minutes = closedMinutes(entry.day.closed_duration);
  const parts = [
    minutes > 0 && `出勤 ${formatDuration(minutes)}`,
    `受注${entry.orders.length}件`,
    entry.bonuses.length > 0 && 'ボーナスあり',
  ].filter((part): part is string => typeof part === 'string');
  return (
    <span className="min-w-0 text-sm text-muted-foreground">
      {parts.map((part, index) => (
        <span key={part}>
          {index > 0 && ' · '}
          <span className="whitespace-nowrap">{part}</span>
        </span>
      ))}
    </span>
  );
}

/** 承諾した後に変わった額。三項が同じでも根拠が変われば「変更あり」になるので、その旨を出す。 */
function changes(day: Day) {
  const latest = day.latest_acceptance;
  if (day.acceptance_state !== 'CHANGED_AFTER_ACCEPTANCE' || !latest) return null;
  return (
    [
      ['受注報酬', latest.order_amount, day.order_amount],
      ['日額保証の不足分', latest.guarantee_amount, day.guarantee_amount],
      ['ボーナス', latest.bonus_amount, day.bonus_amount],
    ] as const
  ).filter(([, before, after]) => before !== after);
}

function Line({
  label,
  children,
  strong,
}: {
  label: React.ReactNode;
  children: React.ReactNode;
  strong?: boolean;
}) {
  return (
    <div className={cn('flex items-baseline justify-between gap-3 py-2', strong && 'font-bold')}>
      <span className="min-w-0 wrap-anywhere">{label}</span>
      <span className="shrink-0 text-right tabular-nums">{children}</span>
    </div>
  );
}

function SubHeading({ children }: { children: React.ReactNode }) {
  return <h4 className="pt-3 text-sm font-semibold text-muted-foreground">{children}</h4>;
}

/** 月次明細の 1 日。見出しの行で開閉し、開くとその日の根拠と承諾のボタン（1 日 1 個）を出す。 */
export function StatementDayFold({
  entry,
  today,
  open,
  onToggle,
  onAccept,
  sending,
}: {
  entry: StatementDay;
  today: string;
  open: boolean;
  onToggle: () => void;
  onAccept: (day: Day) => void;
  sending: boolean;
}) {
  const { day, orders, bonuses } = entry;
  const bodyId = useId();
  const label = formatDate(day.business_date, today);
  const total = dayTotal(day);
  const status = STATUS[day.acceptance_state];
  const minutes = closedMinutes(day.closed_duration);
  const diff = changes(day);
  const pendingGuarantee = PENDING_GUARANTEE[day.guarantee_status];
  return (
    <div className="overflow-hidden rounded-xl border bg-card text-card-foreground">
      <h3>
        <button
          type="button"
          aria-expanded={open}
          aria-controls={bodyId}
          onClick={onToggle}
          className="grid min-h-14 w-full grid-cols-[1fr_auto] items-center gap-x-3 gap-y-1 px-4 py-3 text-left hover:bg-muted/60 focus-visible:ring-2 focus-visible:ring-primary focus-visible:outline-none"
        >
          <span className="font-semibold">{label}</span>
          <span className="flex items-center justify-end gap-1 font-semibold tabular-nums">
            {total === undefined ? (
              <span className="text-muted-foreground">計算待ち</span>
            ) : (
              formatMoney(total)
            )}
            <ChevronDownIcon
              aria-hidden="true"
              className={cn(
                'size-4 text-muted-foreground transition-transform',
                open && 'rotate-180'
              )}
            />
          </span>
          <Headline entry={entry} />
          <span className="justify-self-end">{status && <StatusBadge status={status} />}</span>
        </button>
      </h3>
      {open && (
        <section id={bodyId} aria-label={`${label}の明細`} className="divide-y border-t px-4 pb-4">
          {(minutes > 0 || day.attendance_incomplete) && (
            <Line label="出勤">
              {minutes > 0 && formatDuration(minutes)}
              {day.attendance_incomplete && (
                <span className="block text-sm text-muted-foreground">未終了の記録あり</span>
              )}
            </Line>
          )}
          <div>
            <SubHeading>受注 {orders.length}件</SubHeading>
            {orders.length === 0 ? (
              <p className="py-2 text-sm text-muted-foreground">受注はありません</p>
            ) : (
              <ul>
                {orders.map(order => (
                  <li key={order.order_id}>
                    <Link
                      href={`/cast/remunerations/${encodeURIComponent(order.order_id)}`}
                      className="flex min-h-11 items-baseline justify-between gap-3 py-2 hover:underline focus-visible:ring-2 focus-visible:ring-primary focus-visible:outline-none"
                    >
                      <span className="min-w-0 wrap-anywhere">
                        {order.service_summary}
                        {order.completion_invalidated && (
                          <span className="text-sm text-muted-foreground">（無効化）</span>
                        )}
                      </span>
                      <Money amount={order.accrued_remuneration} className="shrink-0" />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>
          {(pendingGuarantee || (day.guarantee_amount ?? 0) > 0) && (
            <div>
              <SubHeading>日額保証</SubHeading>
              {pendingGuarantee ? (
                <Line label="日額保証の不足分">{`計算待ち（${pendingGuarantee}）`}</Line>
              ) : (
                <Line
                  label={
                    day.daily_amount === undefined
                      ? '日額保証の不足分'
                      : `日額 ${formatMoney(day.daily_amount)} に対する不足分`
                  }
                >
                  <Money amount={day.guarantee_amount ?? 0} />
                </Line>
              )}
            </div>
          )}
          {bonuses.length > 0 && (
            <div>
              <SubHeading>ボーナス</SubHeading>
              {bonuses.map(bonus => (
                <Line
                  key={bonus.id}
                  label={
                    <>
                      {bonus.reason}
                      {bonus.cancelled && (
                        <span className="text-sm text-muted-foreground">（取消済み）</span>
                      )}
                    </>
                  }
                >
                  <Money amount={bonus.effective_amount} />
                </Line>
              ))}
            </div>
          )}
          <Line label="この日の合計" strong>
            {total === undefined ? '計算待ち' : <Money amount={total} />}
          </Line>
          <div className="space-y-3 pt-3">
            {diff && (
              <div className="space-y-1 rounded-lg bg-warning/10 p-3 text-sm">
                <p className="font-semibold">承諾した後に変わりました</p>
                {diff.length === 0 ? (
                  <p>額は同じで内訳が変わりました</p>
                ) : (
                  diff.map(([name, before, after]) => (
                    <p key={name} className="flex flex-wrap justify-between gap-x-3">
                      <span>{name}</span>
                      {after === undefined ? (
                        <span className="tabular-nums">{`${formatMoney(before)} → 計算待ち`}</span>
                      ) : (
                        <Money amount={after} before={before} />
                      )}
                    </p>
                  ))
                )}
              </div>
            )}
            {day.latest_acceptance && (
              <p className="text-sm text-muted-foreground">
                {formatDateTime(day.latest_acceptance.accepted_at, today)}に承諾しました
              </p>
            )}
            {day.not_acceptable_reason && (
              <p className="text-sm text-muted-foreground">
                {NOT_ACCEPTABLE[day.not_acceptable_reason]}
              </p>
            )}
            {entry.awaiting && (
              <Button
                type="button"
                className="h-11 w-full"
                disabled={sending}
                onClick={() => onAccept(day)}
              >
                {`${label}を${day.acceptance_state === 'CHANGED_AFTER_ACCEPTANCE' ? '再承諾' : '承諾'}する`}
              </Button>
            )}
          </div>
        </section>
      )}
    </div>
  );
}
