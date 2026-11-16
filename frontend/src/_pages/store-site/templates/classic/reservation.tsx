import type { StoreSitePageProps } from '../../model/types';
import Frame from './Frame';
import MemberReservationLink from './MemberReservationLink';
import PageSection from './PageSection';
import ReservationForm from './ReservationForm';

/** ゲスト予約申請ページ（classic テンプレート）。 */
export default function ClassicReservationPage({
  store,
  siteConfig,
}: StoreSitePageProps<'reservation'>) {
  return (
    <Frame store={store} siteConfig={siteConfig} current="/reservation">
      <PageSection title="ご予約">
        <ReservationForm storeId={store.id} />
        <div className="c-after">
          <MemberReservationLink className="c-textlink">会員の方は会員予約へ</MemberReservationLink>
        </div>
      </PageSection>
    </Frame>
  );
}
