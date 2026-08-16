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
 * classic 模版の店舗トップページ（Server Component）。
 * 既定模版との排布差: キャッチコピー帯を Banner の直上に置き、明るい第一印象を作る。
 */
export default async function ClassicTemplate() {
  const cookieStore = await cookies();
  const storeName = cookieStore.get('x-mw-store-name')?.value || 'Store';

  // テナントIDは service 内部で解決されるため、引数不要
  const { casts, siteConfig } = await storefrontService.getPageData();

  return (
    <div
      className="storefront-classic min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      {/* 年齢確認ゲート（クライアントサイドのフルスクリーンオーバーレイ） */}
      <AgeGate storeName={storeName} />

      <Header storeName={storeName} logoUrl={siteConfig.logo_url} />

      <main className="grow">
        {/* キャッチコピー帯（設定されている場合のみ表示、Banner の直上） */}
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

        <Banner
          storeName={storeName}
          bannerUrl={siteConfig.banner_url}
          description={siteConfig.description}
        />

        <MVSection mvUrl={siteConfig.mv_url} mvType={siteConfig.mv_type} />

        <CastSection casts={casts} />

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
