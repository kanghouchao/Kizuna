import { Page } from './types';

// 一覧ページ外殻が扱う正規化ページ型。page は 0 起点で統一する
export interface PageResult<T> {
  rows: T[];
  page: number;
  pageCount: number;
  total: number;
}

export function fromSpringPage<T>(raw: Page<T>): PageResult<T> {
  return {
    rows: raw.content,
    page: raw.number,
    pageCount: raw.total_pages,
    total: raw.total_elements,
  };
}

// Spring Data の page クエリパラメータは 0 起点なのでそのまま渡せる
export function toSpringPageParams(page: number, size: number): { page: number; size: number } {
  return { page, size };
}
