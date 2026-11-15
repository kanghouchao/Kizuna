import type { StoreSitePageProps } from '../../model/types';
import AgeGate from '../_sections/AgeVerification';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import PageHero from '../_sections/PageHero';
import ReviewsSection from '../_sections/ReviewsSection';
import './theme.css';

/** 口コミページ（classic テンプレート）。 */
export default function ClassicReviewsPage({
  store,
  siteConfig,
  reviews,
  page,
}: StoreSitePageProps<'reviews'>) {
  return (
    <div
      className="storefront-classic min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={store.name} />
      <Header storeName={store.name} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title="口コミ" />
        <ReviewsSection reviews={reviews} page={page} />
      </main>
      <Footer
        storeName={store.name}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
