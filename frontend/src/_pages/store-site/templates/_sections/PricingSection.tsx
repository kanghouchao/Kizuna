interface PricingSectionProps {
  pricingDescription?: string;
}

/**
 * 料金ページ本文（Server Component）。改行を保持して料金説明文を表示する。
 */
export default function PricingSection({ pricingDescription }: PricingSectionProps) {
  return (
    <section className="py-12 md:py-20 px-5 sm:px-6" style={{ background: 'var(--storefront-bg)' }}>
      <div className="max-w-3xl mx-auto">
        {pricingDescription ? (
          <p
            className="text-[color-mix(in_srgb,var(--storefront-fg)_70%,transparent)] text-sm md:text-base leading-loose whitespace-pre-line tracking-wider"
            style={{ fontFamily: 'var(--storefront-font-display)' }}
          >
            {pricingDescription}
          </p>
        ) : (
          <p
            className="text-center text-[color-mix(in_srgb,var(--storefront-fg)_30%,transparent)] text-sm tracking-wider py-16"
            style={{ fontFamily: 'var(--storefront-font-display)' }}
          >
            料金情報は準備中です
          </p>
        )}
      </div>
    </section>
  );
}
