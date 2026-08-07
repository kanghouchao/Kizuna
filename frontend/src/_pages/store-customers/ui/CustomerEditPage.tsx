'use client';

import { useCallback, useState, useEffect } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { CustomerForm, CustomerFormData, toCustomerRequest } from './CustomerForm';
import { MemberLinkSection } from './MemberLinkSection';
import { CustomerResponse, customerApi } from '@/entities/customer';
import { Order, orderApi } from '@/entities/order';
import { notify } from '@/shared/notify';
import { isNotFound, storePath } from '@/shared/lib';
import {
  RegionError,
  Table,
  TableBody,
  TableCard,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui';

/** 顧客編集ページ（プロフィール編集 + 注文履歴） */
export default function CustomerEditPage() {
  const params = useParams();
  const id = params.id as string;
  const storeId = params.storeId as string;
  const router = useRouter();
  const [customer, setCustomer] = useState<CustomerResponse | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadFailure, setLoadFailure] = useState<'notFound' | 'error' | null>(null);
  const [ordersStatus, setOrdersStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // 再試行から呼び直せるよう effect の外に置く。取得できなかったこの頁自身が失敗を名乗るので、
  // 一覧へ送り返さない — 離脱すると説明責任が着地先へ移り、開いていた頁で再試行できなくなる
  const fetchCustomer = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await customerApi.get(id);
      setCustomer(data);
      setLoadFailure(null);
    } catch (error) {
      setCustomer(null);
      // 404 は何度押しても取れない。再試行ではなく一覧への導線だけを出す
      setLoadFailure(isNotFound(error) ? 'notFound' : 'error');
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  // 注文履歴は附属区画。プロフィールの取得とは独立に取り、独立に再試行できる。
  // 再試行の前に読み込み中へ戻す — 同じ姿のまま失敗を繰り返すと role="alert" が挿入されず、
  // 二度目の失敗が読み上げ利用者に届かない
  const fetchOrders = useCallback(async () => {
    setOrdersStatus('loading');
    try {
      const page = await orderApi.list({ customer_id: id, size: 20, sort: 'businessDate,desc' });
      setOrders(page.rows);
      setOrdersStatus('ready');
    } catch {
      setOrders([]);
      setOrdersStatus('error');
    }
  }, [id]);

  useEffect(() => {
    void fetchCustomer();
  }, [fetchCustomer]);

  useEffect(() => {
    void fetchOrders();
  }, [fetchOrders]);

  const handleSubmit = async (data: CustomerFormData) => {
    try {
      setIsSubmitting(true);
      await customerApi.update(id, toCustomerRequest(data));
      notify.success('顧客情報を更新しました');
      router.push(storePath(storeId, '/customers'));
    } catch {
      notify.error('顧客情報の更新に失敗しました');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return <div className="p-8 text-center text-muted-foreground">読み込み中...</div>;
  }

  if (loadFailure !== null) {
    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-foreground">顧客編集</h1>
        {loadFailure === 'notFound' ? (
          <RegionError
            message="この顧客は見つかりませんでした"
            fallback={{ href: storePath(storeId, '/customers'), label: '顧客一覧へ' }}
          />
        ) : (
          <RegionError
            message="顧客情報の取得に失敗しました"
            onRetry={() => void fetchCustomer()}
          />
        )}
      </div>
    );
  }

  if (!customer) return null;

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-foreground">顧客編集</h1>
          <p className="text-sm text-muted-foreground mt-1">
            「{customer.name}」の情報を編集します。
          </p>
        </div>
        <div className="text-sm text-muted-foreground bg-card px-4 py-2 rounded-md border border-border">
          保有ポイント: <span className="font-semibold">{customer.points ?? 0}</span>
        </div>
      </div>

      <CustomerForm
        initialData={{
          name: customer.name,
          phone_number: customer.phone_number || '',
          phone_number2: customer.phone_number2 || '',
          address: customer.address || '',
          building_name: customer.building_name || '',
          classification: customer.classification || '',
          has_pet: customer.has_pet ?? false,
          rank: customer.rank || '',
          line_id: customer.line_id || '',
          usage_areas: customer.usage_areas || '',
          ng_type: customer.ng_type || '',
          ng_content: customer.ng_content || '',
        }}
        onSubmit={handleSubmit}
        isSubmitting={isSubmitting}
      />

      <MemberLinkSection customerId={id} />

      {/* 注文履歴 */}
      <TableCard>
        <div className="border-b bg-muted/50 px-6 py-4">
          <h2 className="text-lg font-medium text-foreground">注文履歴</h2>
        </div>
        {ordersStatus === 'loading' ? (
          <div className="p-8 text-center text-muted-foreground">読み込み中...</div>
        ) : ordersStatus === 'error' ? (
          // 「注文履歴がありません」と言い切ると、読めなかっただけの状態が事実に化ける。
          // 失敗を名乗るのはこの区画だけで、頁の他の部分は編集できるまま残る
          <RegionError
            message="注文履歴の取得に失敗しました"
            onRetry={() => void fetchOrders()}
            className="justify-center p-8"
          />
        ) : orders.length === 0 ? (
          <div className="p-8 text-center text-muted-foreground">注文履歴がありません</div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>営業日</TableHead>
                <TableHead>キャスト</TableHead>
                <TableHead>コース</TableHead>
                <TableHead>使用ポイント</TableHead>
                <TableHead>ステータス</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {orders.map(order => (
                <TableRow key={order.id}>
                  <TableCell className="text-foreground">{order.business_date}</TableCell>
                  <TableCell className="text-muted-foreground">{order.cast_name || '-'}</TableCell>
                  <TableCell className="text-muted-foreground">
                    {order.course_minutes != null ? `${order.course_minutes}分` : '-'}
                    {(order.extension_minutes ?? 0) > 0 && ` (+延長${order.extension_minutes}分)`}
                  </TableCell>
                  <TableCell className="text-muted-foreground">{order.used_points}</TableCell>
                  <TableCell className="text-muted-foreground">{order.status}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </TableCard>
    </div>
  );
}
