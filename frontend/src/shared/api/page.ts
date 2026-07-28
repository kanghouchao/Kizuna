import { Page, PaginatedResponse } from './types';

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

export function fromLaravelPage<T>(raw: PaginatedResponse<T>): PageResult<T> {
  return {
    rows: raw.data,
    page: raw.current_page - 1,
    pageCount: raw.last_page,
    total: raw.total,
  };
}

// Spring Data の page クエリパラメータは 0 起点なのでそのまま渡せる
export function toSpringPageParams(page: number, size: number): { page: number; size: number } {
  return { page, size };
}

// /platform/stores の Laravel 風封筒は 1 起点
export function toLaravelPageParams(
  page: number,
  size: number
): { page: number; per_page: number } {
  return { page: page + 1, per_page: size };
}
