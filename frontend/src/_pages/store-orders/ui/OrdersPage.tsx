'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { PlusIcon, SquarePenIcon } from 'lucide-react';
import { ORDER_STATUS_LABELS, orderApi } from '@/entities/order';
import { storePath, useListPage } from '@/shared/lib';
import { ListPage } from '@/widgets/list-page';
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

export default function OrderListPage() {
  const params = useParams();
  const storeId = params.storeId as string;
  const list = useListPage(
    // created_at は一意でない可能性があるため、offset ページングの境界を確定させる
    // 一意な副キーを添える（sort=prop1,prop2,direction は Spring Data の複数キー形式）
    page => orderApi.list({ page, size: PAGE_SIZE, sort: 'createdAt,id,desc' }),
    'オーダーの取得に失敗しました'
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
          <Button asChild>
            <Link href={storePath(storeId, '/orders/create')}>
              <PlusIcon aria-hidden="true" />
              新規オーダー登録
            </Link>
          </Button>
        }
        state={list}
        emptyMessage="オーダーがありません"
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
                <TableCell className="text-foreground">{order.customer_name || '-'}</TableCell>
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
                      className="border-transparent bg-success/10 text-success-strong"
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
                    <Button asChild variant="ghost" size="icon-sm">
                      <Link href={storePath(storeId, `/orders/${order.id}/edit`)}>
                        <SquarePenIcon />
                      </Link>
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>
    </div>
  );
}
