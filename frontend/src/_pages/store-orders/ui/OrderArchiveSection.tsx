'use client';

import { useEffect, useState } from 'react';
import { ChevronDownIcon, ChevronLeftIcon, ChevronRightIcon, UserRoundCogIcon } from 'lucide-react';
import { Order, OrderQueryParams, OrderStatus, orderApi } from '@/entities/order';
import { useListPage } from '@/shared/lib';
import { UNLINKED_NOTE, customerLabel } from '../lib/customerLabel';
import { Button, RegionError } from '@/shared/ui';

/** アーカイブの 1 ページ。増えるだけの記録なので、位置をページ番号で指せる。 */
const PAGE_SIZE = 10;

/** 群を跨いで共有する検索・並び替えの条件（状態の軸はこの群自身が持つ）。 */
export type ArchiveCriteria = Omit<OrderQueryParams, 'statuses'>;

interface OrderArchiveSectionProps {
  title: string;
  status: OrderStatus;
  criteria: ArchiveCriteria;
  /** 帰属の訂正モーダルを開く（完了した受注にだけ起こる）。 */
  onCorrectAttribution: (order: Order) => void;
}

function OutcomeLine({ order }: { order: Order }) {
  if (order.status === 'COMPLETED') {
    return (
      <p className="text-muted-foreground text-sm">
        会計 ¥{(order.total_fee ?? 0).toLocaleString()}
        {order.auto_grant_points ? ` ・ 付与 ${order.auto_grant_points}pt` : ''}
        {order.used_points ? ` ・ 利用 ${order.used_points}pt` : ''}
      </p>
    );
  }
  // 取消は誰が・いつ・なぜを行そのものが名乗る（詳細を開かずに経緯を辿れるように）
  const at = order.cancelled_at ? order.cancelled_at.slice(0, 16).replace('T', ' ') : '';
  const by = order.cancelled_by_name ?? '実行者不明';
  return (
    <p className="text-muted-foreground text-sm">
      {[at, by].filter(Boolean).join(' ')}
      {order.cancelled_reason ? ` — ${order.cancelled_reason}` : ''}
    </p>
  );
}

function ArchiveRow({
  order,
  onCorrectAttribution,
}: {
  order: Order;
  onCorrectAttribution: (order: Order) => void;
}) {
  const label = customerLabel(order);
  return (
    <div className="flex items-start justify-between gap-4 border-b px-4 py-3 last:border-b-0">
      <div className="min-w-0 space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          {label === null ? (
            <span className="text-muted-foreground">お客様名なし</span>
          ) : (
            <>
              <span className="text-foreground font-medium">{label.name}</span>
              {label.unlinked && (
                <span className="text-muted-foreground text-sm">{UNLINKED_NOTE}</span>
              )}
            </>
          )}
          <span className="text-muted-foreground text-sm">{order.business_date}</span>
        </div>
        <OutcomeLine order={order} />
      </div>
      {/* 誤帰属の訂正は完了した受注にしか起こらない（帰属が生まれるのは完了と事後申領の瞬間だけ）。
          現に帰属しているかは一覧の読み口が持たないので、開いた先で名乗る */}
      {order.status === 'COMPLETED' && (
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="shrink-0"
          onClick={() => onCorrectAttribution(order)}
        >
          <UserRoundCogIcon aria-hidden="true" />
          会員帰属
        </Button>
      )}
    </div>
  );
}

/**
 * 終端状態の受注をたたんで収めるアーカイブの 1 群。
 *
 * <p>位置をページ番号で指すページャで辿る。作業キューと違い、行が処理で消えて後続が繰り上がることが無い。
 *
 * <p>検索・並び替えは作業キューと同じ条件をそのまま受ける — 群ごとに違う並びだと、同じ画面が 2 つの順序を主張することになる。
 */
export function OrderArchiveSection({
  title,
  status,
  criteria,
  onCorrectAttribution,
}: OrderArchiveSectionProps) {
  const [open, setOpen] = useState(false);
  const list = useListPage<Order, ArchiveCriteria>(
    (page, applied) =>
      orderApi.listArchive({ ...applied, statuses: [status], page, size: PAGE_SIZE }),
    criteria
  );
  const { search } = list;

  // 条件が変わったら取り直す。フックは適用済み条件を自分で持つので、渡し直すだけでは動かない
  useEffect(() => {
    void search(criteria);
  }, [criteria, search]);

  return (
    <div className="bg-card rounded-xl border shadow-sm">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        aria-expanded={open}
        className="hover:bg-muted focus-visible:ring-primary flex w-full items-center gap-2 rounded-xl px-4 py-3 text-left focus-visible:ring-2 focus-visible:outline-none"
      >
        <ChevronDownIcon
          aria-hidden="true"
          className={`size-4 transition-transform ${open ? '' : '-rotate-90'}`}
        />
        <span className="text-foreground text-sm font-medium">{title}</span>
        <span className="text-muted-foreground text-sm">{list.total} 件</span>
      </button>
      {open && (
        <div className="border-t">
          {/* 取得の失敗を空表示と区別する — 「0 件」に見せると、あるはずの記録を無いと誤解させる */}
          {list.failed ? (
            <div className="p-4">
              <RegionError
                message="受注を取得できませんでした。"
                onRetry={() => void list.reload()}
              />
            </div>
          ) : list.isLoading ? (
            <p className="text-muted-foreground p-4 text-sm">読み込み中...</p>
          ) : list.rows.length === 0 ? (
            <p className="text-muted-foreground p-4 text-sm">条件に合う受注がありません</p>
          ) : (
            list.rows.map(order => (
              <ArchiveRow
                key={order.id}
                order={order}
                onCorrectAttribution={onCorrectAttribution}
              />
            ))
          )}
          {list.pageCount > 1 && (
            <div className="flex items-center justify-between border-t px-4 py-3">
              <span className="text-muted-foreground text-sm">
                {list.total} 件中 {list.page * PAGE_SIZE + 1}-
                {list.page * PAGE_SIZE + list.rows.length} を表示
              </span>
              <div className="flex items-center gap-1">
                <Button
                  type="button"
                  variant="outline"
                  size="icon-sm"
                  aria-label="前のページ"
                  disabled={list.page <= 0}
                  onClick={() => void list.onPageChange(list.page - 1)}
                >
                  <ChevronLeftIcon />
                </Button>
                <span className="text-muted-foreground px-2 text-sm">
                  {list.page + 1} / {list.pageCount}
                </span>
                <Button
                  type="button"
                  variant="outline"
                  size="icon-sm"
                  aria-label="次のページ"
                  disabled={list.page >= list.pageCount - 1}
                  onClick={() => void list.onPageChange(list.page + 1)}
                >
                  <ChevronRightIcon />
                </Button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
