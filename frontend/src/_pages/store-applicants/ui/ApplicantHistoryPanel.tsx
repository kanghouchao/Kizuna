'use client';
import { applicantApi, applicantStatusLabels } from '@/entities/applicant';
import { useCursorList } from '@/shared/lib';
import { Button, RegionError } from '@/shared/ui';
export function ApplicantHistoryPanel({ id }: { id: string }) {
  const history = useCursorList(cursor => applicantApi.history(id, cursor));
  return (
    <section className="rounded-xl border bg-card p-6 space-y-4">
      <h2 className="text-lg font-semibold">選考履歴</h2>
      {history.failed ? (
        <RegionError message="選考履歴の取得に失敗しました" onRetry={history.reload} />
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
          {history.hasMore && (
            <Button variant="outline" disabled={history.isLoading} onClick={history.loadMore}>
              過去の履歴を表示
            </Button>
          )}
        </>
      )}
    </section>
  );
}
