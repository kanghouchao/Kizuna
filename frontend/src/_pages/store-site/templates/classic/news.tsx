import type { StoreSitePageProps } from '../../model/types';
import AgeGate from '../_sections/AgeVerification';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import NewsSection from '../_sections/NewsSection';
import PageHero from '../_sections/PageHero';
import './theme.css';

/** お知らせ一覧ページ（classic テンプレート）。 */
export default function ClassicNewsPage({
  store,
  siteConfig,
  articles,
  page,
}: StoreSitePageProps<'news'>) {
  return (
    <div
      className="storefront-classic min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={store.name} />
      <Header storeName={store.name} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title="お知らせ" />
        <NewsSection articles={articles} page={page} />
      </main>
      <Footer
        storeName={store.name}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
