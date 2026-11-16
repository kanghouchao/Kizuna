import type { StoreSitePageProps } from '../../model/types';
import { priceBlocks } from './format';
import Frame from './Frame';
import { Notice } from './Paging';

/** 料金ページ（default テンプレート）。 */
export default function DefaultMenuPage({ store, siteConfig }: StoreSitePageProps<'menu'>) {
  const blocks = priceBlocks(siteConfig.pricing_description ?? '');

  return (
    <Frame store={store} siteConfig={siteConfig}>
      <header className="sfd-pagehead">
        <p className="sfd-label">System</p>
        <h1 className="sfd-h1">料金</h1>
      </header>

      <div className="sfd-page">
        {blocks.length === 0 ? (
          <Notice message="料金情報は準備中です" />
        ) : (
          blocks.map((block, i) =>
            block.kind === 'table' ? (
              <table key={i} className="sfd-price" aria-label="料金表">
                <tbody>
                  {block.rows.map((row, j) => (
                    <tr key={j}>
                      <th scope="row">{row.item}</th>
                      <td>{row.price}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <p key={i} className="sfd-price-note">
                {block.lines.join('\n')}
              </p>
            )
          )
        )}
      </div>
    </Frame>
  );
}
