import type { PageResult } from '@/shared/api';

/** 最後のページまで、または読み進める必要が無くなるまでページを読む。 */
export async function collectPages<T>(
  fetchPage: (page: number) => Promise<PageResult<T>>,
  needsMore: (pageRows: T[]) => boolean = () => true
): Promise<T[]> {
  const rows: T[] = [];
  for (let page = 0; ; page++) {
    const result = await fetchPage(page);
    rows.push(...result.rows);
    if (page + 1 >= result.pageCount || !needsMore(result.rows)) return rows;
  }
}
