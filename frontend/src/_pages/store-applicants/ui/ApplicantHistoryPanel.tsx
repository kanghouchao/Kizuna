'use client';
import { applicantApi, applicantStatusLabels } from '@/entities/applicant';
import { useListPage } from '@/shared/lib';
import { Pager, RegionError } from '@/shared/ui';
export function ApplicantHistoryPanel({ id }: { id: string }) {
  const history = useListPage(page => applicantApi.history(id, page));
  return (
    <section className="rounded-xl border bg-card p-6 space-y-4">
      <h2 className="text-lg font-semibold">選考履歴</h2>
      {history.failed ? (
        <RegionError message="選考履歴の取得に失敗しました" onRetry={() => void history.reload()} />
      ) : (
        <>
          <ol className="space-y-4">
            {history.rows.map(row => (
              <li key={row.id} className="rounded-lg border p-4 space-y-1">
                <div className="font-medium">
                  {row.previous_status ? `${applicantStatusLabels[row.previous_status]} → ` : ''}
                  {applicantStatusLabels[row.new_status]}
                </div>
                <p className="whitespace-pre-wrap break-words">{row.reason}</p>
                <p className="text-sm text-muted-foreground">
                  {new Date(row.created_at).toLocaleString('ja-JP')}・操作ユーザー ID {row.actor_id}
                </p>
              </li>
            ))}
          </ol>
          {history.isLoading && <p>読み込み中...</p>}
          <Pager
            page={history.page}
            pageCount={history.pageCount}
            total={history.total}
            rowCount={history.rows.length}
            disabled={history.isLoading}
            onPageChange={page => void history.onPageChange(page)}
          />
        </>
      )}
    </section>
  );
}
