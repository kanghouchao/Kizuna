import { cookies } from 'next/headers';
import { storefrontService } from '../../api/storefront';
import AgeGate from './AgeVerification';
import Advertisement from './Advertisement';
import Banner from './Banner';
import CastSection from './CastSection';
import Footer from './Footer';
import Header from './Header';
import MVSection from './MVSection';

/**
 * デフォルトのテナントページ模版 (Server Component)
 *
 * 構成:
 * 1. AgeGate    - 年齢確認オーバーレイ（Client Component / localStorage）
 * 2. Header     - スティッキーヘッダー
 * 3. Banner     - フルスクリーンヒーローセクション
 * 4. MVSection  - メインビジュアル
 * 5. CastSection - キャスト紹介カルーセル
 * 6. Advertisement - キャンペーン情報
 * 7. Footer     - フッター
 */
export default async function DefaultTemplate() {
  const cookieStore = await cookies();
  const tenantName = cookieStore.get('x-mw-tenant-name')?.value || 'Store';

  // テナントIDは service 内部で解決されるため、引数不要
  const { casts, siteConfig } = await storefrontService.getPageData();

  return (
    <div className="min-h-screen flex flex-col" style={{ background: '#080808' }}>
      {/* 年齢確認ゲート（クライアントサイドのフルスクリーンオーバーレイ） */}
      <AgeGate storeName={tenantName} />

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
