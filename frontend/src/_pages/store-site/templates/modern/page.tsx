import { cookies } from 'next/headers';
import { storefrontService } from '../../api/storefront';
import AgeGate from '../_sections/AgeVerification';
import Advertisement from '../_sections/Advertisement';
import Banner from '../_sections/Banner';
import CastSection from '../_sections/CastSection';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import MVSection from '../_sections/MVSection';
import './theme.css';

/**
 * modern 模版のテナントページ (Server Component)
 *
 * 構成（排布差: CastSection を MVSection より先に置きキャストを前面に）:
 * 1. AgeGate    - 年齢確認オーバーレイ（Client Component / localStorage）
 * 2. Header     - スティッキーヘッダー
 * 3. Banner     - フルスクリーンヒーローセクション
 * 4. CastSection - キャスト紹介カルーセル
 * 5. MVSection  - メインビジュアル
 * 6. Advertisement - キャンペーン情報
 * 7. Footer     - フッター
 */
export default async function ModernTemplate() {
  const cookieStore = await cookies();
  const storeName = cookieStore.get('x-mw-store-name')?.value || 'Store';

  // テナントIDは service 内部で解決されるため、引数不要
  const { casts, siteConfig } = await storefrontService.getPageData();

  return (
    <div
      className="storefront-modern min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      {/* 年齢確認ゲート（クライアントサイドのフルスクリーンオーバーレイ） */}
      <AgeGate storeName={storeName} />

      <Header storeName={storeName} logoUrl={siteConfig.logo_url} />

      <main className="grow">
        <Banner
          storeName={storeName}
          bannerUrl={siteConfig.banner_url}
          description={siteConfig.description}
        />

        {/* キャッチコピー帯（設定されている場合のみ表示） */}
        {siteConfig.catch_copy && (
          <section
            className="py-10 md:py-14 px-5 sm:px-6 text-center"
            style={{ background: 'var(--storefront-bg)' }}
          >
            <p
              className="max-w-3xl mx-auto text-[var(--storefront-accent)] text-lg md:text-2xl font-light tracking-[0.2em] leading-relaxed whitespace-pre-line"
              style={{ fontFamily: 'var(--storefront-font-display)' }}
            >
              {siteConfig.catch_copy}
            </p>
          </section>
        )}

        <CastSection casts={casts} />

        <MVSection mvUrl={siteConfig.mv_url} mvType={siteConfig.mv_type} />

        <Advertisement />
      </main>

      <Footer
        storeName={storeName}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
