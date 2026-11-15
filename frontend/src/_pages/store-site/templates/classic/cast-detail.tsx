import { ChevronLeftIcon } from 'lucide-react';
import Link from 'next/link';
import type { StoreSitePageProps } from '../../model/types';
import { castProfileRows } from './castProfile';
import Frame from './Frame';
import MemberReservationLink from './MemberReservationLink';
import { nameScaleStyle } from './nameScale';
import Portrait from './Portrait';

/** キャスト詳細ページ（classic テンプレート）。冊子の頁の形で、名前を縦書きで大きく置く。 */
export default function ClassicCastDetailPage({
  store,
  siteConfig,
  cast,
}: StoreSitePageProps<'cast-detail'>) {
  const rows = castProfileRows(cast);

  return (
    <Frame store={store} siteConfig={siteConfig} current="/casts">
      <nav className="c-crumb" aria-label="現在地">
        <Link href="/">TOP</Link>
        <span aria-hidden="true">／</span>
        <Link href="/casts">キャスト一覧</Link>
        <span aria-hidden="true">／</span>
        <span aria-current="page">{cast.name}</span>
      </nav>

      <article className="c-book">
        <header className="c-book-name c-v">
          <p>キャスト</p>
          <h1 className="c-name c-display" style={nameScaleStyle(cast.name)}>
            {cast.name}
          </h1>
        </header>

        <div className="c-book-photo">
          <Portrait
            url={cast.photo_url}
            name={cast.name}
            sizes="(min-width: 1180px) 340px, (min-width: 768px) 300px, 70vw"
            preload
          />
        </div>

        {rows.length > 0 && (
          <div className="c-book-spec">
            <table className="c-spec">
              <caption>プロフィール</caption>
              <tbody>
                {rows.map((row, index) => (
                  <tr key={index}>
                    <th scope="row">{row.label}</th>
                    <td>{row.value}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {cast.introduction && (
          <section className="c-book-intro">
            <h2>紹介</h2>
            <p className="c-prose">{cast.introduction}</p>
          </section>
        )}
      </article>

      <div className="c-book-foot">
        <Link href="/casts" className="c-textlink">
          <ChevronLeftIcon size={14} strokeWidth={1.25} aria-hidden="true" />
          キャスト一覧へ戻る
        </Link>
        <div className="c-cta">
          <p className="c-quiet">ご指名は、ご予約より承ります。</p>
          <Link href="/reservation" className="c-seal c-seal--lg">
            <span className="c-v">ご予約</span>
          </Link>
          <div className="c-cta-links">
            <MemberReservationLink className="c-textlink">会員予約</MemberReservationLink>
          </div>
        </div>
      </div>
    </Frame>
  );
}
