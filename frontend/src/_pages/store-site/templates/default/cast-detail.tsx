import Link from 'next/link';
import type { Cast, StoreSitePageProps } from '../../model/types';
import CastPortrait from './CastPortrait';
import Frame from './Frame';
import MemberReservationLink from './MemberReservationLink';

/** 値の無い項目は行ごと出さない。custom_fields はサーバが公開・値ありのものだけを表示順に返す。 */
function profileRows(cast: Cast): { label: string; value: string }[] {
  const rows: { label: string; value: string }[] = [];
  if (cast.age) rows.push({ label: '年齢', value: `${cast.age}歳` });
  if (cast.height) rows.push({ label: '身長', value: `${cast.height}cm` });
  if (cast.bust) rows.push({ label: 'バスト', value: `${cast.bust}` });
  if (cast.waist) rows.push({ label: 'ウエスト', value: `${cast.waist}` });
  if (cast.hip) rows.push({ label: 'ヒップ', value: `${cast.hip}` });
  cast.custom_fields?.forEach(field =>
    rows.push({ label: field.label ?? '', value: field.value ?? '' })
  );
  return rows;
}

/** キャスト詳細ページ（default テンプレート）。 */
export default function DefaultCastDetailPage({
  store,
  siteConfig,
  cast,
}: StoreSitePageProps<'cast-detail'>) {
  const rows = profileRows(cast);

  return (
    <Frame store={store} siteConfig={siteConfig}>
      <article className="sfd-detail">
        <div className={`sfd-detail-photo ${cast.photo_url ? '' : 'is-empty'}`}>
          <CastPortrait cast={cast} preload sizes="(min-width: 1024px) 50vw, 100vw" />
        </div>

        <div className="sfd-detail-body">
          <Link href="/casts" className="sfd-textlink sfd-back">
            ← キャスト一覧
          </Link>
          <p className="sfd-label">Cast</p>
          <h1 className="sfd-detail-name">{cast.name ?? '—'}</h1>

          {rows.length > 0 && (
            <dl className="sfd-rows">
              {rows.map((row, i) => (
                <div key={i} className="sfd-row">
                  <dt>{row.label}</dt>
                  <dd>{row.value}</dd>
                </div>
              ))}
            </dl>
          )}

          {cast.introduction && (
            <section className="sfd-intro">
              <p className="sfd-label">Message</p>
              <p className="sfd-intro-text">{cast.introduction}</p>
            </section>
          )}

          <div className="sfd-detail-cta">
            <Link href="/reservation" className="sfd-btn">
              ご予約へ進む
            </Link>
            <MemberReservationLink className="sfd-textlink">
              会員の方は WEB予約
            </MemberReservationLink>
            <Link href="/casts" className="sfd-textlink">
              ほかのキャストを見る
            </Link>
          </div>
        </div>
      </article>
    </Frame>
  );
}
