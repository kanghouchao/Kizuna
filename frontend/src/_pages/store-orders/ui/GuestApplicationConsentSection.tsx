import { GuestContactImportsField } from './GuestContactImportsField';
import type { OrderApplicationDetail } from '@/entities/order';

export function GuestApplicationConsentSection({ detail }: { detail: OrderApplicationDetail }) {
  const consent = detail.contact_consent;
  if (!consent) return null;
  return (
    <section className="min-w-0 space-y-3 rounded-lg border p-4 text-sm break-words">
      <h3 className="font-semibold">申請時の連絡同意</h3>
      <p>{consent.business_text}</p>
      <p>業務同意：許可（今回の予約のみ）</p>
      <p>{consent.marketing_text}</p>
      <p>販促同意：{consent.marketing_allowed ? '選択済み' : '未選択'}</p>
      <p>取得日時：{consent.acquired_at}</p>
      {detail.business_contact_permissions.map(permission => (
        <p key={permission.type} className="break-all">
          {permission.value}：
          {permission.decision === 'STORE_DENIED'
            ? '店舗内の拒否により連絡不可'
            : permission.decision === 'ALLOWED'
              ? '今回の業務連絡が可能'
              : '連絡不可'}
        </p>
      ))}
      <GuestContactImportsField detail={detail} />
      <p>同意原文は台帳の変更によって書き換わりません。</p>
    </section>
  );
}
