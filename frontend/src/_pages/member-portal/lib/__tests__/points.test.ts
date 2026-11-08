import { formatPointChange, formatPoints } from '../points';

describe('formatPoints', () => {
  it('残高は桁区切りと単位で出す', () => {
    expect(formatPoints(1200)).toBe('1,200 ポイント');
    expect(formatPoints(0)).toBe('0 ポイント');
  });
});

describe('formatPointChange', () => {
  it('増減は符号を付け、減算はマイナス記号（U+2212）で出す', () => {
    expect(formatPointChange(500)).toBe('+500 ポイント');
    expect(formatPointChange(-1300)).toBe('−1,300 ポイント');
  });
});
