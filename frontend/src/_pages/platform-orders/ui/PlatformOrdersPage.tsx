'use client';

import { useEffect, useState } from 'react';
import { orderApi, ORDER_STATUS_LABELS } from '@/entities/order';
import { hasPermission, readTokenClaims, useListPage } from '@/shared/lib';
import { Button } from '@/shared/ui';
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
        <ul className="divide-y">
          {list.rows.map(order => (
            <li key={order.id} className="space-y-3 p-6">
              <h2 className="font-medium">
                店舗 {order.store_id} / 受注 {order.id}
              </h2>
              <p>
                {ORDER_STATUS_LABELS[order.status]} / 営業日 {order.business_date ?? '未設定'}
              </p>
              <p>
                {order.course.name} / コース費用 ¥{order.course.price.toLocaleString()}
              </p>
              <p>請求総額 ¥{order.total_fee.toLocaleString()}</p>
              <p>
                {order.status === 'CANCELLED'
                  ? '報酬発生なし'
                  : order.status === 'COMPLETED'
                    ? `発生済み報酬 ¥${order.accrued_remuneration.toLocaleString()}`
                    : `予定報酬 ¥${order.total_remuneration.toLocaleString()}`}
              </p>
              {order.completed_at && (
                <p>原完了日時 {new Date(order.completed_at).toLocaleString('ja-JP')}</p>
              )}
              <Button variant="outline" size="sm" onClick={() => setSelected(order.id)}>
                訂正履歴
              </Button>
            </li>
          ))}
        </ul>
      </ListPage>
      {selected && (
        <OrderCorrectionHistoryModal
          orderId={selected}
          scope="platform"
          open
          onOpenChange={open => {
            if (!open) setSelected(null);
          }}
        />
      )}
    </>
  );
}
