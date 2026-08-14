'use client';

import { OrderForm, OrderFormData } from './OrderForm';
import { notify } from '@/shared/notify';
import { useRouter, useParams } from 'next/navigation';
import { useState } from 'react';
import { OrderCreateRequest, orderApi } from '@/entities/order';
import { storePath } from '@/shared/lib';

export default function CreateOrderPage() {
  const router = useRouter();
  const params = useParams();
  const storeId = params.storeId as string;
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (data: OrderFormData) => {
    setIsSubmitting(true);
    try {
      const request: OrderCreateRequest = {
        // 未選択は「自分」の意。項目ごと送らないことでサーバが実行者本人を受付担当に据える
        // （JWT にも /platform/me にも利用者 id が無いため、画面の側で自分を選択値にはできない）
        receptionist_id: data.receptionistId === '' ? undefined : Number(data.receptionistId),
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
        // 空欄は「未入力」として送らない — Number('') は 0 になり、サーバ側の @Min(1) に撥ねられる
        pax: `${data.pax ?? ''}` === '' ? undefined : Number(data.pax),
        reception_route: data.receptionRoute,
        course_minutes: Number(data.courseMinutes),
        extension_minutes: Number(data.extensionMinutes),
        option_codes: data.options || [],
        discount_name: data.discountName,
        manual_discount: Number(data.manualDiscount),
        carrier: data.carrier,
        media_name: data.mediaName,
        remarks: data.remarks,
        cast_driver_message: data.castDriverMessage,
      };

      await orderApi.create(request);

      notify.success('オーダーを登録しました');
      router.push(storePath(storeId, '/orders'));
    } catch (error) {
      console.error(error);
      notify.error('オーダーの登録に失敗しました');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-foreground">新規オーダー登録</h1>
        <p className="text-sm text-muted-foreground mt-1">新しい注文情報を入力してください。</p>
      </div>

      <OrderForm onSubmit={handleSubmit} isSubmitting={isSubmitting} />
    </div>
  );
}
