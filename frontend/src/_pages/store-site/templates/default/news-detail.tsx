import { notFound } from 'next/navigation';
import { storefrontService } from '../../api/storefront';
import type { StoreSitePageProps } from '../../model/types';
import AgeGate from '../_sections/AgeVerification';
import ArticleSection from '../_sections/ArticleSection';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import PageHero from '../_sections/PageHero';
import './theme.css';

/** 記事ページ（default テンプレート）。存在しない・掲出外の記事は 404 にする。 */
export default async function DefaultNewsDetailPage({
  store,
  articleId,
}: StoreSitePageProps & { articleId: string }) {
  const storeName = store.name;
  const [article, siteConfig] = await Promise.all([
    storefrontService.fetchArticle(store.id, articleId),
    storefrontService.fetchSiteConfig(store.id),
  ]);

  if (!article) {
    notFound();
  }

  return (
    <div
      className="storefront-default min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={storeName} />
      <Header storeName={storeName} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title={article.title} />
        <ArticleSection article={article} />
      </main>
      <Footer
        storeName={storeName}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
