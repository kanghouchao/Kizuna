import { storefrontService } from '../../api/storefront';
import type { StoreSitePageProps } from '../../model/types';
import AgeGate from '../_sections/AgeVerification';
import Advertisement from '../_sections/Advertisement';
import Banner from '../_sections/Banner';
import CastSection from '../_sections/CastSection';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import MVSection from '../_sections/MVSection';
import './theme.css';

/** 既定模版の店舗トップページ（Server Component）。 */
export default async function DefaultTemplate({ store }: StoreSitePageProps) {
  const storeName = store.name;

  const { casts, siteConfig } = await storefrontService.getPageData(store.id);

  return (
    <div
      className="storefront-default min-h-screen flex flex-col"
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
