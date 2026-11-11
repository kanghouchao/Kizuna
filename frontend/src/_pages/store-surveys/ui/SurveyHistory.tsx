import Link from 'next/link';
import { surveyApi } from '@/entities/survey';
import { useListPage } from '@/shared/lib';
import { Button, Pager, RegionError } from '@/shared/ui';
import { answerHref } from '../lib/surveyHref';
import { operationLabels, time, useGuardedRead } from './surveyUi';
import { useCurrentStoreId } from '@/entities/user';

export function SurveyHistory({
  target,
  denied,
}: {
  target: { sid: string; rid: string } | { aid: string };
  denied: (message: string) => void;
}) {
  const storeId = useCurrentStoreId();
  const guard = useGuardedRead(denied);
  const history = useListPage(page => guard(() => surveyApi.history(target, page)));
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold">操作履歴</h2>
      {history.failed ? (
        <RegionError message="履歴を取得できませんでした" onRetry={() => void history.reload()} />
      ) : history.isLoading ? (
        <p>読み込み中...</p>
      ) : (
        <ol className="space-y-3">
          {history.rows.map(row => (
            <li key={row.id} className="rounded-lg border p-4 space-y-2">
              <p>
                {operationLabels[row.type]} ／ {time(row.created_at)} ／ {row.actor.display_name}
              </p>
              {row.reason && <p className="whitespace-pre-wrap wrap-anywhere">{row.reason}</p>}
              {row.related_response_id && (
                <Button
                  variant="outline"
                  render={<Link href={answerHref(storeId, row.related_response_id)} />}
                >
                  関連回答 #{row.related_response_id}
                </Button>
              )}
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
