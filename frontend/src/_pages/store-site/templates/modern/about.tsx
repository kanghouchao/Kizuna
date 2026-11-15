import type { StoreSitePageProps } from '../../model/types';
import AgeGate from '../_sections/AgeVerification';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import PageHero from '../_sections/PageHero';
import StoreInfoSection from '../_sections/StoreInfoSection';
import './theme.css';

/** 店舗情報・アクセスページ（modern テンプレート）。 */
export default function ModernAboutPage({ store, siteConfig }: StoreSitePageProps<'about'>) {
  return (
    <div
      className="storefront-modern min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={store.name} />
      <Header storeName={store.name} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title="店舗情報・アクセス" />
        <StoreInfoSection siteConfig={siteConfig} />
      </main>
      <Footer
        storeName={store.name}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
