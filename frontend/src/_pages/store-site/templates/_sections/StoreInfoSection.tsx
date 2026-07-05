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
        <a href={`tel:${siteConfig.phone}`} className="hover:text-[#C9A84C] transition-colors">
          {siteConfig.phone}
        </a>
      ),
    });
  if (siteConfig.business_hours) rows.push({ label: '営業時間', node: siteConfig.business_hours });

  return (
    <section className="py-12 md:py-20 px-5 sm:px-6" style={{ background: '#080808' }}>
      <div className="max-w-2xl mx-auto">
        <dl className="divide-y divide-[#C9A84C]/10 border-t border-b border-[#C9A84C]/10">
          {rows.map(({ label, node }) => (
            <div key={label} className="grid grid-cols-3 gap-4 py-4 md:py-5">
              <dt className="text-[#C9A84C]/60 text-[10px] tracking-[0.3em] uppercase pt-0.5">
                {label}
              </dt>
              <dd
                className="col-span-2 text-[#F8F4F0]/75 text-sm tracking-wider"
                style={{ fontFamily: "'Noto Serif JP', serif" }}
              >
                {node}
              </dd>
            </div>
          ))}
          {accessNote && (
            <div className="grid grid-cols-3 gap-4 py-4 md:py-5">
              <dt className="text-[#C9A84C]/60 text-[10px] tracking-[0.3em] uppercase pt-0.5">
                アクセス
              </dt>
              <dd
                className="col-span-2 text-[#F8F4F0]/75 text-sm leading-relaxed whitespace-pre-line tracking-wider"
                style={{ fontFamily: "'Noto Serif JP', serif" }}
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
