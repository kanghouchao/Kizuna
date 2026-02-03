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
    logoUrl: undefined,
    bannerUrl: undefined,
    description: undefined,
    mvUrl: undefined,
    mvType: 'image' as const,
    snsLinks: undefined,
    partnerLinks: undefined,
  };

  return (
    <div className="min-h-screen flex flex-col">
      <Header tenantName={tenantName} logoUrl={siteConfig.logoUrl} />

      <main className="flex-grow">
        <Banner
          tenantName={tenantName}
          bannerUrl={siteConfig.bannerUrl}
          description={siteConfig.description}
        />

        <MVSection mvUrl={siteConfig.mvUrl} mvType={siteConfig.mvType} />

        <CastSection />

        <Advertisement />
      </main>

      <Footer
        tenantName={tenantName}
        snsLinks={siteConfig.snsLinks}
        partnerLinks={siteConfig.partnerLinks}
      />
    </div>
  );
}
