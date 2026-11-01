'use client';

import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react';
import { cn } from '@/shared/lib/utils';
import { Button } from './button';

interface PagerProps {
  /** 0 起点 */
  page: number;
  pageCount: number;
  total: number;
  /** 現在ページの行数。何件目から何件目かの表示に使う */
  rowCount: number;
  disabled?: boolean;
  onPageChange: (page: number) => void;
  className?: string;
}

/**
 * 全一覧で共通のページ送り（件数の範囲・番号つきのページ・前後の移動。狭い幅では前後の移動だけ）。
 *
 * pageCount が 1 以下に潰れても page > 0 なら出す。削除等で現在ページが範囲外になったとき、戻る手段を残すため。
 */
export function Pager({
  page,
  pageCount,
  total,
  rowCount,
  disabled = false,
  onPageChange,
  className,
}: PagerProps) {
  if (pageCount <= 1 && page <= 0) return null;

  // 非最終ページは常に満杯という offset pagination の前提から、rowCount だけで件数範囲を算出できる
  const isLastPage = page === pageCount - 1;
  const from = isLastPage ? total - rowCount + 1 : page * rowCount + 1;
  const to = isLastPage ? total : from + rowCount - 1;

  const windowSize = Math.min(5, pageCount);
  const windowStart = Math.max(
    0,
    Math.min(page - Math.floor(windowSize / 2), pageCount - windowSize)
  );

  return (
    <nav
      aria-label="ページネーション"
      className={cn('flex items-center justify-between', className)}
    >
      <div className="flex-1 flex justify-between sm:hidden">
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page - 1)}
          disabled={disabled || page <= 0}
        >
          前へ
        </Button>
        <Button
          variant="outline"
          size="sm"
          className="ml-3"
          onClick={() => onPageChange(page + 1)}
          disabled={disabled || page >= pageCount - 1}
        >
          次へ
        </Button>
      </div>
      <div className="hidden sm:flex-1 sm:flex sm:items-center sm:justify-between">
        <p className="text-sm text-muted-foreground">
          {rowCount > 0 ? `${total} 件中 ${from}-${to} を表示` : `${total} 件`}
        </p>
        <div className="flex gap-1">
          <Button
            variant="outline"
            size="icon-sm"
            aria-label="前へ"
            onClick={() => onPageChange(page - 1)}
            disabled={disabled || page <= 0}
          >
            <ChevronLeftIcon />
          </Button>
          {Array.from({ length: windowSize }, (_, i) => {
            const buttonPage = windowStart + i;
            return (
              <Button
                key={buttonPage}
                variant="outline"
                size="sm"
                aria-current={buttonPage === page ? 'page' : undefined}
                className={
                  buttonPage === page
                    ? 'border-primary bg-primary/10 text-primary-strong'
                    : undefined
                }
                disabled={disabled}
                onClick={() => onPageChange(buttonPage)}
              >
                {buttonPage + 1}
              </Button>
            );
          })}
          <Button
            variant="outline"
            size="icon-sm"
            aria-label="次へ"
            onClick={() => onPageChange(page + 1)}
            disabled={disabled || page >= pageCount - 1}
          >
            <ChevronRightIcon />
          </Button>
        </div>
      </div>
    </nav>
  );
}
