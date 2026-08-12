'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { CircleCheckIcon, PlusIcon, SquarePenIcon } from 'lucide-react';
import { ORDER_STATUS_LABELS, Order, OrderStatus, orderApi } from '@/entities/order';
import { storePath, useListPage } from '@/shared/lib';
import { ListPage } from '@/widgets/list-page';
import { UNLINKED_NOTE, customerLabel } from '../lib/customerLabel';
import { OrderCompletionModal } from './OrderCompletionModal';
import { ReservationRequestInbox } from './ReservationRequestInbox';
import {
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';

/** 一覧 1 ページあたりの件数 */
const PAGE_SIZE = 20;

/**
 * 状態バッジの色。全状態を同じ色で塗ると、キャンセルまで成功の緑で出る。
 *
 * 未確定は中立（bg-muted に muted の文字を重ねると光量比が足りないため、文字は foreground）。
 * 申請中の黄は使わない — 未確定は店舗が手入力した受注でも起きるので、店舗側では中立に呼ぶ。
 */
const STATUS_BADGE_CLASS: Record<OrderStatus, string> = {
  CREATED: 'border-transparent bg-muted text-foreground',
  CONFIRMED: 'border-transparent bg-success/10 text-success-strong',
  COMPLETED: 'border-transparent bg-primary/10 text-primary-strong',
  CANCELLED: 'border-transparent bg-destructive/10 text-destructive-strong',
};

/**
 * 一覧の「お客様名」欄。台帳の顧客名が無ければ受付で録入された連絡先で呼び、台帳に行を持たない
 * 申告のままの値であることを注記で添える（注記は他を説明する文字なので muted）。
 */
function CustomerCell({ order }: { order: Order }) {
  const label = customerLabel(order);
  if (label === null) {
    return <>-</>;
  }
  return (
    <>
      <span>{label.name}</span>
      {label.unlinked && <span className="text-muted-foreground">{UNLINKED_NOTE}</span>}
    </>
  );
}

export default function OrderListPage() {
  const params = useParams();
  const storeId = params.storeId as string;
  const [completing, setCompleting] = useState<Order | null>(null);
  const list = useListPage(
    // created_at は一意でない可能性があるため、offset ページングの境界を確定させる
    // 一意な副キーを添える（sort=prop1,prop2,direction は Spring Data の複数キー形式）
    page => orderApi.list({ page, size: PAGE_SIZE, sort: 'createdAt,id,desc' })
  );
  const orders = list.rows;

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle role="heading" aria-level={2}>
            予約受付
          </CardTitle>
        </CardHeader>
        <CardContent>
          <ReservationRequestInbox onProcessed={list.reload} />
        </CardContent>
      </Card>
      <ListPage
        title="オーダー一覧"
        description="当日の注文状況を確認・管理できます。"
        actions={
          <Button render={<Link href={storePath(storeId, '/orders/create')} />}>
            <PlusIcon aria-hidden="true" />
            新規オーダー登録
          </Button>
        }
        state={list}
        emptyMessage="オーダーがありません"
        errorMessage="オーダーの取得に失敗しました"
        onRetry={list.reload}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>営業日</TableHead>
              <TableHead>お客様名</TableHead>
              <TableHead>女の子名</TableHead>
              <TableHead>人数</TableHead>
              <TableHead>コース</TableHead>
              <TableHead>ステータス</TableHead>
              <TableHead className="text-right">アクション</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {orders.map(order => (
              <TableRow key={order.id}>
                <TableCell className="text-muted-foreground">{order.business_date}</TableCell>
                <TableCell className="text-foreground">
                  <CustomerCell order={order} />
                </TableCell>
                <TableCell>
                  <Badge
                    variant="outline"
                    className={
                      !order.cast_name || order.cast_name === 'フリー'
                        ? 'border-transparent bg-muted text-foreground'
                        : 'border-transparent bg-chart-5/10 text-foreground'
                    }
                  >
                    {order.cast_name || 'フリー'}
                  </Badge>
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {order.pax != null ? `${order.pax} 名` : '-'}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {order.course_minutes != null ? `${order.course_minutes} 分` : '-'}
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-1">
                    <Badge
                      variant="outline"
                      className={
                        order.status
                          ? STATUS_BADGE_CLASS[order.status]
                          : 'border-transparent bg-muted text-foreground'
                      }
                    >
                      {order.status ? ORDER_STATUS_LABELS[order.status] : '-'}
                    </Badge>
                    {/* 申請の判定は受付経路だけでは足りない（店舗が手入力の受注にも付けられる）。
                      サーバ側の予約受付 inbox と同じく申請者の有無まで見る。 */}
                    {order.reception_route === 'WEB' && order.requester_member_code && (
                      <Badge
                        variant="outline"
                        className="border-transparent bg-primary/10 text-primary-strong"
                      >
                        WEB申請
                      </Badge>
                    )}
                  </div>
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end gap-1">
                    {/* 完了できるのは確定済みの受注だけ（それ以外はサーバ側が遷移を撥ねる）。
                        会計金額とポイントを決める操作なので、遷移ではなくこの場で開く */}
                    {order.status === 'CONFIRMED' && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={() => setCompleting(order)}
                      >
                        <CircleCheckIcon aria-hidden="true" />
                        完了
                      </Button>
                    )}
                    <Button
                      render={<Link href={storePath(storeId, `/orders/${order.id}/edit`)} />}
                      variant="ghost"
                      size="icon-sm"
                    >
                      <SquarePenIcon />
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>
      <OrderCompletionModal
        order={completing}
        onClose={() => setCompleting(null)}
        // 完了した受注は状態も会計欄も変わって一覧に残る。useListPage は行の差し替え口を
        // 持たないため、現在のページをそのまま取り直す。
        onCompleted={list.reload}
      />
    </div>
  );
}
