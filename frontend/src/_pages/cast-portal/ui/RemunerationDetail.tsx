'use client';

import { useState } from 'react';
import {
  selfRemunerationApi,
  SelfRemunerationChange,
  SelfRemunerationItem,
  SelfRemunerationSummary,
} from '@/entities/order';
import {
  formatDate,
  formatDateTime,
  formatMinutes,
  isForbidden,
  isNotFound,
  tokyoDate,
  useListPage,
  useResource,
} from '@/shared/lib';
import {
  EmptyState,
  KeyValueList,
  KeyValueRow,
  ListRowsSkeleton,
  Money,
  Pager,
  RegionError,
  Skeleton,
} from '@/shared/ui';
import { itemKeys, itemTerms, remunerationChanges } from '../lib/remunerationChanges';

const STATUSES = {
  CONFIRMED: '予定',
  IN_SERVICE: 'サービス中',
  COMPLETED: '完了',
  CANCELLED: '取消',
};
const KINDS = {
  COURSE: 'コース',
  SPECIAL_SERVICE: '特殊サービス',
  EXTENSION: '延長',
  SURCHARGE: '加算',
};

export function remunerationStatus(row: SelfRemunerationSummary): string {
  return row.completion_invalidated ? '無効化' : STATUSES[row.status];
}

/** 一覧の行に出す額。終わった受注は発生済み、これからの受注は予定の報酬。 */
export function shownRemuneration(row: SelfRemunerationSummary): number {
  return row.status === 'CONFIRMED' || row.status === 'IN_SERVICE'
    ? row.planned_remuneration
    : row.accrued_remuneration;
}

export function Failure({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  if (isForbidden(error)) return <p role="alert">報酬明細を確認する権限がありません。</p>;
  if (isNotFound(error)) return <p role="alert">対象の在籍または報酬明細が見つかりません。</p>;
  return <RegionError message="報酬明細の取得に失敗しました" onRetry={onRetry} />;
}

/** 報酬の画面のカード 1 枚の面。 */
export const PANEL = 'space-y-2 rounded-xl border bg-card p-4 text-card-foreground';

function Panel({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <section aria-label={label} className={PANEL}>
      {children}
    </section>
  );
}

function Item({ item }: { item: SelfRemunerationItem }) {
  return (
    <Panel label={item.name}>
      <h3 className="font-semibold wrap-anywhere">{item.name}</h3>
      <KeyValueList className="text-base">
        <KeyValueRow label="種類">{KINDS[item.kind]}</KeyValueRow>
        {item.duration_minutes !== undefined && (
          <KeyValueRow label="時間">{formatMinutes(item.duration_minutes)}</KeyValueRow>
        )}
        <KeyValueRow label="お客様料金" money>
          <Money amount={item.price} />
        </KeyValueRow>
        <KeyValueRow label="固定報酬" money>
          <Money amount={item.remuneration} />
        </KeyValueRow>
        {itemTerms(item) && <KeyValueRow label="条件">{itemTerms(item)}</KeyValueRow>}
      </KeyValueList>
    </Panel>
  );
}

/** 受注 1 件の報酬明細（概要・内訳・変更履歴）。 */
export function RemunerationDetail({ id }: { id: string }) {
  const [error, setError] = useState<unknown>();
  const resource = useResource(async () => {
    try {
      return await selfRemunerationApi.detail(id);
    } catch (failure) {
      setError(failure);
      throw failure;
    }
  });
  if (resource.isLoading)
    return (
      <div role="status" aria-label="読み込み中" className="space-y-3">
        <Skeleton className="h-40 w-full rounded-xl" />
        <Skeleton className="h-32 w-full rounded-xl" />
      </div>
    );
  if (resource.failure) return <Failure error={error} onRetry={() => void resource.reload()} />;
  const row = resource.data;
  if (!row) return null;
  const today = tokyoDate(new Date());
  return (
    <div className="space-y-4">
      <Panel label="概要">
        <KeyValueList className="text-base">
          <KeyValueRow label="店舗">{row.store_name}</KeyValueRow>
          <KeyValueRow label="営業日">{formatDate(row.business_date, today)}</KeyValueRow>
          {row.completed_at && (
            <KeyValueRow label="完了">{formatDateTime(row.completed_at, today)}</KeyValueRow>
          )}
          <KeyValueRow label="状態">{remunerationStatus(row)}</KeyValueRow>
          <KeyValueRow label="約定報酬" money>
            <Money amount={row.agreed_remuneration} />
          </KeyValueRow>
          <KeyValueRow label="予定報酬" money>
            <Money amount={row.planned_remuneration} />
          </KeyValueRow>
          <KeyValueRow label="発生済み報酬" money>
            <Money amount={row.accrued_remuneration} className="font-semibold" />
          </KeyValueRow>
        </KeyValueList>
        <p className="text-sm text-muted-foreground">
          予定・発生済みの固定報酬です。支払済み額ではありません。
        </p>
      </Panel>
      <section aria-labelledby="remuneration-items" className="space-y-3">
        <h2 id="remuneration-items" className="font-semibold">
          内訳
        </h2>
        {itemKeys(row.items).map((key, i) => (
          <Item key={key} item={row.items[i]} />
        ))}
      </section>
      <Changes id={id} today={today} />
    </div>
  );
}

function Change({ change, today }: { change: SelfRemunerationChange; today: string }) {
  const title = change.change_type === 'COMPLETION_INVALIDATION' ? '無効化' : '訂正';
  const headingId = `change-${change.change_id}`;
  return (
    <article aria-labelledby={headingId} className={PANEL}>
      <div className="flex items-baseline justify-between gap-3">
        <h3 id={headingId} className="font-semibold">
          {title}
        </h3>
        <span className="text-sm text-muted-foreground">
          {formatDateTime(change.changed_at, today)}
        </span>
      </div>
      <KeyValueList className="text-base">
        <KeyValueRow label="理由" long>
          {change.reason}
        </KeyValueRow>
        {remunerationChanges(change.before, change.after).map((diff, i) => (
          <KeyValueRow
            key={i}
            // 明細名は長くなり得るので、明細の差はラベルを上に積み、空白の無い名前も途中で折り返す
            label={
              <span className="wrap-anywhere">
                {diff.item === undefined
                  ? diff.label
                  : diff.kind === 'added' || diff.kind === 'removed'
                    ? `${diff.item}（${diff.kind === 'added' ? '追加' : '削除'}）の${diff.label}`
                    : `${diff.item}の${diff.label}`}
              </span>
            }
            long={diff.item !== undefined}
            money={diff.kind !== 'text'}
          >
            {diff.kind === 'text' ? (
              diff.text
            ) : diff.kind === 'changed' ? (
              <Money before={diff.before} amount={diff.after} />
            ) : (
              <Money amount={diff.amount} />
            )}
          </KeyValueRow>
        ))}
      </KeyValueList>
    </article>
  );
}

function Changes({ id, today }: { id: string; today: string }) {
  const [error, setError] = useState<unknown>();
  const history = useListPage(async page => {
    try {
      return await selfRemunerationApi.changes(id, page);
    } catch (failure) {
      setError(failure);
      throw failure;
    }
  });
  return (
    <section aria-labelledby="remuneration-changes" className="space-y-3">
      <h2 id="remuneration-changes" className="font-semibold">
        変更履歴
      </h2>
      {history.failed ? (
        <Failure error={error} onRetry={() => void history.reload()} />
      ) : history.isLoading ? (
        <ListRowsSkeleton rows={1} />
      ) : history.rows.length === 0 ? (
        <EmptyState message="変更履歴はありません" />
      ) : (
        history.rows.map(change => <Change key={change.change_id} change={change} today={today} />)
      )}
      <Pager
        page={history.page}
        pageCount={history.pageCount}
        total={history.total}
        rowCount={history.rows.length}
        disabled={history.isLoading}
        onPageChange={page => void history.onPageChange(page)}
      />
    </section>
  );
}
