import { SiteConfig } from '../../model/types';

interface StoreInfoSectionProps {
  siteConfig: SiteConfig;
  accessNote?: string;
}

/**
 * 店舗情報（Server Component）。住所・電話・営業時間を定義リストで表示する。
 * null の項目は行ごと非表示。電話は tel: リンク。
 */
export default function StoreInfoSection({ siteConfig, accessNote }: StoreInfoSectionProps) {
  const rows: { label: string; node: React.ReactNode }[] = [];
  if (siteConfig.address) rows.push({ label: '住所', node: siteConfig.address });
  if (siteConfig.phone)
    rows.push({
      label: '電話',
      node: (
        <a
          href={`tel:${siteConfig.phone}`}
          className="hover:text-[var(--storefront-accent)] transition-colors"
        >
          {siteConfig.phone}
        </a>
      ),
    });
  if (siteConfig.business_hours) rows.push({ label: '営業時間', node: siteConfig.business_hours });

  return (
    <section className="py-12 md:py-20 px-5 sm:px-6" style={{ background: 'var(--storefront-bg)' }}>
      <div className="max-w-2xl mx-auto">
        <dl className="divide-y divide-[color-mix(in_srgb,var(--storefront-accent)_10%,transparent)] border-t border-b border-[color-mix(in_srgb,var(--storefront-accent)_10%,transparent)]">
          {rows.map(({ label, node }) => (
            <div key={label} className="grid grid-cols-3 gap-4 py-4 md:py-5">
              <dt className="text-[color-mix(in_srgb,var(--storefront-accent)_60%,transparent)] text-[10px] tracking-[0.3em] uppercase pt-0.5">
                {label}
              </dt>
              <dd
                className="col-span-2 text-[color-mix(in_srgb,var(--storefront-fg)_75%,transparent)] text-sm tracking-wider"
                style={{ fontFamily: 'var(--storefront-font-display)' }}
              >
                {node}
              </dd>
            </div>
          ))}
          {accessNote && (
            <div className="grid grid-cols-3 gap-4 py-4 md:py-5">
              <dt className="text-[color-mix(in_srgb,var(--storefront-accent)_60%,transparent)] text-[10px] tracking-[0.3em] uppercase pt-0.5">
                アクセス
              </dt>
              <dd
                className="col-span-2 text-[color-mix(in_srgb,var(--storefront-fg)_75%,transparent)] text-sm leading-relaxed whitespace-pre-line tracking-wider"
                style={{ fontFamily: 'var(--storefront-font-display)' }}
              >
                {accessNote}
              </dd>
            </div>
          )}
        </dl>
      </div>
    </section>
  );
}
