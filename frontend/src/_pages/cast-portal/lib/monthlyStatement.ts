import type { Day, Statement } from '@/widgets/remuneration-statement';

export type StatementOrder = Statement['orders']['content'][number];
export type StatementBonus = Statement['bonus_awards']['content'][number];

export interface StatementDay {
  day: Day;
  orders: StatementOrder[];
  bonuses: StatementBonus[];
  /** 承諾待ち・再承諾が必要。 */
  awaiting: boolean;
}

/** 終了済みの出勤区間 'PT4H30M' を分に（秒は切り捨てる）。 */
export function closedMinutes(duration: string): number {
  const match = /^PT(?:(\d+)H)?(?:(\d+)M)?/.exec(duration);
  return match ? Number(match[1] ?? 0) * 60 + Number(match[2] ?? 0) : 0;
}

/**
 * 別々に取ったページを繋いだ行が、最初の応答の件数と日ごとの額に合うか。ページの間に受注が完了・訂正されると
 * 行が繰り下がって重複・欠落し得る。承諾の照合は日の額だけなので、行のずれは 409 にならない。
 */
export function pagesAgree(
  days: Day[],
  totals: { orders: number; bonuses: number },
  orders: StatementOrder[],
  bonuses: StatementBonus[]
): boolean {
  const unique = <T>(rows: T[], key: (row: T) => string) =>
    new Set(rows.map(key)).size === rows.length;
  if (orders.length !== totals.orders || bonuses.length !== totals.bonuses) return false;
  if (!unique(orders, o => o.order_id) || !unique(bonuses, b => b.id)) return false;
  const sum = <T>(rows: T[], date: (row: T) => string, amount: (row: T) => number, day: string) =>
    rows.filter(row => date(row) === day).reduce((total, row) => total + amount(row), 0);
  return days.every(
    day =>
      sum(
        orders,
        o => o.business_date,
        o => o.accrued_remuneration,
        day.business_date
      ) === day.order_amount &&
      sum(
        bonuses,
        b => b.award_date,
        b => b.effective_amount,
        day.business_date
      ) === day.bonus_amount
  );
}

const isAwaiting = (day: Day) =>
  day.acceptance_state === 'NOT_ACCEPTED' || day.acceptance_state === 'CHANGED_AFTER_ACCEPTANCE';

/**
 * 月次明細を 1 日 1 件にまとめる。明細は暦の全日を返すので、出勤・受注・ボーナス・承諾のどれも無い日は除く（取り消したボーナスだけの日も除く）。
 * 承諾待ちの日を先頭に、それ以外を後ろに、それぞれ日付の順で並べる。
 */
export function statementDays(
  days: Day[],
  orders: StatementOrder[],
  bonuses: StatementBonus[]
): StatementDay[] {
  const entries = days.map(day => ({
    day,
    orders: orders.filter(order => order.business_date === day.business_date),
    bonuses: bonuses.filter(bonus => bonus.award_date === day.business_date),
    awaiting: isAwaiting(day),
  }));
  const active = entries.filter(
    ({ day, orders }) =>
      closedMinutes(day.closed_duration) > 0 ||
      day.attendance_incomplete ||
      orders.length > 0 ||
      day.order_amount !== 0 ||
      day.bonus_amount !== 0 ||
      day.acceptance_state !== 'NOT_ACCEPTABLE' ||
      day.latest_acceptance !== undefined
  );
  const byDate = (a: StatementDay, b: StatementDay) =>
    a.day.business_date.localeCompare(b.day.business_date);
  return [
    ...active.filter(entry => entry.awaiting).sort(byDate),
    ...active.filter(entry => !entry.awaiting).sort(byDate),
  ];
}
