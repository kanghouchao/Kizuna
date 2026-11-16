import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react';
import Link from 'next/link';
import type { ReactNode } from 'react';
import type { Page } from '@/shared/api';
import { pageHref, pagerRange } from '../../lib/paging';
import { RESERVATION_HREF } from './navLook';

/** 各ページの先頭の見出し。英字の添え書きと右端の札は飾りで、見出しの名前には含めない。 */
export function PageHead({ en, title, chip }: { en: string; title: string; chip?: ReactNode }) {
  return (
    <header className="m-pop flex flex-wrap items-end justify-between gap-3 pt-2 pb-6">
      <div className="min-w-0">
        <p className="m-label" aria-hidden>
          {en}
        </p>
        <h1 className="m-display mt-1 text-4xl sm:text-5xl">{title}</h1>
      </div>
      {chip && (
        <p className="m-bg-yellow m-border rounded-full px-4 py-1 text-sm font-bold">{chip}</p>
      )}
    </header>
  );
}

const pill = 'inline-flex items-center gap-1.5 rounded-full px-4 py-1.5 text-sm font-bold';

/** 一覧や本文の代わりに出す案内と、あれば次に進む先。alert は取得の失敗を知らせるときだけ付ける。 */
export function Notice({
  title,
  note,
  link,
  alert = false,
}: {
  title: string;
  note?: string;
  link?: { href: string; label: string };
  alert?: boolean;
}) {
  return (
    <div
      role={alert ? 'alert' : undefined}
      className="m-bg-card rounded-[var(--m-radius)] border-2 border-dashed border-[var(--m-ink)] px-6 py-10 text-center"
    >
      <p className="m-display text-xl">{title}</p>
      {note && <p className="m-sub mt-2 text-sm">{note}</p>}
      {link && (
        <Link href={link.href} className={`m-bg-yellow m-border mt-5 ${pill}`}>
          {link.label}
        </Link>
      )}
    </div>
  );
}

const pagerItem =
  'h-10 min-w-10 items-center justify-center gap-1 rounded-full border-2 px-3 text-sm font-bold';

/** 端に達した前へ・次へはリンクにせず、押せない形で残す。 */
function PagerStep({
  basePath,
  index,
  enabled,
  children,
}: {
  basePath: string;
  index: number;
  enabled: boolean;
  children: ReactNode;
}) {
  return enabled ? (
    <Link
      href={pageHref(basePath, index)}
      className={`m-bg-card inline-flex border-[var(--m-ink)] ${pagerItem}`}
    >
      {children}
    </Link>
  ) : (
    <span
      aria-disabled="true"
      className={`m-sub inline-flex border-dashed border-[var(--m-sub)] ${pagerItem}`}
    >
      {children}
    </span>
  );
}

/** ページ送り。件数と表示範囲、最大 5 件の番号、前へ・次へ。狭い幅では番号を畳む。 */
export function Pager({ basePath, result }: { basePath: string; result: Page<unknown> }) {
  const { page, from, to, pages, hasPrev, hasNext } = pagerRange(result);

  return (
    <div className="mt-8 flex flex-col items-center gap-3 sm:flex-row sm:justify-between">
      <p className="m-sub text-sm font-bold">{`${result.total_elements} 件中 ${from}-${to} を表示`}</p>
      <nav aria-label="ページネーション" className="flex flex-wrap items-center gap-2">
        <PagerStep basePath={basePath} index={page - 1} enabled={hasPrev}>
          <ChevronLeftIcon className="size-4" aria-hidden />
          前へ
        </PagerStep>
        {pages.map(index => {
          const current = index === page;
          return (
            <Link
              key={index}
              href={pageHref(basePath, index)}
              aria-current={current ? 'page' : undefined}
              className={`${current ? 'm-bg-yellow inline-flex' : 'm-bg-card hidden sm:inline-flex'} border-[var(--m-ink)] ${pagerItem}`}
            >
              {index + 1}
            </Link>
          );
        })}
        <PagerStep basePath={basePath} index={page + 1} enabled={hasNext}>
          次へ
          <ChevronRightIcon className="size-4" aria-hidden />
        </PagerStep>
      </nav>
    </div>
  );
}

const tokyoDate = new Intl.DateTimeFormat('ja-JP', {
  timeZone: 'Asia/Tokyo',
  year: 'numeric',
  month: 'long',
  day: 'numeric',
});

/** 記事の公開開始の日時を、店舗の現地日付で出す。 */
export function PublishedOn({ value }: { value: string }) {
  return <time dateTime={value}>{tokyoDate.format(new Date(value))}</time>;
}

/** 予約への入口の帯。予約の前に読むページ（料金・店舗情報・出勤表）の末尾に置く。 */
export function ReserveBand({ lead }: { lead: string }) {
  return (
    <Link
      href={RESERVATION_HREF}
      className="m-bg-ink m-lift mt-8 flex items-center justify-between gap-4 rounded-[24px] border-2 border-[var(--m-ink)] p-5 shadow-[4px_4px_0_var(--m-pink)]"
    >
      <span>
        <span className="m-display block text-2xl">ご予約はこちら</span>
        <span className="text-sm">{lead}</span>
      </span>
      <span className="m-bg-pink grid size-12 shrink-0 place-items-center rounded-full">
        <ChevronRightIcon className="size-6" aria-hidden />
      </span>
    </Link>
  );
}
