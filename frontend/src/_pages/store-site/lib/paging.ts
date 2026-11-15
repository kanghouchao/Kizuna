import type { Page } from '@/shared/api';

export const ARTICLES_PER_PAGE = 10;
export const REVIEWS_PER_PAGE = 10;

/** 0 起点のページ番号を、1 起点の page を付けた一覧の URL にする。先頭ページは page を付けない。 */
export const pageHref = (basePath: string, index: number) =>
  index === 0 ? basePath : `${basePath}?page=${index + 1}`;

/** URL の page（1 起点）を 0 起点のページ番号にする。数値でない・1 未満は先頭ページとして扱う。 */
export function pageIndexOf(page: string | string[] | undefined): number {
  const requested = Number(Array.isArray(page) ? page[0] : page);
  return Number.isInteger(requested) && requested >= 1 ? requested - 1 : 0;
}

/** ページ送りが描く範囲: 表示中の件の位置、最大 5 件の番号窓、前後へ進めるか。 */
export function pagerRange(result: Page<unknown>) {
  const page = result.number;
  const pageCount = result.total_pages;
  const from = page * result.size + 1;
  const windowSize = Math.min(5, pageCount);
  const windowStart = Math.max(
    0,
    Math.min(page - Math.floor(windowSize / 2), pageCount - windowSize)
  );
  return {
    page,
    from,
    to: from + result.content.length - 1,
    pages: Array.from({ length: windowSize }, (_, i) => windowStart + i),
    hasPrev: page > 0,
    hasNext: page < pageCount - 1,
  };
}
