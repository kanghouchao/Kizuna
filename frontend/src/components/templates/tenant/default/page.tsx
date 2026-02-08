import { cookies } from 'next/headers';
import { notFound } from 'next/navigation';
import { storefrontService } from '@/services/storefront';
import Advertisement from './Advertisement';
import Banner from './Banner';
import CastSection from './CastSection';
import Footer from './Footer';
import Header from './Header';
import MVSection from './MVSection';

/**
 * デフォルトのテナントページ模版 (Server Component)
 *
 * 役割:
 * - クッキーからテナント情報を取得し、必要なデータをサービス層から取得する。
 * - 取得したデータを各セクションコンポーネントに配布する。
 */
export default async function DefaultTemplate() {
  const cookieStore = await cookies();
  const tenantId = cookieStore.get('x-mw-tenant-id')?.value;
  const tenantName = cookieStore.get('x-mw-tenant-name')?.value || 'Store';

  if (!tenantId) {
    notFound();
  }

  // この模版に必要なデータを取得
  const { casts, siteConfig } = await storefrontService.getPageData(tenantId);

  return (
    <div className="min-h-screen flex flex-col">
      {/* ヘッダー部分 */}
      <Header tenantName={tenantName} logoUrl={siteConfig.logo_url} />

      <main className="grow">
        {/* バナー部分 */}
        <Banner
          tenantName={tenantName}
          bannerUrl={siteConfig.banner_url}
          description={siteConfig.description}
        />

        {/* メインビジュアル部分 */}
        <MVSection mvUrl={siteConfig.mv_url} mvType={siteConfig.mv_type} />

        {/* キャスト一覧部分 */}
        <CastSection casts={casts} />

        {/* 広告部分 */}
        <Advertisement />
      </main>

      {/* フッター部分 */}
      <Footer
        tenantName={tenantName}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
