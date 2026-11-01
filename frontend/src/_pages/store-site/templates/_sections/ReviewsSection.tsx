import type { Page } from '@/shared/api';
import { PublicReview } from '../../model/types';
import { StorefrontNotice, StorefrontPager, pageHref } from './StorefrontPaging';

export const REVIEWS_PER_PAGE = 10;

const BASE_PATH = '/reviews';

interface ReviewsSectionProps {
  /** 取得に失敗したときは null。 */
  reviews: Page<PublicReview> | null;
  /** 要求したページ（0 起点）。 */
  page: number;
}

/** "YYYY-MM-DD" → "YYYY年M月D日"。店舗の現地日付をそのまま読むため Date を経由しない。 */
const formatDate = (value: string) => {
  const [y, m, d] = value.split('-').map(Number);
  return `${y}年${m}月${d}日`;
};

/**
 * 掲出中の口コミ一覧（Server Component）。
 * 本文はテキストとして出力し（記法として解釈しない）、受付時の改行を保って表示する。
 */
export default function ReviewsSection({ reviews, page }: ReviewsSectionProps) {
  if (reviews === null) {
    return (
      <StorefrontNotice
        alert
        message="口コミを読み込めませんでした"
        link={{ href: pageHref(BASE_PATH, page), label: '再読み込み' }}
      />
    );
  }
  if (reviews.content.length === 0) {
    // 掲出が減ってページ数が縮むと、開いていたページだけが空になる
    return reviews.total_elements > 0 ? (
      <StorefrontNotice
        message="このページに口コミはありません"
        link={{ href: pageHref(BASE_PATH, 0), label: '最初のページへ' }}
      />
    ) : (
      <StorefrontNotice message="掲載中の口コミはありません" />
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
        {last > 0 && <StorefrontPager basePath={BASE_PATH} result={reviews} />}
      </div>
    </section>
  );
}
