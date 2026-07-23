import { CastScheduleItem } from '@/entities/shift';
import { groupByWorkDate } from '../groupSchedule';

function item(overrides: Partial<CastScheduleItem>): CastScheduleItem {
  return {
    work_date: '2026-07-20',
    start_time: '18:00:00',
    end_time: '20:00:00',
    status: 'CONFIRMED',
    store_id: 1,
    store_name: '店舗A',
    ...overrides,
  };
}

describe('groupByWorkDate', () => {
  it('空配列は空配列を返す', () => {
    expect(groupByWorkDate([])).toEqual([]);
  });

  it('同一 work_date の項目を1グループへまとめ、入力順を保つ', () => {
    const a = item({ work_date: '2026-07-20', store_id: 1, start_time: '10:00:00' });
    const b = item({ work_date: '2026-07-20', store_id: 2, start_time: '18:00:00' });

    const result = groupByWorkDate([a, b]);

    expect(result).toHaveLength(1);
    expect(result[0].workDate).toBe('2026-07-20');
    expect(result[0].items).toEqual([a, b]);
  });

  it('異なる work_date は日付昇順で複数グループになる', () => {
    const later = item({ work_date: '2026-07-22' });
    const earlier = item({ work_date: '2026-07-19' });

    const result = groupByWorkDate([later, earlier]);

    expect(result.map(g => g.workDate)).toEqual(['2026-07-19', '2026-07-22']);
  });
});
