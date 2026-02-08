import { cookies } from 'next/headers';
import { storefrontService } from '@/services/storefront';
import Advertisement from './Advertisement';
import Banner from './Banner';
import CastSection from './CastSection';
import Footer from './Footer';
import Header from './Header';
import MVSection from './MVSection';

/**
 * デフォルトのテナントページ模版 (Server Component)
 */
export default async function DefaultTemplate() {
  const cookieStore = await cookies();
  const tenantName = cookieStore.get('x-mw-tenant-name')?.value || 'Store';

  // テナントIDは service 内部で解決されるため、引数不要
  const { casts, siteConfig } = await storefrontService.getPageData();

  return (
    <div className="min-h-screen flex flex-col">
      <Header tenantName={tenantName} logoUrl={siteConfig.logo_url} />

      <main className="grow">
        <Banner
          tenantName={tenantName}
          bannerUrl={siteConfig.banner_url}
          description={siteConfig.description}
        />

        <MVSection mvUrl={siteConfig.mv_url} mvType={siteConfig.mv_type} />

        <CastSection casts={casts} />

        <Advertisement />
      </main>

      <Footer
        tenantName={tenantName}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
