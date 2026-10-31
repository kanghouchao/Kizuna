import Link from 'next/link';
import type { Page } from '@/shared/api';
import { PublicReview } from '../../model/types';

export const REVIEWS_PER_PAGE = 10;

interface ReviewsSectionProps {
  reviews: Page<PublicReview>;
}

/** "YYYY-MM-DD" → "YYYY年M月D日"。店舗の現地日付をそのまま読むため Date を経由しない。 */
const formatDate = (value: string) => {
  const [y, m, d] = value.split('-').map(Number);
  return `${y}年${m}月${d}日`;
};

const pageHref = (index: number) => (index === 0 ? '/reviews' : `/reviews?page=${index + 1}`);

/**
 * 掲出中の口コミ一覧（Server Component）。
 * 本文はテキストとして出力し（記法として解釈しない）、受付時の改行を保って表示する。
 */
export default function ReviewsSection({ reviews }: ReviewsSectionProps) {
  if (reviews.content.length === 0) {
    return (
      <section
        className="py-16 md:py-24 px-5 sm:px-6"
        style={{ background: 'var(--storefront-bg)' }}
      >
        <div className="max-w-2xl mx-auto text-center">
          <p
            className="text-[color-mix(in_srgb,var(--storefront-fg)_70%,transparent)] text-base md:text-lg font-light tracking-[0.15em]"
            style={{ fontFamily: 'var(--storefront-font-display)' }}
          >
            掲載中の口コミはありません
          </p>
        </div>
      </section>
    );
  }

  const current = reviews.number;
  const last = reviews.total_pages - 1;

  return (
    <section className="py-12 md:py-20 px-5 sm:px-6" style={{ background: 'var(--storefront-bg)' }}>
      <div className="max-w-3xl mx-auto">
        <ul className="space-y-4 md:space-y-6">
          {reviews.content.map((review, i) => (
            <li
              key={`${current}-${i}`}
              className="p-5 md:p-7"
              style={{
                background: 'var(--storefront-surface-3)',
                boxShadow:
                  '0 0 0 1px color-mix(in srgb, var(--storefront-accent) 10%, transparent)',
              }}
            >
              <article>
                <p className="whitespace-pre-wrap break-words text-sm md:text-base leading-relaxed text-[color-mix(in_srgb,var(--storefront-fg)_85%,transparent)]">
                  {review.body}
                </p>
                <p className="mt-4 text-[11px] md:text-xs tracking-wider text-[color-mix(in_srgb,var(--storefront-accent)_70%,transparent)]">
                  {review.display_name || '匿名'}
                  <span className="mx-2" aria-hidden="true">
                    ・
                  </span>
                  <time dateTime={review.received_on}>{formatDate(review.received_on)}</time>
                </p>
              </article>
            </li>
          ))}
        </ul>
        {last > 0 && (
          <nav
            aria-label="口コミのページ送り"
            className="mt-10 flex items-center justify-center gap-6 text-xs md:text-sm tracking-wider text-[color-mix(in_srgb,var(--storefront-fg)_70%,transparent)]"
          >
            {current > 0 ? <Link href={pageHref(current - 1)}>前のページ</Link> : <span />}
            <span>
              {current + 1} / {last + 1}
            </span>
            {current < last ? <Link href={pageHref(current + 1)}>次のページ</Link> : <span />}
          </nav>
        )}
      </div>
    </section>
  );
}
