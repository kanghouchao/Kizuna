import type { ReactNode } from 'react';
import type { StoreSitePageProps } from '../../model/types';
import Frame from './Frame';
import { Notice } from './Paging';

/** 店舗情報・アクセスページ（default テンプレート）。値の無い項目は行ごと出さない。 */
export default function DefaultAboutPage({ store, siteConfig }: StoreSitePageProps<'about'>) {
  const rows: { label: string; value: ReactNode }[] = [];
  if (siteConfig.address) rows.push({ label: '住所', value: siteConfig.address });
  if (siteConfig.phone)
    rows.push({
      label: '電話',
      value: (
        <a href={`tel:${siteConfig.phone}`} className="sfd-tel">
          {siteConfig.phone}
        </a>
      ),
    });
  if (siteConfig.business_hours) rows.push({ label: '営業時間', value: siteConfig.business_hours });
  if (siteConfig.access_note) rows.push({ label: 'アクセス', value: siteConfig.access_note });

  return (
    <Frame store={store} siteConfig={siteConfig}>
      <header className="sfd-pagehead">
        <p className="sfd-label">About</p>
        <h1 className="sfd-h1">店舗情報・アクセス</h1>
      </header>

      <div className="sfd-page">
        {rows.length === 0 ? (
          <Notice message="店舗情報は準備中です" />
        ) : (
          <dl className="sfd-rows sfd-rows-page">
            {rows.map(row => (
              <div key={row.label} className="sfd-row">
                <dt>{row.label}</dt>
                <dd>{row.value}</dd>
              </div>
            ))}
          </dl>
        )}
      </div>
    </Frame>
  );
}
