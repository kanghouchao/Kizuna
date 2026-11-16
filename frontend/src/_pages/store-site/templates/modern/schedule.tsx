import { ClockIcon } from 'lucide-react';
import Link from 'next/link';
import type { PublicShift, StoreSitePageProps } from '../../model/types';
import { CastPhoto } from './castParts';
import Frame from './Frame';
import { Notice, PageHead, ReserveBand } from './pageParts';

// 日をまたいで 00:00 に終わる出勤は、続きの時間帯として 24:00 と書く
function timeRange(shift: PublicShift): string {
  const start = (shift.start_time ?? '').slice(0, 5);
  const end = (shift.end_time ?? '').slice(0, 5);
  return `${start}–${end === '00:00' ? '24:00' : end}`;
}

/** 出勤表ページ（modern テンプレート）。本日の出勤を、時間帯の札を付けた角丸カードで受け取った順に並べる。 */
export default function ModernSchedulePage({
  store,
  siteConfig,
  shifts,
}: StoreSitePageProps<'schedule'>) {
  return (
    <Frame store={store} siteConfig={siteConfig}>
      <PageHead
        en="SCHEDULE"
        title="出勤表"
        // 一人が同じ日に複数の出勤を持つことがあるので、人数は出勤の件数ではなくキャストで数える
        chip={shifts.length > 0 ? `本日 ${new Set(shifts.map(s => s.cast_id)).size}人` : undefined}
      />
      {shifts.length === 0 ? (
        <Notice
          title="本日の出勤情報はありません"
          note="出勤情報は準備中です。最新の情報はお電話でお問い合わせください。"
        />
      ) : (
        <>
          <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4 md:grid-cols-4 xl:grid-cols-5">
            {shifts.map((shift, i) => (
              <li key={`${shift.cast_id}-${shift.start_time}`} className="min-w-0">
                <Link
                  href={`/casts/${shift.cast_id ?? ''}`}
                  className="m-bg-card m-sticker m-lift block h-full overflow-hidden rounded-[20px]"
                >
                  <span className="relative block aspect-[4/5] overflow-hidden border-b-2 border-[var(--m-ink)]">
                    <CastPhoto
                      cast={{ name: shift.cast_name, photo_url: shift.cast_photo_url }}
                      index={i}
                      sizes="(min-width: 1280px) 240px, (min-width: 768px) 25vw, (min-width: 640px) 33vw, 50vw"
                      textSize="text-5xl"
                    />
                  </span>
                  <div className="px-3 pt-2 pb-3">
                    <h2 className="m-display text-base sm:text-lg">{shift.cast_name}</h2>
                    <p className="m-bg-yellow mt-1.5 inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-bold sm:text-sm">
                      <ClockIcon className="size-3.5 shrink-0" aria-hidden />
                      <span>{timeRange(shift)}</span>
                    </p>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
          <ReserveBand lead="会いたいキャストが決まったら" />
        </>
      )}
    </Frame>
  );
}
