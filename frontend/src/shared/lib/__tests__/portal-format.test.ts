import {
  formatDate,
  formatDateTime,
  formatDuration,
  formatMinutes,
  formatMoney,
  formatMoneyChange,
  formatPointChange,
  formatPoints,
  formatRevision,
  formatTimeRange,
  tokyoDate,
} from '@/shared/lib';

describe('formatDate', () => {
  it('今年の日付は年を省き、曜日を全角括弧で添える', () => {
    expect(formatDate('2026-11-05', '2026-11-07')).toBe('11/5（木）');
  });

  it('今年でなければ年を付ける（年またぎ）', () => {
    expect(formatDate('2025-11-05', '2026-01-02')).toBe('2025/11/5（水）');
  });
});

describe('formatTimeRange', () => {
  it('同じ日の時間帯を波ダッシュでつなぐ', () => {
    expect(formatTimeRange('19:00:00', '21:00:00')).toBe('19:00〜21:00');
  });

  it('終了が開始以前なら日をまたぐとみなし「翌」を付けて時の 0 埋めを外す', () => {
    expect(formatTimeRange('19:00', '02:00')).toBe('19:00〜翌2:00');
    expect(formatTimeRange('21:00', '00:00')).toBe('21:00〜翌0:00');
    expect(formatTimeRange('10:00', '10:00')).toBe('10:00〜翌10:00');
  });

  it('時は 0 埋めしない', () => {
    expect(formatTimeRange('09:30', '17:05')).toBe('9:30〜17:05');
  });
});

describe('formatMinutes / formatDuration', () => {
  it('サービスの長さは分だけで出す', () => {
    expect(formatMinutes(90)).toBe('90分');
  });

  it('出勤などの経過時間は時間と分で出し、0 の単位は省く', () => {
    expect(formatDuration(90)).toBe('1時間30分');
    expect(formatDuration(270)).toBe('4時間30分');
    expect(formatDuration(120)).toBe('2時間');
    expect(formatDuration(45)).toBe('45分');
    expect(formatDuration(0)).toBe('0分');
  });
});

describe('formatDateTime', () => {
  it('時差の無い日時はそのまま日付と時刻にする', () => {
    expect(formatDateTime('2026-11-05T19:00:00', '2026-11-07')).toBe('11/5（木）19:00');
  });

  it('時差付きの瞬間は東京の時刻に直す（UTC 15 時は翌日の 0 時）', () => {
    expect(formatDateTime('2026-11-04T15:30:00Z', '2026-11-07')).toBe('11/5（木）0:30');
  });

  it('今年でなければ年を付ける', () => {
    expect(formatDateTime('2025-11-05T19:00:00+09:00', '2026-11-07')).toBe('2025/11/5（水）19:00');
  });
});

describe('tokyoDate', () => {
  it('瞬間を東京の暦日にする', () => {
    expect(tokyoDate(new Date('2026-12-31T15:00:00Z'))).toBe('2027-01-01');
  });
});

describe('formatMoney / formatMoneyChange', () => {
  it('円記号と 3 桁区切りを付ける', () => {
    expect(formatMoney(12000)).toBe('¥12,000');
    expect(formatMoney(0)).toBe('¥0');
  });

  it('負の金額は全角のマイナス記号（U+2212）を円記号の前に置く', () => {
    expect(formatMoney(-1000)).toBe('−¥1,000');
  });

  it('変更は前後を矢印でつなぐ', () => {
    expect(formatMoneyChange(8000, 10000)).toBe('¥8,000 → ¥10,000');
  });
});

describe('formatRevision', () => {
  it('版を「第N版」にする', () => {
    expect(formatRevision(2)).toBe('第2版');
  });
});

describe('formatPoints / formatPointChange', () => {
  it('ポイントは 3 桁区切りと単位で出す', () => {
    expect(formatPoints(1200)).toBe('1,200 ポイント');
  });

  it('増減は符号を付け、減算はマイナス記号（U+2212）、0 は符号なしで出す', () => {
    expect(formatPointChange(500)).toBe('+500 ポイント');
    expect(formatPointChange(-1300)).toBe('−1,300 ポイント');
    expect(formatPointChange(0)).toBe('0 ポイント');
  });
});
