interface PageHeroProps {
  title: string;
  subtitle?: string;
}

/**
 * 各サブページ先頭の見出しバンド（Server Component）。
 * 既存区块と同じ暗色系トーン（var(--storefront-bg) / var(--storefront-accent) / Noto Serif JP）で統一する。
 */
export default function PageHero({ title, subtitle }: PageHeroProps) {
  return (
    <section
      className="pt-24 md:pt-32 pb-12 md:pb-16 px-5 sm:px-6 text-center"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <h1
        className="text-2xl md:text-3xl font-light tracking-[0.25em] text-[color-mix(in_srgb,var(--storefront-fg)_90%,transparent)]"
        style={{ fontFamily: 'var(--storefront-font-display)' }}
      >
        {title}
      </h1>
      <div className="flex items-center justify-center gap-3 mt-5 mb-3">
        <div className="h-px w-10 md:w-12 bg-linear-to-r from-transparent to-[color-mix(in_srgb,var(--storefront-accent)_35%,transparent)]" />
        <span className="text-[color-mix(in_srgb,var(--storefront-accent)_40%,transparent)] text-[10px]">
          ◆
        </span>
        <div className="h-px w-10 md:w-12 bg-linear-to-l from-transparent to-[color-mix(in_srgb,var(--storefront-accent)_35%,transparent)]" />
      </div>
      {subtitle && (
        <p
          className="text-[color-mix(in_srgb,var(--storefront-fg)_30%,transparent)] text-[11px] md:text-xs tracking-wider"
          style={{ fontFamily: 'var(--storefront-font-display)' }}
        >
          {subtitle}
        </p>
      )}
    </section>
  );
}
