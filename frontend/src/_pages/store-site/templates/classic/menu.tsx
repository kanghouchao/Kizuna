import Link from 'next/link';
import type { StoreSitePageProps } from '../../model/types';
import Frame from './Frame';
import Notice from './Notice';
import PageSection from './PageSection';
import { pricingRows } from './pricing';

/** 料金ページ（classic テンプレート）。説明文の一行を品書きの一行にして、罫線の表で読ませる。 */
export default function ClassicMenuPage({ store, siteConfig }: StoreSitePageProps<'menu'>) {
  const rows = pricingRows(siteConfig.pricing_description ?? '');

  return (
    <Frame store={store} siteConfig={siteConfig} current="/menu">
      <PageSection title="料金">
        {rows.length === 0 ? (
          <Notice message="料金情報は準備中です" />
        ) : (
          <>
            <table className="c-spec c-price">
              <caption>品書き</caption>
              <tbody>
                {rows.map((row, index) => (
                  <tr key={index}>
                    {row.price ? (
                      <>
                        <th scope="row">{row.item}</th>
                        <td>{row.price}</td>
                      </>
                    ) : (
                      <td colSpan={2} className="c-price-note">
                        {row.item}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="c-after">
              <Link href="/reservation" className="c-textlink">
                ご予約はこちら
              </Link>
            </div>
          </>
        )}
      </PageSection>
    </Frame>
  );
}
