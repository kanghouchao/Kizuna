'use client';

import type { ReactNode } from 'react';
import { Card, CardContent, Pager, RegionError, TableCard } from '@/shared/ui';

export interface ListPageSearch {
  /** 検索カードの中身（入力欄・ボタンなど） */
  content: ReactNode;
  /** フォーム submit（Enter 提出含む）で発火する */
  onSearch: () => void;
}

export interface ListPageState {
  rows: unknown[];
  isLoading: boolean;
  /** 取得に失敗した状態。行が無いだけの空表示（＝0 件）と区別する */
  failed: boolean;
  /** 0 起点。ページングしない一覧（配列形の API）では以下 4 つを省略する */
  page?: number;
  pageCount?: number;
  total?: number;
  onPageChange?: (page: number) => void;
}

interface ListPageProps {
  title: string;
  description?: string;
  actions?: ReactNode;
  search?: ListPageSearch;
  state: ListPageState;
  emptyMessage: ReactNode;
  /** 取得に失敗した一覧が名乗る文言。空表示と同じく、外殻が置き場所を持つ */
  errorMessage: string;
  /** 失敗表示の再試行。一覧は必ず自分の回復導線を持つ */
  onRetry: () => void;
  /** テーブルの markup。汎用テーブル化はせず、各ページの責務のまま受け取る */
  children: ReactNode;
}

/**
 * 一覧ページ外殻（外枠・検索カード・テーブルカード包装・loading/empty 分岐・ページネーション）。
 * widgets/page-header は同一レイヤーの他 slice のため import できず（Steiger fsd/forbidden-imports）、
 * 見出し markup はここに直接複製している。
 */
export function ListPage({
  title,
  description,
  actions,
  search,
  state,
  emptyMessage,
  errorMessage,
  onRetry,
  children,
}: ListPageProps) {
  const {
    rows,
    isLoading,
    failed,
    page = 0,
    pageCount = 1,
    total = rows.length,
    onPageChange,
  } = state;
  const isEmpty = !isLoading && !failed && rows.length === 0;

  const searchCard = search && (
    <Card>
      <CardContent className="flex flex-col md:flex-row md:items-center gap-4">
        {search.content}
      </CardContent>
    </Card>
  );

  // 削除等で現在ページの rows が空になっても（total は残っていても）前ページへ戻れるよう、
  // ページ送りは isEmpty 分岐の外側で常に描画する
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">{title}</h1>
          {description && <p className="text-sm text-muted-foreground mt-1">{description}</p>}
        </div>
        {actions && <div className="flex items-center gap-3">{actions}</div>}
      </div>

      {search && (
        <form
          onSubmit={e => {
            e.preventDefault();
            search.onSearch();
          }}
        >
          {searchCard}
        </form>
      )}

      <TableCard>
        {/* 失敗は空表示より先に見る。順を逆にすると、読めなかった一覧が「登録されていません」を
            名乗り、画面が事実でないことを言う */}
        {isLoading ? (
          <div className="p-8 text-center text-muted-foreground">読み込み中...</div>
        ) : failed ? (
          <RegionError message={errorMessage} onRetry={onRetry} className="justify-center p-8" />
        ) : isEmpty ? (
          <div className="p-8 text-center text-muted-foreground">{emptyMessage}</div>
        ) : (
          children
        )}

        {onPageChange && !isLoading && (
          <Pager
            page={page}
            pageCount={pageCount}
            total={total}
            rowCount={rows.length}
            onPageChange={onPageChange}
            className="px-6 py-4 border-t"
          />
        )}
      </TableCard>
    </div>
  );
}
