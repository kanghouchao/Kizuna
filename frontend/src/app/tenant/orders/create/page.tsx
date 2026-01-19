'use client';

import { OrderForm, OrderFormData } from '../_components/OrderForm';
import { toast } from 'react-hot-toast';
import { useRouter } from 'next/navigation';
import { useState } from 'react';

export default function CreateOrderPage() {
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (data: OrderFormData) => {
    setIsSubmitting(true);
    try {
      // Data docking will happen here later
      console.log('Order submitted:', data);

      // Mock delay
      await new Promise(resolve => setTimeout(resolve, 800));

      toast.success('オーダーを登録しました');
      router.push('/tenant/orders');
    } catch (error) {
      toast.error('オーダーの登録に失敗しました');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">新規オーダー登録</h1>
        <p className="text-sm text-gray-500 mt-1">新しい注文情報を入力してください。</p>
      </div>

      <OrderForm onSubmit={handleSubmit} isSubmitting={isSubmitting} />
    </div>
  );
}
