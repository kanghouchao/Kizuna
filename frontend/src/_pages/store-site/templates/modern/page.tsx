import { ArrowRightIcon, CalendarCheckIcon, SparklesIcon } from 'lucide-react';
import Image from 'next/image';
import Link from 'next/link';
import { STORE_SITE_NAV_PAGES } from '../../model/navigation';
import type { StoreSitePageProps } from '../../model/types';
import { CastRow, EmptyCasts, SectionTitle } from './castParts';
import Frame from './Frame';
import { NAV_LOOK, RESERVATION_HREF } from './navLook';

const TILE_PAGES = STORE_SITE_NAV_PAGES.filter(p => p.href !== '/' && p.href !== RESERVATION_HREF);

/** 店舗トップページ（modern テンプレート）。店名と Banner、キャストの横送り、MV・説明・各ページへの入口の格子。 */
export default function ModernTopPage({ store, siteConfig, casts }: StoreSitePageProps<'page'>) {
  const { banner_url, catch_copy, description, mv_url, mv_type } = siteConfig;

  // 格子は 4 列で、MV が 4 マス・説明が 2 マスを使う。余りが出ない幅を予約の入口に与える
  const cells = (mv_url ? 4 : 0) + (description ? 2 : 0) + TILE_PAGES.length;
  const reserveSpan = cells % 4 === 0 ? 'lg:col-span-4' : '';

  return (
    <Frame store={store} siteConfig={siteConfig}>
      <section
        className={`grid items-center gap-8 pt-2 lg:gap-10 ${banner_url ? 'lg:grid-cols-[1.05fr_1fr]' : ''}`}
      >
        <div
          className={`m-pop relative min-w-0 ${
            banner_url
              ? ''
              : 'm-bg-yellow m-sticker overflow-hidden rounded-[var(--m-radius)] px-6 py-10 sm:px-10 lg:py-16'
          }`}
        >
          {!banner_url && (
            <>
              <span
                aria-hidden
                className="m-float m-bg-pink absolute -top-8 -right-8 size-32 rounded-full sm:size-44"
              />
              <span
                aria-hidden
                className="m-bg-mint absolute right-16 -bottom-10 size-24 rounded-[30%] sm:right-40"
              />
            </>
          )}
          <div className="relative">
            <p className="m-bg-card m-border inline-flex items-center gap-1 rounded-full px-3 py-0.5 text-xs font-bold">
              <SparklesIcon className="size-3.5" aria-hidden />
              18歳以上限定
            </p>
            <h1 className="m-display mt-4 text-[clamp(2.4rem,9vw,5.25rem)]">{store.name}</h1>
            {catch_copy && (
              <p className="m-pop m-pop-2 mt-4 max-w-xl text-lg font-bold whitespace-pre-line sm:text-xl">
                <span className="m-marker">{catch_copy}</span>
              </p>
            )}
            <div className="m-pop m-pop-3 mt-7 flex flex-wrap gap-3">
              <Link
                href={RESERVATION_HREF}
                className="m-bg-pink m-sticker m-lift inline-flex items-center gap-2 rounded-full px-6 py-3 font-bold"
              >
                <CalendarCheckIcon className="size-5" aria-hidden />
                ご予約はこちら
              </Link>
              <Link
                href="/casts"
                className="m-bg-card m-sticker m-lift inline-flex items-center gap-2 rounded-full px-6 py-3 font-bold"
              >
                キャストを見る
                <ArrowRightIcon className="size-4" aria-hidden />
              </Link>
            </div>
          </div>
        </div>

        {banner_url && (
          <div className="m-pop m-pop-2 relative mr-3 mb-3">
            <span
              aria-hidden
              className="m-bg-yellow absolute inset-0 translate-x-3 translate-y-3 rotate-2 rounded-[var(--m-radius)]"
            />
            <div className="m-border relative aspect-[4/3] -rotate-1 overflow-hidden rounded-[var(--m-radius)]">
              <Image
                src={banner_url}
                alt={`${store.name} のバナー`}
                fill
                preload
                sizes="(min-width: 1024px) 600px, 100vw"
                className="object-cover"
              />
            </div>
          </div>
        )}
      </section>

      <section className="mt-10">
        <SectionTitle
          en="CAST"
          ja="在籍キャスト"
          action={
            casts.length > 0 ? (
              <Link
                href="/casts"
                className="m-bg-card m-border shrink-0 rounded-full px-4 py-1.5 text-sm font-bold"
              >
                全員見る（{casts.length}人）
              </Link>
            ) : undefined
          }
        />
        {casts.length > 0 ? <CastRow casts={casts} label="在籍キャスト" /> : <EmptyCasts />}
      </section>

      <section className="mt-8" aria-label="各ページへの入口">
        <ul className="grid grid-flow-dense auto-rows-[minmax(120px,auto)] grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4">
          {mv_url && (
            <li className="m-sticker relative col-span-2 row-span-2 min-h-[260px] overflow-hidden rounded-[var(--m-radius)]">
              {mv_type === 'video' ? (
                <video
                  src={mv_url}
                  controls
                  playsInline
                  aria-label={`${store.name} の紹介動画`}
                  className="absolute inset-0 size-full object-cover"
                />
              ) : (
                <Image
                  src={mv_url}
                  alt={`${store.name} のメインビジュアル`}
                  fill
                  sizes="(min-width: 1024px) 640px, 100vw"
                  className="object-cover"
                />
              )}
            </li>
          )}
          {description && (
            <li className="m-bg-card m-sticker col-span-2 rounded-[var(--m-radius)] p-6">
              <p className="m-label">ABOUT</p>
              <p className="mt-2 text-[15px] leading-8 whitespace-pre-line">{description}</p>
            </li>
          )}
          {TILE_PAGES.map(({ href, label }) => {
            const { Icon, tone, sub } = NAV_LOOK[href];
            return (
              <li key={href}>
                <Link
                  href={href}
                  className={`${tone} m-sticker m-lift flex h-full flex-col justify-between gap-3 rounded-[24px] p-4`}
                >
                  <span className="m-bg-card m-border grid size-10 place-items-center rounded-full">
                    <Icon className="size-5" aria-hidden />
                  </span>
                  <span>
                    <span className="m-display block text-xl">{label}</span>
                    <span className="text-xs font-bold">{sub}</span>
                  </span>
                </Link>
              </li>
            );
          })}
          <li className={`col-span-2 ${reserveSpan}`}>
            <Link
              href={RESERVATION_HREF}
              className="m-bg-ink m-lift flex h-full items-center justify-between gap-4 rounded-[24px] border-2 border-[var(--m-ink)] p-5 shadow-[4px_4px_0_var(--m-pink)]"
            >
              <span>
                <span className="m-display block text-2xl">ご予約</span>
                <span className="text-sm">お好きなキャストを指名して予約</span>
              </span>
              <span className="m-bg-pink grid size-12 shrink-0 place-items-center rounded-full">
                <ArrowRightIcon className="size-6" aria-hidden />
              </span>
            </Link>
          </li>
        </ul>
      </section>
    </Frame>
  );
}
