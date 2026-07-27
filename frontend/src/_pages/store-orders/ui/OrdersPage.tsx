'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { PlusIcon, PencilSquareIcon } from '@heroicons/react/24/outline';
import { Order, orderApi } from '@/entities/order';
import { storePath, useManagedList } from '@/shared/lib';
import { PageHeader } from '@/widgets/page-header';
import {
  Badge,
  Button,
  Card,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';

export default function OrderListPage() {
  const params = useParams();
  const storeId = params.storeId as string;
  const { items: orders, isLoading } = useManagedList<Order>(
    () => orderApi.list({ size: 100, sort: 'createdAt,desc' }).then(page => page.content),
    'オーダーの取得に失敗しました'
  );

  return (
    <div className="space-y-6">
      <PageHeader
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
      />

      {/* Orders Table */}
      <Card className="py-0 overflow-hidden">
        {isLoading ? (
          <div className="p-8 text-center text-muted-foreground">読み込み中...</div>
        ) : orders.length === 0 ? (
          <div className="p-8 text-center text-muted-foreground">オーダーがありません</div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>営業日</TableHead>
                <TableHead>お客様名</TableHead>
                <TableHead>女の子名</TableHead>
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
                  <TableCell className="text-muted-foreground">{order.course_minutes} 分</TableCell>
                  <TableCell>
                    <Badge
                      variant="outline"
                      className="border-transparent bg-success/10 text-success-strong"
                    >
                      {order.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-1">
                      <Button asChild variant="ghost" size="icon-sm">
                        <Link href={storePath(storeId, `/orders/${order.id}/edit`)}>
                          <PencilSquareIcon />
                        </Link>
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
    </div>
  );
}
