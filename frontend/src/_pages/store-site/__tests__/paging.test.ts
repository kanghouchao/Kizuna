import { pageHref, pageIndexOf, pagerRange } from '../lib/paging';
import { memberReservationUrl } from '../lib/memberReservation';

const result = (number: number, totalPages: number, contentLength = 10) => ({
  content: Array.from({ length: contentLength }),
  number,
  size: 10,
  total_pages: totalPages,
  total_elements: totalPages * 10,
});

describe('pageHref', () => {
  it('先頭ページは page を付けず、以降は 1 起点で付ける', () => {
    expect(pageHref('/news', 0)).toBe('/news');
    expect(pageHref('/news', 2)).toBe('/news?page=3');
  });
});

describe('pageIndexOf', () => {
  it('1 起点の page を 0 起点にする', () => {
    expect(pageIndexOf('3')).toBe(2);
    expect(pageIndexOf(['2', '5'])).toBe(1);
  });

  it('数値でない・1 未満・未指定は先頭ページとして扱う', () => {
    expect(pageIndexOf(undefined)).toBe(0);
    expect(pageIndexOf('abc')).toBe(0);
    expect(pageIndexOf('0')).toBe(0);
    expect(pageIndexOf('1.5')).toBe(0);
  });
});

describe('pagerRange', () => {
  it('番号窓は最大 5 件で、表示中のページを中央に置く', () => {
    expect(pagerRange(result(4, 9)).pages).toEqual([2, 3, 4, 5, 6]);
  });

  it('端では番号窓を寄せ、進めない向きを閉じる', () => {
    expect(pagerRange(result(0, 9))).toMatchObject({
      pages: [0, 1, 2, 3, 4],
      hasPrev: false,
      hasNext: true,
    });
    expect(pagerRange(result(8, 9, 3))).toMatchObject({
      pages: [4, 5, 6, 7, 8],
      hasPrev: true,
      hasNext: false,
      from: 81,
      to: 83,
    });
  });
});

describe('memberReservationUrl', () => {
  it('会員ポータルの予約入口へ、訪問中の店舗ドメインを渡す', () => {
    expect(memberReservationUrl('https://kizuna.test', 'store1.kizuna.test')).toBe(
      'https://kizuna.test/member/reservations/new?store=store1.kizuna.test'
    );
  });
});
