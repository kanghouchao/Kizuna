import { storefrontService } from '../../api/storefront';
import type { StoreSitePageProps } from '../../model/types';
import AgeGate from '../_sections/AgeVerification';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import NewsSection, { ARTICLES_PER_PAGE } from '../_sections/NewsSection';
import PageHero from '../_sections/PageHero';
import './theme.css';

/** お知らせ一覧ページ（classic テンプレート）。 */
export default async function ClassicNewsPage({
  store,
  page,
}: StoreSitePageProps & { page: number }) {
  const storeName = store.name;
  const [siteConfig, articles] = await Promise.all([
    storefrontService.fetchSiteConfig(store.id),
    storefrontService.fetchArticles(store.id, page, ARTICLES_PER_PAGE),
  ]);

  return (
    <div
      className="storefront-classic min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={storeName} />
      <Header storeName={storeName} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title="お知らせ" />
        <NewsSection articles={articles} page={page} />
      </main>
      <Footer
        storeName={storeName}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
