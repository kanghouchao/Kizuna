'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useMemo, useState } from 'react';
import { ArrowDownIcon, ArrowUpIcon, PlusIcon } from 'lucide-react';
import {
  ORDER_SORT_KEY_LABELS,
  Order,
  OrderApplicationRow,
  OrderListCriteria,
  OrderSortKey,
  OrderStatus,
  orderApi,
  orderApplicationApi,
} from '@/entities/order';
import { storePath, useCursorList } from '@/shared/lib';
import { PageHeader } from '@/widgets/page-header';
import { OrderApplicationCard } from './OrderApplicationCard';
import { OrderApplicationConfirmModal } from './OrderApplicationConfirmModal';
import { OrderArchiveSection } from './OrderArchiveSection';
import { OrderAttributionModal } from './OrderAttributionModal';
import { OrderCompletionModal } from './OrderCompletionModal';
import { OrderQueueCard } from './OrderQueueCard';
import {
  Button,
  Card,
  CardContent,
  Input,
  Label,
  RegionError,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui';

/** 作業キューの 1 回の読み込み件数。処理で行が消える一覧なので継ぎ足しで辿る。 */
const QUEUE_PAGE_SIZE = 20;

/** 対応が要る受注の群。すべての受注は確定で出生するため、確定済みだけが対象（未処理の申請は受付箱が持つ）。 */
const ACTIVE_STATUSES: OrderStatus[] = ['CONFIRMED'];

/** 作業キューを離れた受注の行き先。終端状態はこの 2 つしかない（ADR 0013）。 */
type ArchiveStatus = Extract<OrderStatus, 'COMPLETED' | 'CANCELLED'>;

const SORT_KEYS = Object.keys(ORDER_SORT_KEY_LABELS) as OrderSortKey[];

interface SearchDraft {
  customerName: string;
  businessDate: string;
}

const EMPTY_DRAFT: SearchDraft = { customerName: '', businessDate: '' };

/**
 * 画面の入力を読み口の条件へ畳む。組み立てを 1 箇所に持つのは、検索・並び替え・初期表示の 3 経路が
 * 同じ条件を作るためで、散らすと片方だけが更新されて群ごとに違う母集合を見に行く。
 */
function toCriteria(draft: SearchDraft, sortKey: OrderSortKey, desc: boolean): OrderListCriteria {
  return {
    customer_name: draft.customerName || undefined,
    business_date: draft.businessDate || undefined,
    sort_key: sortKey,
    desc,
  };
}

export default function OrderListPage() {
  const params = useParams();
  const storeId = params.storeId as string;

  // 入力中の値と「適用済み」の条件を分ける。取得は適用済みだけを読む（DESIGN.md）
  const [draft, setDraft] = useState<SearchDraft>(EMPTY_DRAFT);
  const [applied, setApplied] = useState<SearchDraft>(EMPTY_DRAFT);
  const [sortKey, setSortKey] = useState<OrderSortKey>('BUSINESS_DATE');
  const [descending, setDescending] = useState(false);

  const [confirming, setConfirming] = useState<OrderApplicationRow | null>(null);
  const [completing, setCompleting] = useState<Order | null>(null);
  const [correcting, setCorrecting] = useState<Order | null>(null);

  // 群を跨いで同じ条件を当てる。参照が毎レンダー変わるとアーカイブが取り直し続けるため畳んで持つ
  const criteria: OrderListCriteria = useMemo(
    () => toCriteria(applied, sortKey, descending),
    [applied, sortKey, descending]
  );

  const queue = useCursorList<Order, OrderListCriteria>(
    (cursor, activeCriteria) =>
      orderApi.listWorkQueue({
        ...activeCriteria,
        statuses: ACTIVE_STATUSES,
        cursor,
        size: QUEUE_PAGE_SIZE,
      }),
    criteria
  );

  // 受付箱（未処理の予約申請）。検索・並びの条件は持たない — 希望日の早い順に処理するだけの箱で、
  // 条件を持たせると作業キューと 2 つの母集合を主張し合う。
  const inbox = useCursorList<OrderApplicationRow>(cursor =>
    orderApplicationApi.list({ statuses: ['PENDING'], cursor, size: QUEUE_PAGE_SIZE })
  );

  /** 謝絶し終えた申請を受付箱から取り除く。 */
  const removeFromInbox = (id: string) => inbox.setRows(prev => prev.filter(row => row.id !== id));

  /** 確定し終えた申請の後始末。申請は受付箱から外れ、生まれた受注が作業キューへ現れる。 */
  const settleConfirmed = (application: OrderApplicationRow | null) => {
    if (application?.id) {
      removeFromInbox(application.id);
    }
    // 生まれた受注は検索・並びの条件次第でどこに入るか分からないため、行の差し込みではなく取り直す
    queue.reload();
  };

  const apply = (next: SearchDraft) => {
    setApplied(next);
    queue.search(toCriteria(next, sortKey, descending));
  };

  const applySort = (key: OrderSortKey, desc: boolean) => {
    setSortKey(key);
    setDescending(desc);
    queue.search(toCriteria(applied, key, desc));
  };

  // アーカイブへ移った受注を群ごとに数える。件数の控えはたたんだままでも出ているので、移った先を
  // 取り直さないと「アーカイブに入ったのか」が画面のどこからも読めない
  const [archived, setArchived] = useState<Record<ArchiveStatus, number>>({
    COMPLETED: 0,
    CANCELLED: 0,
  });

  /** 処理し終えた受注を手元から取り除き、行った先のアーカイブを取り直させる。 */
  const removeFromQueue = (id: string, movedTo: ArchiveStatus) => {
    queue.setRows(prev => prev.filter(row => row.id !== id));
    setArchived(prev => ({ ...prev, [movedTo]: prev[movedTo] + 1 }));
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="オーダー一覧"
        description="対応が要る受注を前面に、完了・取消はアーカイブにまとめています。"
        actions={
          <Button render={<Link href={storePath(storeId, '/orders/create')} />}>
            <PlusIcon aria-hidden="true" />
            新規オーダー登録
          </Button>
        }
      />

      {/* ステータスの絞り込みは置かない — 群そのものがその軸で、二本あると群と条件が食い違う */}
      <Card>
        <form
          onSubmit={e => {
            e.preventDefault();
            apply(draft);
          }}
        >
          <CardContent className="flex flex-col gap-4 md:flex-row md:items-end">
            <div className="flex-1 space-y-2">
              <Label htmlFor="order-search-customer">お客様名</Label>
              <Input
                id="order-search-customer"
                value={draft.customerName}
                onChange={e => setDraft({ ...draft, customerName: e.target.value })}
                placeholder="部分一致"
              />
            </div>
            <div className="flex-1 space-y-2">
              <Label htmlFor="order-search-date">営業日</Label>
              <Input
                id="order-search-date"
                type="date"
                value={draft.businessDate}
                onChange={e => setDraft({ ...draft, businessDate: e.target.value })}
              />
            </div>
            {/* 表の見出しが無いので、並び替えは専用のコントロールを持つ */}
            <div className="flex-1 space-y-2">
              <Label htmlFor="order-sort">並び</Label>
              <div className="flex items-center gap-2">
                <Select
                  value={sortKey}
                  onValueChange={v => applySort(v as OrderSortKey, descending)}
                  items={SORT_KEYS.map(k => ({ value: k, label: ORDER_SORT_KEY_LABELS[k] }))}
                >
                  <SelectTrigger id="order-sort" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {SORT_KEYS.map(k => (
                      <SelectItem key={k} value={k}>
                        {ORDER_SORT_KEY_LABELS[k]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Button
                  type="button"
                  variant="outline"
                  size="icon-sm"
                  aria-label={descending ? '降順（押すと昇順）' : '昇順（押すと降順）'}
                  onClick={() => applySort(sortKey, !descending)}
                >
                  {descending ? <ArrowDownIcon /> : <ArrowUpIcon />}
                </Button>
              </div>
            </div>
            <div className="flex items-end gap-2">
              <Button type="submit">検索</Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setDraft(EMPTY_DRAFT);
                  apply(EMPTY_DRAFT);
                }}
              >
                クリア
              </Button>
            </div>
          </CardContent>
        </form>
      </Card>

      <section className="space-y-3">
        <h2 className="text-foreground text-sm font-medium">予約申請</h2>
        {/* 取得の失敗を空表示と区別する — 「申請なし」に見せると未処理を見落とす */}
        {inbox.failed ? (
          <RegionError message="予約申請を取得できませんでした。" onRetry={inbox.reload} />
        ) : inbox.isLoading && inbox.rows.length === 0 ? (
          <p className="text-muted-foreground text-sm">読み込み中...</p>
        ) : inbox.rows.length === 0 && !inbox.hasMore ? (
          <p className="text-muted-foreground bg-card rounded-lg border p-4 text-sm">
            未処理の予約申請はありません
          </p>
        ) : (
          <ul className="space-y-3">
            {inbox.rows.map(application => (
              <OrderApplicationCard
                key={application.id}
                application={application}
                onDeclined={removeFromInbox}
                onConfirm={setConfirming}
              />
            ))}
          </ul>
        )}
        {inbox.hasMore && (
          <Button
            type="button"
            variant="outline"
            className="w-full"
            disabled={inbox.isLoading}
            onClick={inbox.loadMore}
          >
            もっと見る
          </Button>
        )}
      </section>

      <section className="space-y-3">
        <h2 className="text-foreground text-sm font-medium">対応が要る</h2>
        {/* 取得の失敗を空表示と区別する — 「受注なし」に見せると未対応を見落とす */}
        {queue.failed ? (
          <RegionError message="受注を取得できませんでした。" onRetry={queue.reload} />
        ) : queue.isLoading && queue.rows.length === 0 ? (
          <p className="text-muted-foreground text-sm">読み込み中...</p>
        ) : queue.rows.length === 0 && !queue.hasMore ? (
          // 続きが残っているうちは「受注なし」と言い切らない（表示中を処理し終えただけの状態と区別できなくなる）
          <p className="text-muted-foreground bg-card rounded-lg border p-4 text-sm">
            条件に合う受注がありません
          </p>
        ) : (
          <ul className="space-y-3">
            {queue.rows.map(order => (
              <OrderQueueCard
                key={order.id}
                order={order}
                onProcessed={id => removeFromQueue(id, 'CANCELLED')}
                onComplete={setCompleting}
              />
            ))}
          </ul>
        )}
        {queue.hasMore && (
          <Button
            type="button"
            variant="outline"
            className="w-full"
            disabled={queue.isLoading}
            onClick={queue.loadMore}
          >
            もっと見る
          </Button>
        )}
      </section>

      <div className="space-y-3">
        <OrderArchiveSection
          title="完了"
          status="COMPLETED"
          criteria={criteria}
          reloadToken={archived.COMPLETED}
          onCorrectAttribution={setCorrecting}
        />
        <OrderArchiveSection
          title="取消"
          status="CANCELLED"
          criteria={criteria}
          reloadToken={archived.CANCELLED}
          onCorrectAttribution={setCorrecting}
        />
      </div>

      {/* 確定は申請内容を予填した受注の作成操作。申請原文はモーダルの外（受付箱の行）に残り続ける */}
      <OrderApplicationConfirmModal
        application={confirming}
        onClose={() => setConfirming(null)}
        onConfirmed={() => settleConfirmed(confirming)}
      />
      <OrderCompletionModal
        order={completing}
        onClose={() => setCompleting(null)}
        // 完了した受注は作業キューから外れて完了のアーカイブへ移る
        onCompleted={() => removeFromQueue(completing?.id ?? '', 'COMPLETED')}
      />
      {/* 訂正は受注の状態も会計欄も変えないため、一覧の取り直しは要らない */}
      <OrderAttributionModal order={correcting} onClose={() => setCorrecting(null)} />
    </div>
  );
}
