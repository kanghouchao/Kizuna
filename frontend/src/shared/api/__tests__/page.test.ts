import {
  Page,
  PaginatedResponse,
  fromLaravelPage,
  fromSpringPage,
  toLaravelPageParams,
  toSpringPageParams,
} from '@/shared/api';

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

describe('fromLaravelPage', () => {
  it('1 起点の current_page を 0 起点の page に変換する', () => {
    const raw: PaginatedResponse<{ id: string }> = {
      data: [{ id: 'a' }, { id: 'b' }],
      current_page: 1,
      from: 1,
      last_page: 3,
      per_page: 10,
      to: 2,
      total: 25,
      first_page_url: '',
      last_page_url: '',
      next_page_url: null,
      prev_page_url: null,
    };

    expect(fromLaravelPage(raw)).toEqual({
      rows: [{ id: 'a' }, { id: 'b' }],
      page: 0,
      pageCount: 3,
      total: 25,
    });
  });

  it('途中ページの current_page から 1 を引いた値を page とする', () => {
    const raw: PaginatedResponse<{ id: string }> = {
      data: [{ id: 'c' }],
      current_page: 3,
      from: 21,
      last_page: 3,
      per_page: 10,
      to: 25,
      total: 25,
      first_page_url: '',
      last_page_url: '',
      next_page_url: null,
      prev_page_url: null,
    };

    expect(fromLaravelPage(raw).page).toBe(2);
  });
});

describe('toSpringPageParams', () => {
  it('0 起点の page をそのまま渡す', () => {
    expect(toSpringPageParams(2, 20)).toEqual({ page: 2, size: 20 });
  });
});

describe('toLaravelPageParams', () => {
  it('0 起点の page を 1 起点の page / per_page に変換する', () => {
    expect(toLaravelPageParams(0, 10)).toEqual({ page: 1, per_page: 10 });
  });

  it('途中ページも 1 を足した値になる', () => {
    expect(toLaravelPageParams(2, 10)).toEqual({ page: 3, per_page: 10 });
  });
});
