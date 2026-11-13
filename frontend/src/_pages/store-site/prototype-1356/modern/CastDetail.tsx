import { ArrowLeftIcon, CalendarCheckIcon } from 'lucide-react';
import Link from 'next/link';
import { castProfileRows, protoHref, type ProtoPageProps } from '../contract';
import { AvatarRow, CastPhoto, SectionTitle, Shell, toneAt } from './parts';

export default function CastDetail(props: ProtoPageProps) {
  const { cast, casts, q } = props;
  if (!cast) return null;
  const rows = castProfileRows(cast);
  const others = casts.filter(c => c.id !== cast.id).slice(0, 16);

  return (
    <Shell props={props}>
      <Link
        href={protoHref('/casts', q)}
        className="m-bg-card m-border inline-flex items-center gap-1.5 rounded-full px-4 py-1.5 text-sm font-bold"
      >
        <ArrowLeftIcon className="size-4" aria-hidden />
        キャスト一覧
      </Link>

      <div>
      <div className="mt-5 grid gap-8 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)] lg:gap-12">
        <div className="m-pop relative mx-auto w-full max-w-md lg:max-w-none">
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
            />
          </div>
        </div>

        <div className="flex flex-col">
          <p className="text-xs font-bold tracking-[0.2em] text-[#c2185b]">PROFILE</p>
          <h1 className="m-display m-pop m-pop-2 mt-1 text-[clamp(2.75rem,10vw,5rem)] break-words">
            {cast.name}
          </h1>

          {rows.length > 0 && (
            <ul className="mt-5 flex flex-wrap gap-2.5">
              {rows.map((r, i) => (
                <li
                  key={`${r.label}-${i}`}
                  className={`${toneAt(i + 1)} m-sticker m-tilt min-w-[5.5rem] rounded-[18px] px-4 py-2`}
                >
                  <span className="block text-[11px] font-bold">{r.label}</span>
                  <span className="m-display block text-xl">{r.value}</span>
                </li>
              ))}
            </ul>
          )}

          {cast.introduction && (
            <div className="m-bg-card mt-6 rounded-[var(--m-radius)] border-2 border-[#efe4d6] p-6">
              <p className="m-display text-base">ひとこと</p>
              <p className="mt-2 text-[15px] leading-8 whitespace-pre-line">{cast.introduction}</p>
            </div>
          )}

        </div>
      </div>

      {/* 写真と本文を含む範囲で画面下に貼り付く。スマホではタブバーの上に浮かせる */}
      <div className="sticky bottom-28 z-40 mt-6 lg:bottom-6 lg:ml-auto lg:w-[calc((100%-3rem)*6/11)]">
        <Link
          href={protoHref('/reservation', q)}
          className="m-bg-pink m-sticker m-lift flex items-center justify-center gap-2 rounded-full px-6 py-4 text-lg font-bold"
        >
          <CalendarCheckIcon className="size-5" aria-hidden />
          {cast.name ? `${cast.name}さんを予約する` : '予約する'}
        </Link>
      </div>
      </div>

      {others.length > 0 && (
        <section className="mt-14">
          <SectionTitle en="OTHER CAST" ja="ほかのキャスト" />
          <AvatarRow casts={others} q={q} label="ほかのキャスト" />
        </section>
      )}
    </Shell>
  );
}
