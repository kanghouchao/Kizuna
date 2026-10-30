import { notFound } from 'next/navigation';
import { resolveHost } from '@/shared/api/index.server';
import { storefrontService } from '../api/storefront';
import { loadTemplatePage, type StoreSitePageName } from '../templates/loadTemplate';

interface StoreSitePageProps {
  page: StoreSitePageName;
  pageProps?: Record<string, unknown>;
}

/**
 * 公開站ページの共通殻 (Server Component)。
 *
 * リクエストの Host が店舗ドメインのときだけ描き、店舗の公開設定の模版キーで templates/<key>/<page> を
 * dispatch する。解決した店舗は props で模版へ渡す。app 配下の各公開ルートはこの殻を呼ぶだけの薄殻にする。
 */
export default async function StoreSitePage({ page, pageProps = {} }: StoreSitePageProps) {
  const host = await resolveHost();
  if (host.kind !== 'store') {
    notFound();
  }

  const { template_key } = await storefrontService.fetchSiteConfig(host.store.id);
  const PageComponent = await loadTemplatePage(template_key || 'default', page);
  return <PageComponent {...pageProps} store={host.store} />;
}
