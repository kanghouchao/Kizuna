import { storefrontService } from '../../api/storefront';
import type { StoreSitePageProps } from '../../model/types';
import AgeGate from '../_sections/AgeVerification';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import PageHero from '../_sections/PageHero';
import ReviewsSection, { REVIEWS_PER_PAGE } from '../_sections/ReviewsSection';
import './theme.css';

/** 口コミページ（classic 模版）。 */
export default async function ClassicReviewsPage({
  store,
  page,
}: StoreSitePageProps & { page: number }) {
  const storeName = store.name;
  const [siteConfig, reviews] = await Promise.all([
    storefrontService.fetchSiteConfig(store.id),
    storefrontService.fetchReviews(store.id, page, REVIEWS_PER_PAGE),
  ]);

  return (
    <div
      className="storefront-classic min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={storeName} />
      <Header storeName={storeName} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title="口コミ" />
        <ReviewsSection reviews={reviews} />
      </main>
      <Footer
        storeName={storeName}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
