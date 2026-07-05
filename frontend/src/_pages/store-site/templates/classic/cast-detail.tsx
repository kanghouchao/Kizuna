import { cookies } from 'next/headers';
import { notFound } from 'next/navigation';
import { storefrontService } from '../../api/storefront';
import AgeGate from '../_sections/AgeVerification';
import CastDetailSection from '../_sections/CastDetailSection';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import PageHero from '../_sections/PageHero';
import './theme.css';

interface ClassicCastDetailPageProps {
  castId: string;
}

/** キャスト詳細ページ（classic 模版）。 */
export default async function ClassicCastDetailPage({ castId }: ClassicCastDetailPageProps) {
  const cookieStore = await cookies();
  const tenantName = cookieStore.get('x-mw-tenant-name')?.value || 'Store';
  const [cast, siteConfig] = await Promise.all([
    storefrontService.fetchCast(castId),
    storefrontService.fetchSiteConfig(),
  ]);

  if (!cast) {
    notFound();
  }

  return (
    <div
      className="storefront-classic min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={tenantName} />
      <Header tenantName={tenantName} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title={cast.name} />
        <CastDetailSection cast={cast} />
      </main>
      <Footer
        tenantName={tenantName}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
