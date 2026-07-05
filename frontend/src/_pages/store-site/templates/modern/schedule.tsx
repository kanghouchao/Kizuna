import { cookies } from 'next/headers';
import { storefrontService } from '../../api/storefront';
import AgeGate from '../_sections/AgeVerification';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import PageHero from '../_sections/PageHero';
import SchedulePlaceholder from '../_sections/SchedulePlaceholder';
import './theme.css';

/** 出勤表ページ（modern 模版）。 */
export default async function ModernSchedulePage() {
  const cookieStore = await cookies();
  const tenantName = cookieStore.get('x-mw-tenant-name')?.value || 'Store';
  const siteConfig = await storefrontService.fetchSiteConfig();

  return (
    <div
      className="storefront-modern min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={tenantName} />
      <Header tenantName={tenantName} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title="出勤表" />
        <SchedulePlaceholder />
      </main>
      <Footer
        tenantName={tenantName}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
