import Link from 'next/link';
import type { ReactNode } from 'react';
import type { Page } from '@/shared/api';

/** 0 起点のページ番号を、1 起点の page を付けた一覧の URL にする。先頭ページは page を付けない。 */
export const pageHref = (basePath: string, index: number) =>
  index === 0 ? basePath : `${basePath}?page=${index + 1}`;

/** 一覧の代わりに出す一文と、あれば回復の導線。 */
export function StorefrontNotice({
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
  basePath,
  index,
  enabled,
  label,
  children,
}: {
  basePath: string;
  index: number;
  enabled: boolean;
  label: string;
  children: ReactNode;
}) {
  return enabled ? (
    <Link
      href={pageHref(basePath, index)}
      aria-label={label}
      className={`${pagerItem} ${pagerIdle}`}
    >
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
export function StorefrontPager({ basePath, result }: { basePath: string; result: Page<unknown> }) {
  const page = result.number;
  const pageCount = result.total_pages;
  const from = page * result.size + 1;
  const to = from + result.content.length - 1;
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
        <PagerLink basePath={basePath} index={page - 1} enabled={hasPrev} label="前へ">
          前へ
        </PagerLink>
        <PagerLink basePath={basePath} index={page + 1} enabled={hasNext} label="次へ">
          次へ
        </PagerLink>
      </div>
      <p className="hidden sm:block text-xs md:text-sm tracking-wider">
        {`${result.total_elements} 件中 ${from}-${to} を表示`}
      </p>
      <nav aria-label="ページネーション" className="hidden sm:flex gap-1">
        <PagerLink basePath={basePath} index={page - 1} enabled={hasPrev} label="前へ">
          ‹
        </PagerLink>
        {Array.from({ length: windowSize }, (_, i) => {
          const index = windowStart + i;
          const current = index === page;
          return (
            <Link
              key={index}
              href={pageHref(basePath, index)}
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
        <PagerLink basePath={basePath} index={page + 1} enabled={hasNext} label="次へ">
          ›
        </PagerLink>
      </nav>
    </div>
  );
}
