import { cookies } from 'next/headers';
import { notFound } from 'next/navigation';
import { loadTemplatePage, type StoreSitePageName } from '../templates/loadTemplate';

interface StoreSitePageProps {
  page: StoreSitePageName;
  pageProps?: Record<string, unknown>;
}

/**
 * 公開站ページの共通殻 (Server Component)。
 *
 * proxy（middleware）が設定した x-mw-role cookie で store ドメインのみ許可し、
 * x-mw-store-template cookie の模版キーで templates/<key>/<page> を dispatch する。
 * app 配下の各公開ルートはこの殻を呼ぶだけの薄殻にする。
 */
export default async function StoreSitePage({ page, pageProps = {} }: StoreSitePageProps) {
  const cookieStore = await cookies();
  if (cookieStore.get('x-mw-role')?.value !== 'store') {
    notFound();
  }

  const templateKey = cookieStore.get('x-mw-store-template')?.value || 'default';
  const PageComponent = await loadTemplatePage(templateKey, page);
  return <PageComponent {...pageProps} />;
}
