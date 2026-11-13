import { ArrowUpRightIcon, CalendarCheckIcon, CircleUserRoundIcon } from 'lucide-react';
import Image from 'next/image';
import Link from 'next/link';
import type { ReactNode } from 'react';
import type { Cast } from '../../model/types';
import { protoHref, SITE_NAV, type ProtoPageProps } from '../contract';
import { roundedDisplay } from './fonts';
import TabBar from './TabBar';
import './styles.css';

const TONES = ['m-bg-pink', 'm-bg-yellow', 'm-bg-mint', 'm-bg-sky', 'm-bg-lilac', 'm-bg-peach'] as const;

export function toneAt(i: number): string {
  return TONES[i % TONES.length];
}

function initialOf(name?: string): string {
  return name ? Array.from(name)[0] : '?';
}

function Brand({ props, size }: { props: ProtoPageProps; size: 'sm' | 'md' }) {
  const { store, siteConfig, q } = props;
  return (
    <Link href={protoHref('/', q)} className="flex min-w-0 items-center gap-2">
      {siteConfig.logo_url ? (
        <span className={`relative block ${size === 'sm' ? 'h-9 w-28' : 'h-10 w-32'}`}>
          <Image
            src={siteConfig.logo_url}
            alt={store.name}
            fill
            sizes="128px"
            className="object-contain object-left"
          />
        </span>
      ) : (
        <span className="flex min-w-0 items-center gap-2">
          <span className="m-bg-pink m-border m-display grid size-9 shrink-0 place-items-center rounded-full text-base">
            {initialOf(store.name)}
          </span>
          <span className="m-display truncate text-lg">{store.name}</span>
        </span>
      )}
    </Link>
  );
}

function PillNav({ props }: { props: ProtoPageProps }) {
  const { q } = props;
  return (
    <header className="sticky top-4 z-50 mx-auto hidden max-w-7xl px-6 lg:block">
      <nav
        aria-label="メインメニュー"
        className="m-bg-card m-sticker flex items-center gap-4 rounded-full py-2 pr-2 pl-5"
      >
        <Brand props={props} size="md" />
        <ul className="ml-auto flex items-center gap-1 text-sm font-bold">
          {SITE_NAV.filter(n => n.href !== '/reservation').map(n => (
            <li key={n.href}>
              <Link
                href={protoHref(n.href, q)}
                className="rounded-full px-3 py-2 transition-colors hover:bg-[#ffd84d]"
              >
                {n.label}
              </Link>
            </li>
          ))}
        </ul>
        <Link
          href={protoHref('/auth/login', q)}
          className="flex items-center gap-1 rounded-full px-3 py-2 text-sm font-bold hover:bg-[#fff0bf]"
        >
          <CircleUserRoundIcon className="size-4" aria-hidden />
          ログイン
        </Link>
        <Link
          href={protoHref('/reservation', q)}
          className="m-bg-pink m-border flex items-center gap-1.5 rounded-full px-5 py-2.5 text-sm font-bold"
        >
          <CalendarCheckIcon className="size-4" aria-hidden />
          ご予約
        </Link>
      </nav>
    </header>
  );
}

function MobileBar({ props }: { props: ProtoPageProps }) {
  return (
    <header className="flex items-center justify-between gap-3 px-4 pt-4 pb-2 lg:hidden">
      <Brand props={props} size="sm" />
      <Link
        href={protoHref('/auth/login', props.q)}
        className="m-border flex shrink-0 items-center gap-1 rounded-full px-3 py-1.5 text-xs font-bold"
      >
        <CircleUserRoundIcon className="size-4" aria-hidden />
        ログイン
      </Link>
    </header>
  );
}

function Footer({ props }: { props: ProtoPageProps }) {
  const { store, siteConfig, q } = props;
  const sns = siteConfig.sns_links ?? [];
  const partners = siteConfig.partner_links ?? [];
  return (
    <footer className="mx-auto mt-16 max-w-7xl px-4 lg:px-6">
      <div className="m-bg-ink rounded-[var(--m-radius)] px-6 py-10 lg:px-12">
        <div className="flex flex-col gap-8 lg:flex-row lg:justify-between">
          <div>
            <p className="m-display text-3xl text-white">{store.name}</p>
            <p className="m-bg-yellow mt-3 inline-block rounded-full px-3 py-1 text-xs font-bold">
              18歳以上限定
            </p>
            {sns.length > 0 && (
              <ul className="mt-5 flex flex-wrap gap-2">
                {sns.map((s, i) => (
                  <li key={`${s.url}-${i}`}>
                    <a
                      href={s.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className={`${toneAt(i)} m-tilt inline-block rounded-full px-4 py-1.5 text-sm font-bold`}
                    >
                      {s.label || s.platform}
                    </a>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <ul className="grid grid-cols-2 gap-x-8 gap-y-2 text-sm sm:grid-cols-4 lg:grid-cols-2">
            {SITE_NAV.map(n => (
              <li key={n.href}>
                <Link href={protoHref(n.href, q)} className="text-white/90 hover:text-[#ffd84d]">
                  {n.label}
                </Link>
              </li>
            ))}
          </ul>
        </div>
        {partners.length > 0 && (
          <div className="mt-8 border-t border-white/20 pt-6">
            <p className="text-xs font-bold tracking-widest text-white/80">PARTNER</p>
            <ul className="mt-3 flex flex-wrap gap-2">
              {partners.map((p, i) => (
                <li key={`${p.url}-${i}`}>
                  <a
                    href={p.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 rounded-full border border-white/40 px-3 py-1 text-sm text-white hover:bg-white/10"
                  >
                    {p.name}
                    <ArrowUpRightIcon className="size-3.5" aria-hidden />
                  </a>
                </li>
              ))}
            </ul>
          </div>
        )}
        <p className="mt-8 text-xs text-white/70">© {store.name}</p>
      </div>
    </footer>
  );
}

export function Shell({ props, children }: { props: ProtoPageProps; children: ReactNode }) {
  return (
    <div className={`p1356-modern ${roundedDisplay.variable}`}>
      <MobileBar props={props} />
      <PillNav props={props} />
      <main className="mx-auto max-w-7xl px-4 lg:px-6 lg:pt-8">{children}</main>
      <Footer props={props} />
      <TabBar q={props.q} />
    </div>
  );
}

/** 写真の無いキャストは頭文字を色面に置く */
export function CastPhoto({
  cast,
  index,
  sizes,
  textSize,
}: {
  cast: Cast;
  index: number;
  sizes: string;
  textSize: string;
}) {
  if (cast.photo_url) {
    return (
      <Image
        src={cast.photo_url}
        alt={cast.name ?? 'キャスト'}
        fill
        sizes={sizes}
        className="object-cover"
      />
    );
  }
  return (
    <span
      role="img"
      aria-label={cast.name ?? 'キャスト'}
      className={`${toneAt(index)} m-display absolute inset-0 grid place-items-center ${textSize}`}
    >
      {initialOf(cast.name)}
    </span>
  );
}

export function castHref(cast: Cast, q: string): string {
  return protoHref(`/casts/${cast.id ?? ''}`, q);
}

export function CastCard({ cast, index, q }: { cast: Cast; index: number; q: string }) {
  return (
    <Link
      href={castHref(cast, q)}
      className="m-bg-card m-sticker m-lift block overflow-hidden rounded-[20px]"
    >
      <span className="relative block aspect-[4/5] overflow-hidden border-b-2 border-[#1f1a3a]">
        <CastPhoto
          cast={cast}
          index={index}
          sizes="(min-width: 1024px) 200px, (min-width: 640px) 25vw, 33vw"
          textSize="text-4xl"
        />
      </span>
      <span className="block px-2 pt-1.5 pb-2">
        <span className="m-display block truncate text-sm sm:text-base">{cast.name}</span>
        {(cast.age || cast.height) && (
          <span className="mt-1 flex flex-wrap gap-1">
            {cast.age ? (
              <span className="m-bg-yellow rounded-full px-1.5 text-[10px] leading-5 font-bold sm:text-xs">
                {cast.age}歳
              </span>
            ) : null}
            {cast.height ? (
              <span className="m-bg-sky rounded-full px-1.5 text-[10px] leading-5 font-bold sm:text-xs">
                {cast.height}cm
              </span>
            ) : null}
          </span>
        )}
      </span>
    </Link>
  );
}

export function CastGrid({ casts, q }: { casts: Cast[]; q: string }) {
  return (
    <ul className="grid grid-cols-3 gap-2.5 sm:grid-cols-4 sm:gap-4 md:grid-cols-5 xl:grid-cols-6">
      {casts.map((cast, i) => (
        <li key={`${cast.id ?? 'c'}-${i}`}>
          <CastCard cast={cast} index={i} q={q} />
        </li>
      ))}
    </ul>
  );
}

/** 丸いアバターの横スナップ列 */
export function AvatarRow({ casts, q, label }: { casts: Cast[]; q: string; label: string }) {
  return (
    <ul className="m-snap -mx-4" aria-label={label}>
      {casts.map((cast, i) => (
        <li key={`${cast.id ?? 'a'}-${i}`} className="w-20 sm:w-24">
          <Link href={castHref(cast, q)} className="block text-center">
            <span className="m-avatar-ring m-squircle block p-[3px]">
              <span className="m-squircle relative block aspect-square overflow-hidden border-2 border-white">
                <CastPhoto cast={cast} index={i} sizes="96px" textSize="text-2xl" />
              </span>
            </span>
            <span className="mt-1.5 block truncate text-xs font-bold">{cast.name}</span>
          </Link>
        </li>
      ))}
    </ul>
  );
}

export function EmptyCasts() {
  return (
    <div
      className="m-bg-card m-border rounded-[var(--m-radius)] px-6 py-10 text-center"
      style={{ borderStyle: 'dashed' }}
    >
      <p className="m-display text-xl">キャスト準備中です</p>
      <p className="m-sub mt-2 text-sm">もうすぐ紹介できるので、また遊びに来てくださいね。</p>
    </div>
  );
}

export function SectionTitle({ en, ja, action }: { en: string; ja: string; action?: ReactNode }) {
  return (
    <div className="mb-4 flex items-end justify-between gap-3">
      <h2 className="flex items-baseline gap-3">
        <span className="m-display text-2xl sm:text-3xl">{ja}</span>
        <span className="text-xs font-bold tracking-[0.2em] text-[#c2185b]">{en}</span>
      </h2>
      {action}
    </div>
  );
}
