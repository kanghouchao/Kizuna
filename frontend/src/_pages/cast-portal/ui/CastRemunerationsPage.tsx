'use client';

import { useState } from 'react';
import {
  selfRemunerationApi,
  SelfRemunerationItem,
  SelfRemunerationSummary,
} from '@/entities/order';
import { isForbidden, isNotFound, useListPage, useResource, useCursorList } from '@/shared/lib';
import { Button, Card, CardContent, RegionError } from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';

const money = (amount: number) => `${amount.toLocaleString('ja-JP')} 円`;
const statuses = {
  CONFIRMED: '確定',
  IN_SERVICE: 'サービス中',
  COMPLETED: '完了',
  CANCELLED: '取消（報酬発生なし）',
};
const memberships = { ENROLLED: '在籍中', SUSPENDED: '停止中', WITHDRAWN: '退店' };

export function CastRemunerationsPage() {
  const [orderId, setOrderId] = useState<string | null>(null);
  return (
    <div className="mx-auto max-w-3xl space-y-4 p-4">
      {orderId && <h1 className="text-2xl font-bold">報酬明細</h1>}
      <p className="text-sm text-muted-foreground">
        予定・発生済みの固定報酬を確認できます。支払済み額ではありません。
      </p>
      {orderId ? (
        <RemunerationDetail key={orderId} id={orderId} onBack={() => setOrderId(null)} />
      ) : (
        <RemunerationList onOpen={setOrderId} />
      )}
    </div>
  );
}

function Failure({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  if (isForbidden(error)) return <p role="alert">報酬明細を確認する権限がありません。</p>;
  if (isNotFound(error)) return <p role="alert">対象の在籍または報酬明細が見つかりません。</p>;
  return <RegionError message="報酬明細の取得に失敗しました" onRetry={onRetry} />;
}

function RemunerationList({ onOpen }: { onOpen: (id: string) => void }) {
  const enrollments = useListPage(page => selfRemunerationApi.enrollments(page));
  const list = useListPage(
    (page, enrollment: string | undefined) => selfRemunerationApi.list(page, enrollment),
    undefined
  );
  const [selected, setSelected] = useState<string>();
  const choose = (id?: string) => {
    setSelected(id);
    void list.search(id);
  };
  const denied = isForbidden(list.error) || isNotFound(list.error);
  const enrollmentDenied = isForbidden(enrollments.error) || isNotFound(enrollments.error);
  return (
    <>
      <section aria-label="在籍で絞り込む" className="space-y-3">
        <Button
          variant={selected ? 'outline' : 'default'}
          aria-pressed={!selected}
          onClick={() => choose()}
        >
          すべての在籍
        </Button>
        {enrollmentDenied ? (
          <Failure error={enrollments.error} onRetry={() => void enrollments.reload()} />
        ) : (
          <ListPage
            title="在籍で絞り込む"
            state={enrollments}
            emptyMessage="在籍履歴はありません"
            errorMessage="在籍履歴の取得に失敗しました"
            onRetry={() => void enrollments.reload()}
          >
            <div className="flex flex-wrap gap-2 p-4">
              {enrollments.rows.map(e => (
                <Button
                  key={e.enrollment_id}
                  variant={selected === e.enrollment_id ? 'default' : 'outline'}
                  aria-pressed={selected === e.enrollment_id}
                  onClick={() => choose(e.enrollment_id)}
                >
                  {e.store_name}・{memberships[e.status]}
                  {e.ended_at ? `（${e.ended_at.slice(0, 10)}）` : ''}
                </Button>
              ))}
            </div>
          </ListPage>
        )}
      </section>
      <section aria-label="報酬一覧" className="space-y-3">
        {denied ? (
          <>
            <h1 className="text-2xl font-bold">報酬明細</h1>
            <Failure error={list.error} onRetry={() => void list.reload()} />
          </>
        ) : (
          <ListPage
            title="報酬明細"
            state={list}
            emptyMessage="報酬明細はありません"
            errorMessage="報酬明細の取得に失敗しました"
            onRetry={() => void list.reload()}
          >
            <div className="space-y-3">
              {list.rows.map(row => (
                <Card key={row.order_id}>
                  <CardContent className="space-y-3 p-4">
                    <Amounts row={row} />
                    <Button onClick={() => onOpen(row.order_id)}>詳細を確認</Button>
                  </CardContent>
                </Card>
              ))}
            </div>
          </ListPage>
        )}
      </section>
    </>
  );
}

function Amounts({ row }: { row: SelfRemunerationSummary }) {
  return (
    <>
      <h2 className="font-semibold">
        {row.store_name}・{row.business_date}
      </h2>
      <p>{row.completion_invalidated ? '無効化（有効報酬 0 円）' : statuses[row.status]}</p>
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
        <dt>原営業日</dt>
        <dd>{row.business_date}</dd>
        {row.completed_at && (
          <>
            <dt>原完了日時</dt>
            <dd className="break-all">{row.completed_at}</dd>
          </>
        )}
        <dt>約定報酬</dt>
        <dd>{money(row.agreed_remuneration)}</dd>
        <dt>予定報酬</dt>
        <dd>{money(row.planned_remuneration)}</dd>
        <dt>発生済み報酬</dt>
        <dd>{money(row.accrued_remuneration)}</dd>
      </dl>
    </>
  );
}

function Items({ items }: { items: SelfRemunerationItem[] }) {
  return (
    <ul className="space-y-3">
      {items.map((item, index) => (
        <li
          key={item.line_id ?? `${item.kind}-${item.service_id ?? index}`}
          className="space-y-1 rounded-lg border p-3 text-sm"
        >
          <p className="font-semibold">{item.name}</p>
          <p>
            顧客費用: {money(item.price)} / 固定報酬: {money(item.remuneration)}
          </p>
          {item.duration_minutes !== undefined && <p>{item.duration_minutes} 分</p>}
          {item.revision_number !== undefined && <p>採用版本: {item.revision_number}</p>}
          {item.adoption_basis && (
            <p>
              採用根拠:{' '}
              {
                {
                  CURRENT_SETTING: '店舗設定',
                  ACCEPTED_TERMS: '本人受諾',
                  HISTORICAL_CORRECTION: '歴史版本による訂正',
                }[item.adoption_basis]
              }
            </p>
          )}
          {item.adopted_at && <p className="break-all">採用日時: {item.adopted_at}</p>}
          {item.consent_event_id && (
            <p className="break-all">
              受諾記録: {item.consent_event_id}（版 {item.consent_version}）
            </p>
          )}
        </li>
      ))}
    </ul>
  );
}

function RemunerationDetail({ id, onBack }: { id: string; onBack: () => void }) {
  const [error, setError] = useState<unknown>();
  const resource = useResource(async () => {
    try {
      return await selfRemunerationApi.detail(id);
    } catch (failure) {
      setError(failure);
      throw failure;
    }
  });
  const [history, setHistory] = useState(false);
  return (
    <>
      <Button variant="outline" onClick={onBack}>
        一覧へ戻る
      </Button>
      {resource.isLoading ? (
        <p>明細を読み込み中...</p>
      ) : resource.failure ? (
        <Failure error={error} onRetry={() => void resource.reload()} />
      ) : (
        resource.data && (
          <>
            <Amounts row={resource.data} />
            <p>支払済み額ではありません。</p>
            <h2 className="text-lg font-semibold">項目別約定条件</h2>
            <Items items={resource.data.items} />
            <Button variant="outline" onClick={() => setHistory(!history)}>
              {history ? '変更履歴を閉じる' : '変更履歴を確認'}
            </Button>
            {history && <Changes id={id} />}
          </>
        )
      )}
    </>
  );
}

function Changes({ id }: { id: string }) {
  const [error, setError] = useState<unknown>();
  const history = useCursorList(async cursor => {
    try {
      return await selfRemunerationApi.changes(id, cursor);
    } catch (failure) {
      setError(failure);
      throw failure;
    }
  });
  if (history.failed) return <Failure error={error} onRetry={history.reload} />;
  return (
    <section aria-label="変更履歴" className="space-y-4">
      <h2 className="text-lg font-semibold">変更履歴</h2>
      {!history.isLoading && !history.rows.length && <p>変更履歴はありません</p>}
      {history.rows.map(change => (
        <Card key={change.change_id}>
          <CardContent className="space-y-3 break-words pt-4">
            <h3 className="font-semibold">
              {change.change_type === 'COMPLETION_INVALIDATION' ? '無効化' : '訂正'}
            </h3>
            <p>{change.reason}</p>
            <p>{change.changed_at}</p>
            <p className="break-all text-sm">変更 ID: {change.change_id}</p>
            <p className="text-sm">
              原営業日: {change.business_date} / 原完了日時: {change.completed_at}
            </p>
            {(['before', 'after'] as const).map(side => (
              <section key={side} className="space-y-2">
                <h4 className="font-semibold">
                  {side === 'before' ? '変更前' : '変更後'}（版{' '}
                  {side === 'before' ? change.before_version : change.after_version}）
                </h4>
                {change[side].completion_invalidated && <p>無効化済み</p>}
                <p>
                  約定報酬: {money(change[side].agreed_remuneration)} / 発生済み報酬:{' '}
                  {money(change[side].accrued_remuneration)}
                </p>
                <Items items={change[side].items} />
              </section>
            ))}
          </CardContent>
        </Card>
      ))}
      {history.isLoading && <p>履歴を読み込み中...</p>}
      {history.hasMore && (
        <Button variant="outline" disabled={history.isLoading} onClick={history.loadMore}>
          さらに読み込む
        </Button>
      )}
    </section>
  );
}
