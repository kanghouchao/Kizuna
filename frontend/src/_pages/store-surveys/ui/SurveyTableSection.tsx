import type { ReactNode } from 'react';
import { Card, CardContent, Pager, RegionError, TableCard } from '@/shared/ui';

/** 版の詳細の中に置く一覧の節。画面の見出しは h1 が持つので、節は h2 で名乗り Pager で送る。 */
export function SurveyTableSection({
  title,
  description,
  actions,
  search,
  state,
  emptyMessage,
  errorMessage,
  children,
}: {
  title: string;
  description: string;
  actions?: ReactNode;
  search?: { content: ReactNode; onSearch: () => void };
  state: {
    rows: unknown[];
    isLoading: boolean;
    failed: boolean;
    page: number;
    pageCount: number;
    total: number;
    reload: () => Promise<void> | void;
    onPageChange: (page: number) => Promise<void> | void;
  };
  emptyMessage: string;
  errorMessage: string;
  children: ReactNode;
}) {
  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold">{title}</h2>
          <p className="text-sm text-muted-foreground mt-1">{description}</p>
        </div>
        {actions}
      </div>
      {search && (
        <form
          onSubmit={e => {
            e.preventDefault();
            search.onSearch();
          }}
        >
          <Card>
            <CardContent className="flex flex-col md:flex-row md:items-center gap-4">
              {search.content}
            </CardContent>
          </Card>
        </form>
      )}
      <TableCard>
        {state.isLoading ? (
          <div className="p-8 text-center text-muted-foreground">読み込み中...</div>
        ) : state.failed ? (
          <RegionError
            message={errorMessage}
            onRetry={() => void state.reload()}
            className="justify-center p-8"
          />
        ) : state.rows.length === 0 ? (
          <div className="p-8 text-center text-muted-foreground">{emptyMessage}</div>
        ) : (
          children
        )}
        {!state.isLoading && !state.failed && (
          <Pager
            page={state.page}
            pageCount={state.pageCount}
            total={state.total}
            rowCount={state.rows.length}
            onPageChange={page => void state.onPageChange(page)}
            className="px-6 py-4 border-t"
          />
        )}
      </TableCard>
    </section>
  );
}
