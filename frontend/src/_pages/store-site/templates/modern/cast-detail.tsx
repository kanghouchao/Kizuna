import { cookies } from 'next/headers';
import { notFound } from 'next/navigation';
import { storefrontService } from '../../api/storefront';
import AgeGate from '../_sections/AgeVerification';
import CastDetailSection from '../_sections/CastDetailSection';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import PageHero from '../_sections/PageHero';
import './theme.css';

interface ModernCastDetailPageProps {
  castId: string;
}

/** キャスト詳細ページ（modern 模版）。 */
export default async function ModernCastDetailPage({ castId }: ModernCastDetailPageProps) {
  const cookieStore = await cookies();
  const storeName = cookieStore.get('x-mw-store-name')?.value || 'Store';
  const [cast, siteConfig] = await Promise.all([
    storefrontService.fetchCast(castId),
    storefrontService.fetchSiteConfig(),
  ]);

  if (!cast) {
    notFound();
  }

  return (
    <div
      className="storefront-modern min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={storeName} />
      <Header storeName={storeName} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title={cast.name} />
        <CastDetailSection cast={cast} />
      </main>
      <Footer
        storeName={storeName}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
