import { ClockIcon, FootprintsIcon, MapPinIcon, PhoneIcon, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import type { StoreSitePageProps } from '../../model/types';
import Frame from './Frame';
import { Notice, PageHead, ReserveBand } from './pageParts';

type Row = { label: string; Icon: LucideIcon; tone: string; node: ReactNode; wide?: boolean };

/** 店舗情報・アクセスページ（modern テンプレート）。設定された項目だけを色面の札のタイルで出す。 */
export default function ModernAboutPage({ store, siteConfig }: StoreSitePageProps<'about'>) {
  const { address, phone, business_hours, access_note } = siteConfig;
  const rows: Row[] = [];
  if (address) rows.push({ label: '住所', Icon: MapPinIcon, tone: 'm-bg-peach', node: address });
  if (phone)
    rows.push({
      label: '電話',
      Icon: PhoneIcon,
      tone: 'm-bg-mint',
      node: (
        <a href={`tel:${phone}`} className="m-marker">
          {phone}
        </a>
      ),
    });
  if (business_hours)
    rows.push({ label: '営業時間', Icon: ClockIcon, tone: 'm-bg-yellow', node: business_hours });
  if (access_note)
    rows.push({
      label: 'アクセス',
      Icon: FootprintsIcon,
      tone: 'm-bg-sky',
      node: access_note,
      wide: true,
    });

  return (
    <Frame store={store} siteConfig={siteConfig}>
      <PageHead en="ACCESS" title="店舗情報・アクセス" />
      {rows.length === 0 ? (
        <Notice title="店舗情報は準備中です" />
      ) : (
        <dl className="flex flex-wrap gap-4">
          {rows.map(({ label, Icon, tone, node, wide }) => (
            <div
              key={label}
              className={`m-bg-card m-sticker min-w-0 grow rounded-[24px] p-5 ${wide ? 'basis-full' : 'basis-64'}`}
            >
              <dt className="flex items-center gap-2 text-sm font-bold">
                <span className={`${tone} m-border grid size-9 place-items-center rounded-full`}>
                  <Icon className="size-4" aria-hidden />
                </span>
                {label}
              </dt>
              <dd className="mt-3 text-lg leading-8 font-bold break-words whitespace-pre-line">
                {node}
              </dd>
            </div>
          ))}
        </dl>
      )}
      <ReserveBand lead={`${store.name} へのご予約`} />
    </Frame>
  );
}
