'use client';

import {
  BellIcon,
  CalendarCheckIcon,
  CalendarDaysIcon,
  ChevronRightIcon,
  CircleUserRoundIcon,
  CoinsIcon,
  HomeIcon,
  LayoutGridIcon,
  MessageCircleIcon,
  StoreIcon,
  UsersIcon,
  XIcon,
} from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import { protoHref } from '../contract';

const MAIN_TABS = [
  { href: '/', label: 'TOP', Icon: HomeIcon },
  { href: '/casts', label: 'キャスト', Icon: UsersIcon },
  { href: '/reservation', label: 'ご予約', Icon: CalendarCheckIcon },
  { href: '/schedule', label: '出勤表', Icon: CalendarDaysIcon },
] as const;

const SHEET_ITEMS = [
  { href: '/news', label: 'お知らせ', Icon: BellIcon, tone: 'm-bg-lilac' },
  { href: '/reviews', label: '口コミ', Icon: MessageCircleIcon, tone: 'm-bg-sky' },
  { href: '/menu', label: '料金', Icon: CoinsIcon, tone: 'm-bg-mint' },
  { href: '/about', label: '店舗情報', Icon: StoreIcon, tone: 'm-bg-peach' },
] as const;

export default function TabBar({ q }: { q: string }) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);

  const isActive = (href: string) =>
    href === '/' ? pathname === '/' : pathname === href || pathname.startsWith(`${href}/`);

  return (
    <div className="lg:hidden">
      {open && (
        <div className="fixed inset-0 z-[60]" role="dialog" aria-modal="true" aria-label="その他のメニュー">
          <button
            type="button"
            aria-label="閉じる"
            className="absolute inset-0 bg-[#1f1a3a]/40"
            onClick={() => setOpen(false)}
          />
          <div className="m-sheet m-bg-card absolute inset-x-0 bottom-0 rounded-t-[32px] px-5 pt-3 pb-32">
            <div className="mx-auto mb-4 h-1.5 w-12 rounded-full bg-[#1f1a3a]/20" />
            <div className="mb-4 flex items-center justify-between">
              <p className="m-display text-xl">その他</p>
              <button
                type="button"
                onClick={() => setOpen(false)}
                aria-label="閉じる"
                className="m-border grid size-10 place-items-center rounded-full"
              >
                <XIcon className="size-5" />
              </button>
            </div>
            <ul className="grid grid-cols-2 gap-3">
              {SHEET_ITEMS.map(({ href, label, Icon, tone }) => (
                <li key={href}>
                  <Link
                    href={protoHref(href, q)}
                    onClick={() => setOpen(false)}
                    className={`${tone} m-sticker m-lift flex items-center gap-2 rounded-2xl px-4 py-4 font-bold`}
                  >
                    <Icon className="size-5" aria-hidden />
                    {label}
                  </Link>
                </li>
              ))}
            </ul>
            <Link
              href={protoHref('/auth/login', q)}
              onClick={() => setOpen(false)}
              className="m-border mt-4 flex items-center justify-between rounded-2xl px-4 py-3 font-bold"
            >
              <span className="flex items-center gap-2">
                <CircleUserRoundIcon className="size-5" aria-hidden />
                ログイン
              </span>
              <ChevronRightIcon className="size-5" aria-hidden />
            </Link>
          </div>
        </div>
      )}

      <nav
        aria-label="メインメニュー"
        className="fixed inset-x-3 bottom-3 z-50 rounded-[26px] m-bg-card m-sticker"
      >
        <ul className="grid grid-cols-5 items-end px-1 pt-1.5 pb-1">
          {MAIN_TABS.map(({ href, label, Icon }) => {
            const active = isActive(href);
            const reserve = href === '/reservation';
            return (
              <li key={href} className="flex justify-center">
                <Link
                  href={protoHref(href, q)}
                  aria-current={active ? 'page' : undefined}
                  className="flex flex-col items-center gap-0.5 text-[11px] font-bold"
                >
                  <span
                    className={`grid place-items-center rounded-full transition-transform ${
                      reserve
                        ? 'm-bg-pink m-border -mt-6 size-14'
                        : active
                          ? 'm-bg-yellow h-8 w-12'
                          : 'h-8 w-12'
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
              onClick={() => setOpen(true)}
              aria-expanded={open}
              className="flex flex-col items-center gap-0.5 text-[11px] font-bold"
            >
              <span className={`grid h-8 w-12 place-items-center rounded-full ${open ? 'm-bg-yellow' : ''}`}>
                <LayoutGridIcon className="size-5" aria-hidden />
              </span>
              その他
            </button>
          </li>
        </ul>
      </nav>
    </div>
  );
}
