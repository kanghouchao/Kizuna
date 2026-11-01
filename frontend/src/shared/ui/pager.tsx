'use client';

import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react';
import { Button } from './button';

interface PagerProps {
  page: number;
  pageCount: number;
  disabled?: boolean;
  onPageChange: (page: number) => void;
}

/**
 * 区画の中に置く小さなページ送り（0 始まりの page）。
 *
 * pageCount が 1 以下に潰れても page > 0 なら出す。削除等で現在ページが範囲外になったとき、戻る手段を残すため。
 */
export function Pager({ page, pageCount, disabled = false, onPageChange }: PagerProps) {
  if (pageCount <= 1 && page <= 0) return null;
  return (
    <nav aria-label="ページ送り" className="flex items-center justify-end gap-1">
      <Button
        type="button"
        variant="outline"
        size="icon-sm"
        aria-label="前のページ"
        disabled={disabled || page <= 0}
        onClick={() => onPageChange(page - 1)}
      >
        <ChevronLeftIcon />
      </Button>
      <span className="text-muted-foreground px-2 text-sm">
        {page + 1} / {Math.max(pageCount, page + 1)}
      </span>
      <Button
        type="button"
        variant="outline"
        size="icon-sm"
        aria-label="次のページ"
        disabled={disabled || page >= pageCount - 1}
        onClick={() => onPageChange(page + 1)}
      >
        <ChevronRightIcon />
      </Button>
    </nav>
  );
}
