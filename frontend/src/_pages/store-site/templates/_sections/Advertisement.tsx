import Image from 'next/image';

interface Ad {
  id: string;
  title: string;
  description?: string;
  imageUrl?: string;
  linkUrl?: string;
}

interface AdvertisementProps {
  ads?: Ad[];
}

const placeholderAds: Ad[] = [
  {
    id: '1',
    title: '新人割引キャンペーン',
    description: '新人キャストご指名で20%OFF！期間限定の特別価格でお楽しみいただけます。',
  },
  {
    id: '2',
    title: '平日限定イベント',
    description: '平日のご利用でポイント2倍！特別なひとときをよりお得にお過ごしください。',
  },
  {
    id: '3',
    title: 'LINE友達追加特典',
    description: 'LINE登録で初回1,000円OFF！最新情報もいち早くお届けいたします。',
  },
];

export default function Advertisement({ ads }: AdvertisementProps) {
  const displayAds = ads && ads.length > 0 ? ads : placeholderAds;

  return (
    <section
      id="campaign"
      className="py-12 md:py-20 px-5 sm:px-6"
      style={{ background: 'var(--storefront-surface-1)' }}
    >
      <div className="max-w-7xl mx-auto">
        {/* セクションヘッダー */}
        <div className="text-center mb-10 md:mb-14">
          <p className="text-[color-mix(in_srgb,var(--storefront-accent)_40%,transparent)] text-[9px] tracking-[0.6em] uppercase mb-3">
            Campaign
          </p>
          <h2
            className="text-xl md:text-2xl font-light tracking-[0.2em] text-[color-mix(in_srgb,var(--storefront-fg)_85%,transparent)]"
            style={{ fontFamily: 'var(--storefront-font-display)' }}
          >
            キャンペーン情報
          </h2>
          <div className="flex items-center justify-center gap-3 mt-4 md:mt-5 mb-3">
            <div className="h-px w-10 md:w-12 bg-linear-to-r from-transparent to-[color-mix(in_srgb,var(--storefront-accent)_35%,transparent)]" />
            <span className="text-[color-mix(in_srgb,var(--storefront-accent)_40%,transparent)] text-[10px]">
              ◆
            </span>
            <div className="h-px w-10 md:w-12 bg-linear-to-l from-transparent to-[color-mix(in_srgb,var(--storefront-accent)_35%,transparent)]" />
          </div>
          <p
            className="text-[color-mix(in_srgb,var(--storefront-fg)_30%,transparent)] text-[11px] md:text-xs tracking-wider"
            style={{ fontFamily: 'var(--storefront-font-display)' }}
          >
            お得な情報をお見逃しなく
          </p>
        </div>

        {/* キャンペーンカード */}
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4 md:gap-6">
          {displayAds.map((ad, index) => (
            <div
              key={ad.id}
              className="group relative overflow-hidden"
              style={{
                background: 'var(--storefront-surface-3)',
                border: '1px solid color-mix(in srgb, var(--storefront-accent) 10%, transparent)',
              }}
            >
              {/* 連番バッジ */}
              <div
                className="absolute top-3 left-3 md:top-4 md:left-4 w-6 h-6 md:w-7 md:h-7 flex items-center justify-center z-10"
                style={{
                  border: '1px solid color-mix(in srgb, var(--storefront-accent) 30%, transparent)',
                }}
              >
                <span className="text-[color-mix(in_srgb,var(--storefront-accent)_60%,transparent)] text-[8px] md:text-[9px] font-medium">
                  {String(index + 1).padStart(2, '0')}
                </span>
              </div>

              {/* 画像エリア */}
              <div
                className="h-36 sm:h-40 md:h-44 relative overflow-hidden"
                style={{ background: 'var(--storefront-surface-2)' }}
              >
                {ad.imageUrl ? (
                  <Image
                    src={ad.imageUrl}
                    alt={ad.title}
                    fill
                    className="object-cover opacity-50 group-hover:opacity-60 transition-opacity duration-500"
                    sizes="(max-width: 640px) 100vw, (max-width: 768px) 50vw, 400px"
                  />
                ) : (
                  <div className="absolute inset-0 flex items-center justify-center">
                    <div
                      className="w-full h-full opacity-5"
                      style={{
                        backgroundImage: `repeating-linear-gradient(45deg, color-mix(in srgb, var(--storefront-accent) 50%, transparent) 0px, color-mix(in srgb, var(--storefront-accent) 50%, transparent) 1px, transparent 1px, transparent 12px)`,
                      }}
                    />
                    <span className="absolute text-[color-mix(in_srgb,var(--storefront-accent)_20%,transparent)] text-[9px] md:text-[10px] tracking-[0.3em]">
                      CAMPAIGN
                    </span>
                  </div>
                )}
                <div
                  className="absolute inset-0"
                  style={{
                    background:
                      'linear-gradient(to bottom, transparent 40%, var(--storefront-surface-3) 100%)',
                  }}
                />
              </div>

              {/* コンテンツ */}
              <div className="px-4 pb-4 pt-3 md:px-5 md:pb-5 md:pt-4">
                <div className="w-5 md:w-6 h-px bg-[color-mix(in_srgb,var(--storefront-accent)_40%,transparent)] mb-3 md:mb-4" />
                <h3
                  className="text-[color-mix(in_srgb,var(--storefront-fg)_85%,transparent)] text-sm font-light tracking-wider mb-2 md:mb-3 leading-relaxed"
                  style={{ fontFamily: 'var(--storefront-font-display)' }}
                >
                  {ad.title}
                </h3>
                {ad.description && (
                  <p
                    className="text-[color-mix(in_srgb,var(--storefront-fg)_35%,transparent)] text-[10px] md:text-[11px] leading-relaxed mb-3 md:mb-4"
                    style={{ fontFamily: 'var(--storefront-font-display)' }}
                  >
                    {ad.description}
                  </p>
                )}
                {ad.linkUrl && (
                  <a
                    href={ad.linkUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-[color-mix(in_srgb,var(--storefront-accent)_60%,transparent)] text-[9px] tracking-[0.3em] uppercase hover:text-[var(--storefront-accent)] transition-colors duration-200"
                  >
                    詳細を見る →
                  </a>
                )}
              </div>

              {/* ホバー時ゴールドボーダー */}
              <div
                className="absolute inset-0 pointer-events-none opacity-0 group-hover:opacity-100 transition-opacity duration-300"
                style={{
                  boxShadow:
                    'inset 0 0 0 1px color-mix(in srgb, var(--storefront-accent) 25%, transparent)',
                }}
              />
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
