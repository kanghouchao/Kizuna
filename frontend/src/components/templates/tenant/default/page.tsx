import { cookies } from 'next/headers';

import Advertisement from './Advertisement';
import Banner from './Banner';
import CastSection from './CastSection';
import Footer from './Footer';
import Header from './Header';
import MVSection from './MVSection';

export default async function Page() {
  const cookieStore = await cookies();
  const tenantName = cookieStore.get('x-mw-tenant-name')?.value || 'Store';

  // TODO: Fetch site config from API when backend is ready
  // For now, using static placeholder content
  const siteConfig = {
    logo_url: undefined,
    banner_url: undefined,
    description: undefined,
    mv_url: undefined,
    mv_type: 'image' as const,
    sns_links: undefined,
    partner_links: undefined,
  };

  return (
    <div className="min-h-screen flex flex-col">
      <Header tenantName={tenantName} logoUrl={siteConfig.logo_url} />

      <main className="flex-grow">
        <Banner
          tenantName={tenantName}
          bannerUrl={siteConfig.banner_url}
          description={siteConfig.description}
        />

        <MVSection mvUrl={siteConfig.mv_url} mvType={siteConfig.mv_type} />

        <CastSection />

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
