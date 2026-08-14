'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useMemo, useState } from 'react';
import { ArrowDownIcon, ArrowUpIcon, PlusIcon } from 'lucide-react';
import {
  ORDER_SORT_KEY_LABELS,
  Order,
  OrderSortKey,
  OrderStatus,
  orderApi,
} from '@/entities/order';
import { storePath, useCursorList } from '@/shared/lib';
import { PageHeader } from '@/widgets/page-header';
import { OrderArchiveSection, type ArchiveCriteria } from './OrderArchiveSection';
import { OrderAttributionModal } from './OrderAttributionModal';
import { OrderCompletionModal } from './OrderCompletionModal';
import { OrderEditModal } from './OrderEditModal';
import { OrderQueueCard } from './OrderQueueCard';
import { ReservationRequestEditModal } from './ReservationRequestEditModal';
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

/** 対応が要る受注の群。未確定（会員申請）と確定済みを 1 つの面に出す。 */
const ACTIVE_STATUSES: OrderStatus[] = ['CREATED', 'CONFIRMED'];

const SORT_KEYS = Object.keys(ORDER_SORT_KEY_LABELS) as OrderSortKey[];

interface SearchDraft {
  customerName: string;
  businessDate: string;
}

const EMPTY_DRAFT: SearchDraft = { customerName: '', businessDate: '' };

export default function OrderListPage() {
  const params = useParams();
  const storeId = params.storeId as string;

  // 入力中の値と「適用済み」の条件を分ける。取得は適用済みだけを読む（DESIGN.md）
  const [draft, setDraft] = useState<SearchDraft>(EMPTY_DRAFT);
  const [applied, setApplied] = useState<SearchDraft>(EMPTY_DRAFT);
  const [sortKey, setSortKey] = useState<OrderSortKey>('BUSINESS_DATE');
  const [descending, setDescending] = useState(false);

  const [editing, setEditing] = useState<Order | null>(null);
  const [editingRequest, setEditingRequest] = useState<Order | null>(null);
  const [completing, setCompleting] = useState<Order | null>(null);
  const [correcting, setCorrecting] = useState<Order | null>(null);

  // 群を跨いで同じ条件を当てる。参照が毎レンダー変わるとアーカイブが取り直し続けるため畳んで持つ
  const criteria: ArchiveCriteria = useMemo(
    () => ({
      customer_name: applied.customerName || undefined,
      business_date: applied.businessDate || undefined,
      sort_key: sortKey,
      desc: descending,
    }),
    [applied, sortKey, descending]
  );

  const queue = useCursorList<Order, ArchiveCriteria>(
    (cursor, activeCriteria) =>
      orderApi.listWorkQueue({
        ...activeCriteria,
        statuses: ACTIVE_STATUSES,
        cursor,
        size: QUEUE_PAGE_SIZE,
      }),
    criteria
  );

  const apply = (next: SearchDraft) => {
    setApplied(next);
    queue.search({
      customer_name: next.customerName || undefined,
      business_date: next.businessDate || undefined,
      sort_key: sortKey,
      desc: descending,
    });
  };

  const applySort = (key: OrderSortKey, desc: boolean) => {
    setSortKey(key);
    setDescending(desc);
    queue.search({
      customer_name: applied.customerName || undefined,
      business_date: applied.businessDate || undefined,
      sort_key: key,
      desc,
    });
  };

  /** 処理し終えた受注は群の対象から外れるので、手元から取り除くだけで一覧は正しくなる。 */
  const removeFromQueue = (id: string) => queue.setRows(prev => prev.filter(row => row.id !== id));

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
                onProcessed={removeFromQueue}
                onEdit={target =>
                  target.status === 'CREATED' ? setEditingRequest(target) : setEditing(target)
                }
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
          onCorrectAttribution={setCorrecting}
        />
        <OrderArchiveSection
          title="取消"
          status="CANCELLED"
          criteria={criteria}
          onCorrectAttribution={setCorrecting}
        />
      </div>

      <OrderEditModal
        order={editing}
        onClose={() => setEditing(null)}
        onSaved={updated =>
          queue.setRows(prev => prev.map(row => (row.id === updated.id ? updated : row)))
        }
      />
      {/* 未確定の申請は指名・受付担当を可空として扱う専用の契約で編集する（端点も契約も従来どおり） */}
      <ReservationRequestEditModal
        request={editingRequest}
        onClose={() => setEditingRequest(null)}
        onSaved={updated =>
          queue.setRows(prev => prev.map(row => (row.id === updated.id ? updated : row)))
        }
      />
      <OrderCompletionModal
        order={completing}
        onClose={() => setCompleting(null)}
        // 完了した受注は作業キューから外れてアーカイブへ移る
        onCompleted={() => removeFromQueue(completing?.id ?? '')}
      />
      {/* 訂正は受注の状態も会計欄も変えないため、一覧の取り直しは要らない */}
      <OrderAttributionModal order={correcting} onClose={() => setCorrecting(null)} />
    </div>
  );
}
