import { cookies } from 'next/headers';
import { storefrontService } from '../../api/storefront';
import AgeGate from '../_sections/AgeVerification';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import PageHero from '../_sections/PageHero';
import PricingSection from '../_sections/PricingSection';
import './theme.css';

/** 料金ページ（classic 模版）。 */
export default async function ClassicMenuPage() {
  const cookieStore = await cookies();
  const storeName = cookieStore.get('x-mw-store-name')?.value || 'Store';
  const siteConfig = await storefrontService.fetchSiteConfig();

  return (
    <div
      className="storefront-classic min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={storeName} />
      <Header storeName={storeName} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title="料金" />
        <PricingSection pricingDescription={siteConfig.pricing_description} />
      </main>
      <Footer
        storeName={storeName}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
