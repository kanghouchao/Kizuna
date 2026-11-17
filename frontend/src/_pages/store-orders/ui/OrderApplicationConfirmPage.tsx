'use client';

import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { orderApplicationApi } from '@/entities/order';
import { useCurrentStoreId, useStoreContext } from '@/entities/user';
import { useResource, useWrite } from '@/shared/lib';
import { RegionError } from '@/shared/ui';
import { toApplicationConfirmationBody } from '../lib/orderFormAssembly';
import { orderListHref } from '../lib/orderListHref';
import { OrderForm, type OrderFormData } from './OrderForm';
import { useOrderConfirmation } from './useOrderConfirmation';

export default function OrderApplicationConfirmPage() {
  const id = useParams().id as string;
  const storeId = useStoreContext().currentStoreId;
  return <ApplicationConfirm key={`${storeId}:${id}`} id={id} />;
}

/**
 * 予約申請の確定。確定は申請内容を事前入力した受注の作成操作で、受注作成と同じフォームを使い、送信先だけが
 * 申請確定の端点になる。ここに無い項目（割引・キャリアなど）は、確定後の受注を汎用更新で整える。
 */
function ApplicationConfirm({ id }: { id: string }) {
  const storeId = useCurrentStoreId();
  const router = useRouter();
  // 一覧から渡された検索条件。戻る・確定後の遷移で同じ条件の作業キューへ帰る
  const backHref = orderListHref(storeId, useSearchParams().toString());
  const confirmation = useOrderConfirmation(id);
  const {
    data: application,
    failure,
    isLoading,
    reload,
  } = useResource(() => orderApplicationApi.detail(id), [id]);
  const { busy, write } = useWrite();

  const submit = async (data: OrderFormData) => {
    if (!application) return;
    const request = toApplicationConfirmationBody(data, !application.requester_member_code);
    const token = await confirmation.confirm(() =>
      orderApplicationApi.previewConfirmation(id, request)
    );
    if (!token) return;
    await write(() => orderApplicationApi.confirm(id, { ...request, confirmation_token: token }), {
      success: '予約を確定しました',
      // 指名の再検証や失効など、サーバは対処方法を含む文言を返す。汎用文言に潰さない
      failure: '予約の確定に失敗しました',
      // 履歴を置き換え、戻るで処理済みの申請の画面へ帰らないようにする
      onSuccess: () => router.replace(backHref),
    });
  };

  const fallback = { href: backHref, label: 'オーダー一覧へ' };
  return (
    <>
      {confirmation.dialog}
      <div>
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-foreground">予約申請を確定</h1>
          <p className="text-sm text-muted-foreground mt-1">
            申請の内容を確認・補正し、料金を確認して受注として確定します。申請の原文は変わりません。
          </p>
        </div>
        {isLoading && <p>読み込み中...</p>}
        {failure === 'error' && (
          <RegionError message="予約申請を取得できませんでした。" onRetry={() => void reload()} />
        )}
        {failure === 'notFound' && (
          <RegionError message="予約申請が見つかりません。" fallback={fallback} />
        )}
        {application &&
          !isLoading &&
          !failure &&
          // 処理済み・失効の申請はサーバも確定を拒む。フォームを出さずに一覧へ帰す
          (application.status !== 'PENDING' || application.expired ? (
            <RegionError
              message="この予約申請は処理済みか失効しているため確定できません。"
              fallback={fallback}
            />
          ) : (
            <OrderForm confirming={{ application, backHref }} onSubmit={submit} busy={busy} />
          ))}
      </div>
    </>
  );
}
