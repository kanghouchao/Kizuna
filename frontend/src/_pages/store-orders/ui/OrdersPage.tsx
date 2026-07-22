'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { PlusIcon, PencilSquareIcon } from '@heroicons/react/24/outline';
import { Order, orderApi } from '@/entities/order';
import { storePath, useManagedList } from '@/shared/lib';

export default function OrderListPage() {
  const params = useParams();
  const storeId = params.storeId as string;
  const { items: orders, isLoading } = useManagedList<Order>(
    () => orderApi.list({ size: 100, sort: 'createdAt,desc' }).then(page => page.content),
    'オーダーの取得に失敗しました'
  );

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">オーダー一覧</h1>
          <p className="text-sm text-gray-500 mt-1">当日の注文状況を確認・管理できます。</p>
        </div>
        <Link
          href={storePath(storeId, '/orders/create')}
          className="inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700"
        >
          <PlusIcon className="-ml-1 mr-2 h-5 w-5" aria-hidden="true" />
          新規オーダー登録
        </Link>
      </div>

      {/* Orders Table */}
      <div className="bg-white shadow-sm border border-gray-200 rounded-lg overflow-hidden">
        {isLoading ? (
          <div className="p-8 text-center text-gray-500">読み込み中...</div>
        ) : orders.length === 0 ? (
          <div className="p-8 text-center text-gray-500">オーダーがありません</div>
        ) : (
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  店舗 / 営業日
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  お客様名
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  女の子名
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  コース
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  ステータス
                </th>
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                  アクション
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {orders.map(order => (
                <tr key={order.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm font-medium text-gray-900">{order.store_name}</div>
                    <div className="text-sm text-gray-500">{order.business_date}</div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="text-sm text-gray-900">{order.customer_name || '-'}</div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span
                      className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                        !order.cast_name || order.cast_name === 'フリー'
                          ? 'bg-gray-100 text-gray-800'
                          : 'bg-pink-100 text-pink-800'
                      }`}
                    >
                      {order.cast_name || 'フリー'}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {order.course_minutes} 分
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <span className="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-green-100 text-green-800">
                      {order.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium space-x-3">
                    <Link
                      href={storePath(storeId, `/orders/${order.id}/edit`)}
                      className="text-gray-400 hover:text-amber-600 inline-block"
                    >
                      <PencilSquareIcon className="h-5 w-5" />
                    </Link>
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
