'use client';

import Link from 'next/link';
import { usePathname, useSearchParams } from 'next/navigation';
import { useMemo, useState } from 'react';
import { ArrowDownIcon, ArrowUpIcon, PlusIcon } from 'lucide-react';
import {
  ORDER_SORT_KEY_LABELS,
  OrderArchiveRow,
  OrderWorkQueueRow,
  OrderApplicationRow,
  OrderListCriteria,
  OrderSortKey,
  OrderStatus,
  orderApi,
  orderApplicationApi,
} from '@/entities/order';
import { hasPermission, useMe } from '@/entities/user';
import { storePath, useListPage } from '@/shared/lib';
import { PageHeader } from '@/widgets/page-header';
import { OrderApplicationCard } from './OrderApplicationCard';
import { OrderArchiveSection } from './OrderArchiveSection';
import { OrderAttributionModal } from './OrderAttributionModal';
import { OrderQueueCard } from './OrderQueueCard';
import { useSearchOnCriteriaChange } from '../lib/useSearchOnCriteriaChange';
import { applicationConfirmHref, orderCompletionHref } from '../lib/orderListHref';
import {
  Button,
  Card,
  CardContent,
  Input,
  Label,
  Pager,
  RegionError,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui';

/** 作業キューと受付箱の 1 ページの件数。 */
const QUEUE_PAGE_SIZE = 20;

/** 対応が要る未完了受注の群。未処理の予約申請は受付箱が持つ。 */
const ACTIVE_STATUSES: OrderStatus[] = ['CONFIRMED', 'IN_SERVICE'];

/** 作業キューを離れた受注の行き先。終端状態はこの 2 つしかない（ADR 0013）。 */
type ArchiveStatus = Extract<OrderStatus, 'COMPLETED' | 'CANCELLED'>;

const SORT_KEYS = Object.keys(ORDER_SORT_KEY_LABELS) as OrderSortKey[];

interface SearchDraft {
  customerName: string;
  businessDate: string;
}

const EMPTY_DRAFT: SearchDraft = { customerName: '', businessDate: '' };

const DEFAULT_SORT_KEY: OrderSortKey = 'BUSINESS_DATE';

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/**
 * 暦に有る日付か。Date は 2 月 30 日を 3 月 2 日へ繰り上げるので書き戻して一致を見る。13 月などは
 * 無効な Date になり toISOString が投げる（描画中なので一覧ごと落ちる）ため、先に除く。
 */
const isCalendarDate = (value: string) => {
  if (!ISO_DATE.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(value);
};

/** 画面が適用している条件。並びは常にどれかが選ばれている。 */
type AppliedCriteria = OrderListCriteria & { sort_key: OrderSortKey; desc: boolean };

/**
 * URL の query を読み口の条件へ読む。適用済みの条件は URL だけが持ち、再読込・戻る・進むで同じ一覧に着く。
 * 手で書き換えられた値は既定へ倒す（不正な並びや日付をそのまま送ると一覧ごと取得に失敗する）。
 */
function parseCriteria(query: string): AppliedCriteria {
  const params = new URLSearchParams(query);
  const sortKey = params.get('sort_key');
  const businessDate = params.get('business_date');
  return {
    customer_name: params.get('customer_name') || undefined,
    business_date: businessDate && isCalendarDate(businessDate) ? businessDate : undefined,
    sort_key: SORT_KEYS.includes(sortKey as OrderSortKey)
      ? (sortKey as OrderSortKey)
      : DEFAULT_SORT_KEY,
    desc: params.get('desc') === 'true',
  };
}

/** 条件を query へ書く。既定値は書かず、条件の無い一覧の URL を素のままに保つ。 */
function toQuery(criteria: AppliedCriteria): string {
  const params = new URLSearchParams();
  if (criteria.customer_name) params.set('customer_name', criteria.customer_name);
  if (criteria.business_date) params.set('business_date', criteria.business_date);
  if (criteria.sort_key !== DEFAULT_SORT_KEY) params.set('sort_key', criteria.sort_key);
  if (criteria.desc) params.set('desc', 'true');
  return params.toString();
}

const toDraft = (criteria: OrderListCriteria): SearchDraft => ({
  customerName: criteria.customer_name ?? '',
  businessDate: criteria.business_date ?? '',
});

export default function OrderListPage() {
  // 完了後訂正は ORDER_CORRECT（店長）限定。導線の表示制御だけで、強制はサーバ側 @PreAuthorize。
  const { me } = useMe();
  const canCorrect = hasPermission(me, 'ORDER_CORRECT');
  const canReport =
    hasPermission(me, 'OPERATIONAL_REPORT_VIEW') && hasPermission(me, 'ORDER_MANAGE');

  const pathname = usePathname();
  // 参照ではなく文字列で畳む。参照が描画ごとに変わると、群が条件の変化と見て取り直し続ける
  const query = useSearchParams().toString();
  const criteria = useMemo(() => parseCriteria(query), [query]);
  const { sort_key: sortKey, desc: descending } = criteria;

  // 入力中の値と「適用済み」の条件（URL）を分ける。取得は適用済みだけを読む（DESIGN.md）。
  // 戻る・進むで適用済みの検索語が変わったときだけ入力を合わせる — 並びだけの変更で未適用の入力を捨てない
  const [draft, setDraft] = useState<SearchDraft>(() => toDraft(criteria));
  const [draftSource, setDraftSource] = useState(criteria);
  if (
    draftSource.customer_name !== criteria.customer_name ||
    draftSource.business_date !== criteria.business_date
  ) {
    setDraftSource(criteria);
    setDraft(toDraft(criteria));
  }

  // 退出中は対象を保持し、再度開くときだけ key を変えて入力と取得結果を初期化する。
  const [correctingDialog, setCorrectingDialog] = useState<{
    target: OrderArchiveRow;
    open: boolean;
    key: number;
  } | null>(null);
  const correcting = correctingDialog?.target ?? null;
  const setCorrecting = (target: OrderArchiveRow) =>
    setCorrectingDialog(previous => ({ target, open: true, key: (previous?.key ?? 0) + 1 }));

  const queue = useListPage<OrderWorkQueueRow, OrderListCriteria>(
    (page, activeCriteria) =>
      orderApi.listWorkQueue({
        ...activeCriteria,
        statuses: ACTIVE_STATUSES,
        page,
        size: QUEUE_PAGE_SIZE,
      }),
    criteria
  );
  useSearchOnCriteriaChange(criteria, queue.search);

  // 受付箱（未処理の予約申請）。検索・並びの条件は持たない — 希望日の早い順に処理するだけの箱で、
  // 条件を持たせると作業キューと 2 つの母集合を主張し合う。
  const inbox = useListPage<OrderApplicationRow>(page =>
    orderApplicationApi.list({ statuses: ['PENDING'], page, size: QUEUE_PAGE_SIZE })
  );

  // 条件の適用は history.pushState で履歴に積み、取得は URL の変化から起きる。次の条件は描画済みの
  // criteria ではなく今の URL から組む — 描画が URL に追いつく前の操作で、直前に送った条件を消さないため。
  // 同じ条件の検索は URL が変わらず取得も起きないので、履歴を積まずに作業キューだけを取り直す
  const pushCriteria = (update: (current: AppliedCriteria) => AppliedCriteria) => {
    const current = parseCriteria(window.location.search);
    const nextQuery = toQuery(update(current));
    if (nextQuery === toQuery(current)) {
      void queue.search(current);
      return;
    }
    window.history.pushState(null, '', nextQuery ? `${pathname}?${nextQuery}` : pathname);
  };

  const apply = (next: SearchDraft) =>
    pushCriteria(current => ({
      ...current,
      customer_name: next.customerName || undefined,
      business_date: next.businessDate || undefined,
    }));

  const applySortKey = (key: OrderSortKey) =>
    pushCriteria(current => ({ ...current, sort_key: key }));

  const toggleDirection = () => pushCriteria(current => ({ ...current, desc: !current.desc }));

  // アーカイブへ移った受注を群ごとに数える。件数の控えはたたんだままでも出ているので、移った先を
  // 取り直さないと「アーカイブに入ったのか」が画面のどこからも読めない
  const [archived, setArchived] = useState<Record<ArchiveStatus, number>>({
    COMPLETED: 0,
    CANCELLED: 0,
  });

  /** 取消した受注の後始末。作業キューを取り直し、取消のアーカイブを取り直させる。 */
  const settleCancelled = () => {
    void queue.reload();
    setArchived(prev => ({ ...prev, CANCELLED: prev.CANCELLED + 1 }));
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="オーダー一覧"
        description="対応が要る受注を前面に、完了・取消はアーカイブにまとめています。"
        actions={
          <>
            {canReport && (
              <Button variant="outline" render={<Link href={storePath('/operational-reports')} />}>
                運営金額集計
              </Button>
            )}
            <Button
              variant="outline"
              render={<Link href={storePath('/orders/monthly-remunerations')} />}
            >
              月次給与明細
            </Button>
            <Button render={<Link href={storePath('/orders/create')} />}>
              <PlusIcon aria-hidden="true" />
              新規オーダー登録
            </Button>
          </>
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
                  onValueChange={v => applySortKey(v as OrderSortKey)}
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
                  onClick={toggleDirection}
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
          <RegionError
            message="予約申請を取得できませんでした。"
            onRetry={() => void inbox.reload()}
          />
        ) : inbox.isLoading && inbox.rows.length === 0 ? (
          <p className="text-muted-foreground text-sm">読み込み中...</p>
        ) : inbox.rows.length === 0 && inbox.page === 0 ? (
          <p className="text-muted-foreground bg-card rounded-lg border p-4 text-sm">
            未処理の予約申請はありません
          </p>
        ) : (
          <ul className="space-y-3">
            {inbox.rows.map(application => (
              <OrderApplicationCard
                key={application.id}
                application={application}
                onDeclined={() => void inbox.reload()}
                confirmHref={
                  application.id ? applicationConfirmHref(application.id, query) : undefined
                }
              />
            ))}
          </ul>
        )}
        <Pager
          page={inbox.page}
          pageCount={inbox.pageCount}
          total={inbox.total}
          rowCount={inbox.rows.length}
          disabled={inbox.isLoading}
          onPageChange={page => void inbox.onPageChange(page)}
        />
      </section>

      <section className="space-y-3">
        <h2 className="text-foreground text-sm font-medium">対応が要る</h2>
        {/* 取得の失敗を空表示と区別する — 「受注なし」に見せると未対応を見落とす */}
        {queue.failed ? (
          <RegionError message="受注を取得できませんでした。" onRetry={() => void queue.reload()} />
        ) : queue.isLoading && queue.rows.length === 0 ? (
          <p className="text-muted-foreground text-sm">読み込み中...</p>
        ) : queue.rows.length === 0 && queue.page === 0 ? (
          // 後ろのページで行が尽きたときは「受注なし」と言い切らない（前のページへ戻るページ送りが残る）
          <p className="text-muted-foreground bg-card rounded-lg border p-4 text-sm">
            条件に合う受注がありません
          </p>
        ) : (
          <ul className="space-y-3">
            {queue.rows.map(order => (
              <OrderQueueCard
                key={order.id}
                order={order}
                onProcessed={settleCancelled}
                completionHref={order.id ? orderCompletionHref(order.id, query) : undefined}
              />
            ))}
          </ul>
        )}
        <Pager
          page={queue.page}
          pageCount={queue.pageCount}
          total={queue.total}
          rowCount={queue.rows.length}
          disabled={queue.isLoading}
          onPageChange={page => void queue.onPageChange(page)}
        />
      </section>

      <div className="space-y-3">
        <OrderArchiveSection
          title="完了"
          status="COMPLETED"
          criteria={criteria}
          reloadToken={archived.COMPLETED}
          onCorrectAttribution={setCorrecting}
          canCorrect={canCorrect}
        />
        <OrderArchiveSection
          title="取消"
          status="CANCELLED"
          criteria={criteria}
          reloadToken={archived.CANCELLED}
          onCorrectAttribution={setCorrecting}
          canCorrect={canCorrect}
        />
      </div>

      {/* 訂正は受注の状態も会計欄も変えないため、一覧の取り直しは要らない */}
      <OrderAttributionModal
        key={`correcting-${correctingDialog?.key}`}
        open={correctingDialog?.open ?? false}
        order={correcting}
        onClose={missing => {
          setCorrectingDialog(previous => previous && { ...previous, open: false });
          if (missing) setArchived(prev => ({ ...prev, COMPLETED: prev.COMPLETED + 1 }));
        }}
      />
    </div>
  );
}
