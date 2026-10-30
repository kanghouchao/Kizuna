import { notFound } from 'next/navigation';
import { storefrontService } from '../../api/storefront';
import type { StoreSitePageProps } from '../../model/types';
import AgeGate from '../_sections/AgeVerification';
import CastDetailSection from '../_sections/CastDetailSection';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import PageHero from '../_sections/PageHero';
import './theme.css';

interface DefaultCastDetailPageProps extends StoreSitePageProps {
  castId: string;
}

/** キャスト詳細ページ（default 模版）。 */
export default async function DefaultCastDetailPage({ store, castId }: DefaultCastDetailPageProps) {
  const storeName = store.name;
  const [cast, siteConfig] = await Promise.all([
    storefrontService.fetchCast(store.id, castId),
    storefrontService.fetchSiteConfig(store.id),
  ]);

  if (!cast) {
    notFound();
  }

  return (
    <div
      className="storefront-default min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={storeName} />
      <Header storeName={storeName} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title={cast.name ?? ''} />
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
