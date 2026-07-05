import { cookies } from 'next/headers';
import { storefrontService } from '../../api/storefront';
import AgeGate from '../_sections/AgeVerification';
import CastGrid from '../_sections/CastGrid';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import PageHero from '../_sections/PageHero';
import './theme.css';

/** キャスト一覧ページ（default 模版）。 */
export default async function DefaultCastsPage() {
  const cookieStore = await cookies();
  const tenantName = cookieStore.get('x-mw-tenant-name')?.value || 'Store';
  const { casts, siteConfig } = await storefrontService.getPageData();

  return (
    <div
      className="storefront-default min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={tenantName} />
      <Header tenantName={tenantName} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title="キャスト一覧" />
        <CastGrid casts={casts} />
      </main>
      <Footer
        tenantName={tenantName}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
