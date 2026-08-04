import { CursorPage, Page } from './types';

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

/**
 * 行が処理で消えていく作業キュー型一覧の正規化ページ型。
 *
 * 総件数を持たないのは、続きの位置さえ辿れれば到達性が保てるため。位置を「何件目か」で持つと、
 * 手前の行が処理で消えた分だけ後続が繰り上がり、続きを取った時点で境界の行を飛ばす。
 */
export interface CursorPageResult<T> {
  rows: T[];
  /** 続きの取得に渡す位置。続きが無ければ null */
  nextCursor: string | null;
}

export function fromCursorPage<T>(raw: CursorPage<T>): CursorPageResult<T> {
  return {
    rows: raw.content,
    nextCursor: raw.next_cursor ?? null,
  };
}

// Spring Data の page クエリパラメータは 0 起点なのでそのまま渡せる
export function toSpringPageParams(page: number, size: number): { page: number; size: number } {
  return { page, size };
}
