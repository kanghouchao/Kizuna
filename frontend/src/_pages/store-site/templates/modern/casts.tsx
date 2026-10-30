import { storefrontService } from '../../api/storefront';
import type { StoreSitePageProps } from '../../model/types';
import AgeGate from '../_sections/AgeVerification';
import CastGrid from '../_sections/CastGrid';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import PageHero from '../_sections/PageHero';
import './theme.css';

/** キャスト一覧ページ（modern 模版）。 */
export default async function ModernCastsPage({ store }: StoreSitePageProps) {
  const storeName = store.name;
  const { casts, siteConfig } = await storefrontService.getPageData(store.id);

  return (
    <div
      className="storefront-modern min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={storeName} />
      <Header storeName={storeName} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title="キャスト一覧" />
        <CastGrid casts={casts} />
      </main>
      <Footer
        storeName={storeName}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
