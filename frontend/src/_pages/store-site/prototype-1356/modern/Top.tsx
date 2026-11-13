import {
  ArrowRightIcon,
  BellIcon,
  CalendarCheckIcon,
  CalendarDaysIcon,
  CoinsIcon,
  MessageCircleIcon,
  SparklesIcon,
  StoreIcon,
  UsersIcon,
} from 'lucide-react';
import Image from 'next/image';
import Link from 'next/link';
import { protoHref, type ProtoPageProps } from '../contract';
import { AvatarRow, CastGrid, EmptyCasts, SectionTitle, Shell } from './parts';

const TILES = [
  { href: '/schedule', label: '出勤表', sub: '今日会える子', Icon: CalendarDaysIcon, tone: 'm-bg-yellow' },
  { href: '/menu', label: '料金', sub: 'システム', Icon: CoinsIcon, tone: 'm-bg-mint' },
  { href: '/reviews', label: '口コミ', sub: 'みんなの声', Icon: MessageCircleIcon, tone: 'm-bg-sky' },
  { href: '/news', label: 'お知らせ', sub: '最新情報', Icon: BellIcon, tone: 'm-bg-lilac' },
  { href: '/casts', label: 'キャスト', sub: '全員見る', Icon: UsersIcon, tone: 'm-bg-peach' },
  { href: '/about', label: '店舗情報', sub: 'アクセス', Icon: StoreIcon, tone: 'm-bg-card' },
] as const;

const TOP_CAST_LIMIT = 12;

export default function Top(props: ProtoPageProps) {
  const { store, siteConfig, casts, q } = props;
  const { banner_url, catch_copy, description, mv_url, mv_type } = siteConfig;

  // 予約タイルの横幅で、PC の 4 列が端数なく埋まるようにする
  const cells = (mv_url ? 4 : 0) + (description ? 2 : 0) + TILES.length;
  const reserveWide = cells % 4 === 0;

  return (
    <Shell props={props}>
      {/* ヒーロー：見出しが主役。バナーは角丸の額に収め、無ければ見出しの面だけで立たせる */}
      <section
        className={`grid items-center gap-6 pt-2 lg:gap-10 ${banner_url ? 'lg:grid-cols-[1.05fr_1fr]' : ''}`}
      >
        <div
          className={`m-pop relative ${
            banner_url
              ? ''
              : 'm-bg-yellow m-sticker overflow-hidden rounded-[var(--m-radius)] px-6 py-10 sm:px-10 lg:py-16'
          }`}
        >
          {!banner_url && (
            <>
              <span
                aria-hidden
                className="m-float m-bg-pink absolute -top-8 -right-8 size-32 rounded-full opacity-90 sm:size-44"
              />
              <span
                aria-hidden
                className="m-bg-mint absolute right-16 -bottom-10 size-24 rounded-[30%] opacity-90 sm:right-40"
              />
            </>
          )}
          <div className="relative">
            <p className="m-bg-card m-border inline-flex items-center gap-1 rounded-full px-3 py-0.5 text-xs font-bold">
              <SparklesIcon className="size-3.5" aria-hidden />
              18歳以上限定
            </p>
            <h1 className="m-display mt-4 text-[clamp(2.4rem,9vw,5.25rem)] break-words">
              {store.name}
            </h1>
            {catch_copy && (
              <p className="m-pop m-pop-2 mt-4 max-w-xl text-lg font-bold sm:text-xl">
                <span className="bg-[linear-gradient(transparent_60%,#ff5c9366_60%)]">{catch_copy}</span>
              </p>
            )}
            <div className="m-pop m-pop-3 mt-7 flex flex-wrap gap-3">
              <Link
                href={protoHref('/reservation', q)}
                className="m-bg-pink m-sticker m-lift inline-flex items-center gap-2 rounded-full px-6 py-3 font-bold"
              >
                <CalendarCheckIcon className="size-5" aria-hidden />
                ご予約はこちら
              </Link>
              <Link
                href={protoHref('/casts', q)}
                className="m-bg-card m-sticker m-lift inline-flex items-center gap-2 rounded-full px-6 py-3 font-bold"
              >
                キャストを見る
                <ArrowRightIcon className="size-4" aria-hidden />
              </Link>
            </div>
          </div>
        </div>

        {banner_url && (
          <div className="m-pop m-pop-2 relative">
            <span
              aria-hidden
              className="m-bg-yellow absolute inset-0 translate-x-3 translate-y-3 rotate-2 rounded-[var(--m-radius)]"
            />
            <div className="m-border relative aspect-[4/3] -rotate-1 overflow-hidden rounded-[var(--m-radius)]">
              <Image
                src={banner_url}
                alt={`${store.name} のバナー`}
                fill
                priority
                sizes="(min-width: 1024px) 600px, 100vw"
                className="object-cover"
              />
            </div>
          </div>
        )}
      </section>

      {casts.length > 0 && (
        <section className="mt-10">
          <SectionTitle en="PICK UP" ja="本日のおすすめ" />
          <AvatarRow casts={casts.slice(0, 16)} q={q} label="本日のおすすめキャスト" />
        </section>
      )}

      <section className="mt-8" aria-label="ショートカット">
        <ul className="grid grid-flow-dense auto-rows-[minmax(120px,auto)] grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4">
          {mv_url && (
            <li className="m-sticker relative col-span-2 row-span-2 min-h-[260px] overflow-hidden rounded-[var(--m-radius)]">
              {mv_type === 'video' ? (
                <video
                  src={mv_url}
                  autoPlay
                  muted
                  loop
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
              <p className="text-xs font-bold tracking-[0.2em] text-[#c2185b]">ABOUT</p>
              <p className="mt-2 text-[15px] leading-8 whitespace-pre-line">{description}</p>
            </li>
          )}
          {TILES.map(({ href, label, sub, Icon, tone }) => (
            <li key={href}>
              <Link
                href={protoHref(href, q)}
                className={`${tone} m-sticker m-lift flex h-full flex-col justify-between rounded-[24px] p-4`}
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
          ))}
          <li className={`col-span-2 ${reserveWide ? 'lg:col-span-4' : ''}`}>
            <Link
              href={protoHref('/reservation', q)}
              className="m-bg-ink m-lift flex h-full items-center justify-between gap-4 rounded-[24px] border-2 border-[#1f1a3a] p-5 shadow-[4px_4px_0_#ff5c93]"
            >
              <span>
                <span className="m-display block text-2xl text-white">ご予約</span>
                <span className="text-sm text-white/85">お好きなキャストを指名して予約</span>
              </span>
              <span className="m-bg-pink grid size-12 shrink-0 place-items-center rounded-full">
                <ArrowRightIcon className="size-6" aria-hidden />
              </span>
            </Link>
          </li>
        </ul>
      </section>

      <section className="mt-12">
        <SectionTitle
          en="CAST"
          ja="在籍キャスト"
          action={
            casts.length > TOP_CAST_LIMIT ? (
              <Link
                href={protoHref('/casts', q)}
                className="m-border shrink-0 rounded-full px-4 py-1.5 text-sm font-bold"
              >
                全員見る（{casts.length}人）
              </Link>
            ) : undefined
          }
        />
        {casts.length > 0 ? (
          <CastGrid casts={casts.slice(0, TOP_CAST_LIMIT)} q={q} />
        ) : (
          <EmptyCasts />
        )}
      </section>
    </Shell>
  );
}
