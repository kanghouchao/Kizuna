'use client';

import { useEffect, useRef, useState } from 'react';
import { ChevronDownIcon, ChevronLeftIcon, ChevronRightIcon, UserRoundCogIcon } from 'lucide-react';
import { Order, OrderListCriteria, OrderStatus, orderApi } from '@/entities/order';
import { useListPage } from '@/shared/lib';
import { UNLINKED_NOTE, customerLabel } from '../lib/customerLabel';
import { Button, RegionError } from '@/shared/ui';

/** アーカイブの 1 ページ。増えるだけの記録なので、位置をページ番号で指せる。 */
const PAGE_SIZE = 10;

interface OrderArchiveSectionProps {
  title: string;
  status: OrderStatus;
  criteria: OrderListCriteria;
  /**
   * この群へ受注が移ってくるたびに増える値。変わったら取り直す。
   *
   * 中身は読まない — 「何件移ったか」ではなく「移った」ことだけが取り直しの合図で、値を解釈すると
   * 手元の件数を推算する誘惑が生まれる（他の操作者の処理が入るとその推算は必ず狂う）。
   */
  reloadToken: number;
  /** 帰属の訂正モーダルを開く（完了した受注にだけ起こる）。 */
  onCorrectAttribution: (order: Order) => void;
}

/** 時刻は応答のオフセット付き文字列をそのまま切らず、閲覧者の時間帯へ直して出す（切ると +09:00 が落ちる）。 */
function formatDateTime(value: string): string {
  return new Date(value).toLocaleString('ja-JP');
}

function OutcomeLine({ order }: { order: Order }) {
  if (order.status === 'COMPLETED') {
    return (
      <p className="text-muted-foreground text-sm">
        会計 ¥{(order.total_fee ?? 0).toLocaleString()}
        {/* 0 を畳むと「付与なし」と「名乗っていない」が区別できなくなる（完了した受注は必ず持つ） */}
        {order.auto_grant_points != null ? ` ・ 付与 ${order.auto_grant_points}pt` : ''}
        {order.used_points ? ` ・ 利用 ${order.used_points}pt` : ''}
      </p>
    );
  }
  // 取消の記録を持たない CANCELLED は、確定へ至らないまま消えた申請（店舗の謝絶か会員の取り下げ）。
  // 理由も実行者も構造的に存在しないので、欠落として「実行者不明」と呼ばずそう名乗る — どちらだったかは
  // 記録が無く、読み口に無い区別を画面が作ってはならない。
  if (!order.cancelled_at) {
    return <p className="text-muted-foreground text-sm">確定前に取り消された申請</p>;
  }
  // 取消は誰が・いつ・なぜを行そのものが名乗る（詳細を開かずに経緯を辿れるように）。
  // 実行者の欠落はここだけで起こる — 操作者が削除された取消（FK が SET NULL）。
  return (
    <p className="text-muted-foreground text-sm">
      {formatDateTime(order.cancelled_at)} {order.cancelled_by_name ?? '実行者不明'}
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
  reloadToken,
  onCorrectAttribution,
}: OrderArchiveSectionProps) {
  const [open, setOpen] = useState(false);
  const list = useListPage<Order, OrderListCriteria>(
    (page, applied) =>
      orderApi.listArchive({ ...applied, statuses: [status], page, size: PAGE_SIZE }),
    criteria
  );
  const { search, reload } = list;

  // 条件が変わったら取り直す。フックは適用済み条件を自分で持つので、渡し直すだけでは動かない。
  // 初回はフック自身の取得と重なるため飛ばす（同じページを 2 回取りに行かないため）。
  const appliedRef = useRef(criteria);
  useEffect(() => {
    if (appliedRef.current === criteria) {
      return;
    }
    appliedRef.current = criteria;
    void search(criteria);
  }, [criteria, search]);

  // 作業キューから受注が移ってきたら取り直す。たたんでいても件数の控えは出ているので、開閉は問わない。
  // 条件の取り直し（上）と違い今のページに留まるのは、増えた 1 件を見に行くために閲覧位置を
  // 動かすと、続けて処理している最中に手元のページが勝手に飛ぶため。
  const reloadTokenRef = useRef(reloadToken);
  useEffect(() => {
    if (reloadTokenRef.current === reloadToken) {
      return;
    }
    reloadTokenRef.current = reloadToken;
    void reload();
  }, [reloadToken, reload]);

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
