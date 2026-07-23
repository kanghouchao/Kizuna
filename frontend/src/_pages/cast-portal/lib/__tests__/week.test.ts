import { formatEndTime, formatTime, toDateStr, weekDates, weekStart } from '../week';

describe('week', () => {
  describe('toDateStr', () => {
    it('ローカル日付を yyyy-MM-dd に整形する', () => {
      expect(toDateStr(new Date(2026, 6, 8))).toBe('2026-07-08');
    });
    it('月・日を 2 桁ゼロ埋めする', () => {
      expect(toDateStr(new Date(2026, 0, 5))).toBe('2026-01-05');
    });
  });

  describe('weekStart', () => {
    it('日曜日を起点にすでに日曜なら同日を返す', () => {
      // 2026-07-19 は日曜日
      expect(toDateStr(weekStart(new Date(2026, 6, 19)))).toBe('2026-07-19');
    });
    it('週の途中の日から直近の過去の日曜日を返す', () => {
      // 2026-07-22 は水曜日 → 直近の日曜は 2026-07-19
      expect(toDateStr(weekStart(new Date(2026, 6, 22)))).toBe('2026-07-19');
    });
    it('土曜日からも同じ週の日曜日を返す', () => {
      // 2026-07-25 は土曜日
      expect(toDateStr(weekStart(new Date(2026, 6, 25)))).toBe('2026-07-19');
    });
  });

  describe('weekDates', () => {
    it('起点から連続7日分の日付を返す', () => {
      const dates = weekDates(new Date(2026, 6, 19));
      expect(dates).toHaveLength(7);
      expect(dates.map(toDateStr)).toEqual([
        '2026-07-19',
        '2026-07-20',
        '2026-07-21',
        '2026-07-22',
        '2026-07-23',
        '2026-07-24',
        '2026-07-25',
      ]);
    });
  });

  describe('formatTime', () => {
    it('秒付き時刻を HH:mm に切り詰める', () => {
      expect(formatTime('18:00:00')).toBe('18:00');
    });
  });

  describe('formatEndTime', () => {
    it('00:00 終了は 24:00 として表示する', () => {
      expect(formatEndTime('00:00:00')).toBe('24:00');
    });
    it('00:00 以外は HH:mm のまま表示する', () => {
      expect(formatEndTime('23:00:00')).toBe('23:00');
    });
  });
});
