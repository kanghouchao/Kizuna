'use client';

import Link from 'next/link';
import { useState } from 'react';
import { orderApi, ORDER_STATUS_LABELS, type AdminOrder } from '@/entities/order';
import { MePending, hasPermission, useMe } from '@/entities/user';
import { useListPage } from '@/shared/lib';
import {
  Badge,
  Button,
  Dialog,
  DialogFrame,
  KeyValueList,
  KeyValueRow,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';
import { OrderCorrectionHistoryModal } from '@/widgets/order-correction-history';
import { DeniedPage } from '@/widgets/admin-shell';

export default function AdminOrdersPage() {
  const { me } = useMe();
  if (me === null) return <MePending />;
  if (!hasPermission(me, 'ORDER_SET_VIEW')) return <DeniedPage />;
  return <OrderList />;
}

function OrderList() {
  const list = useListPage(orderApi.adminList);
  const { me } = useMe();
  const canReport = hasPermission(me, 'OPERATIONAL_REPORT_SET_VIEW');
  // 訂正履歴は行の店舗の店舗級の端点を読むので、店舗の木の受注の権限を持つ者にだけ出す。
  const managesOrders = hasPermission(me, 'ORDER_MANAGE');
  const [details, setDetails] = useState<AdminOrder | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [selected, setSelected] = useState<AdminOrder | null>(null);
  return (
    <>
      <ListPage
        title="受注照会"
        actions={
          canReport && (
            <Button variant="outline" render={<Link href="/admin/operational-reports" />}>
              運営金額集計
            </Button>
          )
        }
        description="授権された店舗の受注を閲覧できます。報酬は支払済み額ではありません。"
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
                    {managesOrders && (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          setSelected(order);
                          setHistoryOpen(true);
                        }}
                      >
                        訂正履歴<span className="sr-only">：受注 {order.id}</span>
                      </Button>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ListPage>
      <Dialog open={detailsOpen} onOpenChange={setDetailsOpen}>
        <DialogFrame
          size="md"
          title="受注詳細"
          description="受注時の条件と現在の金額を確認できます。報酬は支払済み額ではありません。"
        >
          {details && (
            <KeyValueList>
              <KeyValueRow label="受注ID">{details.id}</KeyValueRow>
              <KeyValueRow label="店舗">{details.store_id}</KeyValueRow>
              <KeyValueRow label="状態">
                {details.completion_invalidated
                  ? '誤完了・無効化済み'
                  : ORDER_STATUS_LABELS[details.status]}
              </KeyValueRow>
              <KeyValueRow label="営業日">{details.business_date ?? '未設定'}</KeyValueRow>
              <KeyValueRow label="コース">{details.course.name}</KeyValueRow>
              <KeyValueRow
                label={details.completion_invalidated ? '原コース費用' : 'コース費用'}
                money
              >
                ¥{details.course.price.toLocaleString()}
              </KeyValueRow>
              <KeyValueRow label="請求総額" money>
                <span className="font-semibold">¥{details.total_fee.toLocaleString()}</span>
              </KeyValueRow>
              <KeyValueRow
                label={details.status === 'COMPLETED' ? '発生済み報酬' : '予定報酬'}
                money
              >
                {details.status === 'CANCELLED'
                  ? '報酬発生なし'
                  : `¥${(details.status === 'COMPLETED' ? details.accrued_remuneration : details.total_remuneration).toLocaleString()}`}
              </KeyValueRow>
              {details.completed_at && (
                <KeyValueRow label="原完了日時">
                  {new Date(details.completed_at).toLocaleString('ja-JP')}
                </KeyValueRow>
              )}
              {details.replacement_for_order_id && (
                <KeyValueRow label="再提供の元受注">{details.replacement_for_order_id}</KeyValueRow>
              )}
            </KeyValueList>
          )}
        </DialogFrame>
      </Dialog>
      {selected && (
        <OrderCorrectionHistoryModal
          orderId={selected.id}
          storeId={selected.store_id}
          open={historyOpen}
          onOpenChange={setHistoryOpen}
        />
      )}
    </>
  );
}
