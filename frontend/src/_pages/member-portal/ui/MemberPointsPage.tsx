'use client';

import Link from 'next/link';
import { MemberVisit, memberVisitApi } from '@/entities/order';
import { MemberPointEntry, POINT_ENTRY_TYPE_LABELS, memberPointApi } from '@/entities/point';
import { formatDate, formatPointChange, tokyoDate, useListPage, useResource } from '@/shared/lib';
import {
  Button,
  EmptyState,
  ListRow,
  ListRows,
  ListRowsSkeleton,
  Pager,
  RegionError,
} from '@/shared/ui';
import { MEMBER_ACTION, MEMBER_PAGER } from './memberPager';
import { PointBalance } from './PointBalance';
import { ReceiptHint, VisitRows } from './VisitRows';

const RECENT_VISITS = 5;

/**
 * 会員ポータルのポイント。残高 → 明細 → 最近の来店を縦に並べる。
 *
 * 区画は別々に取る。片方が読めなくてもほかは読めてよく、失敗はそれぞれの区画が自分で名乗る。
 * 残高は明細の増減の総和ではない — 有効期限切れは仕訳を積まずに残高だけを減らすため、足し上げても一致しない。
 */
export function MemberPointsPage() {
  const today = tokyoDate(new Date());
  return (
    <div className="space-y-6">
      <PointBalance />
      <Entries today={today} />
      <RecentVisits today={today} />
    </div>
  );
}

/** 明細 1 行。発生店舗と有効期限は持たない仕訳があるため、無い場合はその項目だけ落とす。 */
function EntryRow({ entry, today }: { entry: MemberPointEntry; today: string }) {
  const when = entry.occurred_on && formatDate(entry.occurred_on, today);
  return (
    <ListRow
      title={entry.entry_type ? POINT_ENTRY_TYPE_LABELS[entry.entry_type] : 'ポイント'}
      meta={
        <>
          <span className="block">{[when, entry.store_name].filter(Boolean).join('・')}</span>
          {entry.expires_on && (
            <span className="block">有効期限 {formatDate(entry.expires_on, today)}</span>
          )}
        </>
      }
      trailing={
        <span
          className={`font-semibold tabular-nums ${entry.amount > 0 ? 'text-primary-strong' : ''}`}
        >
          {formatPointChange(entry.amount)}
        </span>
      }
    />
  );
}

function Entries({ today }: { today: string }) {
  const list = useListPage<MemberPointEntry>(page => memberPointApi.entries(page));
  return (
    <section aria-labelledby="points-entries" className="space-y-3">
      <h2 id="points-entries" className="font-semibold">
        明細
      </h2>
      {list.failed ? (
        <RegionError message="明細を取得できませんでした" onRetry={() => void list.reload()} />
      ) : list.isLoading && list.rows.length === 0 ? (
        <ListRowsSkeleton />
      ) : list.rows.length === 0 ? (
        <EmptyState message="まだポイントの増減はありません" action={<ReceiptHint />} />
      ) : (
        <ListRows label="ポイントの明細">
          {/* 明細は台帳内部の ID を持たない（応答に出さない）。ページ内の行は並べ替えも削除も起きない
              ため、位置がそのまま識別子になる。 */}
          {list.rows.map((entry, index) => (
            <EntryRow key={index} entry={entry} today={today} />
          ))}
        </ListRows>
      )}
      <Pager
        className={MEMBER_PAGER}
        page={list.page}
        pageCount={list.pageCount}
        total={list.total}
        rowCount={list.rows.length}
        disabled={list.isLoading}
        onPageChange={page => void list.onPageChange(page)}
      />
    </section>
  );
}

function RecentVisits({ today }: { today: string }) {
  const visits = useResource(() => memberVisitApi.list(0));
  const recent: MemberVisit[] = visits.data?.rows.slice(0, RECENT_VISITS) ?? [];
  return (
    <section aria-labelledby="points-visits" className="space-y-3">
      <h2 id="points-visits" className="font-semibold">
        来店履歴
      </h2>
      {visits.failure ? (
        <RegionError
          message="来店履歴を取得できませんでした"
          onRetry={() => void visits.reload()}
        />
      ) : visits.isLoading ? (
        <ListRowsSkeleton />
      ) : recent.length === 0 ? (
        <EmptyState message="まだ来店の記録はありません" action={<ReceiptHint />} />
      ) : (
        <>
          <VisitRows visits={recent} today={today} />
          {/* 最近の分で全件が見えているなら、同じ行しか無い画面へ送らない */}
          {(visits.data?.total ?? 0) > RECENT_VISITS && (
            <Button
              render={<Link href="/member/visits/" />}
              variant="outline"
              className={MEMBER_ACTION}
            >
              すべて見る
            </Button>
          )}
        </>
      )}
    </section>
  );
}
