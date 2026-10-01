'use client';

import { useState } from 'react';
import Link from 'next/link';
import { selfRemunerationApi } from '@/entities/order';
import { isForbidden, isNotFound, useListPage } from '@/shared/lib';
import { Button, Card, CardContent } from '@/shared/ui';
import { ListPage } from '@/widgets/list-page';
import { Amounts, Failure, RemunerationDetail } from './RemunerationDetail';

const memberships = { ENROLLED: '在籍中', SUSPENDED: '停止中', WITHDRAWN: '退店' };

export function CastRemunerationsPage() {
  const [orderId, setOrderId] = useState<string | null>(null);
  return (
    <div className="mx-auto max-w-3xl space-y-4 p-4">
      {orderId && <h1 className="text-2xl font-bold">報酬明細</h1>}
      <Button variant="outline" render={<Link href="/cast/remunerations/monthly" />}>
        月次給与明細
      </Button>
      <p className="text-sm text-muted-foreground">
        予定・発生済みの固定報酬を確認できます。支払済み額ではありません。
      </p>
      {orderId ? (
        <RemunerationDetail key={orderId} id={orderId} onBack={() => setOrderId(null)} />
      ) : (
        <RemunerationList onOpen={setOrderId} />
      )}
    </div>
  );
}

function RemunerationList({ onOpen }: { onOpen: (id: string) => void }) {
  const enrollments = useListPage(page => selfRemunerationApi.enrollments(page));
  const list = useListPage(
    (page, enrollment: string | undefined) => selfRemunerationApi.list(page, enrollment),
    undefined
  );
  const [selected, setSelected] = useState<string>();
  const choose = (id?: string) => {
    setSelected(id);
    void list.search(id);
  };
  const denied = isForbidden(list.error) || isNotFound(list.error);
  const enrollmentDenied = isForbidden(enrollments.error) || isNotFound(enrollments.error);
  return (
    <>
      <section aria-label="在籍で絞り込む" className="space-y-3">
        <Button
          variant={selected ? 'outline' : 'default'}
          aria-pressed={!selected}
          onClick={() => choose()}
        >
          すべての在籍
        </Button>
        {enrollmentDenied ? (
          <Failure error={enrollments.error} onRetry={() => void enrollments.reload()} />
        ) : (
          <ListPage
            title="在籍で絞り込む"
            state={enrollments}
            emptyMessage="在籍履歴はありません"
            errorMessage="在籍履歴の取得に失敗しました"
            onRetry={() => void enrollments.reload()}
          >
            <div className="flex flex-wrap gap-2 p-4">
              {enrollments.rows.map(e => (
                <Button
                  key={e.enrollment_id}
                  variant={selected === e.enrollment_id ? 'default' : 'outline'}
                  aria-pressed={selected === e.enrollment_id}
                  onClick={() => choose(e.enrollment_id)}
                >
                  {e.store_name}・{memberships[e.status]}
                  {e.ended_at ? `（${e.ended_at.slice(0, 10)}）` : ''}
                </Button>
              ))}
            </div>
          </ListPage>
        )}
      </section>
      <section aria-label="報酬一覧" className="space-y-3">
        {denied ? (
          <>
            <h1 className="text-2xl font-bold">報酬明細</h1>
            <Failure error={list.error} onRetry={() => void list.reload()} />
          </>
        ) : (
          <ListPage
            title="報酬明細"
            state={list}
            emptyMessage="報酬明細はありません"
            errorMessage="報酬明細の取得に失敗しました"
            onRetry={() => void list.reload()}
          >
            <div className="space-y-3">
              {list.rows.map(row => (
                <Card key={row.order_id}>
                  <CardContent className="space-y-3 p-4">
                    <Amounts row={row} />
                    <Button onClick={() => onOpen(row.order_id)}>詳細を確認</Button>
                  </CardContent>
                </Card>
              ))}
            </div>
          </ListPage>
        )}
      </section>
    </>
  );
}
