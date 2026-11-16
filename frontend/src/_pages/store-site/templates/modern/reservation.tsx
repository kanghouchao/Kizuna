import type { StoreSitePageProps } from '../../model/types';
import Frame from './Frame';
import { PageHead } from './pageParts';
import ReservationForm from './ReservationForm';

const STEPS = [
  ['ご希望を送る', 'ご希望の内容とご連絡先をフォームからお送りください。'],
  ['店舗からご連絡', '店舗より折り返しご連絡し、内容を確認します。'],
  ['予約の確定', '確認が済んだ時点で予約が確定します。'],
] as const;

const STEP_TONES = ['m-bg-yellow', 'm-bg-mint', 'm-bg-sky'];

/** ゲスト予約申請ページ（modern テンプレート）。流れの説明と申請フォームを並べる。 */
export default function ModernReservationPage({
  store,
  siteConfig,
}: StoreSitePageProps<'reservation'>) {
  return (
    <Frame store={store} siteConfig={siteConfig}>
      <PageHead en="RESERVATION" title="ご予約" />
      <div className="grid gap-8 lg:grid-cols-[minmax(0,4fr)_minmax(0,7fr)] lg:gap-12">
        <ol className="space-y-3 lg:sticky lg:top-28 lg:self-start">
          {STEPS.map(([title, text], i) => (
            <li key={title} className="m-bg-card m-sticker flex gap-3 rounded-[20px] p-4">
              <span
                aria-hidden
                className={`${STEP_TONES[i]} m-border m-display grid size-9 shrink-0 place-items-center rounded-full`}
              >
                {i + 1}
              </span>
              <span className="min-w-0">
                <span className="m-display block text-base">{title}</span>
                <span className="m-sub text-sm">{text}</span>
              </span>
            </li>
          ))}
        </ol>
        <ReservationForm storeId={store.id} />
      </div>
    </Frame>
  );
}
