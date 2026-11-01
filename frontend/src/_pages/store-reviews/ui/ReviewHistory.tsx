import { reviewApi } from '@/entities/review';
import { useListPage } from '@/shared/lib';
import { Pager, RegionError } from '@/shared/ui';
import { operations, time, publicationSummary } from './labels';
export function ReviewHistory({ id }: { id: string }) {
  const history = useListPage(page => reviewApi.history(id, page));
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold">操作履歴</h2>
      {history.failed ? (
        <RegionError message="履歴を取得できませんでした" onRetry={() => void history.reload()} />
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
              {h.type === 'PUBLICATION_CHANGED' && <p>{publicationSummary(h.after_publication)}</p>}
            </li>
          ))}
        </ol>
      )}
      {!history.failed && (
        <Pager
          page={history.page}
          pageCount={history.pageCount}
          total={history.total}
          rowCount={history.rows.length}
          disabled={history.isLoading}
          onPageChange={page => void history.onPageChange(page)}
        />
      )}
    </section>
  );
}
