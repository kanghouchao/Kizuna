'use client';

import {
  CalendarCheckIcon,
  ChevronRightIcon,
  CircleUserRoundIcon,
  LayoutGridIcon,
  XIcon,
} from 'lucide-react';
import Image from 'next/image';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useRef, useState } from 'react';
import { STORE_SITE_NAV_PAGES } from '../../model/navigation';
import { useMemberReservationUrl } from '../../model/useMemberReservationUrl';
import { NAV_LOOK, RESERVATION_HREF, type NavHref } from './navLook';

const LOGIN_HREF = '/auth/login';

// タブバーに常に出すページ。予約を中央に置き、残りは「その他」のシートに入る
const TAB_HREFS: NavHref[] = ['/', '/casts', RESERVATION_HREF, '/schedule'];
const TAB_PAGES = TAB_HREFS.flatMap(href => STORE_SITE_NAV_PAGES.filter(p => p.href === href));
const SHEET_PAGES = STORE_SITE_NAV_PAGES.filter(p => !TAB_HREFS.includes(p.href));
const PILL_PAGES = STORE_SITE_NAV_PAGES.filter(p => p.href !== RESERVATION_HREF);

function useIsActive() {
  const pathname = usePathname();
  return (href: string) =>
    href === '/' ? pathname === '/' : pathname === href || pathname.startsWith(`${href}/`);
}

function Brand({ storeName, logoUrl }: { storeName: string; logoUrl?: string }) {
  return (
    <Link href="/" className="flex min-w-0 items-center gap-2">
      {logoUrl ? (
        <span className="relative block h-9 w-28 shrink-0">
          <Image
            src={logoUrl}
            alt={storeName}
            fill
            sizes="112px"
            className="object-contain object-left"
          />
        </span>
      ) : (
        <>
          <span
            aria-hidden
            className="m-bg-pink m-border m-display grid size-9 shrink-0 place-items-center rounded-full text-base"
          >
            {Array.from(storeName)[0]}
          </span>
          <span className="m-display truncate text-lg">{storeName}</span>
        </>
      )}
    </Link>
  );
}

/** 上部のナビゲーション。スマートフォンは店名とログインだけ、デスクトップは浮いた角丸のナビに全ページを並べる。 */
export function TopNav({ storeName, logoUrl }: { storeName: string; logoUrl?: string }) {
  const isActive = useIsActive();
  const memberReservationUrl = useMemberReservationUrl();

  return (
    <>
      <header className="flex items-center justify-between gap-3 px-4 pt-4 pb-2 lg:hidden">
        <Brand storeName={storeName} logoUrl={logoUrl} />
        <Link
          href={LOGIN_HREF}
          className="m-bg-card m-border flex shrink-0 items-center gap-1 rounded-full px-3 py-1.5 text-xs font-bold"
        >
          <CircleUserRoundIcon className="size-4" aria-hidden />
          ログイン
        </Link>
      </header>

      <header className="sticky top-4 z-50 mx-auto hidden max-w-7xl px-6 lg:block">
        <nav
          aria-label="メインメニュー"
          className="m-bg-card m-sticker flex items-center gap-2 rounded-full py-2 pr-2 pl-5 whitespace-nowrap xl:gap-3"
        >
          <Brand storeName={storeName} logoUrl={logoUrl} />
          <ul className="ml-auto flex shrink-0 items-center text-[13px] font-bold xl:gap-1 xl:text-sm">
            {PILL_PAGES.map(({ href, label }) => {
              const active = isActive(href);
              return (
                <li key={href}>
                  <Link
                    href={href}
                    aria-current={active ? 'page' : undefined}
                    className={`block rounded-full border-2 px-2.5 py-1.5 transition-colors hover:bg-[var(--m-yellow)] xl:px-3 ${
                      active ? 'border-[var(--m-ink)] bg-[var(--m-yellow)]' : 'border-transparent'
                    }`}
                  >
                    {label}
                  </Link>
                </li>
              );
            })}
          </ul>
          <Link
            href={LOGIN_HREF}
            className="flex shrink-0 items-center gap-1 rounded-full px-2.5 py-2 text-[13px] font-bold transition-colors hover:bg-[var(--m-yellow)] xl:text-sm"
          >
            <CircleUserRoundIcon className="size-4" aria-hidden />
            ログイン
          </Link>
          {memberReservationUrl && (
            <a
              href={memberReservationUrl}
              className="m-border shrink-0 rounded-full px-3 py-2 text-[13px] font-bold transition-colors hover:bg-[var(--m-yellow)] xl:text-sm"
            >
              WEB予約
            </a>
          )}
          <Link
            href={RESERVATION_HREF}
            className="m-bg-pink m-border flex shrink-0 items-center gap-1.5 rounded-full px-4 py-2 text-[13px] font-bold xl:px-5 xl:text-sm"
          >
            <CalendarCheckIcon className="size-4" aria-hidden />
            ご予約
          </Link>
        </nav>
      </header>
    </>
  );
}

/**
 * スマートフォンの下部のタブバーと「その他」のシート。シートは dialog の showModal で開くので、
 * 開いている間は外へフォーカスが出ず、Esc で閉じ、閉じている間は中の項目に届かない。
 */
export function TabBar() {
  const isActive = useIsActive();
  const memberReservationUrl = useMemberReservationUrl();
  const sheet = useRef<HTMLDialogElement>(null);
  const [open, setOpen] = useState(false);

  const openSheet = () => {
    sheet.current?.showModal();
    setOpen(true);
  };
  const closeSheet = () => sheet.current?.close();

  return (
    <>
      <nav
        aria-label="メインメニュー"
        className="m-bg-card m-sticker fixed inset-x-3 bottom-3 z-50 rounded-[26px] lg:hidden"
      >
        <ul className="grid grid-cols-5 items-end px-1 pt-1.5 pb-1">
          {TAB_PAGES.map(({ href, label }) => {
            const { Icon } = NAV_LOOK[href];
            const active = isActive(href);
            const reserve = href === RESERVATION_HREF;
            return (
              <li key={href} className="flex justify-center">
                <Link
                  href={href}
                  aria-current={active ? 'page' : undefined}
                  className="flex flex-col items-center gap-0.5 text-[11px] font-bold"
                >
                  <span
                    className={`grid place-items-center rounded-full border-2 ${
                      reserve
                        ? 'm-bg-pink -mt-6 size-14 border-[var(--m-ink)]'
                        : active
                          ? 'm-bg-yellow h-8 w-12 border-[var(--m-ink)]'
                          : 'h-8 w-12 border-transparent'
                    }`}
                  >
                    <Icon className={reserve ? 'size-6' : 'size-5'} aria-hidden />
                  </span>
                  {label}
                </Link>
              </li>
            );
          })}
          <li className="flex justify-center">
            <button
              type="button"
              onClick={openSheet}
              aria-haspopup="dialog"
              aria-expanded={open}
              className="flex flex-col items-center gap-0.5 text-[11px] font-bold"
            >
              <span
                className={`grid h-8 w-12 place-items-center rounded-full border-2 ${
                  open ? 'm-bg-yellow border-[var(--m-ink)]' : 'border-transparent'
                }`}
              >
                <LayoutGridIcon className="size-5" aria-hidden />
              </span>
              その他
            </button>
          </li>
        </ul>
      </nav>

      <dialog
        ref={sheet}
        className="m-sheet"
        aria-labelledby="m-sheet-title"
        onClose={() => setOpen(false)}
        onClick={e => {
          if (e.target === e.currentTarget) closeSheet();
        }}
      >
        <div className="px-5 pt-3 pb-8">
          <div aria-hidden className="m-bg-ink mx-auto mb-4 h-1.5 w-12 rounded-full opacity-20" />
          <div className="mb-4 flex items-center justify-between">
            <h2 id="m-sheet-title" className="m-display text-xl">
              その他
            </h2>
            <button
              type="button"
              onClick={closeSheet}
              aria-label="閉じる"
              className="m-border grid size-10 place-items-center rounded-full"
            >
              <XIcon className="size-5" aria-hidden />
            </button>
          </div>
          <ul className="grid grid-cols-2 gap-3">
            {SHEET_PAGES.map(({ href, label }) => {
              const { Icon, tone } = NAV_LOOK[href];
              return (
                <li key={href}>
                  <Link
                    href={href}
                    onClick={closeSheet}
                    aria-current={isActive(href) ? 'page' : undefined}
                    className={`${tone} m-sticker flex items-center gap-2 rounded-2xl px-4 py-4 font-bold`}
                  >
                    <Icon className="size-5 shrink-0" aria-hidden />
                    {label}
                  </Link>
                </li>
              );
            })}
          </ul>
          {memberReservationUrl && (
            <a
              href={memberReservationUrl}
              onClick={closeSheet}
              className="m-border mt-4 flex items-center justify-between rounded-2xl px-4 py-3 font-bold"
            >
              <span className="flex items-center gap-2">
                <CalendarCheckIcon className="size-5" aria-hidden />
                WEB予約
              </span>
              <ChevronRightIcon className="size-5" aria-hidden />
            </a>
          )}
          <Link
            href={LOGIN_HREF}
            onClick={closeSheet}
            className="m-border mt-3 flex items-center justify-between rounded-2xl px-4 py-3 font-bold"
          >
            <span className="flex items-center gap-2">
              <CircleUserRoundIcon className="size-5" aria-hidden />
              ログイン
            </span>
            <ChevronRightIcon className="size-5" aria-hidden />
          </Link>
        </div>
      </dialog>
    </>
  );
}
