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
      <div className="absolute inset-0" style={{ background: 'var(--storefront-bg)' }}>
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
              'linear-gradient(to bottom, color-mix(in srgb, var(--storefront-bg) 40%, transparent) 0%, color-mix(in srgb, var(--storefront-bg) 50%, transparent) 50%, color-mix(in srgb, var(--storefront-bg) 95%, transparent) 100%)',
          }}
        />
        {/* 放射状グロー */}
        <div
          className="absolute inset-0 pointer-events-none"
          style={{
            background:
              'radial-gradient(ellipse at 50% 40%, color-mix(in srgb, var(--storefront-accent) 4%, transparent) 0%, transparent 65%)',
          }}
        />
      </div>

      {/* コーナーオーナメント（lg以上のみ） */}
      <div className="absolute top-10 left-10 w-16 h-16 border-t border-l border-[color-mix(in_srgb,var(--storefront-accent)_20%,transparent)] hidden lg:block" />
      <div className="absolute top-10 right-10 w-16 h-16 border-t border-r border-[color-mix(in_srgb,var(--storefront-accent)_20%,transparent)] hidden lg:block" />
      <div className="absolute bottom-10 left-10 w-16 h-16 border-b border-l border-[color-mix(in_srgb,var(--storefront-accent)_20%,transparent)] hidden lg:block" />
      <div className="absolute bottom-10 right-10 w-16 h-16 border-b border-r border-[color-mix(in_srgb,var(--storefront-accent)_20%,transparent)] hidden lg:block" />

      {/* メインコンテンツ */}
      <div className="relative z-10 text-center px-5 sm:px-8 max-w-3xl mx-auto w-full">
        {/* 英語サブタイトル */}
        <p className="text-[color-mix(in_srgb,var(--storefront-accent)_50%,transparent)] text-[8px] sm:text-[9px] tracking-[0.5em] sm:tracking-[0.7em] uppercase mb-6 md:mb-8">
          Premium Entertainment Club
        </p>

        {/* 店舗名 */}
        <h1
          className="text-4xl sm:text-5xl md:text-6xl lg:text-7xl font-light tracking-[0.12em] sm:tracking-[0.15em] text-[var(--storefront-fg)] mb-3 md:mb-4"
          style={{ fontFamily: 'var(--storefront-font-display)' }}
        >
          {tenantName}
        </h1>

        {/* ゴールド区切り */}
        <div className="flex items-center justify-center gap-3 sm:gap-4 my-6 md:my-8">
          <div className="h-px w-16 sm:w-24 bg-linear-to-r from-transparent to-[color-mix(in_srgb,var(--storefront-accent)_50%,transparent)]" />
          <span className="text-[color-mix(in_srgb,var(--storefront-accent)_65%,transparent)] text-[10px] sm:text-xs">
            ◆
          </span>
          <div className="h-px w-16 sm:w-24 bg-linear-to-l from-transparent to-[color-mix(in_srgb,var(--storefront-accent)_50%,transparent)]" />
        </div>

        {/* 説明文 */}
        <p
          className="text-[color-mix(in_srgb,var(--storefront-fg)_50%,transparent)] text-xs sm:text-sm md:text-base font-light leading-relaxed mb-8 md:mb-12 max-w-xs sm:max-w-xl mx-auto"
          style={{ fontFamily: 'var(--storefront-font-display)' }}
        >
          {description || '最高のおもてなしと上質な時間をご提供する、大人のための特別な空間'}
        </p>

        {/* CTAボタン */}
        <div className="flex flex-col sm:flex-row justify-center items-center gap-3 sm:gap-4">
          <a
            href="#cast"
            className="w-full sm:w-auto px-8 py-3 border border-[color-mix(in_srgb,var(--storefront-accent)_60%,transparent)] text-[var(--storefront-accent)] text-[10px] tracking-[0.4em] uppercase hover:bg-[var(--storefront-accent)] hover:text-[var(--storefront-bg)] transition-all duration-300 text-center"
            style={{ fontFamily: 'var(--storefront-font-display)' }}
          >
            キャストを見る
          </a>
          <a
            href="#campaign"
            className="w-full sm:w-auto px-8 py-3 border border-[color-mix(in_srgb,var(--storefront-fg)_15%,transparent)] text-[color-mix(in_srgb,var(--storefront-fg)_50%,transparent)] text-[10px] tracking-[0.4em] uppercase hover:border-[color-mix(in_srgb,var(--storefront-fg)_30%,transparent)] hover:text-[color-mix(in_srgb,var(--storefront-fg)_80%,transparent)] transition-all duration-300 text-center"
            style={{ fontFamily: 'var(--storefront-font-display)' }}
          >
            キャンペーン
          </a>
        </div>
      </div>

      {/* スクロール示唆 */}
      <div className="absolute bottom-8 left-1/2 -translate-x-1/2 flex flex-col items-center gap-2">
        <span className="text-[color-mix(in_srgb,var(--storefront-accent)_30%,transparent)] text-[8px] tracking-[0.4em]">
          SCROLL
        </span>
        <div className="w-px h-6 sm:h-8 bg-linear-to-b from-[color-mix(in_srgb,var(--storefront-accent)_30%,transparent)] to-transparent" />
      </div>
    </section>
  );
}
