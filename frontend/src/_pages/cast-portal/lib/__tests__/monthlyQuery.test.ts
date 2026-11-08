import { monthlyHref, queryMonth } from '../monthlyQuery';

describe('queryMonth', () => {
  it.each([
    ['2026-11', '2026-11'],
    ['2025-12', '2025-12'],
    ['2025-11', '2026-11'],
    ['2026-12', '2026-11'],
    ['2026-1', '2026-11'],
    ['2026-00', '2026-11'],
    ['', '2026-11'],
    [null, '2026-11'],
  ])('URL の %p は %p を開く（選べるのは今月から遡る 12 か月）', (value, expected) => {
    expect(queryMonth(value, '2026-11')).toBe(expected);
  });
});

it('monthlyHref は店舗と月を検索パラメータに載せる', () => {
  expect(monthlyHref(3, '2026-10')).toBe('/cast/remunerations/monthly?store_id=3&month=2026-10');
});
