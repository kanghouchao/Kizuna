import { Page, fromSpringPage, toSpringPageParams } from '@/shared/api';

describe('fromSpringPage', () => {
  it('0 起点の Spring Data Page をそのまま正規化する', () => {
    const raw: Page<{ id: string }> = {
      content: [{ id: 'a' }, { id: 'b' }],
      total_pages: 5,
      total_elements: 42,
      size: 10,
      number: 0,
    };

    expect(fromSpringPage(raw)).toEqual({
      rows: [{ id: 'a' }, { id: 'b' }],
      page: 0,
      pageCount: 5,
      total: 42,
    });
  });

  it('途中ページの number をそのまま page として渡す', () => {
    const raw: Page<{ id: string }> = {
      content: [{ id: 'c' }],
      total_pages: 5,
      total_elements: 42,
      size: 10,
      number: 3,
    };

    expect(fromSpringPage(raw).page).toBe(3);
  });
});

describe('toSpringPageParams', () => {
  it('0 起点の page をそのまま渡す', () => {
    expect(toSpringPageParams(2, 20)).toEqual({ page: 2, size: 20 });
  });
});
