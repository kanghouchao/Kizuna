'use client';

import { OrderForm, OrderFormData } from '../../_components/OrderForm';
import { toast } from 'react-hot-toast';
import { useRouter, useParams } from 'next/navigation';
import { useState } from 'react';

export default function EditOrderPage() {
  const router = useRouter();
  const params = useParams();
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Mock initial data - in real app, fetch by id
  const mockInitialData: Partial<OrderFormData> = {
    customerName: '山田太郎',
    storeName: '沼津H',
    courseMinutes: 60,
    phoneNumber: '09012345678',
  };

  const handleSubmit = async (data: OrderFormData) => {
    setIsSubmitting(true);
    try {
      console.log('Order updated:', params.id, data);
      await new Promise(resolve => setTimeout(resolve, 800));

      toast.success('オーダーを更新しました');
      router.push('/tenant/orders');
    } catch (error) {
      toast.error('オーダーの更新に失敗しました');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">オーダー編集</h1>
        <p className="text-sm text-gray-500 mt-1">オーダー ID: {params.id} の情報を編集します。</p>
      </div>

      <OrderForm
        initialData={mockInitialData}
        onSubmit={handleSubmit}
        isSubmitting={isSubmitting}
      />
    </div>
  );
}
