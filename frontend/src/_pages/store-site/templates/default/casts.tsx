import Link from 'next/link';
import type { StoreSitePageProps } from '../../model/types';
import CastPortrait, { castNo } from './CastPortrait';
import Frame from './Frame';

/** キャスト一覧ページ（default テンプレート）。 */
export default function DefaultCastsPage({
  store,
  siteConfig,
  casts,
}: StoreSitePageProps<'casts'>) {
  return (
    <Frame store={store} siteConfig={siteConfig}>
      <header className="sfd-pagehead">
        <p className="sfd-label">Cast</p>
        <h1 className="sfd-h1">キャスト一覧</h1>
        {casts.length > 0 && <p className="sfd-count">全 {casts.length} 名</p>}
      </header>

      {casts.length === 0 ? (
        <p className="sfd-quiet sfd-quiet-page">ただいまご紹介できるキャストはおりません。</p>
      ) : (
        <ul className="sfd-grid">
          {casts.map((cast, i) => (
            <li key={cast.id ?? i}>
              <Link href={`/casts/${cast.id}`} className="sfd-tile">
                <CastPortrait
                  cast={cast}
                  sizes="(min-width: 1280px) 25vw, (min-width: 768px) 33vw, 50vw"
                />
                <span className="sfd-tile-no" aria-hidden="true">
                  {castNo(i)}
                </span>
                <h2 className="sfd-tile-name">{cast.name ?? '—'}</h2>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Frame>
  );
}
