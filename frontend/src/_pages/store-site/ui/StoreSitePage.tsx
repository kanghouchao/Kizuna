import { notFound } from 'next/navigation';
import { resolveHost } from '@/shared/api/index.server';
import { loadPageData } from '../api/pageData';
import { storefrontService } from '../api/storefront';
import type { StoreSiteRoute } from '../model/types';
import { loadTemplatePage } from '../templates/loadTemplate';

/**
 * 公開サイトのページの共通の殻（Server Component）。
 *
 * リクエストの Host が店舗ドメインのときだけ描く。店舗・サイト設定・ページのデータを取得し、店舗の
 * template_key が指すテンプレートのページへ props で渡す。最も外側の要素は、実際に描いたテンプレートの
 * 標識を data-storefront-template に出す。app 配下の各公開ルートはこの殻を呼ぶだけにする。
 */
export default async function StoreSitePage(route: StoreSiteRoute) {
  const host = await resolveHost();
  if (host.kind !== 'store') {
    notFound();
  }
  const { store } = host;

  const [siteConfig, data] = await Promise.all([
    storefrontService.fetchSiteConfig(store.id),
    loadPageData(route, store.id),
  ]);
  const { templateKey, Page } = await loadTemplatePage(
    siteConfig.template_key || 'default',
    route.page
  );

  return (
    <div data-storefront-template={templateKey}>
      <Page {...data} store={store} siteConfig={siteConfig} />
    </div>
  );
}
