import Link from 'next/link';
import type { StoreSitePageProps } from '../../model/types';
import { castProfileRows } from './castProfile';
import Frame from './Frame';
import PageSection from './PageSection';
import Portrait from './Portrait';
import Tally from './Tally';

/** キャスト一覧ページ（classic テンプレート）。カードではなく目録の行で並べる。 */
export default function ClassicCastsPage({
  store,
  siteConfig,
  casts,
}: StoreSitePageProps<'casts'>) {
  return (
    <Frame store={store} siteConfig={siteConfig} current="/casts">
      <PageSection
        title="キャスト一覧"
        note={casts.length > 0 && <Tally prefix="全" count={casts.length} />}
      >
        {casts.length === 0 ? (
          <p className="c-quiet">キャスト情報は準備中です</p>
        ) : (
          <ol className="c-rows">
            {casts.map((cast, index) => {
              const spec = castProfileRows(cast)
                .slice(0, 5)
                .map(row => `${row.label} ${row.value}`)
                .join('　');
              const intro = cast.introduction?.split(/\r?\n/).find(line => line.trim());
              return (
                <li key={cast.id}>
                  <Link href={`/casts/${cast.id}`} className="c-row">
                    <Portrait url={cast.photo_url} name={cast.name} sizes="78px" />
                    <h2 className="c-row-name c-v c-name c-display">{cast.name}</h2>
                    <div className="c-row-info">
                      <span className="c-row-no" aria-hidden="true">
                        {String(index + 1).padStart(2, '0')}
                      </span>
                      {spec && <p className="c-row-spec">{spec}</p>}
                      {intro && <p className="c-row-intro">{intro}</p>}
                    </div>
                    <span className="c-row-go c-v" aria-hidden="true">
                      詳しく
                    </span>
                  </Link>
                </li>
              );
            })}
          </ol>
        )}
      </PageSection>
    </Frame>
  );
}
