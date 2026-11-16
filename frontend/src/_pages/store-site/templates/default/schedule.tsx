import Link from 'next/link';
import type { StoreSitePageProps } from '../../model/types';
import CastPortrait, { castNo } from './CastPortrait';
import { shiftHours } from './format';
import Frame from './Frame';
import { Notice } from './Paging';

/** 出勤表ページ（default テンプレート）。受け取った順のまま並べる。 */
export default function DefaultSchedulePage({
  store,
  siteConfig,
  shifts,
}: StoreSitePageProps<'schedule'>) {
  return (
    <Frame store={store} siteConfig={siteConfig}>
      <header className="sfd-pagehead">
        <p className="sfd-label">Schedule</p>
        <h1 className="sfd-h1">出勤表</h1>
        {shifts.length > 0 && <p className="sfd-count">本日 {shifts.length} 件</p>}
      </header>

      <div className="sfd-page">
        {shifts.length === 0 ? (
          <Notice
            message="本日の出勤情報はありません"
            note="出勤情報は準備中です。最新の情報はお電話でお問い合わせください。"
          />
        ) : (
          <ol className="sfd-shifts">
            {shifts.map((shift, i) => (
              <li key={`${shift.cast_id}-${shift.start_time}`}>
                <Link href={`/casts/${shift.cast_id}`} className="sfd-shift">
                  <span className="sfd-no" aria-hidden="true">
                    {castNo(i)}
                  </span>
                  <span className="sfd-shift-photo">
                    <CastPortrait
                      cast={{ name: shift.cast_name, photo_url: shift.cast_photo_url }}
                      sizes="96px"
                    />
                  </span>
                  <h2 className="sfd-shift-name">{shift.cast_name ?? '—'}</h2>
                  <p className="sfd-shift-hours">{shiftHours(shift.start_time, shift.end_time)}</p>
                </Link>
              </li>
            ))}
          </ol>
        )}
      </div>
    </Frame>
  );
}
