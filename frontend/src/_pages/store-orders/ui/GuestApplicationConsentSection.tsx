import { useEffect } from 'react';
import { GuestContactImportsField } from './GuestContactImportsField';
import { orderApplicationApi } from '@/entities/order';
import { useResource } from '@/shared/lib';
import { RegionError } from '@/shared/ui';

export function GuestApplicationConsentSection({
  id,
  onMissing,
}: {
  id: string;
  onMissing: () => void;
}) {
  const detail = useResource(() => orderApplicationApi.detail(id), [id]);
  useEffect(() => {
    if (detail.failure === 'notFound') onMissing();
  }, [detail.failure, onMissing]);
  if (detail.failure === 'notFound') return null;
  if (detail.isLoading) return <p>申請の同意を読み込み中...</p>;
  if (detail.failure)
    return (
      <RegionError
        message="申請の同意を取得できませんでした"
        onRetry={() => void detail.reload()}
      />
    );
  const consent = detail.data?.contact_consent;
  if (!consent) return null;
  return (
    <section className="min-w-0 space-y-3 rounded-lg border p-4 text-sm break-words">
      <h3 className="font-semibold">申請時の連絡同意</h3>
      <p>{consent.business_text}</p>
      <p>業務同意：許可（今回の予約のみ）</p>
      <p>{consent.marketing_text}</p>
      <p>販促同意：{consent.marketing_allowed ? '選択済み' : '未選択'}</p>
      <p>取得日時：{consent.acquired_at}</p>
      {detail.data?.business_contact_permissions.map(permission => (
        <p key={permission.type} className="break-all">
          {permission.value}：
          {permission.decision === 'STORE_DENIED'
            ? '店舗内の拒否により連絡不可'
            : permission.decision === 'ALLOWED'
              ? '今回の業務連絡が可能'
              : '連絡不可'}
        </p>
      ))}
      {detail.data && <GuestContactImportsField detail={detail.data} />}
      <p>同意原文は台帳の変更によって書き換わりません。</p>
    </section>
  );
}
