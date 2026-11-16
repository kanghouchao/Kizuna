import type { StoreSitePageProps } from '../../model/types';
import { CastGrid, EmptyCasts } from './castParts';
import Frame from './Frame';
import { PageHead } from './pageParts';

/** キャスト一覧ページ（modern テンプレート）。角丸カードの密な格子。 */
export default function ModernCastsPage({ store, siteConfig, casts }: StoreSitePageProps<'casts'>) {
  return (
    <Frame store={store} siteConfig={siteConfig}>
      <PageHead
        en="CAST"
        title="キャスト一覧"
        chip={casts.length > 0 ? `在籍 ${casts.length}人` : undefined}
      />
      {casts.length > 0 ? <CastGrid casts={casts} /> : <EmptyCasts />}
    </Frame>
  );
}
