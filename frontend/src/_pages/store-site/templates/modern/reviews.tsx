import { pageHref } from '../../lib/paging';
import type { StoreSitePageProps } from '../../model/types';
import { toneAt } from './castParts';
import Frame from './Frame';
import { Notice, PageHead, Pager } from './pageParts';

const BASE_PATH = '/reviews';

// 店舗の現地日付をそのまま読む。Date を通すと閲覧する端末の時間帯で日がずれる
function receivedOn(value: string): string {
  const [y, m, d] = value.split('-').map(Number);
  return `${y}年${m}月${d}日`;
}

/** 口コミページ（modern テンプレート）。吹き出しの形のカードを二列に流す。本文は文字のまま、改行を保って出す。 */
export default function ModernReviewsPage({
  store,
  siteConfig,
  reviews,
  page,
}: StoreSitePageProps<'reviews'>) {
  return (
    <Frame store={store} siteConfig={siteConfig}>
      <PageHead
        en="VOICE"
        title="口コミ"
        chip={reviews && reviews.total_elements > 0 ? `全 ${reviews.total_elements}件` : undefined}
      />
      {reviews === null ? (
        <Notice
          alert
          title="口コミを読み込めませんでした"
          link={{ href: pageHref(BASE_PATH, page), label: '再読み込み' }}
        />
      ) : reviews.content.length === 0 ? (
        // 掲出が減ってページ数が縮むと、開いていたページだけが空になる
        reviews.total_elements > 0 ? (
          <Notice
            title="このページに口コミはありません"
            link={{ href: pageHref(BASE_PATH, 0), label: '最初のページへ' }}
          />
        ) : (
          <Notice title="掲載中の口コミはありません" />
        )
      ) : (
        <>
          <ul className="gap-5 md:columns-2">
            {reviews.content.map((review, i) => {
              const name = review.display_name || '匿名';
              return (
                <li key={`${reviews.number}-${i}`} className="mb-5 break-inside-avoid pr-1 pb-1">
                  <article>
                    <p className="m-bg-card m-sticker rounded-[24px] rounded-bl-md p-5 text-[15px] leading-8 break-words whitespace-pre-wrap">
                      {review.body}
                    </p>
                    <p className="mt-3 flex items-center gap-2.5 pl-1 text-sm">
                      <span
                        aria-hidden
                        className={`${toneAt(i)} m-border m-display grid size-9 shrink-0 place-items-center rounded-full text-base`}
                      >
                        {Array.from(name)[0]}
                      </span>
                      <span className="min-w-0 font-bold break-words">{name}</span>
                      <time dateTime={review.received_on} className="m-sub shrink-0">
                        {receivedOn(review.received_on)}
                      </time>
                    </p>
                  </article>
                </li>
              );
            })}
          </ul>
          {reviews.total_pages > 1 && <Pager basePath={BASE_PATH} result={reviews} />}
        </>
      )}
    </Frame>
  );
}
