import type { StoreSitePageProps } from '../../model/types';
import AgeGate from '../_sections/AgeVerification';
import Footer from '../_sections/Footer';
import GuestReservationSection from '../_sections/GuestReservationSection';
import Header from '../_sections/Header';
import PageHero from '../_sections/PageHero';
import './theme.css';

/** ゲスト予約申請ページ（modern テンプレート）。 */
export default function ModernReservationPage({
  store,
  siteConfig,
}: StoreSitePageProps<'reservation'>) {
  return (
    <div
      className="storefront-modern min-h-screen flex flex-col"
      style={{ background: 'var(--storefront-bg)' }}
    >
      <AgeGate storeName={store.name} />
      <Header storeName={store.name} logoUrl={siteConfig.logo_url} />
      <main className="grow">
        <PageHero title="ご予約" subtitle="RESERVATION" />
        <GuestReservationSection storeId={store.id} />
      </main>
      <Footer
        storeName={store.name}
        snsLinks={siteConfig.sns_links}
        partnerLinks={siteConfig.partner_links}
      />
    </div>
  );
}
