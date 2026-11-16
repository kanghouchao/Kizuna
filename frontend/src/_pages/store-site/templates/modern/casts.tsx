import type { StoreSitePageProps } from '../../model/types';
import { CastGrid, EmptyCasts } from './castParts';
import Frame from './Frame';

/** キャスト一覧ページ（modern テンプレート）。角丸カードの密な格子。 */
export default function ModernCastsPage({ store, siteConfig, casts }: StoreSitePageProps<'casts'>) {
  return (
    <Frame store={store} siteConfig={siteConfig}>
      <header className="m-pop flex flex-wrap items-end justify-between gap-3 pt-2 pb-6">
        <div>
          <p className="m-label" aria-hidden>
            CAST
          </p>
          <h1 className="m-display mt-1 text-4xl sm:text-5xl">キャスト一覧</h1>
        </div>
        {casts.length > 0 && (
          <p className="m-bg-yellow m-border rounded-full px-4 py-1 text-sm font-bold">
            在籍 {casts.length}人
          </p>
        )}
      </header>
      {casts.length > 0 ? <CastGrid casts={casts} /> : <EmptyCasts />}
    </Frame>
  );
}
