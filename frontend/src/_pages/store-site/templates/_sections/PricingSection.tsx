interface PricingSectionProps {
  pricingDescription?: string;
}

/**
 * 料金ページ本文（Server Component）。改行を保持して料金説明文を表示する。
 */
export default function PricingSection({ pricingDescription }: PricingSectionProps) {
  return (
    <section className="py-12 md:py-20 px-5 sm:px-6" style={{ background: '#080808' }}>
      <div className="max-w-3xl mx-auto">
        {pricingDescription ? (
          <p
            className="text-[#F8F4F0]/70 text-sm md:text-base leading-loose whitespace-pre-line tracking-wider"
            style={{ fontFamily: "'Noto Serif JP', serif" }}
          >
            {pricingDescription}
          </p>
        ) : (
          <p
            className="text-center text-[#F8F4F0]/30 text-sm tracking-wider py-16"
            style={{ fontFamily: "'Noto Serif JP', serif" }}
          >
            料金情報は準備中です
          </p>
        )}
      </div>
    </section>
  );
}
