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
      style={{ background: '#0A0A0A' }}
    >
      <div className="max-w-7xl mx-auto">
        {/* セクションヘッダー */}
        <div className="text-center mb-10 md:mb-14">
          <p className="text-[#C9A84C]/40 text-[9px] tracking-[0.6em] uppercase mb-3">Campaign</p>
          <h2
            className="text-xl md:text-2xl font-light tracking-[0.2em] text-[#F8F4F0]/85"
            style={{ fontFamily: "'Noto Serif JP', 'Hiragino Mincho Pro', serif" }}
          >
            キャンペーン情報
          </h2>
          <div className="flex items-center justify-center gap-3 mt-4 md:mt-5 mb-3">
            <div className="h-px w-10 md:w-12 bg-linear-to-r from-transparent to-[#C9A84C]/35" />
            <span className="text-[#C9A84C]/40 text-[10px]">◆</span>
            <div className="h-px w-10 md:w-12 bg-linear-to-l from-transparent to-[#C9A84C]/35" />
          </div>
          <p
            className="text-[#F8F4F0]/30 text-[11px] md:text-xs tracking-wider"
            style={{ fontFamily: "'Noto Serif JP', serif" }}
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
                background: '#0F0F0F',
                border: '1px solid rgba(201,168,76,0.1)',
              }}
            >
              {/* 連番バッジ */}
              <div
                className="absolute top-3 left-3 md:top-4 md:left-4 w-6 h-6 md:w-7 md:h-7 flex items-center justify-center z-10"
                style={{ border: '1px solid rgba(201,168,76,0.3)' }}
              >
                <span className="text-[#C9A84C]/60 text-[8px] md:text-[9px] font-medium">
                  {String(index + 1).padStart(2, '0')}
                </span>
              </div>

              {/* 画像エリア */}
              <div
                className="h-36 sm:h-40 md:h-44 relative overflow-hidden"
                style={{ background: '#0D0D0D' }}
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
                        backgroundImage: `repeating-linear-gradient(45deg, rgba(201,168,76,0.5) 0px, rgba(201,168,76,0.5) 1px, transparent 1px, transparent 12px)`,
                      }}
                    />
                    <span className="absolute text-[#C9A84C]/20 text-[9px] md:text-[10px] tracking-[0.3em]">
                      CAMPAIGN
                    </span>
                  </div>
                )}
                <div
                  className="absolute inset-0"
                  style={{
                    background: 'linear-gradient(to bottom, transparent 40%, #0F0F0F 100%)',
                  }}
                />
              </div>

              {/* コンテンツ */}
              <div className="px-4 pb-4 pt-3 md:px-5 md:pb-5 md:pt-4">
                <div className="w-5 md:w-6 h-px bg-[#C9A84C]/40 mb-3 md:mb-4" />
                <h3
                  className="text-[#F8F4F0]/85 text-sm font-light tracking-wider mb-2 md:mb-3 leading-relaxed"
                  style={{ fontFamily: "'Noto Serif JP', serif" }}
                >
                  {ad.title}
                </h3>
                {ad.description && (
                  <p
                    className="text-[#F8F4F0]/35 text-[10px] md:text-[11px] leading-relaxed mb-3 md:mb-4"
                    style={{ fontFamily: "'Noto Serif JP', serif" }}
                  >
                    {ad.description}
                  </p>
                )}
                {ad.linkUrl && (
                  <a
                    href={ad.linkUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-[#C9A84C]/60 text-[9px] tracking-[0.3em] uppercase hover:text-[#C9A84C] transition-colors duration-200"
                  >
                    詳細を見る →
                  </a>
                )}
              </div>

              {/* ホバー時ゴールドボーダー */}
              <div
                className="absolute inset-0 pointer-events-none opacity-0 group-hover:opacity-100 transition-opacity duration-300"
                style={{ boxShadow: 'inset 0 0 0 1px rgba(201,168,76,0.25)' }}
              />
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
