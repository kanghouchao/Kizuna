import { MergeProfile, MergeSnapshot } from '@/entities/customer';

export const profileFields: [Exclude<keyof MergeProfile, 'has_pet'>, string, number | undefined][] =
  [
    ['name', '氏名', 255],
    ['address', '住所', 500],
    ['building_name', '建物名', 255],
    ['landmark', '目印', 255],
    ['classification', '区分', 50],
    ['usage_areas', '利用エリア', 255],
    ['ng_type', '注意区分', 50],
    ['ng_content', '注意事項', undefined],
  ];
export function MergeSnapshotView({ title, snapshot }: { title: string; snapshot: MergeSnapshot }) {
  return (
    <section className="min-w-0 space-y-3 rounded-lg border p-4 break-words">
      <h3 className="font-medium">{title}</h3>
      <p className="text-xs">顧客 ID: {snapshot.id}</p>
      <dl className="space-y-2">
        {profileFields.map(([key, label]) => (
          <div key={key}>
            <dt className="text-sm text-muted-foreground">{label}</dt>
            <dd className="whitespace-pre-wrap text-sm">{snapshot.profile[key] ?? '未設定'}</dd>
          </div>
        ))}
        <dt>ペットの有無</dt>
        <dd>
          {snapshot.profile.has_pet === null
            ? '未確認'
            : snapshot.profile.has_pet
              ? 'あり'
              : 'なし'}
        </dd>
      </dl>
      <h4 className="font-medium">連絡先</h4>
      {snapshot.contacts.length === 0 && <p>なし</p>}
      {snapshot.contacts.map(c => (
        <div key={c.id} className="space-y-1 border-t pt-2 text-sm">
          <p>
            {c.type}: {c.value} {c.deleted ? '（削除済み）' : c.preferred ? '（優先）' : ''}
          </p>
          <p>
            ID: {c.id} / 由来: {c.origin_customer_id}
          </p>
          <p>
            業務: {permission(c.effective_business_status)} / 販促:{' '}
            {permission(c.effective_marketing_status)}
          </p>
        </div>
      ))}
      <h4 className="font-medium">会員関連の区間</h4>
      {snapshot.member_links.length === 0 && <p>なし</p>}
      {snapshot.member_links.map(l => (
        <div key={l.id} className="border-t pt-2 text-sm">
          <p>
            {l.member_code} · {l.status === 'ACTIVE' ? '有効' : '解除済み'} · ID: {l.id}
          </p>
          <p>
            成立: {l.linked_at} / {l.operation_reason ?? l.reason}
          </p>
          {l.released_at && (
            <p>
              解除: {l.released_at} / {l.release_reason}
            </p>
          )}
        </div>
      ))}
    </section>
  );
}
function permission(value: string) {
  return value === 'DENIED' ? '拒否' : value === 'ALLOWED' ? '許可' : '未確認';
}
