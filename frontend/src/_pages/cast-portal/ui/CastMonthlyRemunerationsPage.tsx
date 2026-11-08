'use client';

import { useState } from 'react';
import { selfMonthlyRemunerationApi } from '@/entities/order';
import { MonthlyPdfActions } from '@/features/monthly-remuneration-pdf';
import { formatDate, getApiErrorMessage, isConflict, tokyoDate, useResource } from '@/shared/lib';
import { notify } from '@/shared/notify';
import {
  EmptyState,
  KeyValueList,
  KeyValueRow,
  ListRowsSkeleton,
  Money,
  MonthPicker,
  RegionError,
  Segmented,
  Skeleton,
  StoreChips,
} from '@/shared/ui';
import { remunerationApi, type Day, type Statement } from '@/widgets/remuneration-statement';
import { collectPages } from '../lib/collectPages';
import { statementDays, type StatementBonus, type StatementOrder } from '../lib/monthlyStatement';
import { PANEL } from './RemunerationDetail';
import { StatementDayFold } from './StatementDayFold';

const PAGE_SIZE = 100;

interface FullStatement {
  statement: Statement;
  orders: StatementOrder[];
  bonuses: StatementBonus[];
}

/**
 * 受注とボーナスを各日へ割り振るため、両方のページを最後まで読む。合計と日ごとの額は最初の応答のもの。
 * 後のページの取得の間に訂正が入ると根拠の行と額がずれ得るが、承諾は内容の要約で照合され 409 になる。
 */
async function fullStatement(storeId: number, month: string): Promise<FullStatement> {
  const scope = { scope: 'self', storeId } as const;
  const first = await remunerationApi.statement(scope, month, 0, 0, PAGE_SIZE);
  const pages = Math.max(first.orders.total_pages, first.bonus_awards.total_pages);
  const rest = await Promise.all(
    Array.from({ length: Math.max(pages - 1, 0) }, (_, i) =>
      remunerationApi.statement(scope, month, i + 1, i + 1, PAGE_SIZE)
    )
  );
  const all = [first, ...rest];
  return {
    statement: first,
    orders: all.flatMap(result => result.orders.content),
    bonuses: all.flatMap(result => result.bonus_awards.content),
  };
}

export function CastMonthlyRemunerationsPage() {
  const today = tokyoDate(new Date());
  const thisMonth = today.slice(0, 7);
  const stores = useResource(() => collectPages(selfMonthlyRemunerationApi.stores));
  const [selected, setSelected] = useState<string | null>(null);
  const [month, setMonth] = useState(thisMonth);

  if (stores.isLoading)
    return (
      <div className="space-y-4">
        <Skeleton className="h-11 w-full" />
        <Skeleton className="h-48 w-full rounded-xl" />
        <ListRowsSkeleton />
      </div>
    );
  if (stores.failure)
    return <RegionError message="店舗の取得に失敗しました" onRetry={() => void stores.reload()} />;
  const rows = stores.data ?? [];
  if (rows.length === 0) return <EmptyState message="まだ報酬明細はありません" />;
  const current = rows.find(row => String(row.store_id) === selected) ?? rows[0];
  return (
    <div className="space-y-4">
      <StoreChips
        stores={rows.map(row => ({ id: String(row.store_id), name: row.store_name }))}
        value={String(current.store_id)}
        onValueChange={setSelected}
      />
      <MonthPicker value={month} currentMonth={thisMonth} onValueChange={setMonth} />
      <MonthlyStatement
        key={`${current.store_id}-${month}`}
        storeId={current.store_id}
        month={month}
        today={today}
      />
    </div>
  );
}

type Filter = 'all' | 'awaiting';

function MonthlyStatement({
  storeId,
  month,
  today,
}: {
  storeId: number;
  month: string;
  today: string;
}) {
  const result = useResource(() => fullStatement(storeId, month));
  const [filter, setFilter] = useState<Filter>('all');
  // 未操作の間は承諾待ちの日を開く。承諾で取り直しても開閉が変わらないよう、操作の時点で固定する。
  const [opened, setOpened] = useState<ReadonlySet<string> | null>(null);
  const [sending, setSending] = useState(false);

  if (result.failure)
    return (
      <RegionError message="月次明細の取得に失敗しました" onRetry={() => void result.reload()} />
    );
  if (!result.data)
    return (
      <div className="space-y-4">
        <Skeleton className="h-48 w-full rounded-xl" />
        <ListRowsSkeleton />
      </div>
    );
  const { statement, orders, bonuses } = result.data;
  const days = statementDays(statement.days, orders, bonuses);
  const open =
    opened ?? new Set(days.filter(entry => entry.awaiting).map(entry => entry.day.business_date));
  const awaiting = days.filter(entry => entry.awaiting).length;
  const shown = filter === 'awaiting' ? days.filter(entry => entry.awaiting) : days;

  const toggle = (date: string) => {
    const next = new Set(open);
    if (!next.delete(date)) next.add(date);
    setOpened(next);
  };
  // 409 は見ていた内容が古いことを示す。どの結果でも明細を取り直し、今の状態を見せる。
  const accept = async (day: Day) => {
    if (day.content_digest === undefined) return;
    setOpened(open);
    setSending(true);
    try {
      await remunerationApi.accept({
        store_id: storeId,
        business_date: day.business_date,
        content_digest: day.content_digest,
      });
      notify.success(`${formatDate(day.business_date, today)}の明細を承諾しました`);
    } catch (error) {
      if (isConflict(error))
        notify.warning(
          '明細の内容が変わっていたため、最新の明細を表示しました。内容を確かめてから承諾してください'
        );
      else notify.error(getApiErrorMessage(error, '明細を承諾できませんでした'));
    } finally {
      // 取り直す間は古い明細のボタンが残るので、押せないままにして同じ要約の再送を防ぐ
      await result.reload();
      setSending(false);
    }
  };

  return (
    <>
      <MonthSummary statement={statement} storeId={storeId} month={month} />
      {days.length === 0 ? (
        <EmptyState message="この月の出勤はありません" />
      ) : (
        <section aria-labelledby="statement-days" className="space-y-3">
          <h2 id="statement-days" className="sr-only">
            日ごとの明細
          </h2>
          <Segmented<Filter>
            label="表示する日"
            options={[
              { value: 'all', label: '出勤日すべて' },
              { value: 'awaiting', label: `承諾待ち ${awaiting}` },
            ]}
            value={filter}
            onValueChange={setFilter}
          />
          {shown.length === 0 ? (
            <EmptyState message="承諾待ちの日はありません" />
          ) : (
            <ul className="space-y-2">
              {shown.map(entry => (
                <li key={entry.day.business_date}>
                  <StatementDayFold
                    entry={entry}
                    today={today}
                    open={open.has(entry.day.business_date)}
                    onToggle={() => toggle(entry.day.business_date)}
                    onAccept={accept}
                    sending={sending}
                  />
                </li>
              ))}
            </ul>
          )}
          <p className="text-center text-sm text-muted-foreground">
            出勤も受注も無い日は表示していません
          </p>
        </section>
      )}
    </>
  );
}

function MonthSummary({
  statement,
  storeId,
  month,
}: {
  statement: Statement;
  storeId: number;
  month: string;
}) {
  const monthNumber = Number(month.slice(5, 7));
  return (
    <section aria-labelledby="month-summary" className={PANEL}>
      <h2 id="month-summary" className="text-sm text-muted-foreground">
        {monthNumber}月の合計
      </h2>
      {statement.total === undefined ? (
        <p className="text-3xl font-bold text-muted-foreground">計算待ち</p>
      ) : (
        <Money amount={statement.total} className="block text-3xl font-bold" />
      )}
      <KeyValueList className="text-base">
        <KeyValueRow label="受注報酬" money>
          <Money amount={statement.order_total} />
        </KeyValueRow>
        <KeyValueRow label="日額保証の不足分" money>
          {statement.guarantee_total === undefined ? (
            '計算待ち'
          ) : (
            <Money amount={statement.guarantee_total} />
          )}
        </KeyValueRow>
        <KeyValueRow label="ボーナス" money>
          <Money amount={statement.bonus_total} />
        </KeyValueRow>
      </KeyValueList>
      <p>
        承諾 {statement.accepted_days} / {statement.acceptable_days} 日
      </p>
      <p className="text-sm text-muted-foreground">支払済み額ではありません。</p>
      <MonthlyPdfActions criteria={{ scope: 'self', storeId, month }} />
    </section>
  );
}
