import Image from 'next/image';
import Link from 'next/link';
import { PublicShift } from '../../model/types';

interface ScheduleSectionProps {
  shifts: PublicShift[];
}

/** "HH:mm(:ss)" → "HH:mm" 表示。 */
const formatTime = (t: string) => t.slice(0, 5);

/** 終了時刻の表示。00:00 終了は跨夜の連続表記として 24:00 と表示する。 */
const formatEndTime = (t: string) => {
  const hm = t.slice(0, 5);
  return hm === '00:00' ? '24:00' : hm;
};

/**
 * 本日の出勤表セクション（Server Component）。
 * シフトが無ければ既存の空状態文言を表示し、あれば CastGrid のカード意匠を踏襲した
 * グリッドで時間帯付きに表示する。
 */
export default function ScheduleSection({ shifts }: ScheduleSectionProps) {
  if (shifts.length === 0) {
    return (
      <section
        className="py-16 md:py-24 px-5 sm:px-6"
        style={{ background: 'var(--storefront-bg)' }}
      >
        <div className="max-w-2xl mx-auto text-center">
          <p
            className="text-[color-mix(in_srgb,var(--storefront-fg)_70%,transparent)] text-base md:text-lg font-light tracking-[0.15em] mb-4"
            style={{ fontFamily: 'var(--storefront-font-display)' }}
          >
            本日の出勤情報はありません
          </p>
          <p
            className="text-[color-mix(in_srgb,var(--storefront-fg)_30%,transparent)] text-xs md:text-sm leading-relaxed tracking-wider"
            style={{ fontFamily: 'var(--storefront-font-display)' }}
          >
            出勤情報は準備中です。最新の情報はお電話でお問い合わせください。
          </p>
        </div>
      </section>
    );
  }

  return (
    <section className="py-12 md:py-20 px-5 sm:px-6" style={{ background: 'var(--storefront-bg)' }}>
      <div className="max-w-7xl mx-auto">
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4 md:gap-6">
          {shifts.map(shift => (
            <Link
              key={`${shift.cast_id}-${shift.start_time}`}
              href={`/casts/${shift.cast_id}`}
              className="group/card block"
            >
              <div
                className="aspect-3/4 relative overflow-hidden mb-2 md:mb-3"
                style={{
                  background: 'var(--storefront-surface-3)',
                  boxShadow:
                    '0 0 0 1px color-mix(in srgb, var(--storefront-accent) 10%, transparent)',
                }}
              >
                {shift.cast_photo_url ? (
                  <Image
                    src={shift.cast_photo_url}
                    alt={shift.cast_name}
                    fill
                    className="object-cover transition-transform duration-700 group-hover/card:scale-105"
                    sizes="(max-width: 768px) 50vw, (max-width: 1024px) 33vw, 25vw"
                  />
                ) : (
                  <div className="absolute inset-0 flex items-center justify-center">
                    <svg
                      className="w-10 h-10 md:w-14 md:h-14"
                      fill="color-mix(in srgb, var(--storefront-accent) 15%, transparent)"
                      viewBox="0 0 24 24"
                    >
                      <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
                    </svg>
                  </div>
                )}
                <div
                  className="absolute inset-0 opacity-0 group-hover/card:opacity-100 transition-opacity duration-300"
                  style={{
                    boxShadow:
                      'inset 0 0 0 1px color-mix(in srgb, var(--storefront-accent) 40%, transparent)',
                  }}
                />
              </div>
              <div className="text-center px-1">
                <h3
                  className="text-[color-mix(in_srgb,var(--storefront-fg)_85%,transparent)] text-xs md:text-sm font-light tracking-wider mb-1"
                  style={{ fontFamily: 'var(--storefront-font-display)' }}
                >
                  {shift.cast_name}
                </h3>
                <p className="text-[10px] md:text-xs tracking-wider text-[color-mix(in_srgb,var(--storefront-accent)_70%,transparent)]">
                  {formatTime(shift.start_time)}–{formatEndTime(shift.end_time)}
                </p>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}
