import { ArrowLeftIcon, CalendarCheckIcon } from 'lucide-react';
import Link from 'next/link';
import type { Cast, StoreSitePageProps } from '../../model/types';
import { CastPhoto, toneAt } from './castParts';
import Frame from './Frame';
import { RESERVATION_HREF } from './navLook';

// 値の無い項目はタイルごと出さない。custom_fields はサーバが公開する項目だけを表示順に返す
function profileOf(cast: Cast): { label: string; value: string }[] {
  const rows: { label: string; value: string }[] = [];
  if (cast.age) rows.push({ label: '年齢', value: `${cast.age}歳` });
  if (cast.height) rows.push({ label: '身長', value: `${cast.height}cm` });
  if (cast.bust) rows.push({ label: 'バスト', value: `${cast.bust}` });
  if (cast.waist) rows.push({ label: 'ウエスト', value: `${cast.waist}` });
  if (cast.hip) rows.push({ label: 'ヒップ', value: `${cast.hip}` });
  cast.custom_fields?.forEach(f => rows.push({ label: f.label ?? '', value: f.value ?? '' }));
  return rows;
}

/** キャスト詳細ページ（modern テンプレート）。大きな角丸の写真、数値のタイル、追従する予約のボタン。 */
export default function ModernCastDetailPage({
  store,
  siteConfig,
  cast,
}: StoreSitePageProps<'cast-detail'>) {
  const profile = profileOf(cast);

  return (
    <Frame store={store} siteConfig={siteConfig}>
      <Link
        href="/casts"
        className="m-bg-card m-border inline-flex items-center gap-1.5 rounded-full px-4 py-1.5 text-sm font-bold"
      >
        <ArrowLeftIcon className="size-4" aria-hidden />
        キャスト一覧へ
      </Link>

      {/* 写真と本文を合わせた範囲で予約のボタンを画面の下端に追従させる */}
      <div>
        <div className="mt-5 grid gap-8 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)] lg:gap-12">
          <div className="m-pop relative mx-auto mb-3 w-full max-w-md pr-3 lg:max-w-none">
            <div className="relative">
              <span
                aria-hidden
                className="m-bg-pink absolute inset-0 translate-x-3 translate-y-3 rotate-3 rounded-[36px]"
              />
              <div className="m-border relative aspect-[4/5] overflow-hidden rounded-[36px]">
                <CastPhoto
                  cast={cast}
                  index={0}
                  sizes="(min-width: 1024px) 520px, 100vw"
                  textSize="text-[8rem]"
                  labelled
                  preload
                />
              </div>
            </div>
          </div>

          <div className="flex min-w-0 flex-col">
            <p className="m-label" aria-hidden>
              PROFILE
            </p>
            <h1 className="m-display m-pop m-pop-2 mt-1 text-[clamp(2rem,9vw,5rem)]">
              {cast.name}
            </h1>

            {profile.length > 0 && (
              <dl className="mt-5 flex flex-wrap gap-2.5">
                {profile.map(({ label, value }, i) => (
                  <div
                    key={`${label}-${i}`}
                    className={`${toneAt(i + 1)} m-sticker max-w-full min-w-[5.5rem] rounded-[18px] px-4 py-2`}
                  >
                    <dt className="text-[11px] font-bold">{label}</dt>
                    <dd className="m-display text-xl">{value}</dd>
                  </div>
                ))}
              </dl>
            )}

            {cast.introduction && (
              <div className="m-bg-card mt-6 rounded-[var(--m-radius)] border-2 border-[var(--m-line)] p-6">
                <h2 className="m-display text-base">ひとこと</h2>
                <p className="mt-2 text-[15px] leading-8 whitespace-pre-line">
                  {cast.introduction}
                </p>
              </div>
            )}
          </div>
        </div>
        <div className="sticky bottom-[var(--m-tabbar-space)] z-40 mt-6 lg:bottom-6 lg:ml-auto lg:w-[calc((100%-3rem)*6/11)]">
          <Link
            href={RESERVATION_HREF}
            className="m-bg-pink m-sticker m-lift flex items-center justify-center gap-2 rounded-full px-6 py-4 text-lg font-bold"
          >
            <CalendarCheckIcon className="size-5 shrink-0" aria-hidden />
            <span className="min-w-0 truncate">
              {cast.name ? `${cast.name}さんを予約する` : '予約する'}
            </span>
          </Link>
        </div>
      </div>
    </Frame>
  );
}
