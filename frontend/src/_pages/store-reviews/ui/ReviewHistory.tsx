import { reviewApi } from '@/entities/review';
import { useCursorList } from '@/shared/lib';
import { Button, RegionError } from '@/shared/ui';
import { operations, time } from './labels';
export function ReviewHistory({ id }: { id: string }) {
  const history = useCursorList(cursor => reviewApi.history(id, cursor));
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold">操作履歴</h2>
      {history.failed ? (
        <RegionError message="履歴を取得できませんでした" onRetry={history.reload} />
      ) : history.isLoading ? (
        <p>読み込み中...</p>
      ) : history.rows.length === 0 ? (
        <p>操作履歴はありません</p>
      ) : (
        <ol className="space-y-3">
          {history.rows.map(h => (
            <li key={h.id} className="rounded-lg border p-4 space-y-2">
              <p>
                {operations[h.type]} / {time(h.created_at)}
              </p>
              <p>
                {h.actor.display_name}・版 {h.after_version}
              </p>
              {h.reason && <p className="whitespace-pre-wrap wrap-anywhere">{h.reason}</p>}
              {h.related_review_id && <p>関連口コミ #{h.related_review_id}</p>}
            </li>
          ))}
        </ol>
      )}
      {history.hasMore && !history.failed && (
        <Button variant="outline" disabled={history.isLoading} onClick={history.loadMore}>
          履歴をさらに表示
        </Button>
      )}
    </section>
  );
}
