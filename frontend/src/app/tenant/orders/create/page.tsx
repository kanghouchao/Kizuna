'use client';

import { OrderForm, OrderFormData } from '../_components/OrderForm';
import { toast } from 'react-hot-toast';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { orderApi } from '@/services/tenant/api';
import { OrderCreateRequest } from '@/types/order';

export default function CreateOrderPage() {
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (data: OrderFormData) => {
    setIsSubmitting(true);
    try {
      const request: OrderCreateRequest = {
        storeName: data.storeName,
        receptionistId: data.receptionistId || undefined,
        businessDate: data.businessDate,
        arrivalScheduledStartTime: data.arrivalStartTime
          ? `${data.arrivalStartTime}:00`
          : undefined,
        arrivalScheduledEndTime: data.arrivalEndTime ? `${data.arrivalEndTime}:00` : undefined,
        customerName: data.customerName,
        phoneNumber: data.phoneNumber,
        phoneNumber2: data.phoneNumber2,
        address: data.address,
        buildingName: data.buildingName,
        classification: data.classification,
        landmark: data.landmark,
        hasPet: data.hasPet,
        ngType: data.ngType,
        ngContent: data.ngContent,
        girlId: data.girlId, // Note: ID might need to be resolved if user inputs name, but form says 'girlId'
        courseMinutes: Number(data.courseMinutes),
        extensionMinutes: Number(data.extensionMinutes),
        optionCodes: data.options || [],
        discountName: data.discountName,
        manualDiscount: Number(data.manualDiscount),
        carrier: data.carrier,
        mediaName: data.mediaName,
        usedPoints: Number(data.usedPoints),
        manualGrantPoints: Number(data.manualGrantPoints),
        remarks: data.remarks,
        girlDriverMessage: data.girlDriverMessage,
      };

      await orderApi.create(request);

      toast.success('オーダーを登録しました');
      router.push('/tenant/orders');
    } catch (error) {
      console.error(error);
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
