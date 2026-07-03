'use client';

import { OrderForm, OrderFormData } from './OrderForm';
import { toast } from 'react-hot-toast';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { OrderCreateRequest, orderApi } from '@/entities/order';

export default function CreateOrderPage() {
  const router = useRouter();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (data: OrderFormData) => {
    setIsSubmitting(true);
    try {
      const request: OrderCreateRequest = {
        store_name: data.storeName,
        receptionist_id: data.receptionistId || undefined,
        business_date: data.businessDate,
        arrival_scheduled_start_time: data.arrivalStartTime
          ? `${data.arrivalStartTime}:00`
          : undefined,
        arrival_scheduled_end_time: data.arrivalEndTime ? `${data.arrivalEndTime}:00` : undefined,
        customer_name: data.customerName,
        phone_number: data.phoneNumber,
        phone_number2: data.phoneNumber2,
        address: data.address,
        building_name: data.buildingName,
        classification: data.classification,
        landmark: data.landmark,
        has_pet: data.hasPet,
        ng_type: data.ngType,
        ng_content: data.ngContent,
        cast_id: data.castId, // 注: ユーザーが名前を入力する場合、ID解決が必要かもしれないが、フォーム上は 'castId' となっている
        course_minutes: Number(data.courseMinutes),
        extension_minutes: Number(data.extensionMinutes),
        option_codes: data.options || [],
        discount_name: data.discountName,
        manual_discount: Number(data.manualDiscount),
        carrier: data.carrier,
        media_name: data.mediaName,
        used_points: Number(data.usedPoints),
        manual_grant_points: Number(data.manualGrantPoints),
        remarks: data.remarks,
        cast_driver_message: data.castDriverMessage,
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
