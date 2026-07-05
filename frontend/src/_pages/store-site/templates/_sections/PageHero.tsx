interface PageHeroProps {
  title: string;
  subtitle?: string;
}

/**
 * 各サブページ先頭の見出しバンド（Server Component）。
 * 既存区块と同じ暗色系トーン（#080808 / #C9A84C / Noto Serif JP）で統一する。
 */
export default function PageHero({ title, subtitle }: PageHeroProps) {
  return (
    <section
      className="pt-24 md:pt-32 pb-12 md:pb-16 px-5 sm:px-6 text-center"
      style={{ background: '#080808' }}
    >
      <h1
        className="text-2xl md:text-3xl font-light tracking-[0.25em] text-[#F8F4F0]/90"
        style={{ fontFamily: "'Noto Serif JP', 'Hiragino Mincho Pro', serif" }}
      >
        {title}
      </h1>
      <div className="flex items-center justify-center gap-3 mt-5 mb-3">
        <div className="h-px w-10 md:w-12 bg-linear-to-r from-transparent to-[#C9A84C]/35" />
        <span className="text-[#C9A84C]/40 text-[10px]">◆</span>
        <div className="h-px w-10 md:w-12 bg-linear-to-l from-transparent to-[#C9A84C]/35" />
      </div>
      {subtitle && (
        <p
          className="text-[#F8F4F0]/30 text-[11px] md:text-xs tracking-wider"
          style={{ fontFamily: "'Noto Serif JP', serif" }}
        >
          {subtitle}
        </p>
      )}
    </section>
  );
}
