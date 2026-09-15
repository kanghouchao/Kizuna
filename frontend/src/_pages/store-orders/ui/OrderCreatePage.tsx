'use client';

import { useOrderConfirmation } from './useOrderConfirmation';

import { OrderForm, OrderFormData } from './OrderForm';
import { notify } from '@/shared/notify';
import { useRouter, useParams } from 'next/navigation';
import { useState } from 'react';
import { OrderCreateRequest, orderApi, toFeeLineInputs } from '@/entities/order';
import { getApiErrorMessage, storePath } from '@/shared/lib';

export default function CreateOrderPage() {
  const confirmation = useOrderConfirmation();
  const router = useRouter();
  const params = useParams();
  const storeId = params.storeId as string;
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (data: OrderFormData) => {
    setIsSubmitting(true);
    try {
      const request: OrderCreateRequest = {
        ...data,
        // 未選択は「自分」の意。項目ごと送らないことでサーバが実行者本人を受付担当に据える
        // （JWT にも /platform/me にも利用者 id が無いため、画面の側で自分を選択値にはできない）
        receptionist_id: data.receptionist_id === '' ? undefined : Number(data.receptionist_id),
        arrival_scheduled_start_time: data.arrival_scheduled_start_time
          ? `${data.arrival_scheduled_start_time}:00`
          : undefined,
        arrival_scheduled_end_time: data.arrival_scheduled_end_time
          ? `${data.arrival_scheduled_end_time}:00`
          : undefined,
        // 空欄は「未入力」として送らない — Number('') は 0 になり、サーバ側の @Min(1) に撥ねられる
        pax: `${data.pax ?? ''}` === '' ? undefined : Number(data.pax),
        fee_lines: toFeeLineInputs(data.fee_lines),
      };

      const token = await confirmation.confirm(() => orderApi.previewCreate(request));
      if (!token) return;
      await orderApi.create({ ...request, confirmation_token: token });

      notify.success('オーダーを登録しました');
      router.push(storePath(storeId, '/orders'));
    } catch (error) {
      notify.error(getApiErrorMessage(error, 'オーダーの登録に失敗しました'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <>
      {confirmation.dialog}
      <div>
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-foreground">新規オーダー登録</h1>
          <p className="text-sm text-muted-foreground mt-1">新しい注文情報を入力してください。</p>
        </div>

        <OrderForm onSubmit={handleSubmit} isSubmitting={isSubmitting} />
      </div>
    </>
  );
}
