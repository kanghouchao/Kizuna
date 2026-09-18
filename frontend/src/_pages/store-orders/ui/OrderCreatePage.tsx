'use client';

import { useOrderConfirmation } from './useOrderConfirmation';

import { OrderForm, OrderFormData } from './OrderForm';
import { RegionError } from '@/shared/ui';
import { notify } from '@/shared/notify';
import { useRouter, useParams, useSearchParams } from 'next/navigation';
import { useState } from 'react';
import { OrderCreateRequest, orderApi, toFeeLineInputs } from '@/entities/order';
import { getApiErrorMessage, storePath, useResource } from '@/shared/lib';

export default function CreateOrderPage() {
  const params = useParams();
  const replacement = useSearchParams().get('replacement_for_order_id') ?? undefined;
  return <CreateOrder key={`${params.storeId}:${replacement ?? ''}`} replacement={replacement} />;
}

function CreateOrder({ replacement }: { replacement?: string }) {
  const confirmation = useOrderConfirmation();
  const router = useRouter();
  const params = useParams();
  const storeId = params.storeId as string;
  const original = useResource(replacement ? () => orderApi.get(replacement) : null, [
    replacement,
    storeId,
  ]);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (data: OrderFormData) => {
    setIsSubmitting(true);
    try {
      const request: OrderCreateRequest = {
        ...data,
        replacement_for_order_id: replacement,
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
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-foreground">新規オーダー登録</h1>
          <p className="text-sm text-muted-foreground mt-1">
            受付情報・訪問先・提供内容を入力し、料金を確認して登録します。
          </p>
        </div>

        {replacement && (
          <div className="mb-6 space-y-3">
            {original.isLoading && <p>元受注を読み込み中...</p>}
            {original.failure === 'error' && (
              <RegionError
                message="元受注を取得できませんでした。"
                onRetry={() => void original.reload()}
              />
            )}
            {original.failure === 'notFound' && (
              <RegionError
                message="元受注が見つかりません。"
                fallback={{ href: storePath(storeId, '/orders'), label: 'オーダー一覧へ' }}
              />
            )}
            {original.data &&
              !original.isLoading &&
              !original.failure &&
              (original.data.completion_invalidated ? (
                <p>
                  再提供の元受注：{replacement}。現在のコース・担当・提供条件を選び直してください。
                </p>
              ) : (
                <p role="alert">無効化した受注だけが再提供の対象です。</p>
              ))}
          </div>
        )}
        {(!replacement ||
          (original.data?.completion_invalidated && !original.isLoading && !original.failure)) && (
          <OrderForm onSubmit={handleSubmit} isSubmitting={isSubmitting} />
        )}
      </div>
    </>
  );
}
