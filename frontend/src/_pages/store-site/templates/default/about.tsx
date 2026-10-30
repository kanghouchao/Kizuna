import { storefrontService } from '../../api/storefront';
import type { StoreSitePageProps } from '../../model/types';
import AgeGate from '../_sections/AgeVerification';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import PageHero from '../_sections/PageHero';
import StoreInfoSection from '../_sections/StoreInfoSection';
import './theme.css';

/** 店舗情報・アクセスページ（default 模版）。 */
export default async function DefaultAboutPage({ store }: StoreSitePageProps) {
  const storeName = store.name;
  const siteConfig = await storefrontService.fetchSiteConfig(store.id);

  return (
    <div
      className="storefront-default min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={storeName} />
      <Header storeName={storeName} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title="店舗情報・アクセス" />
        <StoreInfoSection
          siteConfig={siteConfig}
          accessNote={siteConfig.custom_texts?.['access_note']}
        />
      </main>
      <Footer
        storeName={storeName}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
