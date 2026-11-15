import type { StoreSitePageProps } from '../../model/types';
import AgeGate from '../_sections/AgeVerification';
import CastDetailSection from '../_sections/CastDetailSection';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import PageHero from '../_sections/PageHero';
import './theme.css';

/** キャスト詳細ページ（default テンプレート）。 */
export default function DefaultCastDetailPage({
  store,
  siteConfig,
  cast,
}: StoreSitePageProps<'cast-detail'>) {
  return (
    <div
      className="storefront-default min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={store.name} />
      <Header storeName={store.name} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title={cast.name ?? ''} />
        <CastDetailSection cast={cast} />
      </main>
      <Footer
        storeName={store.name}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
