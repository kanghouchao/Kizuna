import type { StoreSitePageProps } from '../../model/types';
import AgeGate from '../_sections/AgeVerification';
import CastGrid from '../_sections/CastGrid';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import PageHero from '../_sections/PageHero';
import './theme.css';

/** キャスト一覧ページ（modern テンプレート）。 */
export default function ModernCastsPage({ store, siteConfig, casts }: StoreSitePageProps<'casts'>) {
  return (
    <div
      className="storefront-modern min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={store.name} />
      <Header storeName={store.name} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title="キャスト一覧" />
        <CastGrid casts={casts} />
      </main>
      <Footer
        storeName={store.name}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
