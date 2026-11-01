import Link from 'next/link';
import type { ReactNode } from 'react';
import type { Page } from '@/shared/api';
import { PublicReview } from '../../model/types';

export const REVIEWS_PER_PAGE = 10;

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

const pageHref = (index: number) => (index === 0 ? '/reviews' : `/reviews?page=${index + 1}`);

/** 一覧の代わりに出す一文と、あれば回復の導線。 */
function ReviewsNotice({
  message,
  link,
  alert = false,
}: {
  message: string;
  link?: { href: string; label: string };
  alert?: boolean;
}) {
  return (
    <section className="py-16 md:py-24 px-5 sm:px-6" style={{ background: 'var(--storefront-bg)' }}>
      <div
        role={alert ? 'alert' : undefined}
        className="max-w-2xl mx-auto flex flex-col items-center gap-6 text-center"
      >
        <p
          className="text-[color-mix(in_srgb,var(--storefront-fg)_70%,transparent)] text-base md:text-lg font-light tracking-[0.15em]"
          style={{ fontFamily: 'var(--storefront-font-display)' }}
        >
          {message}
        </p>
        {link && (
          <Link
            href={link.href}
            className="text-xs tracking-[0.2em] text-[var(--storefront-accent)] px-5 py-2 border border-[color-mix(in_srgb,var(--storefront-accent)_40%,transparent)] hover:bg-[var(--storefront-accent)] hover:text-[var(--storefront-bg)] transition-colors duration-300"
          >
            {link.label}
          </Link>
        )}
      </div>
    </section>
  );
}

const pagerItem =
  'inline-flex h-8 min-w-8 items-center justify-center px-2 border text-xs md:text-sm transition-colors duration-300';
const pagerIdle =
  'border-[color-mix(in_srgb,var(--storefront-accent)_25%,transparent)] text-[color-mix(in_srgb,var(--storefront-fg)_70%,transparent)] hover:border-[var(--storefront-accent)] hover:text-[var(--storefront-accent)]';
const pagerDisabled =
  'border-[color-mix(in_srgb,var(--storefront-accent)_10%,transparent)] text-[var(--storefront-subtle)]';

/** 端に達した前へ/次へはリンクにせず、押せない状態として残す。 */
function PagerLink({
  index,
  enabled,
  label,
  children,
}: {
  index: number;
  enabled: boolean;
  label: string;
  children: ReactNode;
}) {
  return enabled ? (
    <Link href={pageHref(index)} aria-label={label} className={`${pagerItem} ${pagerIdle}`}>
      {children}
    </Link>
  ) : (
    <span aria-disabled="true" className={`${pagerItem} ${pagerDisabled}`}>
      <span className="sr-only">{label}</span>
      <span aria-hidden="true">{children}</span>
    </span>
  );
}

/**
 * 共通の Pager（shared/ui）と同じ構成：件数と表示範囲、最大 5 件の番号窓、狭い幅では前へ/次へだけ。
 * Pager は管理画面の色とクリック処理で組まれているため、公開サイトでは --storefront-* とリンクで組み直す。
 */
function ReviewsPager({ reviews }: { reviews: Page<PublicReview> }) {
  const page = reviews.number;
  const pageCount = reviews.total_pages;
  const from = page * reviews.size + 1;
  const to = from + reviews.content.length - 1;
  const windowSize = Math.min(5, pageCount);
  const windowStart = Math.max(
    0,
    Math.min(page - Math.floor(windowSize / 2), pageCount - windowSize)
  );
  const hasPrev = page > 0;
  const hasNext = page < pageCount - 1;

  return (
    <div className="mt-10 flex items-center justify-between gap-4 text-[color-mix(in_srgb,var(--storefront-fg)_70%,transparent)]">
      <div className="flex flex-1 justify-between sm:hidden">
        <PagerLink index={page - 1} enabled={hasPrev} label="前へ">
          前へ
        </PagerLink>
        <PagerLink index={page + 1} enabled={hasNext} label="次へ">
          次へ
        </PagerLink>
      </div>
      <p className="hidden sm:block text-xs md:text-sm tracking-wider">
        {`${reviews.total_elements} 件中 ${from}-${to} を表示`}
      </p>
      <nav aria-label="ページネーション" className="hidden sm:flex gap-1">
        <PagerLink index={page - 1} enabled={hasPrev} label="前へ">
          ‹
        </PagerLink>
        {Array.from({ length: windowSize }, (_, i) => {
          const index = windowStart + i;
          const current = index === page;
          return (
            <Link
              key={index}
              href={pageHref(index)}
              aria-current={current ? 'page' : undefined}
              className={`${pagerItem} ${
                current
                  ? 'border-[var(--storefront-accent)] text-[var(--storefront-accent)] bg-[color-mix(in_srgb,var(--storefront-accent)_10%,transparent)]'
                  : pagerIdle
              }`}
            >
              {index + 1}
            </Link>
          );
        })}
        <PagerLink index={page + 1} enabled={hasNext} label="次へ">
          ›
        </PagerLink>
      </nav>
    </div>
  );
}

/**
 * 掲出中の口コミ一覧（Server Component）。
 * 本文はテキストとして出力し（記法として解釈しない）、受付時の改行を保って表示する。
 */
export default function ReviewsSection({ reviews, page }: ReviewsSectionProps) {
  if (reviews === null) {
    return (
      <ReviewsNotice
        alert
        message="口コミを読み込めませんでした"
        link={{ href: pageHref(page), label: '再読み込み' }}
      />
    );
  }
  if (reviews.content.length === 0) {
    // 掲出が減ってページ数が縮むと、開いていたページだけが空になる
    return reviews.total_elements > 0 ? (
      <ReviewsNotice
        message="このページに口コミはありません"
        link={{ href: pageHref(0), label: '最初のページへ' }}
      />
    ) : (
      <ReviewsNotice message="掲載中の口コミはありません" />
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
        {last > 0 && <ReviewsPager reviews={reviews} />}
      </div>
    </section>
  );
}
