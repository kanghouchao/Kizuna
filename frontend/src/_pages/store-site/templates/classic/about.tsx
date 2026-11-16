import type { ReactNode } from 'react';
import type { StoreSitePageProps } from '../../model/types';
import Frame from './Frame';
import Notice from './Notice';
import PageSection from './PageSection';

/** 店舗情報・アクセスページ（classic テンプレート）。設定のある項目だけを罫線の表に並べる。 */
export default function ClassicAboutPage({ store, siteConfig }: StoreSitePageProps<'about'>) {
  const rows: { label: string; node: ReactNode }[] = [];
  if (siteConfig.address) rows.push({ label: '住所', node: siteConfig.address });
  if (siteConfig.phone)
    rows.push({
      label: '電話',
      node: (
        <a href={`tel:${siteConfig.phone}`} className="c-textlink">
          {siteConfig.phone}
        </a>
      ),
    });
  if (siteConfig.business_hours) rows.push({ label: '営業時間', node: siteConfig.business_hours });
  if (siteConfig.access_note) rows.push({ label: 'アクセス', node: siteConfig.access_note });

  return (
    <Frame store={store} siteConfig={siteConfig} current="/about">
      <PageSection title="店舗情報・アクセス">
        {rows.length === 0 ? (
          <Notice message="店舗情報は準備中です" />
        ) : (
          <table className="c-spec c-about">
            <caption>{store.name}</caption>
            <tbody>
              {rows.map(({ label, node }) => (
                <tr key={label}>
                  <th scope="row">{label}</th>
                  <td>{node}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </PageSection>
    </Frame>
  );
}
