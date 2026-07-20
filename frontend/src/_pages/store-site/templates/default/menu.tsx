import { cookies } from 'next/headers';
import { storefrontService } from '../../api/storefront';
import AgeGate from '../_sections/AgeVerification';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import PageHero from '../_sections/PageHero';
import PricingSection from '../_sections/PricingSection';
import './theme.css';

/** 料金ページ（default 模版）。 */
export default async function DefaultMenuPage() {
  const cookieStore = await cookies();
  const tenantName = cookieStore.get('x-mw-store-name')?.value || 'Store';
  const siteConfig = await storefrontService.fetchSiteConfig();

  return (
    <div
      className="storefront-default min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={tenantName} />
      <Header tenantName={tenantName} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title="料金" />
        <PricingSection pricingDescription={siteConfig.pricing_description} />
      </main>
      <Footer
        tenantName={tenantName}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
