'use client';

import { useOrderConfirmation } from './useOrderConfirmation';

import { OrderForm, OrderFormData } from './OrderForm';
import { toOrderRequestBody } from '../lib/orderFormAssembly';
import { RegionError } from '@/shared/ui';
import { useRouter, useSearchParams } from 'next/navigation';
import { useCurrentStoreId, useStoreContext } from '@/entities/user';
import { OrderCreateRequest, orderApi } from '@/entities/order';
import { storePath, useResource, useWrite } from '@/shared/lib';

export default function CreateOrderPage() {
  const storeId = useStoreContext().currentStoreId;
  const replacement = useSearchParams().get('replacement_for_order_id') ?? undefined;
  return <CreateOrder key={`${storeId}:${replacement ?? ''}`} replacement={replacement} />;
}

function CreateOrder({ replacement }: { replacement?: string }) {
  const confirmation = useOrderConfirmation();
  const router = useRouter();
  const storeId = useCurrentStoreId();
  const original = useResource(replacement ? () => orderApi.get(replacement) : null, [
    replacement,
    storeId,
  ]);
  const { busy, write } = useWrite();

  const handleSubmit = async (data: OrderFormData) => {
    const request: OrderCreateRequest = {
      ...toOrderRequestBody(data),
      replacement_for_order_id: replacement,
    };
    const token = await confirmation.confirm(() => orderApi.previewCreate(request));
    if (!token) return;
    await write(() => orderApi.create({ ...request, confirmation_token: token }), {
      success: 'オーダーを登録しました',
      failure: 'オーダーの登録に失敗しました',
      onSuccess: () => router.push(storePath(storeId, '/orders')),
    });
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
          <OrderForm onSubmit={handleSubmit} busy={busy} />
        )}
      </div>
    </>
  );
}
