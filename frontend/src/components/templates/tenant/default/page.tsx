import { cookies } from 'next/headers';

import Advertisement from './Advertisement';
import Banner from './Banner';
import CastSection from './CastSection';
import Footer from './Footer';
import Header from './Header';
import MVSection from './MVSection';

interface Cast {
  id: string;
  name: string;
  photo_url?: string;
  age?: number;
  height?: number;
  bust?: number;
  waist?: number;
  hip?: number;
}

async function fetchCasts(tenantId: string): Promise<Cast[]> {
  try {
    const backendUrl =
      process.env.TENANT_VALIDATION_API_URL?.replace('/central/tenant', '') ||
      'http://backend:8080';
    const response = await fetch(`${backendUrl}/tenant/casts/public`, {
      headers: {
        'X-Role': 'tenant',
        'X-Tenant-ID': tenantId,
      },
      next: { revalidate: 60 },
    });
    if (!response.ok) return [];
    return await response.json();
  } catch {
    return [];
  }
}

export default async function Page() {
  const cookieStore = await cookies();
  const tenantName = cookieStore.get('x-mw-tenant-name')?.value || 'Store';
  const tenantId = cookieStore.get('x-mw-tenant-id')?.value || '';

  // キャストデータを取得
  const casts = tenantId ? await fetchCasts(tenantId) : [];

  // TODO: サイト設定をAPIから取得（バックエンド準備完了後）
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
