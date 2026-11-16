import type { StoreSitePageProps } from '../../model/types';
import Frame from './Frame';
import ReservationForm from './ReservationForm';

/** ゲスト予約申請ページ（default テンプレート）。 */
export default function DefaultReservationPage({
  store,
  siteConfig,
}: StoreSitePageProps<'reservation'>) {
  return (
    <Frame store={store} siteConfig={siteConfig}>
      <header className="sfd-pagehead">
        <p className="sfd-label">Reservation</p>
        <h1 className="sfd-h1">ご予約</h1>
      </header>

      <div className="sfd-page">
        <ReservationForm storeId={store.id} />
      </div>
    </Frame>
  );
}
