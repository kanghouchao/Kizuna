'use client';

import { useEffect, useState } from 'react';
import { orderApi, ORDER_STATUS_LABELS, type PlatformOrder } from '@/entities/order';
import { hasPermission, readTokenClaims, useListPage } from '@/shared/lib';
import {
  Badge,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';
import { OrderCorrectionHistoryModal } from '@/widgets/order-correction-history';

export default function PlatformOrdersPage() {
  const [allowed, setAllowed] = useState<boolean | null>(null);
  useEffect(() => setAllowed(hasPermission(readTokenClaims(), 'ORDER_SET_MANAGE')), []);
  if (allowed === null) return <p role="status">権限を確認中...</p>;
  if (!allowed) return <p role="alert">受注を閲覧する権限がありません。</p>;
  return <OrderList />;
}

function OrderList() {
  const list = useListPage(orderApi.platformList);
  const [details, setDetails] = useState<PlatformOrder | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  return (
    <>
      <ListPage
        title="受注照会"
        description="授権された店舗の受注と訂正履歴を閲覧できます。報酬は支払済み額ではありません。"
        state={list}
        emptyMessage="受注はありません。"
        errorMessage="受注を取得できませんでした。権限を確認して再試行してください。"
        onRetry={() => void list.reload()}
      >
        <div className="flex items-center justify-between gap-4 border-b px-6 py-3 text-sm">
          <p className="font-medium">
            受注一覧 <span className="ml-2 tabular-nums">{list.total.toLocaleString()} 件</span>
          </p>
          <p className="text-muted-foreground">登録日時の新しい順 · 20件ずつ表示</p>
        </div>
        <Table aria-label="受注一覧">
          <TableHeader>
            <TableRow>
              <TableHead scope="col">営業日</TableHead>
              <TableHead scope="col">店舗 / 受注ID</TableHead>
              <TableHead scope="col">状態</TableHead>
              <TableHead scope="col">コース</TableHead>
              <TableHead scope="col" className="text-right">
                請求総額
              </TableHead>
              <TableHead scope="col" className="text-right">
                報酬
              </TableHead>
              <TableHead scope="col" className="text-right">
                照会
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {list.rows.map(order => (
              <TableRow key={order.id} className="group">
                <TableCell className="tabular-nums">{order.business_date ?? '未設定'}</TableCell>
                <TableCell>
                  <p className="font-medium">店舗 {order.store_id}</p>
                  <p
                    className="mt-1 w-36 whitespace-normal break-all text-xs text-muted-foreground group-hover:text-foreground"
                    title={order.id}
                  >
                    {order.id}
                  </p>
                </TableCell>
                <TableCell>
                  <Badge
                    variant="outline"
                    className={`border-transparent ${order.completion_invalidated ? 'bg-destructive/10 text-destructive-strong' : order.status === 'COMPLETED' ? 'bg-success/10 text-success-strong' : order.status === 'IN_SERVICE' ? 'bg-warning/10 text-warning-strong' : 'bg-muted text-foreground'}`}
                  >
                    {order.completion_invalidated
                      ? '誤完了・無効化済み'
                      : ORDER_STATUS_LABELS[order.status]}
                  </Badge>
                  {order.replacement_for_order_id && <p className="mt-1 text-xs">再提供</p>}
                </TableCell>
                <TableCell>
                  <p className="max-w-40 truncate" title={order.course.name}>
                    {order.course.name}
                  </p>
                </TableCell>
                <TableCell className="text-right font-semibold tabular-nums">
                  ¥{order.total_fee.toLocaleString()}
                </TableCell>
                <TableCell className="text-right tabular-nums">
                  {order.status === 'CANCELLED' ? (
                    '報酬発生なし'
                  ) : (
                    <>
                      <p>
                        ¥
                        {(order.status === 'COMPLETED'
                          ? order.accrued_remuneration
                          : order.total_remuneration
                        ).toLocaleString()}
                      </p>
                      <p className="mt-1 text-xs text-muted-foreground group-hover:text-foreground">
                        {order.status === 'COMPLETED' ? '発生済み' : '予定'}
                      </p>
                    </>
                  )}
                </TableCell>
                <TableCell>
                  <div className="flex justify-end gap-1">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        setDetails(order);
                        setDetailsOpen(true);
                      }}
                    >
                      詳細<span className="sr-only">：受注 {order.id}</span>
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => {
                        setSelected(order.id);
                        setHistoryOpen(true);
                      }}
                    >
                      訂正履歴<span className="sr-only">：受注 {order.id}</span>
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>
      <Dialog open={detailsOpen} onOpenChange={setDetailsOpen}>
        <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>受注詳細</DialogTitle>
            <DialogDescription>
              受注時の条件と現在の金額を確認できます。報酬は支払済み額ではありません。
            </DialogDescription>
          </DialogHeader>
          {details && (
            <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-6 gap-y-4 text-sm [&_dd]:break-words [&_dd]:text-right">
              <dt>受注ID</dt>
              <dd>{details.id}</dd>
              <dt>店舗</dt>
              <dd>{details.store_id}</dd>
              <dt>状態</dt>
              <dd>
                {details.completion_invalidated
                  ? '誤完了・無効化済み'
                  : ORDER_STATUS_LABELS[details.status]}
              </dd>
              <dt>営業日</dt>
              <dd>{details.business_date ?? '未設定'}</dd>
              <dt>コース</dt>
              <dd>{details.course.name}</dd>
              <dt>{details.completion_invalidated ? '原コース費用' : 'コース費用'}</dt>
              <dd>¥{details.course.price.toLocaleString()}</dd>
              <dt>請求総額</dt>
              <dd className="font-semibold tabular-nums">¥{details.total_fee.toLocaleString()}</dd>
              <dt>{details.status === 'COMPLETED' ? '発生済み報酬' : '予定報酬'}</dt>
              <dd>
                {details.status === 'CANCELLED'
                  ? '報酬発生なし'
                  : `¥${(details.status === 'COMPLETED' ? details.accrued_remuneration : details.total_remuneration).toLocaleString()}`}
              </dd>
              {details.completed_at && (
                <>
                  <dt>原完了日時</dt>
                  <dd>{new Date(details.completed_at).toLocaleString('ja-JP')}</dd>
                </>
              )}
              {details.replacement_for_order_id && (
                <>
                  <dt>再提供の元受注</dt>
                  <dd>{details.replacement_for_order_id}</dd>
                </>
              )}
            </dl>
          )}
        </DialogContent>
      </Dialog>
      {selected && (
        <OrderCorrectionHistoryModal
          orderId={selected}
          scope="platform"
          open={historyOpen}
          onOpenChange={setHistoryOpen}
        />
      )}
    </>
  );
}
