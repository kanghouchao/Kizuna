import { storefrontService } from '../../api/storefront';
import type { StoreSitePageProps } from '../../model/types';
import AgeGate from '../_sections/AgeVerification';
import Footer from '../_sections/Footer';
import Header from '../_sections/Header';
import PageHero from '../_sections/PageHero';
import ScheduleSection from '../_sections/ScheduleSection';
import './theme.css';

/** 出勤表ページ（default 模版）。 */
export default async function DefaultSchedulePage({ store }: StoreSitePageProps) {
  const storeName = store.name;
  const [siteConfig, shifts] = await Promise.all([
    storefrontService.fetchSiteConfig(store.id),
    storefrontService.fetchShifts(store.id),
  ]);

  return (
    <div
      className="storefront-default min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={storeName} />
      <Header storeName={storeName} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title="出勤表" />
        <ScheduleSection shifts={shifts} />
      </main>
      <Footer
        storeName={storeName}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
