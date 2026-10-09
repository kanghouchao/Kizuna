import { useState } from 'react';
import { useParams } from 'next/navigation';
import { advertisingApi, type Change, categoryLabels, type Cost } from '../api/advertising';
import { fromCursorPage } from '@/shared/api';
import { storePath, useCursorList, useResource } from '@/shared/lib';
import {
  Button,
  RegionError,
  Table,
  TableCard,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';
const actions = { CREATED: '登録', UPDATED: '変更', DELETED: '削除', COPIED: '前月コピー' };
export function CostHistory({ month }: { month: string }) {
  const { storeId } = useParams<{ storeId: string }>();
  const list = useCursorList<Change>(async cursor =>
    fromCursorPage(await advertisingApi.changes(month, cursor))
  );
  const [selected, setSelected] = useState<string | null>(null);
  const detail = useResource(selected ? () => advertisingApi.change(month, selected) : null, [
    selected,
    month,
  ]);
  return (
    <section aria-label="変更履歴" className="space-y-4">
      <h2 className="text-lg font-semibold">変更履歴</h2>
      {list.isLoading && <p>読み込み中...</p>}
      {list.failed ? (
        <RegionError message="履歴を取得できませんでした。" onRetry={list.reload} />
      ) : (
        <>
          <TableCard>
            <Table>
              <TableHeader>
                <TableRow>
                  {['日時', '操作', '操作者ID', '費用ID', '詳細'].map(h => (
                    <TableHead key={h}>{h}</TableHead>
                  ))}
                </TableRow>
              </TableHeader>
              <TableBody>
                {list.rows.map(c => (
                  <TableRow key={c.id}>
                    <TableCell>{new Date(c.occurred_at).toLocaleString('ja-JP')}</TableCell>
                    <TableCell>{actions[c.action]}</TableCell>
                    <TableCell>{c.actor_id}</TableCell>
                    <TableCell>{c.cost_id}</TableCell>
                    <TableCell>
                      <Button variant="outline" onClick={() => setSelected(c.id)}>
                        履歴を表示
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableCard>
          {!list.isLoading && !list.rows.length && <p>変更履歴はありません。</p>}
          {list.hasMore && (
            <Button variant="outline" disabled={list.isLoading} onClick={list.loadMore}>
              続きを表示
            </Button>
          )}
        </>
      )}
      {selected && (
        <div className="rounded-lg border p-4 space-y-3">
          {detail.isLoading ? (
            <p>詳細を読み込み中...</p>
          ) : detail.failure === 'notFound' ? (
            <RegionError
              message="変更履歴が見つかりません。"
              fallback={{
                href: storePath(storeId, '/advertising-costs'),
                label: '広告費一覧へ戻る',
              }}
            />
          ) : detail.failure ? (
            <RegionError
              message="履歴詳細を取得できませんでした。"
              onRetry={() => void detail.reload()}
            />
          ) : (
            detail.data && (
              <>
                <p>理由：{detail.data.reason ?? '初回登録'}</p>
                {detail.data.source_cost_id && (
                  <p>コピー元の費用ID：{detail.data.source_cost_id}</p>
                )}
                <div className="grid gap-4 sm:grid-cols-2">
                  <Snapshot title="変更前" cost={detail.data.before} />
                  <Snapshot title="変更後" cost={detail.data.after} />
                </div>
              </>
            )
          )}
        </div>
      )}
    </section>
  );
}
function Snapshot({ title, cost }: { title: string; cost: Cost | null }) {
  return (
    <section>
      <h3 className="font-semibold">{title}</h3>
      {cost ? (
        <dl className="space-y-1 break-words">
          {Object.entries({
            区分: categoryLabels[cost.category],
            媒体: cost.media_name,
            広告会社: cost.agency_name ?? '未設定',
            プラン: cost.plan_name ?? '未設定',
            問い合わせ人数: cost.inquiry_count === null ? '未計測' : `${cost.inquiry_count}人`,
            金額: `${cost.amount.toLocaleString()}円`,
            版: cost.version,
          }).map(([k, v]) => (
            <div key={k}>
              <dt className="text-sm text-muted-foreground">{k}</dt>
              <dd>{v}</dd>
            </div>
          ))}
        </dl>
      ) : (
        <p>記録なし</p>
      )}
    </section>
  );
}
