import type { StoreSitePageProps } from '../../model/types';
import Frame from './Frame';
import { Notice, PageHead, ReserveBand } from './pageParts';

type Block = { rows: [string, string][] } | { text: string };

// 行末の語が金額（数字と通貨の印）なら「項目 | 金額」の行、それ以外は書いたままの文章として扱う
const PRICE_ROW = /^(.*\S)[ \t\u3000]+(\S*[0-9０-９]\S*)$/;
const CURRENCY = /[円¥￥]/;

/** 料金の説明文を、順序を保ったまま表と文章の塊に分ける。どの行も落とさない。 */
function blocksOf(description: string): Block[] {
  const blocks: Block[] = [];
  for (const line of description.split(/\r?\n/)) {
    const match = PRICE_ROW.exec(line.trim());
    const last = blocks.at(-1);
    if (match && CURRENCY.test(match[2])) {
      if (last && 'rows' in last) last.rows.push([match[1], match[2]]);
      else blocks.push({ rows: [[match[1], match[2]]] });
    } else if (last && 'text' in last) {
      last.text += `\n${line}`;
    } else {
      blocks.push({ text: line });
    }
  }
  return blocks.filter(block => 'rows' in block || block.text.trim() !== '');
}

/** 料金ページ（modern テンプレート）。金額で終わる行を表に組み、それ以外の文章は書いたとおりに出す。 */
export default function ModernMenuPage({ store, siteConfig }: StoreSitePageProps<'menu'>) {
  const blocks = blocksOf(siteConfig.pricing_description ?? '');

  return (
    <Frame store={store} siteConfig={siteConfig}>
      <PageHead en="SYSTEM" title="料金" />
      {blocks.length === 0 ? (
        <Notice title="料金情報は準備中です" />
      ) : (
        <div className="mx-auto max-w-3xl space-y-5">
          {blocks.map((block, i) =>
            'rows' in block ? (
              <div
                key={i}
                className="m-bg-card m-sticker overflow-hidden rounded-[var(--m-radius)]"
              >
                <table className="w-full border-collapse text-left">
                  <caption className="sr-only">料金表</caption>
                  <thead className="sr-only">
                    <tr>
                      <th scope="col">項目</th>
                      <th scope="col">料金</th>
                    </tr>
                  </thead>
                  <tbody>
                    {block.rows.map(([label, price], row) => (
                      <tr
                        key={row}
                        className="border-b-2 border-dashed border-[var(--m-line)] last:border-b-0"
                      >
                        <th
                          scope="row"
                          className="w-1/2 px-5 py-4 text-[15px] font-bold break-words"
                        >
                          {label}
                        </th>
                        <td className="py-4 pr-5 text-right align-middle">
                          <span className="m-bg-yellow m-display inline-block rounded-[18px] px-3 py-1 text-lg sm:text-xl">
                            {price}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p
                key={i}
                className="m-bg-card rounded-[var(--m-radius)] border-2 border-[var(--m-line)] p-6 text-[15px] leading-8 break-words whitespace-pre-line"
              >
                {block.text.replace(/^\n+|\n+$/g, '')}
              </p>
            )
          )}
          <ReserveBand lead="ご希望の内容を送るだけ" />
        </div>
      )}
    </Frame>
  );
}
