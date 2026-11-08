import type { Day } from '@/widgets/remuneration-statement';
import { closedMinutes, pagesAgree, statementDays } from '../monthlyStatement';

const day = (over: Partial<Day>): Day => ({
  business_date: '2026-10-01',
  order_amount: 0,
  closed_duration: 'PT0S',
  attendance_incomplete: false,
  guarantee_status: 'NOT_ELIGIBLE',
  guarantee_amount: 0,
  bonus_amount: 0,
  acceptance_state: 'NOT_ACCEPTABLE',
  ...over,
});
const order = (business_date: string, order_id: string, accrued_remuneration = 7000) => ({
  order_id,
  business_date,
  service_summary: `${order_id} のコース`,
  accrued_remuneration,
  completion_invalidated: false,
});
const bonus = (award_date: string, id: string) => ({
  id,
  award_date,
  effective_amount: 3000,
  reason: '指名ボーナス',
  cancelled: false,
});

describe('closedMinutes', () => {
  it.each([
    ['PT0S', 0],
    ['PT4H30M', 270],
    ['PT2H', 120],
    ['PT45M30S', 45],
    ['PT1H0.5S', 60],
  ])('%s は %i 分', (duration, minutes) => expect(closedMinutes(duration)).toBe(minutes));
});

describe('statementDays', () => {
  const days = [
    day({ business_date: '2026-10-01' }),
    day({
      business_date: '2026-10-02',
      closed_duration: 'PT5H',
      order_amount: 7000,
      acceptance_state: 'ACCEPTED',
    }),
    day({ business_date: '2026-10-03', attendance_incomplete: true }),
    day({ business_date: '2026-10-04', bonus_amount: 3000, acceptance_state: 'NOT_ACCEPTED' }),
    day({ business_date: '2026-10-05', acceptance_state: 'CHANGED_AFTER_ACCEPTANCE' }),
    day({ business_date: '2026-10-06', order_amount: 0 }),
  ];
  const orders = [order('2026-10-02', 'b'), order('2026-10-02', 'a'), order('2026-10-06', 'z', 0)];
  const bonuses = [bonus('2026-10-04', 'x')];
  const shown = statementDays(days, orders, bonuses);

  it('出勤・受注・ボーナス・承諾のどれも無い日は出さない', () => {
    expect(shown.map(entry => entry.day.business_date)).not.toContain('2026-10-01');
  });

  it('取り消したボーナスだけの日は出さない', () => {
    const cancelled = { ...bonus('2026-10-01', 'y'), effective_amount: 0, cancelled: true };
    const dates = statementDays(days, orders, [cancelled]).map(entry => entry.day.business_date);
    expect(dates).not.toContain('2026-10-01');
  });

  it('承諾待ち・再承諾が必要な日を先頭に、それぞれ日付の順に並べる', () => {
    expect(shown.map(entry => entry.day.business_date)).toEqual([
      '2026-10-04',
      '2026-10-05',
      '2026-10-02',
      '2026-10-03',
      '2026-10-06',
    ]);
  });

  it('受注とボーナスをその日へ割り振る（無効化で 0 円の受注も、受注のある日として残す）', () => {
    const byDate = Object.fromEntries(shown.map(entry => [entry.day.business_date, entry]));
    expect(byDate['2026-10-02'].orders.map(o => o.order_id)).toEqual(['b', 'a']);
    expect(byDate['2026-10-04'].bonuses.map(b => b.id)).toEqual(['x']);
    expect(byDate['2026-10-06'].orders.map(o => o.order_id)).toEqual(['z']);
    expect(byDate['2026-10-03'].orders).toEqual([]);
  });

  it('承諾待ちかどうかを持つ', () => {
    expect(shown.filter(entry => entry.awaiting).map(entry => entry.day.business_date)).toEqual([
      '2026-10-04',
      '2026-10-05',
    ]);
  });
});

describe('pagesAgree', () => {
  const days = [
    day({ business_date: '2026-10-02', order_amount: 14000, bonus_amount: 3000 }),
    day({ business_date: '2026-10-03', order_amount: 7000 }),
  ];
  const orders = [order('2026-10-03', 'c'), order('2026-10-02', 'b'), order('2026-10-02', 'a')];
  const bonuses = [bonus('2026-10-02', 'x')];
  const totals = { orders: 3, bonuses: 1 };

  it('件数・重複・日ごとの額が最初の応答と合えば真', () => {
    expect(pagesAgree(days, totals, orders, bonuses)).toBe(true);
  });

  it('ページの間で行が増えて境界の行が重複したら偽', () => {
    expect(pagesAgree(days, totals, [orders[0], orders[1], orders[1]], bonuses)).toBe(false);
  });

  it('件数が合わなければ偽', () => {
    expect(pagesAgree(days, totals, orders.slice(1), bonuses)).toBe(false);
  });

  it('ある日の受注の和がその日の受注報酬と合わなければ偽', () => {
    expect(pagesAgree(days, totals, [order('2026-10-02', 'c'), ...orders.slice(1)], bonuses)).toBe(
      false
    );
  });

  it('ある日のボーナスの和がその日のボーナスと合わなければ偽', () => {
    expect(pagesAgree(days, { ...totals, bonuses: 1 }, orders, [bonus('2026-10-03', 'x')])).toBe(
      false
    );
  });
});
