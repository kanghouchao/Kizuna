'use client';

import { useState, useEffect } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { CustomerForm, CustomerFormData, toCustomerRequest } from './CustomerForm';
import { CustomerResponse, customerApi } from '@/entities/customer';
import { Order, orderApi } from '@/entities/order';
import { toast } from 'react-hot-toast';
import { storePath } from '@/shared/lib';
import {
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
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const data = await customerApi.get(id);
        setCustomer(data);
      } catch {
        toast.error('顧客情報の取得に失敗しました');
        router.push(storePath(storeId, '/customers'));
        return;
      } finally {
        setIsLoading(false);
      }
      try {
        const page = await orderApi.list({ customer_id: id, size: 20, sort: 'businessDate,desc' });
        setOrders(page.rows);
      } catch {
        // 注文履歴が取れなくてもプロフィール編集は可能にする
        toast.error('注文履歴の取得に失敗しました');
      }
    };
    fetchData();
  }, [id, router, storeId]);

  const handleSubmit = async (data: CustomerFormData) => {
    try {
      setIsSubmitting(true);
      await customerApi.update(id, toCustomerRequest(data));
      toast.success('顧客情報を更新しました');
      router.push(storePath(storeId, '/customers'));
    } catch {
      toast.error('顧客情報の更新に失敗しました');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return <div className="p-8 text-center text-muted-foreground">読み込み中...</div>;
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

      {/* 注文履歴 */}
      <TableCard>
        <div className="border-b bg-muted/50 px-6 py-4">
          <h2 className="text-lg font-medium text-foreground">注文履歴</h2>
        </div>
        {orders.length === 0 ? (
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
