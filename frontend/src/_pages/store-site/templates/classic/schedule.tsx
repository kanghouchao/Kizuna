import Link from 'next/link';
import type { StoreSitePageProps } from '../../model/types';
import { shiftHours } from './format';
import Frame from './Frame';
import Notice from './Notice';
import PageSection from './PageSection';
import Portrait from './Portrait';
import Tally from './Tally';

/** 出勤表ページ（classic テンプレート）。キャスト一覧と同じ目録の行に、時間帯を大きく添える。 */
export default function ClassicSchedulePage({
  store,
  siteConfig,
  shifts,
}: StoreSitePageProps<'schedule'>) {
  return (
    <Frame store={store} siteConfig={siteConfig} current="/schedule">
      <PageSection
        title="出勤表"
        note={shifts.length > 0 && <Tally prefix="本日" count={shifts.length} />}
      >
        {shifts.length === 0 ? (
          <Notice
            message="本日の出勤情報はありません"
            detail="出勤情報は準備中です。最新の情報はお電話でお問い合わせください。"
          />
        ) : (
          <ul className="c-rows">
            {shifts.map(shift => (
              <li key={`${shift.cast_id}-${shift.start_time}`}>
                <Link href={`/casts/${shift.cast_id}`} className="c-row">
                  <Portrait url={shift.cast_photo_url} name={shift.cast_name} sizes="78px" />
                  <h2 className="c-row-name c-v c-name c-display">{shift.cast_name}</h2>
                  <div className="c-row-info">
                    <span className="c-row-no">出勤</span>
                    <p className="c-shift-hours c-display">
                      {shiftHours(shift.start_time, shift.end_time)}
                    </p>
                  </div>
                  <span className="c-row-go c-v" aria-hidden="true">
                    詳しく
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </PageSection>
    </Frame>
  );
}
