interface BannerProps {
  tenantName: string;
  bannerUrl?: string;
  description?: string;
}

function sanitizeBannerUrl(url: string | undefined): string | undefined {
  if (!url) return undefined;
  const trimmed = url.trim();
  if (trimmed.startsWith('/')) return trimmed;
  try {
    const parsed = new URL(trimmed);
    if (['http:', 'https:'].includes(parsed.protocol)) return parsed.toString();
  } catch {
    // Invalid URL
  }
  return undefined;
}

export default function Banner({ tenantName, bannerUrl, description }: BannerProps) {
  const safeBannerUrl = sanitizeBannerUrl(bannerUrl);

  return (
    <section className="relative min-h-[85vh] md:min-h-[90vh] flex items-center justify-center overflow-hidden">
      {/* 背景 */}
      <div className="absolute inset-0" style={{ background: '#080808' }}>
        {safeBannerUrl && (
          <div
            className="absolute inset-0 bg-cover bg-center opacity-35"
            style={{ backgroundImage: `url("${safeBannerUrl}")` }}
          />
        )}
        {/* グラデーションオーバーレイ */}
        <div
          className="absolute inset-0"
          style={{
            background:
              'linear-gradient(to bottom, rgba(8,8,8,0.4) 0%, rgba(8,8,8,0.5) 50%, rgba(8,8,8,0.95) 100%)',
          }}
        />
        {/* 放射状グロー */}
        <div
          className="absolute inset-0 pointer-events-none"
          style={{
            background:
              'radial-gradient(ellipse at 50% 40%, rgba(201,168,76,0.04) 0%, transparent 65%)',
          }}
        />
      </div>

      {/* コーナーオーナメント（lg以上のみ） */}
      <div className="absolute top-10 left-10 w-16 h-16 border-t border-l border-[#C9A84C]/20 hidden lg:block" />
      <div className="absolute top-10 right-10 w-16 h-16 border-t border-r border-[#C9A84C]/20 hidden lg:block" />
      <div className="absolute bottom-10 left-10 w-16 h-16 border-b border-l border-[#C9A84C]/20 hidden lg:block" />
      <div className="absolute bottom-10 right-10 w-16 h-16 border-b border-r border-[#C9A84C]/20 hidden lg:block" />

      {/* メインコンテンツ */}
      <div className="relative z-10 text-center px-5 sm:px-8 max-w-3xl mx-auto w-full">
        {/* 英語サブタイトル */}
        <p className="text-[#C9A84C]/50 text-[8px] sm:text-[9px] tracking-[0.5em] sm:tracking-[0.7em] uppercase mb-6 md:mb-8">
          Premium Entertainment Club
        </p>

        {/* 店舗名 */}
        <h1
          className="text-4xl sm:text-5xl md:text-6xl lg:text-7xl font-light tracking-[0.12em] sm:tracking-[0.15em] text-[#F8F4F0] mb-3 md:mb-4"
          style={{ fontFamily: "'Noto Serif JP', 'Hiragino Mincho Pro', 'Yu Mincho', serif" }}
        >
          {tenantName}
        </h1>

        {/* ゴールド区切り */}
        <div className="flex items-center justify-center gap-3 sm:gap-4 my-6 md:my-8">
          <div className="h-px w-16 sm:w-24 bg-linear-to-r from-transparent to-[#C9A84C]/50" />
          <span className="text-[#C9A84C]/65 text-[10px] sm:text-xs">◆</span>
          <div className="h-px w-16 sm:w-24 bg-linear-to-l from-transparent to-[#C9A84C]/50" />
        </div>

        {/* 説明文 */}
        <p
          className="text-[#F8F4F0]/50 text-xs sm:text-sm md:text-base font-light leading-relaxed mb-8 md:mb-12 max-w-xs sm:max-w-xl mx-auto"
          style={{ fontFamily: "'Noto Serif JP', serif" }}
        >
          {description || '最高のおもてなしと上質な時間をご提供する、大人のための特別な空間'}
        </p>

        {/* CTAボタン */}
        <div className="flex flex-col sm:flex-row justify-center items-center gap-3 sm:gap-4">
          <a
            href="#cast"
            className="w-full sm:w-auto px-8 py-3 border border-[#C9A84C]/60 text-[#C9A84C] text-[10px] tracking-[0.4em] uppercase hover:bg-[#C9A84C] hover:text-[#080808] transition-all duration-300 text-center"
            style={{ fontFamily: "'Noto Serif JP', serif" }}
          >
            キャストを見る
          </a>
          <a
            href="#campaign"
            className="w-full sm:w-auto px-8 py-3 border border-[#F8F4F0]/15 text-[#F8F4F0]/50 text-[10px] tracking-[0.4em] uppercase hover:border-[#F8F4F0]/30 hover:text-[#F8F4F0]/80 transition-all duration-300 text-center"
            style={{ fontFamily: "'Noto Serif JP', serif" }}
          >
            キャンペーン
          </a>
        </div>
      </div>

      {/* スクロール示唆 */}
      <div className="absolute bottom-8 left-1/2 -translate-x-1/2 flex flex-col items-center gap-2">
        <span className="text-[#C9A84C]/30 text-[8px] tracking-[0.4em]">SCROLL</span>
        <div className="w-px h-6 sm:h-8 bg-linear-to-b from-[#C9A84C]/30 to-transparent" />
      </div>
    </section>
  );
}
