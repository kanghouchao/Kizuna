import Image from 'next/image';
import Link from 'next/link';
import type { ReactNode } from 'react';
import type { Cast } from '../../model/types';

const TONES = [
  'm-bg-pink',
  'm-bg-yellow',
  'm-bg-mint',
  'm-bg-sky',
  'm-bg-lilac',
  'm-bg-peach',
] as const;

export function toneAt(index: number): string {
  return TONES[index % TONES.length];
}

/**
 * 写真の無いキャストは、代わりの画像ではなく名前の頭文字を色面に置く。
 * 名前を隣に文字で出すカードでは labelled を外し、リンクの名前が二重にならないようにする。
 */
export function CastPhoto({
  cast,
  index,
  sizes,
  textSize,
  labelled,
  preload,
}: {
  cast: Cast;
  index: number;
  sizes: string;
  textSize: string;
  labelled?: boolean;
  preload?: boolean;
}) {
  if (cast.photo_url) {
    return (
      <Image
        src={cast.photo_url}
        alt={labelled ? (cast.name ?? 'キャスト') : ''}
        fill
        sizes={sizes}
        preload={preload}
        className="object-cover"
      />
    );
  }
  return (
    <span
      aria-hidden
      className={`${toneAt(index)} m-display absolute inset-0 grid place-items-center ${textSize}`}
    >
      {cast.name ? Array.from(cast.name)[0] : ''}
    </span>
  );
}

const castHref = (cast: Cast) => `/casts/${cast.id ?? ''}`;

export function CastGrid({ casts }: { casts: Cast[] }) {
  return (
    <ul className="grid grid-cols-3 gap-2.5 sm:grid-cols-4 sm:gap-4 md:grid-cols-5 xl:grid-cols-6">
      {casts.map((cast, i) => (
        <li key={cast.id ?? i} className="min-w-0">
          <Link
            href={castHref(cast)}
            className="m-bg-card m-sticker m-lift block h-full overflow-hidden rounded-[20px]"
          >
            <span className="relative block aspect-[4/5] overflow-hidden border-b-2 border-[var(--m-ink)]">
              <CastPhoto
                cast={cast}
                index={i}
                sizes="(min-width: 1280px) 200px, (min-width: 768px) 20vw, (min-width: 640px) 25vw, 33vw"
                textSize="text-4xl"
              />
            </span>
            <div className="px-2 pt-1.5 pb-2">
              <h2 className="m-display truncate text-sm sm:text-base">{cast.name}</h2>
              {(cast.age || cast.height) && (
                <span className="mt-1 flex flex-wrap gap-1 text-[10px] leading-5 font-bold sm:text-xs">
                  {cast.age ? (
                    <span className="m-bg-yellow rounded-full px-1.5">{cast.age}歳</span>
                  ) : null}
                  {cast.height ? (
                    <span className="m-bg-sky rounded-full px-1.5">{cast.height}cm</span>
                  ) : null}
                </span>
              )}
            </div>
          </Link>
        </li>
      ))}
    </ul>
  );
}

/** 丸い写真の横送り。scroll-snap だけで送り、端は本文の余白の外まで流す。 */
export function CastRow({ casts, label }: { casts: Cast[]; label: string }) {
  return (
    <ul className="m-snap -mx-4" aria-label={label}>
      {casts.map((cast, i) => (
        <li key={cast.id ?? i} className="w-20 sm:w-24">
          <Link href={castHref(cast)} className="block text-center">
            <span className="m-avatar-ring m-squircle block p-[3px]">
              <span className="m-squircle relative block aspect-square overflow-hidden border-2 border-white">
                <CastPhoto cast={cast} index={i} sizes="96px" textSize="text-2xl" />
              </span>
            </span>
            <h3 className="mt-1.5 truncate text-xs font-bold">{cast.name}</h3>
          </Link>
        </li>
      ))}
    </ul>
  );
}

export function EmptyCasts() {
  return (
    <div className="m-bg-card rounded-[var(--m-radius)] border-2 border-dashed border-[var(--m-ink)] px-6 py-10 text-center">
      <p className="m-display text-xl">キャスト情報は準備中です</p>
      <p className="m-sub mt-2 text-sm">もうすぐ紹介できるので、また遊びに来てくださいね。</p>
    </div>
  );
}

export function SectionTitle({ en, ja, action }: { en: string; ja: string; action?: ReactNode }) {
  return (
    <div className="mb-4 flex items-end justify-between gap-3">
      <h2 className="flex flex-wrap items-baseline gap-x-3">
        <span className="m-display text-2xl sm:text-3xl">{ja}</span>
        <span className="m-label" aria-hidden>
          {en}
        </span>
      </h2>
      {action}
    </div>
  );
}
