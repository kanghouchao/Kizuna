'use client';

import { useState, useEffect } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { CustomerForm, CustomerFormData, toCustomerRequest } from './CustomerForm';
import { CustomerResponse, customerApi } from '@/entities/customer';
import { Order, orderApi } from '@/entities/order';
import { toast } from 'react-hot-toast';

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
        router.push(`/store/${storeId}/customers`);
        return;
      } finally {
        setIsLoading(false);
      }
      try {
        const page = await orderApi.list({ customer_id: id, size: 20, sort: 'businessDate,desc' });
        setOrders(page.content);
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
      router.push(`/store/${storeId}/customers`);
    } catch {
      toast.error('顧客情報の更新に失敗しました');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return <div className="p-8 text-center text-gray-500">読み込み中...</div>;
  }

  if (!customer) return null;

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">顧客編集</h1>
          <p className="text-sm text-gray-500 mt-1">「{customer.name}」の情報を編集します。</p>
        </div>
        <div className="text-sm text-gray-600 bg-white px-4 py-2 rounded-md border border-gray-200">
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
      <div className="bg-white shadow-sm border border-gray-200 rounded-lg overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-200 bg-gray-50">
          <h3 className="text-lg font-medium text-gray-900">注文履歴</h3>
        </div>
        {orders.length === 0 ? (
          <div className="p-8 text-center text-gray-500">注文履歴がありません</div>
        ) : (
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  営業日
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  キャスト
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  コース
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  使用ポイント
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  ステータス
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {orders.map(order => (
                <tr key={order.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {order.business_date}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {order.cast_name || '-'}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {order.course_minutes}分
                    {order.extension_minutes > 0 && ` (+延長${order.extension_minutes}分)`}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {order.used_points}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {order.status}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
